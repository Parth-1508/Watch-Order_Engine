package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.gemini.GeminiResult
import com.example.watchorderengine.network.gemini.GeminiService
import com.example.watchorderengine.network.model.TmdbWatchProvider
import com.example.watchorderengine.network.model.TmdbWatchProviderCountry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaRepository"

@Singleton
class MediaRepository @Inject constructor(
    private val db: WatchOrderDatabase,
    private val moshi: Moshi,
    private val apiService: TmdbApiService,
    private val geminiService: GeminiService,
    private val watchOrderRepository: WatchOrderRepository
) {
    // ─── ID helpers ───────────────────────────────────────────────────────────

    /**
     * Builds a collision-free media ID using the TMDB media type as a prefix.
     *
     * TMDB uses completely independent ID spaces for movies and TV shows, so
     * movie #10193 ("Toy Story 3") and TV show #10193 ("Sorority Life") are
     * entirely different entities that happen to share a numeric ID.
     * Prefixing with "_m_" vs "_t_" ensures they are never stored at the same
     * Room primary key.
     */
    private fun buildMediaId(tmdbId: Int, mediaType: String?): String {
        val prefix = when (mediaType?.lowercase()) {
            "movie" -> "tmdb_m_"
            "tv"    -> "tmdb_t_"
            else    -> "tmdb_"   // fallback — only used when type is truly unknown
        }
        return "$prefix$tmdbId"
    }

    /**
     * Extracts the raw TMDB numeric ID from any ID format:
     *   "tmdb_m_10193" → 10193
     *   "tmdb_t_10193" → 10193
     *   "tmdb_10193"   → 10193  (legacy untyped format)
     *   "10193"        → 10193  (very old format)
     */
    private fun extractTmdbId(mediaId: String): Int? =
        mediaId.substringAfterLast("_").toIntOrNull()

    private fun isMovieId(mediaId: String): Boolean = mediaId.contains("_m_")
    private fun isTvId(mediaId: String):    Boolean = mediaId.contains("_t_")

    fun findUniversesForMedia(tmdbId: Int) = watchOrderRepository.findUniversesForMedia(tmdbId)

    suspend fun backfillMissingUniversePosters(universes: List<Universe>) {
        withContext(Dispatchers.IO) {
            universes.filter { it.posterUrl.isNullOrBlank() && it.bannerUrl.isNullOrBlank() }
                .forEach { universe ->
                    try {
                        val firstNode = watchOrderRepository.getNodes(universe.id).first().firstOrNull()
                        val tmdbId = firstNode?.tmdb_id ?: return@forEach
                        if (tmdbId <= 0) return@forEach
                        val isMovie = firstNode.tmdb_media_type == "movie"
                        val response = if (isMovie) apiService.getMovie(tmdbId) else apiService.getTvShow(tmdbId)
                        if (!response.isSuccessful) return@forEach
                        val posterUrl = TmdbConfig.buildImageUrl(response.body()?.posterPath) ?: return@forEach
                        watchOrderRepository.updateUniversePoster(universe.id, posterUrl)
                    } catch (e: Exception) {
                        Log.w(TAG, "Poster backfill failed for universe ${universe.id}: ${e.message}")
                    }
                }
        }
    }

    private val castType    = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }

    // ─── Media Detail flow ────────────────────────────────────────────────────

    fun getMediaDetailFlow(mediaId: String): Flow<MediaDetail?> = flow {
        // Emit cached entity first for instant display (may be null on first visit).
        val cached = buildMediaDetail(mediaId)
        emit(cached)

        // Always refresh from TMDB; if refresh wrote new data OR nothing was cached,
        // emit again so the UI gets the correct/complete version.
        val refreshed = refreshDetail(mediaId)
        if (refreshed || cached == null) {
            emit(buildMediaDetail(mediaId))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun refreshDetail(mediaId: String): Boolean {
        val tmdbId = extractTmdbId(mediaId) ?: return false
        // Intentionally only look up the exact ID here, not legacy fallbacks.
        // buildMediaDetail uses the fallback lookup; refreshDetail writes to
        // the canonical typed-prefix ID, keeping the two concerns separate.
        val cachedEntity = db.mediaDao().getById(mediaId)

        return when (cachedEntity?.mediaCategory) {
            "MOVIE"   -> fetchAndCacheMovie(tmdbId, mediaId)
            "TV_SHOW",
            "ANIME"   -> fetchAndCacheTv(tmdbId, mediaId)
            else -> {
                // Entity not yet in DB (first visit) or unknown category.
                // Route by the typed prefix first; fall back to trying both.
                when {
                    isMovieId(mediaId) -> fetchAndCacheMovie(tmdbId, mediaId)
                    isTvId(mediaId)    -> fetchAndCacheTv(tmdbId, mediaId)
                    else -> fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
                }
            }
        }
    }

    private suspend fun buildMediaDetail(mediaId: String): MediaDetail? {
        val tmdbId = extractTmdbId(mediaId)

        // FIX: type-safe lookup — NEVER use the untyped "tmdb_{id}" key.
        // Type-safe fallback: filter by category so movie #20 never
        // collides with TV show #20 ("half the time" navigation bug).
        val typedCategories = when {
            isMovieId(mediaId) -> listOf("MOVIE")
            isTvId(mediaId)    -> listOf("TV_SHOW", "ANIME")
            else               -> listOf("MOVIE", "TV_SHOW", "ANIME")
        }
        val entity = db.mediaDao().getById(mediaId)
            ?: tmdbId?.let { db.mediaDao().getByTmdbIdAndCategory(it, typedCategories) }
            ?: tmdbId?.let { 
                // Legacy Fallback for very old IDs
                db.mediaDao().getById("tmdb_$tmdbId") ?: db.mediaDao().getById(tmdbId.toString())
            }

        if (entity == null) {
            Log.d(TAG, "buildMediaDetail: no entity for $mediaId (tmdbId=$tmdbId) — will refresh")
            return null
        }

        Log.d(TAG, "buildMediaDetail: found entity id=${entity.id} title=${entity.title}")

        val rawId    = tmdbId?.toString() ?: mediaId.substringAfterLast("_")
        val seasons  = db.seasonDao().getSeasonsByMedia(entity.id)
        
        // Progress lookup: check current ID and legacy ID formats for history recovery
        val legacyPrefix = "tmdb_$rawId"
        val progress = db.userProgressDao().getProgress(entity.id)
            ?: db.userProgressDao().getProgress(mediaId)
            ?: db.userProgressDao().getProgress(legacyPrefix)
            ?: db.userProgressDao().getProgress(rawId)

        // Count watched episodes — handle both the entity's canonical ID, navigation ID, 
        // and legacy IDs (Boruto progress recovery fix).
        // FIX: Use a Set to avoid double-counting One Piece episodes across legacy ID formats.
        val watchedCount = buildSet {
            addAll(db.episodeWatchedDao().getWatchedIds(entity.id))
            if (entity.id != mediaId) addAll(db.episodeWatchedDao().getWatchedIds(mediaId))
            val legacyPrefix = "tmdb_$rawId"
            if (entity.id != legacyPrefix && mediaId != legacyPrefix) {
                addAll(db.episodeWatchedDao().getWatchedIds(legacyPrefix))
            }
            if (entity.id != rawId && mediaId != rawId && legacyPrefix != rawId) {
                addAll(db.episodeWatchedDao().getWatchedIds(rawId))
            }
        }.map { id -> 
            // Normalize ID by removing known prefixes to find truly unique episodes
            id.removePrefix("tmdb_m_").removePrefix("tmdb_t_").removePrefix("tmdb_").removePrefix("anilist_")
        }.toSet().size
        
        val totalEps = entity.numberOfEpisodes ?: 0
        val finalWatchedCount = if (totalEps > 0) watchedCount.coerceAtMost(totalEps) else watchedCount

        val cast  = runCatching { castAdapter.fromJson(entity.castJson) }.getOrDefault(emptyList())

        val arcsType    = Types.newParameterizedType(List::class.java, StoryArc::class.java)
        val arcsAdapter = moshi.adapter<List<StoryArc>>(arcsType)
        val arcs        = runCatching { arcsAdapter.fromJson(entity.arcsJson) }.getOrDefault(emptyList())

        val wpType      = Types.newParameterizedType(List::class.java, WatchProviderItem::class.java)
        val wpAdapter   = moshi.adapter<List<WatchProviderItem>>(wpType)
        val providers   = runCatching { wpAdapter.fromJson(entity.watchProvidersJson) }.getOrDefault(emptyList())

        return MediaDetail(
            id               = entity.id,
            tmdbId           = entity.tmdbId,
            anilistId        = entity.anilistId,
            title            = entity.title,
            originalTitle    = entity.originalTitle,
            overview         = entity.overview,
            tagline          = entity.tagline,
            status           = entity.status,
            posterUrl        = entity.posterUrl,
            backdropUrl      = entity.backdropUrl,
            mediaCategory    = when {
                entity.genres.contains("Animation") -> MediaCategory.ANIME
                entity.mediaCategory == "MOVIE"     -> MediaCategory.MOVIE
                else                                -> MediaCategory.TV_SHOW
            },
            genres           = entity.genres,
            ageRating        = entity.ageRating.ifBlank { "NR" },
            voteAverage      = entity.voteAverage,
            voteCount        = entity.voteCount,
            runtime          = entity.runtime,
            numberOfSeasons  = entity.numberOfSeasons,
            numberOfEpisodes = totalEps,
            releaseDate      = entity.releaseDate,
            releaseYear      = entity.releaseDate?.take(4) ?: "",
            trailerKey       = entity.trailerKey,
            watchProviders   = providers ?: emptyList(),
            cast             = cast ?: emptyList(),
            recommendations  = emptyList(),
            seasons          = seasons.map { it.toDomain() },
            arcs             = arcs ?: emptyList(),
            userProgress     = progress?.toDomain(finalWatchedCount) ?: if (finalWatchedCount > 0) {
                UserProgress(mediaId = entity.id, trackingState = TrackingState.WATCHING,
                    totalEpisodesWatched = finalWatchedCount)
            } else null
        )
    }
  private suspend fun fetchAndCacheMovie(tmdbId: Int, mediaId: String): Boolean {
        return try {
            val response = apiService.getMovie(tmdbId)
            if (!response.isSuccessful || response.body() == null) return false
            val body = response.body()!!
            val entity   = body.toMediaEntity(mediaId)
            val castJson = buildCastJson(body, isMovie = true)
            db.mediaDao().upsert(entity.copy(castJson = castJson))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Movie fetch failed for $mediaId: ${e.message}")
            false
        }
    }

    private suspend fun fetchAndCacheTv(tmdbId: Int, mediaId: String): Boolean {
        return try {
            val response = apiService.getTvShow(tmdbId)
            if (!response.isSuccessful || response.body() == null) return false
            val body = response.body()!!
            val entity = body.toMediaEntity(mediaId)
            db.mediaDao().upsert(entity)

            if (body.seasons != null) {
                supervisorScope {
                    body.seasons
                        .map { season ->
                            async { refreshSeasonEpisodes(tmdbId, mediaId, season.seasonNumber, season.episodeCount) }
                        }
                        .forEach { it.await() }
                }
            }

            val castJson = buildCastJson(body, isMovie = false)
            db.mediaDao().upsert(entity.copy(castJson = castJson))
            true
        } catch (e: Exception) {
            Log.w(TAG, "TV fetch failed for $mediaId: ${e.message}")
            false
        }
    }

    private suspend fun refreshSeasonEpisodes(
        tmdbId: Int, mediaId: String, seasonNumber: Int, episodeCount: Int
    ) {
        try {
            val response = apiService.getTvSeason(tmdbId, seasonNumber)
            if (!response.isSuccessful || response.body() == null) return
            val seasonBody = response.body()!!
            val seasonId   = "${mediaId}_s$seasonNumber"

            db.seasonDao().upsertAll(listOf(SeasonEntity(
                id = seasonId, mediaId = mediaId, seasonNumber = seasonNumber,
                name = seasonBody.name, overview = seasonBody.overview ?: "",
                posterUrl = TmdbConfig.buildImageUrl(seasonBody.posterPath),
                airDate = seasonBody.airDate, episodeCount = episodeCount
            )))

            val previousEpisodes = db.episodeDao()
                .getEpisodesInRange(mediaId, 0, (seasonNumber - 1) * 1000).size
            val existingEpisodes = db.episodeDao().getEpisodesBySeason(seasonId)
                .associateBy { it.episodeNumber }

            val entities = seasonBody.episodes?.mapIndexed { idx, ep ->
                val existing = existingEpisodes[ep.episodeNumber]
                EpisodeEntity(
                    id = "${mediaId}_s${seasonNumber}e${ep.episodeNumber}",
                    seasonId = seasonId, mediaId = mediaId,
                    episodeNumber = ep.episodeNumber, seasonNumber = seasonNumber,
                    absoluteEpisodeNumber = previousEpisodes + idx + 1,
                    title = ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview ?: "", airDate = ep.airDate, runtime = ep.runtime,
                    stillUrl = TmdbConfig.buildImageUrl(ep.stillPath, TmdbConfig.PosterSize.SMALL),
                    voteAverage = ep.voteAverage?.toFloat() ?: 0f,
                    episodeType = existing?.episodeType ?: "CANON",
                    arcName = existing?.arcName
                )
            } ?: return

            if (entities.isNotEmpty()) db.episodeDao().upsertAll(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Season $seasonNumber fetch failed for $mediaId", e)
        }
    }

    private fun buildCastJson(
        body: com.example.watchorderengine.network.model.TmdbDetailResponse,
        isMovie: Boolean
    ): String {
        val cast = if (isMovie) {
            body.credits?.cast?.take(15)?.map { c ->
                CastMember(c.id, c.name, c.character ?: "", TmdbConfig.buildProfileUrl(c.profilePath), c.order ?: 99)
            } ?: emptyList()
        } else {
            body.aggregateCredits?.cast?.take(15)?.map { c ->
                CastMember(c.id, c.name, c.roles?.firstOrNull()?.character ?: "", TmdbConfig.buildProfileUrl(c.profilePath), c.order ?: 99)
            } ?: emptyList()
        }
        return castAdapter.toJson(cast) ?: "[]"
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────

    suspend fun getEpisodesBySeason(mediaId: String, seasonNumber: Int): List<EpisodeItem> =
        withContext(Dispatchers.IO) {
            val seasonId   = "${mediaId}_s$seasonNumber"
            val episodes   = db.episodeDao().getEpisodesBySeason(seasonId)

            // Recover watched status from legacy ID formats if needed
            val tmdbId = extractTmdbId(mediaId)
            val rawId = tmdbId?.toString() ?: mediaId.removePrefix("tmdb_").removePrefix("anilist_")

            val watchedIds = (db.episodeWatchedDao().getWatchedIds(mediaId) +
                             db.episodeWatchedDao().getWatchedIds("tmdb_$rawId") +
                             db.episodeWatchedDao().getWatchedIds(rawId)).toSet()

            episodes.map { it.toDomain(watchedIds) }
        }

    fun observeEpisodesBySeason(mediaId: String, seasonNumber: Int): Flow<List<EpisodeItem>> {
        val seasonId = "${mediaId}_s$seasonNumber"
        val tmdbId = extractTmdbId(mediaId)
        val rawId = tmdbId?.toString() ?: mediaId.removePrefix("tmdb_").removePrefix("anilist_")

        return db.episodeDao().observeEpisodesBySeason(seasonId).map { episodes ->
            val watchedIds = (db.episodeWatchedDao().getWatchedIds(mediaId) + 
                            db.episodeWatchedDao().getWatchedIds("tmdb_$rawId") +
                            db.episodeWatchedDao().getWatchedIds(rawId)).toSet()
            episodes.map { it.toDomain(watchedIds) }
        }.flowOn(Dispatchers.IO)
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    suspend fun searchMedia(query: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = apiService.searchMulti(query)
            if (!response.isSuccessful) return@withContext emptyList()
            val results = response.body()?.results
                ?.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                ?: return@withContext emptyList()
            results.forEach { result ->
                val mediaId = buildMediaId(result.id, result.mediaType)
                if (db.mediaDao().getById(mediaId) == null)
                    db.mediaDao().upsert(result.toMinimalEntity(mediaId))
            }
            results.mapNotNull { it.toSummary() }
        } catch (e: Exception) { emptyList() }
    }

    // ─── generateWatchOrder ───────────────────────────────────────────────────

    suspend fun generateWatchOrder(mediaId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext "Show not found."
        val isMovie = entity.mediaCategory == "MOVIE"
        val seasonCounts = db.seasonDao().getSeasonsByMedia(mediaId)
            .sortedBy { it.seasonNumber }.map { it.episodeCount }

        val result = geminiService.generateWatchOrder(
            showTitle          = entity.title,
            overview           = entity.overview,
            seasonEpisodeCounts = seasonCounts,
            mediaType          = if (isMovie) "movie" else "tv"
        )

        when (result) {
            is GeminiResult.Error -> result.message
            is GeminiResult.Success -> {
                val watchOrder = result.watchOrder
                val universeId = mediaId
                val idMap      = java.util.concurrent.ConcurrentHashMap<String, String>()

                Log.d(TAG, "GENERATION: ${watchOrder.nodes.size} nodes, ${watchOrder.edges.size} edges")

                val mediaNodes = supervisorScope {
                    watchOrder.nodes.map { node ->
                        async {
                            var resolvedId   = entity.tmdbId
                            var resolvedType = if (node.contentType == "MOVIE") "movie" else "tv"

                            // Verify Gemini's supplied TMDB ID before trusting it.
                            if (node.tmdbId > 0) {
                                val valid = verifyTmdbIdMatchesTitle(node.tmdbId,
                                    node.contentType == "MOVIE", node.title)
                                if (valid) {
                                    resolvedId = node.tmdbId
                                } else {
                                    Log.w(TAG, "Gemini tmdb_id=${node.tmdbId} rejected for '${node.title}'")
                                }
                            }

                            // Search TMDB for nodes that differ from the parent show.
                            if (resolvedId == entity.tmdbId &&
                                (node.contentType == "MOVIE" || node.contentType == "SERIES") &&
                                !isTitleMatch(node.title, entity.title)) {
                                val query = node.searchQuery ?: node.title
                                val best  = searchMedia(query).find { isTitleMatch(it.title, node.title) }
                                if (best != null) {
                                    resolvedId   = best.tmdbId
                                    resolvedType = if (isMovieId(best.id)) "movie" else "tv"
                                    Log.d(TAG, "NODE_RESOLVE: '${node.title}' → id=$resolvedId, type=$resolvedType via search")
                                }
                            }

                            // ALWAYS derive finalId from buildMediaId — guarantees Detail points to exactly one entity.
                            val finalId = buildMediaId(resolvedId, resolvedType)
                            idMap[node.nodeId] = finalId

                            // Ensure a skeleton entity exists so Detail can render immediately.
                            if (db.mediaDao().getById(finalId) == null) {
                                db.mediaDao().upsert(MediaEntity(
                                    id = finalId, tmdbId = resolvedId,
                                    anilistId = null, title = node.title,
                                    originalTitle = node.title,
                                    overview = "", tagline = "", status = "Skeleton",
                                    posterUrl = null, backdropUrl = null,
                                    mediaCategory = if (resolvedType == "movie") "MOVIE" else "TV_SHOW",
                                    genres = emptyList(), ageRating = "NR",
                                    voteAverage = 0f, voteCount = 0,
                                    runtime = null, numberOfSeasons = null,
                                    numberOfEpisodes = null, releaseDate = null,
                                    releaseYear = "", trailerKey = null,
                                    watchProvidersJson = "[]",
                                    castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
                                ))
                            }

                            MediaNode(
                                id               = finalId,
                                title            = node.title,
                                tmdb_id          = resolvedId,
                                tmdb_media_type  = resolvedType,
                                content_type     = node.contentType,
                                type             = if (resolvedType == "movie") MediaCategory.MOVIE else MediaCategory.TV_SHOW,
                                chrono_order     = node.chronoOrder,
                                release_order    = node.releaseOrder,
                                phase            = node.phase,
                                tags             = node.tags
                            )
                        }
                    }.awaitAll()
                }

                val mediaEdges = watchOrder.edges.mapNotNull { edge ->
                    val from = idMap[edge.fromNodeId] ?: buildMediaId(
                        extractTmdbId(edge.fromNodeId) ?: return@mapNotNull null, "tv")
                    val to   = idMap[edge.toNodeId]   ?: buildMediaId(
                        extractTmdbId(edge.toNodeId)   ?: return@mapNotNull null, "tv")
                    if (from == to) return@mapNotNull null
                    Edge(from_node_id = from, to_node_id = to, type = edge.type)
                }

                watchOrderRepository.clearGeneratedUniverse(universeId)
                val publishResult = watchOrderRepository.publishGeneratedUniverse(
                    universeId    = universeId,
                    universeName  = entity.title,
                    coverUrl      = entity.posterUrl ?: "",
                    nodes         = mediaNodes,
                    edges         = mediaEdges
                )
                if (publishResult.isFailure) {
                    return@withContext "Firestore push failed: ${publishResult.exceptionOrNull()?.message}"
                }

                supervisorScope {
                    watchOrder.nodes.forEach { node ->
                        val finalId = idMap[node.nodeId] ?: return@forEach
                        if (finalId != mediaId) {
                            launch { refreshDetail(finalId) }
                        }
                        val classification = when {
                            "FILLER" in node.tags -> "FILLER"
                            "MIXED"  in node.tags -> "MIXED"
                            else                  -> "CANON"
                        }
                        val seasonId     = "${finalId}_s${node.startSeason}"
                        val nodeEpisodes = db.episodeDao().getEpisodesBySeason(seasonId)
                            .filter { it.episodeNumber in node.startEpisode..node.endEpisode }
                        nodeEpisodes.forEach { ep ->
                            db.episodeDao().upsertAll(listOf(ep.copy(
                                episodeType = classification, arcName = node.title
                            )))
                        }
                    }
                }
                null
            }
        }
    }

    // ─── Title matching (used by generateWatchOrder) ──────────────────────────

    private fun normalizeTitle(title: String): String {
        var t = title.lowercase()
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("\\bpart\\b"), "")
            .trim()
        mapOf("viii" to "8", "vii" to "7", "vi" to "6", "iv" to "4",
              "v"    to "5", "iii" to "3", "ii" to "2", "i"  to "1")
            .forEach { (roman, digit) -> t = t.replace(Regex("\\b$roman\\b"), digit) }
        return t.replace(Regex("[^a-z0-9]"), "").trim()
    }

    private fun isTitleMatch(actual: String, expected: String): Boolean {
        val a = normalizeTitle(actual)
        val b = normalizeTitle(expected)
        if (a == b || a.isEmpty() || b.isEmpty()) return a == b
        // Prevent "Toy Story" matching "Toy Story 2"
        val aNum = a.filter { it.isDigit() }
        val bNum = b.filter { it.isDigit() }
        if (aNum != bNum) return false
        return a.contains(b) || b.contains(a)
    }

    private suspend fun verifyTmdbIdMatchesTitle(
        tmdbId: Int, isMovie: Boolean, expectedTitle: String
    ): Boolean = try {
        val response = if (isMovie) apiService.getMovie(tmdbId) else apiService.getTvShow(tmdbId)
        if (!response.isSuccessful) false
        else isTitleMatch(response.body()?.let { it.title ?: it.name } ?: "", expectedTitle)
    } catch (e: Exception) { false }

    // ─── Tracking states & watchlists ─────────────────────────────────────────

    suspend fun updateTrackingState(mediaId: String, state: TrackingState) =
        withContext(Dispatchers.IO) {
            val current = db.userProgressDao().getProgress(mediaId)
            db.userProgressDao().upsert(UserProgressEntity(
                mediaId = mediaId, trackingState = state.name,
                userNotes    = current?.userNotes    ?: "",
                priorityTag  = current?.priorityTag  ?: "NONE"
            ))
            db.discoverySkippedDao().removeSkipped(mediaId)
        }

    /** Removes a show from the user's watchlist by deleting its progress record and history. */
    suspend fun removeFromWatchlist(mediaId: String) = withContext(Dispatchers.IO) {
        db.userProgressDao().deleteByMediaId(mediaId)
        db.episodeWatchedDao().deleteByMediaId(mediaId)
        
        // Also clear for legacy ID formats to be thorough
        val tmdbId = extractTmdbId(mediaId)
        val rawId = tmdbId?.toString() ?: mediaId.removePrefix("tmdb_").removePrefix("anilist_")
        val legacyPrefix = "tmdb_$rawId"
        
        if (mediaId != legacyPrefix) {
            db.userProgressDao().deleteByMediaId(legacyPrefix)
            db.episodeWatchedDao().deleteByMediaId(legacyPrefix)
        }
        if (mediaId != rawId && legacyPrefix != rawId) {
            db.userProgressDao().deleteByMediaId(rawId)
            db.episodeWatchedDao().deleteByMediaId(rawId)
        }
    }

    suspend fun getAllTrackedMediaIds(): Set<String> = withContext(Dispatchers.IO) {
        db.userProgressDao().getAll().map { it.mediaId }.toSet()
    }

    suspend fun getSkippedMediaIds(): Set<String> = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().getAllSkippedIds().toSet()
    }

    suspend fun markSkipped(mediaId: String) = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().markSkipped(DiscoverySkippedEntity(mediaId))
    }

    suspend fun clearSkipped() = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().clearAllSkipped()
    }

    suspend fun getListByState(
        trackingState: TrackingState,
        sortType: SortType = SortType.DATE_ADDED
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(trackingState.name)
        val summaries    = progressList.mapNotNull { progress ->
            val tmdbId = extractTmdbId(progress.mediaId)
            // Type-safe fallback: derive category from the progress.mediaId prefix
            // so a TV-show progress entry never resolves to a movie entity.
            val progressCategories = when {
                isMovieId(progress.mediaId) -> listOf("MOVIE")
                isTvId(progress.mediaId)    -> listOf("TV_SHOW", "ANIME")
                else                        -> listOf("MOVIE", "TV_SHOW", "ANIME")
            }
            val entity = db.mediaDao().getById(progress.mediaId)
                ?: tmdbId?.let { db.mediaDao().getByTmdbIdAndCategory(it, progressCategories) }
                ?: return@mapNotNull null
            entity.toSummary(trackingState, PriorityTag.valueOf(progress.priorityTag))
        }
        sortSummaries(summaries, sortType)
    }

    suspend fun getWatchingList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> =
        getListByState(TrackingState.WATCHING, sortType)

    suspend fun getPlannedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> =
        getListByState(TrackingState.PLANNED, sortType)

    suspend fun getCompletedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> =
        getListByState(TrackingState.COMPLETED, sortType)

    suspend fun getDroppedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> =
        getListByState(TrackingState.DROPPED, sortType)

    suspend fun getPausedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> =
        getListByState(TrackingState.PAUSED, sortType)

    private fun sortSummaries(list: List<MediaSummary>, sortType: SortType) = when (sortType) {
        SortType.ALPHABETICAL            -> list.sortedBy { it.title }
        SortType.USER_RATING,
        SortType.GLOBAL_SCORE            -> list.sortedByDescending { it.voteAverage }
        SortType.DATE_ADDED              -> list
    }

    // ─── Episode watched ──────────────────────────────────────────────────────

    suspend fun toggleEpisodeWatched(episodeId: String, mediaId: String): Boolean =
        withContext(Dispatchers.IO) {
            val isWatched = db.episodeWatchedDao().isWatched(episodeId)
            if (isWatched) db.episodeWatchedDao().unmarkWatched(episodeId)
            else db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(episodeId, mediaId))
            !isWatched
        }

    suspend fun markAllPreviousAsWatched(mediaId: String, upToAbsoluteNumber: Int) =
        withContext(Dispatchers.IO) {
            db.episodeDao().getAllEpisodesByMedia(mediaId)
                .filter { it.absoluteEpisodeNumber < upToAbsoluteNumber }
                .forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
        }

    suspend fun markPreviousEpisodesAsWatchedSequentially(
        mediaId: String, targetSeason: Int, targetEpisode: Int
    ) = withContext(Dispatchers.IO) {
        db.episodeDao().getAllEpisodesByMedia(mediaId)
            .filter { it.seasonNumber < targetSeason ||
                (it.seasonNumber == targetSeason && it.episodeNumber < targetEpisode) }
            .forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
    }

    suspend fun markAllAsWatched(mediaId: String) = withContext(Dispatchers.IO) {
        db.episodeDao().getAllEpisodesByMedia(mediaId)
            .forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
    }

    suspend fun markSeasonAsWatched(mediaId: String, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val seasonId = "${mediaId}_s$seasonNumber"
        db.episodeDao().getEpisodesBySeason(seasonId)
            .forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
    }

    suspend fun unmarkSeasonAsWatched(mediaId: String, seasonNumber: Int) = withContext(Dispatchers.IO) {
        db.episodeWatchedDao().unmarkSeasonWatched(mediaId, "${mediaId}_s${seasonNumber}e%")
    }

    suspend fun hasUnwatchedEpisodesBefore(
        mediaId: String, targetSeason: Int, targetEpisode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val episodes   = db.episodeDao().getAllEpisodesByMedia(mediaId)
        val watchedIds = db.episodeWatchedDao().getWatchedIds(mediaId).toSet()
        episodes.any {
            (it.seasonNumber < targetSeason ||
             (it.seasonNumber == targetSeason && it.episodeNumber < targetEpisode)) &&
            it.id !in watchedIds
        }
    }

    // ─── Trending / Discovery ─────────────────────────────────────────────────

    suspend fun getTrending(): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrending()
            if (!response.isSuccessful) return@withContext emptyList()
            val results = response.body()?.results ?: return@withContext emptyList()
            results.forEach { result ->
                if (result.mediaType == "movie" || result.mediaType == "tv") {
                    val mediaId = buildMediaId(result.id, result.mediaType)
                    if (db.mediaDao().getById(mediaId) == null)
                        db.mediaDao().upsert(result.toMinimalEntity(mediaId))
                }
            }
            results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .mapNotNull { it.toSummary() }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun discoverByGenre(category: TmdbConfig.DiscoveryCategory): List<MediaSummary> =
        withContext(Dispatchers.IO) {
            try {
                val movieResponse = apiService.discoverMovies(genreId = category.movieGenreId)
                val tvResponse    = apiService.discoverTvShows(genreId = category.tvGenreId)
                val movies = if (movieResponse.isSuccessful) movieResponse.body()?.results ?: emptyList() else emptyList()
                val shows  = if (tvResponse.isSuccessful)    tvResponse.body()?.results    ?: emptyList() else emptyList()

                movies.forEach { result ->
                    val id = buildMediaId(result.id, "movie")
                    if (db.mediaDao().getById(id) == null) db.mediaDao().upsert(result.toMinimalEntity(id, isMovie = true))
                }
                shows.forEach { result ->
                    val id = buildMediaId(result.id, "tv")
                    if (db.mediaDao().getById(id) == null) db.mediaDao().upsert(result.toMinimalEntity(id, isMovie = false))
                }

                val movieSummaries = movies.mapNotNull { it.toSummary(isMovie = true) }
                val tvSummaries    = shows.mapNotNull  { it.toSummary(isMovie = false) }
                movieSummaries.zip(tvSummaries) { m, t -> listOf(m, t) }.flatten() +
                    movieSummaries.drop(tvSummaries.size) + tvSummaries.drop(movieSummaries.size)
            } catch (e: Exception) {
                Log.w(TAG, "discoverByGenre failed for ${category.label}: ${e.message}")
                emptyList()
            }
        }

    // ─── Profile / stats ──────────────────────────────────────────────────────

    suspend fun getPersonBiography(personId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val r = apiService.getPerson(personId)
            if (r.isSuccessful) r.body()?.biography else null
        } catch (e: Exception) { null }
    }

    suspend fun countWatchedEpisodes(): Int = withContext(Dispatchers.IO) {
        try { 
            db.episodeWatchedDao().getAllWatchedIds()
                .map { it.removePrefix("tmdb_m_").removePrefix("tmdb_t_").removePrefix("tmdb_").removePrefix("anilist_") }
                .toSet()
                .size 
        } catch (e: Exception) { 0 }
    }

    /** Real total minutes watched — handles dual-ID mapping to ensure legacy history is counted. */
    suspend fun getTotalWatchedMinutes(): Int = withContext(Dispatchers.IO) {
        try { db.episodeWatchedDao().sumWatchedRuntimeMinutesTypeSafe() } catch (e: Exception) { 0 }
    }

    suspend fun getAllRatedMedia(): List<Pair<String, Float>> = withContext(Dispatchers.IO) {
        try { db.userProgressDao().getAll().mapNotNull { p -> p.userRating?.let { p.mediaId to it } } }
        catch (e: Exception) { emptyList() }
    }

    suspend fun updateRating(mediaId: String, rating: Float) = withContext(Dispatchers.IO) {
        val now     = System.currentTimeMillis()
        val updated = db.userProgressDao().updateRating(mediaId, rating, now)
        if (updated == 0) {
            db.userProgressDao().upsert(UserProgressEntity(
                mediaId = mediaId, trackingState = "PLANNED", userRating = rating
            ))
        }
    }

    suspend fun computeWatchStreak(): Int = withContext(Dispatchers.IO) {
        try {
            val timestamps = db.episodeWatchedDao().getAllWatchedTimestamps()
            if (timestamps.isEmpty()) return@withContext 0
            val oneDayMs = 24L * 60L * 60L * 1000L
            val cal = java.util.Calendar.getInstance()
            val days = timestamps.map { ts ->
                cal.timeInMillis = ts
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND,       0); cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }.toSortedSet(reverseOrder())
            cal.timeInMillis = System.currentTimeMillis()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND,       0); cal.set(java.util.Calendar.MILLISECOND, 0)
            var streak = 0; var expected = cal.timeInMillis
            for (day in days) {
                if (day == expected) { streak++; expected -= oneDayMs }
                else if (day < expected) break
            }
            streak
        } catch (e: Exception) { 0 }
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private fun SeasonEntity.toDomain() = SeasonSummary(
        id = id, mediaId = mediaId, seasonNumber = seasonNumber, name = name,
        overview = overview, posterUrl = posterUrl, airDate = airDate, episodeCount = episodeCount
    )

    private fun EpisodeEntity.toDomain(watchedIds: Set<String>) = EpisodeItem(
        id = id, seasonId = seasonId, mediaId = mediaId,
        episodeNumber = episodeNumber, seasonNumber = seasonNumber,
        absoluteEpisodeNumber = absoluteEpisodeNumber,
        title = title, overview = overview, airDate = airDate,
        runtime = runtime, stillUrl = stillUrl, voteAverage = voteAverage,
        episodeType = EpisodeType.entries.find { it.name == episodeType } ?: EpisodeType.CANON,
        arcName = arcName, isWatched = id in watchedIds
    )

    private fun UserProgressEntity.toDomain(watchedCount: Int = 0) = UserProgress(
        mediaId = mediaId,
        trackingState = TrackingState.valueOf(trackingState),
        currentSeasonNumber = currentSeasonNumber,
        currentEpisodeNumber = currentEpisodeNumber,
        totalEpisodesWatched = watchedCount,
        userRating = userRating,
        startedDate = startedDate, completedDate = completedDate,
        updatedAt = updatedAt, userNotes = userNotes,
        priorityTag = PriorityTag.valueOf(priorityTag)
    )

    private fun MediaEntity.toSummary(
        state: TrackingState? = null,
        priority: PriorityTag = PriorityTag.NONE
    ) = MediaSummary(
        id = id, tmdbId = tmdbId, title = title,
        posterUrl = posterUrl, backdropUrl = backdropUrl,
        mediaCategory = when {
            genres.contains("Animation") -> MediaCategory.ANIME
            mediaCategory == "MOVIE"     -> MediaCategory.MOVIE
            else                         -> MediaCategory.TV_SHOW
        },
        voteAverage = voteAverage, releaseYear = releaseYear,
        trackingState = state, ageRating = ageRating,
        priorityTag = priority, releaseDate = releaseDate,
        genres = genres
    )

    private fun com.example.watchorderengine.network.model.TmdbDetailResponse.toMediaEntity(
        mediaId: String
    ): MediaEntity {
        val genresList = genres?.map { it.name } ?: emptyList()
        val isMovie    = if (isMovieId(mediaId)) true else if (isTvId(mediaId)) false else title != null
        val category   = if (isMovie) "MOVIE" else "TV_SHOW"

        // Always honour the typed prefix passed in — never compute a new ID here.
        // The original sanitizedMediaId calculation that stripped _m_/_t_ to "tmdb_{id}"
        // was removed as part of the collision fix.
        val trailerKey = videos?.results
            ?.filter { it.site == "YouTube" && it.type == "Trailer" && it.official }
            ?.maxByOrNull { it.publishedAt ?: "" }?.key

        val providers     = resolveWatchProviders(watchProviders?.results)
        val wpType        = Types.newParameterizedType(List::class.java, WatchProviderItem::class.java)
        val wpAdapter     = moshi.adapter<List<WatchProviderItem>>(wpType)
        val providersJson = runCatching { wpAdapter.toJson(providers) }.getOrDefault("[]")

        return MediaEntity(
            id = mediaId, tmdbId = this.id,
            anilistId = null,
            title = title ?: name ?: "",
            originalTitle = originalTitle ?: originalName ?: "",
            overview = overview ?: "", tagline = tagline ?: "", status = status ?: "",
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = category,
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage.toFloat(), voteCount = voteCount,
            runtime = runtime ?: episodeRunTime?.firstOrNull(),
            numberOfSeasons = numberOfSeasons, numberOfEpisodes = numberOfEpisodes,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = trailerKey,
            watchProvidersJson = providersJson,
            castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun resolveWatchProviders(
        results: Map<String, TmdbWatchProviderCountry>?
    ): List<WatchProviderItem> {
        if (results.isNullOrEmpty()) return emptyList()
        val countryCode = TmdbConfig.PROVIDER_COUNTRY_PRIORITY
            .firstOrNull { results.containsKey(it) }
            ?: results.keys.firstOrNull() ?: return emptyList()
        val country      = results[countryCode] ?: return emptyList()
        val justWatchUrl = country.link
        fun List<TmdbWatchProvider>?.toItems(type: String) =
            this?.sortedBy { it.displayPriority }?.map { p ->
                WatchProviderItem(
                    providerId   = p.providerId,
                    providerName = TmdbConfig.PROVIDER_SHORT_NAMES[p.providerId] ?: p.providerName,
                    logoUrl      = TmdbConfig.buildImageUrl(p.logoPath, TmdbConfig.PosterSize.THUMBNAIL),
                    offerType    = type, justWatchUrl = justWatchUrl
                )
            } ?: emptyList()
        return (country.flatrate.toItems("stream") + country.free.toItems("free") +
                country.rent.toItems("rent") + country.buy.toItems("buy"))
            .distinctBy { it.providerId }
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toMinimalEntity(
        mediaId: String
    ): MediaEntity {
        val isMovie    = mediaType == "movie"
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        return MediaEntity(
            id = mediaId, tmdbId = extractTmdbId(mediaId) ?: id,
            anilistId = null, title = title ?: name ?: "", originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (isMovie) "MOVIE" else "TV_SHOW",
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage?.toFloat() ?: 0f, voteCount = 0,
            runtime = null, numberOfSeasons = null, numberOfEpisodes = null,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = null, castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toMinimalEntity(
        mediaId: String, isMovie: Boolean
    ): MediaEntity {
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        return MediaEntity(
            id = mediaId, tmdbId = extractTmdbId(mediaId) ?: id,
            anilistId = null, title = title ?: name ?: "", originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (isMovie) "MOVIE" else "TV_SHOW",
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage?.toFloat() ?: 0f, voteCount = 0,
            runtime = null, numberOfSeasons = null, numberOfEpisodes = null,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = null, castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(): MediaSummary? {
        if (mediaType == null || (mediaType != "movie" && mediaType != "tv")) return null
        val mediaId    = buildMediaId(id, mediaType)
        val isMovie    = mediaType == "movie"
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                genresList.contains("Animation") -> MediaCategory.ANIME
                isMovie                          -> MediaCategory.MOVIE
                else                             -> MediaCategory.TV_SHOW
            },
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trackingState = null, ageRating = "NR",
            genres = genresList, releaseDate = releaseDate ?: firstAirDate
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(
        isMovie: Boolean
    ): MediaSummary? {
        val mediaId    = buildMediaId(id, if (isMovie) "movie" else "tv")
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                genresList.contains("Animation") -> MediaCategory.ANIME
                isMovie                          -> MediaCategory.MOVIE
                else                             -> MediaCategory.TV_SHOW
            },
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trackingState = null, ageRating = "NR",
            genres = genresList, releaseDate = releaseDate ?: firstAirDate
        )
    }
}
