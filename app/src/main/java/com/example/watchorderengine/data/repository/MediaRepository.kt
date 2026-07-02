package com.example.watchorderengine.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.sync.SyncWorker
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.gemini.GeminiResult
import com.example.watchorderengine.network.gemini.GeminiService
import com.example.watchorderengine.network.model.TmdbWatchProvider
import com.example.watchorderengine.network.model.TmdbWatchProviderCountry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val watchOrderRepository: WatchOrderRepository,
    private val userPrefs: UserPreferencesRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
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

    /**
     * Returns a set of normalized episode IDs that the user has watched for this show,
     * including history recovered from legacy ID formats.
     */
    suspend fun getNormalizedWatchedIds(mediaId: String): Set<String> = withContext(Dispatchers.IO) {
        val tmdbId = extractTmdbId(mediaId)
        val rawId    = tmdbId?.toString() ?: mediaId.substringAfterLast("_")
        val legacyPrefix = "tmdb_$rawId"

        buildSet {
            addAll(db.episodeWatchedDao().getWatchedIds(mediaId))
            addAll(db.episodeWatchedDao().getWatchedIds(legacyPrefix))
            addAll(db.episodeWatchedDao().getWatchedIds(rawId))
            // Also include current entity ID if different
            db.mediaDao().getById(mediaId)?.let { addAll(db.episodeWatchedDao().getWatchedIds(it.id)) }
        }.map { id ->
            id.removePrefix("tmdb_m_")
              .removePrefix("tmdb_t_")
              .removePrefix("tmdb_")
              .removePrefix("anilist_")
        }.toSet()
    }

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

    private val wpType      = Types.newParameterizedType(List::class.java, WatchProviderItem::class.java)
    private val wpAdapter   by lazy { moshi.adapter<List<WatchProviderItem>>(wpType) }
    private val arcsType    = Types.newParameterizedType(List::class.java, StoryArc::class.java)
    private val arcsAdapter by lazy { moshi.adapter<List<StoryArc>>(arcsType) }
    private val castType    = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }

    suspend fun getCachedWatchProviders(mediaId: String): List<WatchProviderItem> {
        val entity = db.mediaDao().getById(mediaId) ?: return emptyList()
        return try {
            wpAdapter.fromJson(entity.watchProvidersJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

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
            // e.g. "tmdb_m_123_s1e1" and "tmdb_123_s1e1" both become "123_s1e1"
            id.removePrefix("tmdb_m_")
              .removePrefix("tmdb_t_")
              .removePrefix("tmdb_")
              .removePrefix("anilist_")
        }.toSet().size
        
        val totalEps = entity.numberOfEpisodes ?: 0
        val finalWatchedCount = if (totalEps > 0) watchedCount.coerceAtMost(totalEps) else watchedCount

        val cast = runCatching { castAdapter.fromJson(entity.castJson) }.getOrNull() ?: emptyList<CastMember>()
        var arcs = runCatching { arcsAdapter.fromJson(entity.arcsJson) }.getOrNull() ?: emptyList<StoryArc>()
        val providers = runCatching { wpAdapter.fromJson(entity.watchProvidersJson) }.getOrNull() ?: emptyList<WatchProviderItem>()

        // If no curated arcs, try to derive them from Gemini-tagged episodes
        if (arcs.isEmpty()) {
            val episodesList = db.episodeDao().getAllEpisodesByMedia(entity.id)
            arcs = episodesList.filter { it.arcName != null }
                .groupBy { it.arcName }
                .map { (name, eps) ->
                    val first = eps.minBy { it.absoluteEpisodeNumber }
                    val last = eps.maxBy { it.absoluteEpisodeNumber }
                    StoryArc(
                        name = name!!,
                        startAbsoluteEpisode = first.absoluteEpisodeNumber,
                        endAbsoluteEpisode = last.absoluteEpisodeNumber,
                        startSeason = first.seasonNumber,
                        startEpisode = first.episodeNumber,
                        endSeason = last.seasonNumber,
                        endEpisode = last.episodeNumber,
                        synopsis = "Generated via Watch Order Engine."
                    )
                }
                .sortedBy { it.startAbsoluteEpisode }
        }

        // Content-based recommendations from local cache
        val allMedia = db.mediaDao().getAll()
        val recs = com.example.watchorderengine.data.recommendation.RecommendationEngine.generateRecommendations(
            completedMedia = listOf(entity to (progress ?: UserProgressEntity(entity.id, TrackingState.WATCHING.name))),
            candidates = allMedia.filter { it.id != entity.id },
            topK = 6
        ).map { rec -> rec.media.toSummary() }

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
            recommendations  = recs,
            seasons          = seasons.map { it.toDomain() },
            arcs             = arcs,
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
                // Pre-calculate season offsets for absolute episode numbering to ensure
                // deterministic order regardless of which season-refresh finishes first.
                val seasonOffsets = mutableMapOf<Int, Int>()
                var cumulativeCount = 0
                body.seasons.sortedBy { it.seasonNumber }.forEach { s ->
                    if (s.seasonNumber > 0) {
                        seasonOffsets[s.seasonNumber] = cumulativeCount
                        cumulativeCount += s.episodeCount
                    }
                }

                supervisorScope {
                    body.seasons
                        .map { season ->
                            async { 
                                val offset = seasonOffsets[season.seasonNumber] ?: 0
                                refreshSeasonEpisodes(tmdbId, mediaId, season.seasonNumber, season.episodeCount, offset) 
                            }
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
        tmdbId: Int, mediaId: String, seasonNumber: Int, episodeCount: Int, offset: Int
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

            val existingEpisodes = db.episodeDao().getEpisodesBySeason(seasonId)
                .associateBy { it.episodeNumber }

            val entities = seasonBody.episodes?.mapIndexed { idx, ep ->
                val existing = existingEpisodes[ep.episodeNumber]
                EpisodeEntity(
                    id = "${mediaId}_s${seasonNumber}e${ep.episodeNumber}",
                    seasonId = seasonId, mediaId = mediaId,
                    episodeNumber = ep.episodeNumber, seasonNumber = seasonNumber,
                    absoluteEpisodeNumber = offset + idx + 1,
                    title = ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview ?: "", airDate = ep.airDate, runtime = ep.runtime,
                    stillUrl = TmdbConfig.buildImageUrl(ep.stillPath, TmdbConfig.PosterSize.HD),
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
                val profileUrl = TmdbConfig.buildProfileUrl(c.profilePath)
                    ?.takeIf { TmdbConfig.isValidImageUrl(it) }
                CastMember(c.id, c.name, c.character ?: "", profileUrl, c.order ?: 99)
            } ?: emptyList()
        } else {
            body.aggregateCredits?.cast?.take(15)?.map { c ->
                val profileUrl = TmdbConfig.buildProfileUrl(c.profilePath)
                    ?.takeIf { TmdbConfig.isValidImageUrl(it) }
                CastMember(c.id, c.name, c.roles?.firstOrNull()?.character ?: "", profileUrl, c.order ?: 99)
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

    // ─── generateWatchOrder ───────────────────────────────────────────────────────

    suspend fun generateWatchOrder(mediaId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext "Show not found."
        val isMovie = entity.mediaCategory == "MOVIE"

        // ── Step 1: RAW DATA FIRST — build the FULL franchise item list ──────────
        // For movies: expand to the entire TMDB Collection so Gemini can sort all
        // films in a franchise (Toy Story, MCU Infinity Saga, etc.) instead of
        // receiving a single node and producing a degenerate single-hexagon graph.
        // For TV: seasons are already a list; no change needed.
        val rawItems: List<com.example.watchorderengine.network.gemini.RawMediaItem> = if (isMovie) {

            // ── Franchise expansion ───────────────────────────────────────────────
            val collectionItems: List<com.example.watchorderengine.network.gemini.RawMediaItem>? = try {
                // 1. Re-fetch the movie to get belongs_to_collection (may not be in Room)
                val detailResponse = apiService.getMovie(entity.tmdbId)
                val collectionId = detailResponse.body()?.belongsToCollection?.id

                if (collectionId != null && collectionId > 0) {
                    Log.d(TAG, "Expanding franchise via collection $collectionId for ${entity.title}")
                    val collectionResponse = apiService.getMovieCollection(collectionId)
                    collectionResponse.body()?.parts
                        ?.filter { !it.releaseDate.isNullOrBlank() }  // skip unannounced entries
                        ?.sortedBy { it.releaseDate }                  // chronological seed
                        ?.map { part ->
                            val partMediaId = buildMediaId(part.id, "movie")
                            // Pre-cache a minimal entity so timeline navigation works instantly;
                            // full detail is fetched on demand when the user taps the node.
                            if (db.mediaDao().getById(partMediaId) == null) {
                                db.mediaDao().upsert(
                                    com.example.watchorderengine.data.db.entity.MediaEntity(
                                        id = partMediaId,            tmdbId = part.id,
                                        anilistId = null,
                                        title = part.title,          originalTitle = part.title,
                                        overview = part.overview ?: "", tagline = "", status = "",
                                        posterUrl   = TmdbConfig.buildImageUrl(part.posterPath),
                                        backdropUrl = null,
                                        mediaCategory = "MOVIE",
                                        genres = emptyList(),        ageRating = "NR",
                                        voteAverage = part.voteAverage?.toFloat() ?: 0f,
                                        voteCount = 0,               runtime = null,
                                        numberOfSeasons = null,      numberOfEpisodes = null,
                                        releaseDate = part.releaseDate,
                                        releaseYear = part.releaseDate?.take(4) ?: "",
                                        trailerKey = null,
                                        castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
                                    )
                                )
                            }
                            com.example.watchorderengine.network.gemini.RawMediaItem(
                                itemId      = partMediaId,
                                title       = part.title,
                                overview    = part.overview ?: "",
                                contentType = "MOVIE",
                                releaseDate = part.releaseDate,
                                tmdbId      = part.id,
                                source      = "TMDB_COLLECTION"
                            )
                        }
                } else {
                    Log.d(TAG, "No collection found for ${entity.title} — using single-item list")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Collection fetch failed for ${entity.title}: ${e.message}")
                null
            }

            // Fall back to the original single-item list for truly standalone movies
            collectionItems?.takeIf { it.isNotEmpty() } ?: listOf(
                com.example.watchorderengine.network.gemini.RawMediaItem(
                    itemId      = mediaId,
                    title       = entity.title,
                    overview    = entity.overview,
                    contentType = "MOVIE",
                    releaseDate = entity.releaseDate,
                    tmdbId      = entity.tmdbId,
                    source      = "TMDB_MOVIE"
                )
            )

        } else {
            // TV / Anime: map seasons (unchanged from original)
            db.seasonDao().getSeasonsByMedia(mediaId).sortedBy { it.seasonNumber }.map { season ->
                com.example.watchorderengine.network.gemini.RawMediaItem(
                    itemId       = season.id,
                    title        = "${entity.title} — ${season.name}",
                    overview     = season.overview.ifBlank { entity.overview },
                    contentType  = "SERIES",
                    seasonNumber = season.seasonNumber,
                    episodeCount = season.episodeCount,
                    releaseDate  = season.airDate,
                    tmdbId       = entity.tmdbId,
                    source       = "TMDB_SEASON"
                )
            }
        }

        if (rawItems.isEmpty()) {
            return@withContext "No season data cached yet for this show — open it once before generating a watch order."
        }

        // ── Step 2: Gemini SORTS the real data ──────────────────────────────────
        val result = geminiService.generateWatchOrder(showTitle = entity.title, rawItems = rawItems)

        when (result) {
            is com.example.watchorderengine.network.gemini.GeminiResult.Error -> result.message
            is com.example.watchorderengine.network.gemini.GeminiResult.Success -> {
                val sortedNodes = result.watchOrder.nodes
                val sortedEdges = result.watchOrder.edges
                Log.d(TAG, "SORTED: ${sortedNodes.size} nodes (of ${rawItems.size} raw items), ${sortedEdges.size} edges")

                // ── Step 3: Publish ──────────────────────────────────────────────
                watchOrderRepository.clearGeneratedUniverse(mediaId)
                val publishResult = watchOrderRepository.publishSortedUniverse(
                    universeId    = mediaId,
                    universeName  = entity.title,
                    coverUrl      = entity.posterUrl ?: "",
                    rawItems      = rawItems,
                    sortedNodes   = sortedNodes,
                    sortedEdges   = sortedEdges,
                    resolveMediaId = { raw ->
                        if (raw.tmdbId == entity.tmdbId) {
                            mediaId to (if (isMovie) "movie" else "tv")
                        } else {
                            val type = if (raw.contentType == "MOVIE") "movie" else "tv"
                            buildMediaId(raw.tmdbId, type) to type
                        }
                    }
                )
                if (publishResult.isFailure) {
                    return@withContext "Firestore push failed: ${publishResult.exceptionOrNull()?.message}"
                }

                // ── Step 4: Tag episodes CANON/FILLER ───────────────────────────
                supervisorScope {
                    sortedNodes.forEach { node ->
                        val raw = rawItems.find { it.itemId == node.itemId } ?: return@forEach
                        val seasonNumber = raw.seasonNumber ?: return@forEach
                        val classification = if (node.filler) "FILLER" else "CANON"
                        val seasonId = "${mediaId}_s$seasonNumber"
                        val seasonEpisodes = db.episodeDao().getEpisodesBySeason(seasonId)
                        if (seasonEpisodes.isNotEmpty()) {
                            db.episodeDao().upsertAll(seasonEpisodes.map {
                                it.copy(episodeType = classification, arcName = node.phase)
                            })
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
            val tmdbId = extractTmdbId(mediaId)
            val rawId = tmdbId?.toString() ?: mediaId.removePrefix("tmdb_").removePrefix("anilist_")
            val legacyPrefix = "tmdb_$rawId"
            
            // Clean up other variants before setting new state to prevent duplicate "Watching" entries
            val otherVariants = setOf("tmdb_m_$rawId", "tmdb_t_$rawId", legacyPrefix, rawId) - mediaId
            otherVariants.forEach { db.userProgressDao().deleteByMediaId(it) }

            val current = db.userProgressDao().getProgress(mediaId)
            val entity = UserProgressEntity(
                mediaId = mediaId, trackingState = state.name,
                userNotes    = current?.userNotes    ?: "",
                priorityTag  = current?.priorityTag  ?: "NONE"
            )
            db.userProgressDao().upsert(entity)

            // SYNC TO FIRESTORE: Save watchlist progress
            if (userPrefs.cloudSyncEnabled.first()) {
                try {
                    val uid = auth.currentUser?.uid ?: return@withContext
                    firestore.collection("users").document(uid)
                        .collection("watchlist").document(mediaId)
                        .set(entity).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync watchlist to cloud: ${e.message}")
                }
            }

            // SYNC TO GRAPH: If completed, mark in any universes containing this media
            if (state == TrackingState.COMPLETED && tmdbId != null) {
                try {
                    val universes = watchOrderRepository.findUniversesForMedia(tmdbId).first()
                    universes.forEach { universe ->
                        // Attempt to find the node ID in this universe. 
                        // Usually nodeId == mediaId if generated by Gemini.
                        watchOrderRepository.setNodeCompletionDirect(universe.id, mediaId, true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync completion to graph: ${e.message}")
                }
            }

            // FIX: If moving from Neutral to something else, don't clear.
            // But if we're technically re-adding it, clear from skipped.
            db.discoverySkippedDao().removeSkipped(mediaId)
        }

    /** Removes a show from the user's watchlist by deleting its progress record and history. */
    suspend fun removeFromWatchlist(mediaId: String) = withContext(Dispatchers.IO) {
        val tmdbId = extractTmdbId(mediaId)
        // Resolve entity first to find its internal canonical ID which might differ from navigation ID
        val entity = db.mediaDao().getById(mediaId) ?: run {
            val typedCategories = if (isMovieId(mediaId)) listOf("MOVIE") else listOf("TV_SHOW", "ANIME")
            tmdbId?.let { db.mediaDao().getByTmdbIdAndCategory(it, typedCategories) }
        }

        // Clear progress across all possible ID variants
        val rawId = tmdbId?.toString() ?: mediaId.removePrefix("tmdb_").removePrefix("anilist_")
        val legacyPrefix = "tmdb_$rawId"
        
        val idVariants = mutableSetOf(mediaId, legacyPrefix, rawId, "tmdb_m_$rawId", "tmdb_t_$rawId")
        entity?.let { idVariants.add(it.id) }

        idVariants.forEach { id ->
            db.userProgressDao().deleteByMediaId(id)
            db.episodeWatchedDao().deleteByMediaId(id)
        }

        // SYNC TO FIRESTORE: Remove from cloud watchlist
        if (userPrefs.cloudSyncEnabled.first()) {
            try {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    firestore.collection("users").document(uid)
                        .collection("watchlist").document(mediaId)
                        .delete().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync watchlist removal: ${e.message}")
            }
        }

        // SYNC TO GRAPH: Unmark in any universes containing this media
        if (tmdbId != null) {
            try {
                val universes = watchOrderRepository.findUniversesForMedia(tmdbId).first()
                universes.forEach { universe ->
                    watchOrderRepository.setNodeCompletionDirect(universe.id, mediaId, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync removal to graph: ${e.message}")
            }
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

    /**
     * Synchronizes all user data from Firestore to the local Room database.
     * Call this after a successful login.
     */
    suspend fun syncAllFromCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        try {
            // 1. Sync Watchlist
            val watchlistSnap = firestore.collection("users").document(uid)
                .collection("watchlist").get().await()
            val watchlist = watchlistSnap.documents.mapNotNull { it.toObject(UserProgressEntity::class.java) }
            Log.d(TAG, "Sync: found ${watchlist.size} watchlist items in cloud")
            watchlist.forEach { db.userProgressDao().upsert(it) }

            // 1.5 Backfill missing media metadata from TMDB in parallel
            supervisorScope {
                watchlist.filter { db.mediaDao().getById(it.mediaId) == null }
                    .map { progress ->
                        async {
                            val tmdbId = extractTmdbId(progress.mediaId)
                            if (tmdbId != null) {
                                if (isMovieId(progress.mediaId)) {
                                    fetchAndCacheMovie(tmdbId, progress.mediaId)
                                } else {
                                    fetchAndCacheTv(tmdbId, progress.mediaId)
                                }
                            }
                        }
                    }.forEach { it.await() }
            }

            // 2. Sync Episode Progress
            val episodeSnap = firestore.collection("users").document(uid)
                .collection("episode_progress").get().await()
            val episodes = episodeSnap.documents.mapNotNull { doc ->
                val epId = doc.id
                val mediaId = doc.getString("media_id") ?: ""
                val watched = doc.getBoolean("watched") ?: false
                if (watched) EpisodeWatchedEntity(epId, mediaId) else null
            }
            Log.d(TAG, "Sync: found ${episodes.size} watched episodes in cloud")
            episodes.forEach { db.episodeWatchedDao().markWatched(it) }

            // 3. Sync Profile Data (Streak, Taste Completion)
            val profileSnap = firestore.collection("users").document(uid)
                .collection("profile").document("metadata").get().await()
            
            if (profileSnap.exists()) {
                val isTasteDone = profileSnap.getBoolean("is_taste_profile_completed") ?: false
                val lastActive = profileSnap.getLong("last_active_date") ?: 0L
                val streak = profileSnap.getLong("current_streak")?.toInt() ?: 0
                val genres = profileSnap.get("selected_genres") as? List<String> ?: emptyList()
                
                Log.d(TAG, "Sync: profile metadata found (tasteDone=$isTasteDone, streak=$streak)")
                userPrefs.setTasteProfileCompleted(isTasteDone)
                userPrefs.updateStreak(lastActive, streak)
                userPrefs.setSelectedGenres(genres.toSet())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync from cloud failed: ${e.message}", e)
            Result.failure(e)
        }
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

    suspend fun getWatchingList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        getListByState(TrackingState.WATCHING, sortType)
    }

    suspend fun getPlannedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        getListByState(TrackingState.PLANNED, sortType)
    }

    suspend fun getCompletedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        getListByState(TrackingState.COMPLETED, sortType)
    }

    suspend fun getDroppedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        getListByState(TrackingState.DROPPED, sortType)
    }

    suspend fun getPausedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        getListByState(TrackingState.PAUSED, sortType)
    }

    fun observeCompletedMediaIds(): Flow<Set<String>> =
        db.userProgressDao().observeCompletedMediaIds().map { it.toSet() }

    private fun sortSummaries(list: List<MediaSummary>, sortType: SortType) = when (sortType) {
        SortType.ALPHABETICAL            -> list.sortedBy { it.title }
        SortType.USER_RATING,
        SortType.GLOBAL_SCORE            -> list.sortedByDescending { it.voteAverage }
        SortType.DATE_ADDED              -> list
    }

    // ─── Episode watched ──────────────────────────────────────────────────────

    suspend fun toggleEpisodeWatched(
        episodeId: String,
        mediaId: String,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        val isWatched = db.episodeWatchedDao().isWatched(episodeId)
        
        // 1. Always commit to Room first
        if (isWatched) db.episodeWatchedDao().unmarkWatched(episodeId)
        else db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(episodeId, mediaId))
        val nowWatched = !isWatched

        // 2. Sync gate
        val syncEnabled = userPrefs.cloudSyncEnabled.first()
        if (!syncEnabled) return@withContext nowWatched

        // 3. Online: mirror to Firestore immediately
        if (watchOrderRepository.isNetworkAvailable(context)) {
            watchOrderRepository.mirrorEpisodeWatchedToFirestore(episodeId, mediaId, nowWatched)
                .onFailure { e ->
                    Log.w(TAG, "Firestore episode mirror failed — queuing: ${e.message}")
                    db.pendingSyncTaskDao().insert(
                        PendingSyncTaskEntity(
                            taskType  = TaskType.EPISODE_WATCHED,
                            episodeId = episodeId,
                            mediaId   = mediaId,
                            completed = nowWatched
                        )
                    )
                    SyncWorker.enqueue(context)
                }
        } else {
            // 4. Offline: queue for later
            db.pendingSyncTaskDao().insert(
                PendingSyncTaskEntity(
                    taskType   = TaskType.EPISODE_WATCHED,
                    episodeId  = episodeId,
                    mediaId    = mediaId,
                    completed  = nowWatched
                )
            )
            Log.i(TAG, "Queued offline mutation: EPISODE_WATCHED $episodeId")
            SyncWorker.enqueue(context)
        }
        
        nowWatched
    }

    suspend fun markAllPreviousAsWatched(mediaId: String, upToAbsoluteNumber: Int) =
        withContext(Dispatchers.IO) {
            val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
                .filter { it.absoluteEpisodeNumber < upToAbsoluteNumber }
            
            episodes.forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
            syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
        }

    suspend fun markPreviousEpisodesAsWatchedSequentially(
        mediaId: String, targetSeason: Int, targetEpisode: Int
    ) = withContext(Dispatchers.IO) {
        val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
            .filter { it.seasonNumber < targetSeason ||
                (it.seasonNumber == targetSeason && it.episodeNumber < targetEpisode) }
        
        episodes.forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
    }

    suspend fun markAllAsWatched(mediaId: String) = withContext(Dispatchers.IO) {
        val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
        episodes.forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
    }

    suspend fun markSeasonAsWatched(mediaId: String, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val seasonId = "${mediaId}_s$seasonNumber"
        val episodes = db.episodeDao().getEpisodesBySeason(seasonId)
        episodes.forEach { db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(it.id, mediaId)) }
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
    }

    suspend fun unmarkSeasonAsWatched(mediaId: String, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val seasonId = "${mediaId}_s$seasonNumber"
        val episodes = db.episodeDao().getEpisodesBySeason(seasonId)
        db.episodeWatchedDao().unmarkSeasonWatched(mediaId, "${mediaId}_s${seasonNumber}e%")
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, false)
    }

    private suspend fun syncEpisodesToFirestore(mediaId: String, episodeIds: List<String>, watched: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (!userPrefs.cloudSyncEnabled.first()) return

        try {
            supervisorScope {
                episodeIds.chunked(500).map { batchIds ->
                    async {
                        val batch = firestore.batch()
                        batchIds.forEach { epId ->
                            val docRef = firestore.collection("users").document(uid)
                                .collection("episode_progress").document(epId)
                            batch.set(docRef, mapOf(
                                "media_id" to mediaId,
                                "watched" to watched,
                                "updated_at" to FieldValue.serverTimestamp()
                            ), SetOptions.merge())
                        }
                        batch.commit().await()
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bulk episode sync failed: ${e.message}")
        }
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

    suspend fun getTrending(providerIds: Set<Int> = emptySet()): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            
            if (providerIds.isEmpty()) {
                val response1 = apiService.getTrending(page = 1)
                val response2 = apiService.getTrending(page = 2)
                if (response1.isSuccessful) response1.body()?.results?.let { results.addAll(it) }
                if (response2.isSuccessful) response2.body()?.results?.let { results.addAll(it) }
            } else {
                val providersStr = providerIds.joinToString("|")
                // Fetch popular on these platforms
                val mResp = apiService.discoverMovies(providerIds = providersStr, page = 1)
                val tResp = apiService.discoverTvShows(providerIds = providersStr, page = 1)
                
                // Add mediaType manually since discover results don't have it (it's implicit)
                mResp.body()?.results?.forEach { results.add(it.copy(mediaType = "movie")) }
                tResp.body()?.results?.forEach { results.add(it.copy(mediaType = "tv")) }
            }
            
            results.forEach { result ->
                if (result.mediaType == "movie" || result.mediaType == "tv") {
                    val mediaId = buildMediaId(result.id, result.mediaType)
                    if (db.mediaDao().getById(mediaId) == null)
                        db.mediaDao().upsert(result.toMinimalEntity(mediaId))
                }
            }
            results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .mapNotNull { it.toSummary() }
                .distinctBy { it.id }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun discoverByGenre(
        category: TmdbConfig.DiscoveryCategory,
        providerIds: Set<Int> = emptySet()
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val providersStr = providerIds.joinToString("|").takeIf { it.isNotBlank() }
            
            // If providers are selected, fetch 2 pages to ensure we have enough "Great" shows
            val pagesToFetch = if (providerIds.isNotEmpty()) 2 else 1
            
            val movieResults = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            val tvResults = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            
            for (page in 1..pagesToFetch) {
                val mResp = apiService.discoverMovies(genreId = category.movieGenreId.toString(), providerIds = providersStr, page = page)
                if (mResp.isSuccessful) mResp.body()?.results?.let { movieResults.addAll(it) }
                
                val tResp = apiService.discoverTvShows(genreId = category.tvGenreId.toString(), providerIds = providersStr, page = page)
                if (tResp.isSuccessful) tResp.body()?.results?.let { tvResults.addAll(it) }
            }

            movieResults.forEach { result ->
                val id = buildMediaId(result.id, "movie")
                if (db.mediaDao().getById(id) == null) db.mediaDao().upsert(result.toMinimalEntity(id, explicitIsMovie = true))
            }
            tvResults.forEach { result ->
                val id = buildMediaId(result.id, "tv")
                if (db.mediaDao().getById(id) == null) db.mediaDao().upsert(result.toMinimalEntity(id, explicitIsMovie = false))
            }

            val movieSummaries = movieResults.mapNotNull { it.toSummary(explicitIsMovie = true) }.distinctBy { it.id }
            val tvSummaries    = tvResults.mapNotNull  { it.toSummary(explicitIsMovie = false) }.distinctBy { it.id }
            
            // Interleave and ensure we have a good number of results
            val results = movieSummaries.zip(tvSummaries) { m, t -> listOf(m, t) }.flatten() +
                movieSummaries.drop(tvSummaries.size) + tvSummaries.drop(movieSummaries.size)
            
            results.take(40)
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

    suspend fun syncProfileToCloud(isTasteDone: Boolean, lastActive: Long, streak: Int, genres: Set<String> = emptySet()) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val data = mutableMapOf<String, Any>(
                "is_taste_profile_completed" to isTasteDone,
                "last_active_date" to lastActive,
                "current_streak" to streak
            )
            if (genres.isNotEmpty()) {
                data["selected_genres"] = genres.toList()
            }

            firestore.collection("users").document(uid)
                .collection("profile").document("metadata")
                .set(data, SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync profile to cloud: ${e.message}")
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
        posterUrl = posterUrl?.takeIf { TmdbConfig.isValidImageUrl(it) }, 
        backdropUrl = backdropUrl?.takeIf { TmdbConfig.isValidImageUrl(it) },
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
        mediaId: String,
        explicitIsMovie: Boolean? = null
    ): MediaEntity {
        val isMovie = explicitIsMovie ?: (mediaType == "movie")
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        return MediaEntity(
            id = mediaId, tmdbId = extractTmdbId(mediaId) ?: id,
            anilistId = null, title = title ?: name ?: "", originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (isMovie) "MOVIE" else "TV_SHOW",
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage?.toFloat() ?: 0f, voteCount = voteCount ?: 0,
            runtime = null, numberOfSeasons = null, numberOfEpisodes = null,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = null, castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(
        explicitIsMovie: Boolean? = null
    ): MediaSummary? {
        val effectiveType = mediaType ?: if (explicitIsMovie == true) "movie" else if (explicitIsMovie == false) "tv" else null
        if (effectiveType == null || (effectiveType != "movie" && effectiveType != "tv")) return null
        
        val mediaId    = buildMediaId(id, effectiveType)
        val isMovie    = effectiveType == "movie"
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
