package com.example.watchorderengine.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.graph.FranchiseAnchors
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.sync.SyncWorker
import com.example.watchorderengine.network.JikanApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.gemini.GeminiResult
import com.example.watchorderengine.network.gemini.GeminiService
import com.example.watchorderengine.network.model.TmdbWatchProvider
import com.example.watchorderengine.network.model.TmdbWatchProviderCountry
import androidx.paging.map
import com.example.watchorderengine.util.retry
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaRepository"

@Singleton
class MediaRepository @Inject constructor(
    private val db: WatchOrderDatabase,
    private val moshi: Moshi,
    private val apiService: TmdbApiService,
    private val jikanApiService: JikanApiService,
    private val geminiService: GeminiService,
    private val watchOrderRepository: WatchOrderRepository,
    private val userPrefs: UserPreferencesRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // ─── Repository-owned scope for long-running background tasks ─────────────────
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Track media IDs currently being fetched to avoid redundant requests during list scroll. */
    private val pendingFetches = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
    internal fun extractTmdbId(mediaId: String): Int? =
        mediaId.substringAfterLast("_").toIntOrNull()

    internal fun isMovieId(mediaId: String): Boolean = mediaId.contains("_m_")
    internal fun isTvId(mediaId: String):    Boolean = mediaId.contains("_t_")

    /**
     * Returns a set of normalized episode IDs that the user has watched for this show.
     */
    suspend fun getNormalizedWatchedIds(mediaId: String): Set<String> = withContext(Dispatchers.IO) {
        db.episodeWatchedDao().getWatchedIds(mediaId).map { id ->
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

    /**
     * Ensures that a MediaEntity exists in the local Room database for the given node.
     * This prevents "Unknown Movie" labels in the watchlist if the user marks a
     * timeline as completed before ever opening the individual media detail screens.
     */
    suspend fun ensureMetadataCached(node: MediaNode) = withContext(Dispatchers.IO) {
        val mediaId = buildMediaId(node.tmdb_id, node.tmdb_media_type)
        if (db.mediaDao().getById(mediaId) != null) return@withContext

        // Pre-seed with the data we already have from the timeline/Gemini
        db.mediaDao().upsert(
            MediaEntity(
                id = mediaId,
                tmdbId = node.tmdb_id,
                anilistId = null,
                title = node.title,
                originalTitle = node.title,
                overview = "",
                tagline = "",
                status = "RELEASED",
                posterUrl = node.posterUrl,
                backdropUrl = null,
                mediaCategory = if (node.tmdb_media_type == "movie") "MOVIE" else "TV_SHOW",
                genres = emptyList(),
                ageRating = "NR",
                voteAverage = 0f,
                voteCount = 0,
                runtime = null,
                numberOfSeasons = null,
                numberOfEpisodes = null,
                releaseDate = "${node.releaseYear}-01-01",
                releaseYear = node.releaseYear.toString(),
                trailerKey = null,
                watchProvidersJson = "[]",
                castJson = "[]",
                recommendationsJson = "[]",
                arcsJson = "[]"
            )
        )

        // Optionally trigger a full fetch in the background to get overview/genres/etc.
        repositoryScope.launch {
            refreshDetail(mediaId)
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

    /**
     * Ensures that the full details (seasons, episodes) for a media item are
     * fetched and cached in Room. If it's a TV show and it only has a minimal
     * entity (e.g. from search), this will trigger a full TMDB refresh.
     */
    suspend fun ensureDetailsFetched(mediaId: String) = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId)
        if (entity == null || (isTvId(mediaId) && entity.numberOfSeasons == null)) {
            refreshDetail(mediaId)
        }
    }

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
            originalLanguage = entity.originalLanguage,
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
            val response = retry { apiService.getMovie(tmdbId) }
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
            val response = retry { apiService.getTvShow(tmdbId) }
            if (!response.isSuccessful || response.body() == null) return false
            val body = response.body()!!
            val entity = body.toMediaEntity(mediaId)
            db.mediaDao().upsert(entity)

            if (body.seasons != null) {
                // FIX: Fetch seasons SERIALLY and build the running offset from the
                // ACTUAL episode count returned by each detail response — not from
                // body.seasons[n].episodeCount (the TV-show-header summary field).
                //
                // WHY THIS MATTERS FOR ONE PIECE:
                //   TMDB's summary says Season 14 has 118 episodes.
                //   The actual /season/14 response may return 103 (some unaired or
                //   reorganised). If we use 118 as the offset seed, Season 15's
                //   absolute numbers start 15 too high, and the drift compounds
                //   across 21 seasons. By Season 20, absolute numbers can be 50+
                //   off, so Jikan's mal_id filler list never matches any Room row.
                //
                // Serial fetching costs a few extra seconds vs parallel but
                // guarantees correct absolute numbers for every long-running anime.

                var cumulativeOffset = 0
                body.seasons.sortedBy { it.seasonNumber }.forEach { seasonSummary ->
                    // Season 0 (Specials) episodes get offset=0 but their absolute
                    // numbers are never used for filler matching (guarded elsewhere),
                    // so their offset doesn't matter — just don't add them to the
                    // cumulative count.
                    val offset = if (seasonSummary.seasonNumber > 0) cumulativeOffset else 0

                    val actualEpisodeCount = refreshSeasonEpisodesReturnCount(
                        tmdbId       = tmdbId,
                        mediaId      = mediaId,
                        seasonNumber = seasonSummary.seasonNumber,
                        episodeCount = seasonSummary.episodeCount,
                        offset       = offset
                    )

                    if (seasonSummary.seasonNumber > 0) {
                        cumulativeOffset += actualEpisodeCount
                    }
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

    /**
     * Fetches one season's episodes, writes them to Room, and returns the ACTUAL
     * number of episodes that were returned by the API.
     *
     * The return value is used as the offset increment for the next season so that
     * [absoluteEpisodeNumber] is always derived from real data, not the (possibly
     * wrong) [episodeCount] summary field in the TV-show header.
     */
    private suspend fun refreshSeasonEpisodesReturnCount(
        tmdbId: Int, mediaId: String, seasonNumber: Int, episodeCount: Int, offset: Int
    ): Int {
        return try {
            val response = retry { apiService.getTvSeason(tmdbId, seasonNumber) }
            if (!response.isSuccessful || response.body() == null) return episodeCount
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

            val episodes = seasonBody.episodes ?: return 0

            val entities = episodes.mapIndexed { idx, ep ->
                val existing = existingEpisodes[ep.episodeNumber]
                EpisodeEntity(
                    id = "${mediaId}_s${seasonNumber}e${ep.episodeNumber}",
                    seasonId = seasonId, mediaId = mediaId,
                    episodeNumber = ep.episodeNumber, seasonNumber = seasonNumber,
                    absoluteEpisodeNumber = offset + idx + 1,   // offset from ACTUAL count
                    title = ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview ?: "", airDate = ep.airDate, runtime = ep.runtime,
                    stillUrl = TmdbConfig.buildImageUrl(ep.stillPath, TmdbConfig.PosterSize.HD),
                    voteAverage = ep.voteAverage?.toFloat() ?: 0f,
                    episodeType = existing?.episodeType ?: "CANON",
                    arcName = existing?.arcName
                )
            }

            if (entities.isNotEmpty()) db.episodeDao().upsertAll(entities)

            // Return the ACTUAL count so the next season's offset is correct
            entities.size

        } catch (e: Exception) {
            Log.e(TAG, "Season $seasonNumber fetch failed for $mediaId", e)
            episodeCount  // fall back to header count on network error
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

    fun observeMaxWatchedAbsoluteEpisode(mediaId: String): Flow<Int> {
        return db.episodeDao().observeMaxWatchedAbsoluteEpisode(mediaId)
            .map { it ?: 0 }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
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

        val rawItems: List<com.example.watchorderengine.network.gemini.RawMediaItem> = if (isMovie) {
            buildMovieRawItems(entity, mediaId)
        } else {
            buildTvRawItems(entity, mediaId)
        }

        if (rawItems.isEmpty()) {
            return@withContext "No data found for this title — open it once before generating a watch order."
        }

        val result = geminiService.generateWatchOrder(showTitle = entity.title, rawItems = rawItems)

        when (result) {
            is com.example.watchorderengine.network.gemini.GeminiResult.Error -> result.message
            is com.example.watchorderengine.network.gemini.GeminiResult.Success -> {
                val sortedNodes = result.watchOrder.nodes
                val sortedEdges = result.watchOrder.edges
                Log.d(TAG, "SORTED: ${sortedNodes.size} nodes, ${sortedEdges.size} edges")

                watchOrderRepository.clearGeneratedUniverse(mediaId)
                val publishResult = watchOrderRepository.publishSortedUniverse(
                    universeId     = mediaId,
                    universeName   = entity.title,
                    coverUrl       = entity.posterUrl ?: "",
                    rawItems       = rawItems,
                    sortedNodes    = sortedNodes,
                    sortedEdges    = sortedEdges,
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

    // ─── Movie raw-item builder (franchise-anchor aware) ──────────────────────

    private suspend fun buildMovieRawItems(
        entity: MediaEntity,
        mediaId: String
    ): List<com.example.watchorderengine.network.gemini.RawMediaItem> {
        return try {
            // Step 1: fetch the live movie detail to get belongs_to_collection
            val detailResponse = apiService.getMovie(entity.tmdbId)
            val subCollectionId = detailResponse.body()?.belongsToCollection?.id

            // Step 2: franchise-anchor reverse lookup
            val rootCollectionId = FranchiseAnchors.resolveRootCollectionId(
                movieTmdbId     = entity.tmdbId,
                subCollectionId = subCollectionId
            )

            val targetCollectionId = rootCollectionId ?: subCollectionId

            if (targetCollectionId != null && targetCollectionId > 0) {
                val franchiseLabel = FranchiseAnchors.labelFor(targetCollectionId)
                    ?: detailResponse.body()?.belongsToCollection?.name
                    ?: entity.title
                Log.d(TAG, "Expanding franchise '$franchiseLabel' via collection $targetCollectionId (${
                    if (rootCollectionId != null) "FRANCHISE ANCHOR" else "sub-collection"
                })")

                val collectionResponse = apiService.getMovieCollection(targetCollectionId)
                val parts = collectionResponse.body()?.parts
                    ?.filter { !it.releaseDate.isNullOrBlank() }
                    ?.sortedBy { it.releaseDate }

                if (!parts.isNullOrEmpty()) {
                    Log.d(TAG, "Collection expanded to ${parts.size} films for Gemini")
                    return parts.map { part ->
                        val partMediaId = buildMediaId(part.id, "movie")
                        // Pre-cache minimal entity so timeline navigation is instant
                        if (db.mediaDao().getById(partMediaId) == null) {
                            db.mediaDao().upsert(
                                MediaEntity(
                                    id = partMediaId, tmdbId = part.id, anilistId = null,
                                    title = part.title, originalTitle = part.title,
                                    overview = part.overview ?: "", tagline = "", status = "",
                                    posterUrl   = TmdbConfig.buildImageUrl(part.posterPath),
                                    backdropUrl = null, mediaCategory = "MOVIE",
                                    genres = emptyList(), ageRating = "NR",
                                    voteAverage = part.voteAverage?.toFloat() ?: 0f,
                                    voteCount = 0, runtime = null,
                                    numberOfSeasons = null, numberOfEpisodes = null,
                                    releaseDate = part.releaseDate,
                                    releaseYear = part.releaseDate?.take(4) ?: "",
                                    trailerKey = null,
                                    watchProvidersJson = "[]", castJson = "[]",
                                    recommendationsJson = "[]", arcsJson = "[]"
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
                            source      = if (rootCollectionId != null) "TMDB_FRANCHISE" else "TMDB_COLLECTION",
                            posterPath  = part.posterPath
                        )
                    }
                }
            }

            // Step 3: fallback — try a collection keyword search using the title
            val keywordSearch = apiService.searchCollection(
                query = stripTitleSuffix(entity.title)
            )
            val bestCollection = keywordSearch.body()?.results
                ?.filter { it.title.contains(entity.title.take(5), ignoreCase = true) }
                ?.firstOrNull()

            if (bestCollection != null) {
                Log.d(TAG, "Collection keyword search found: '${bestCollection.title}' (id=${bestCollection.id})")
                val fallbackParts = apiService.getMovieCollection(bestCollection.id).body()?.parts
                    ?.filter { !it.releaseDate.isNullOrBlank() }
                    ?.sortedBy { it.releaseDate }
                if (!fallbackParts.isNullOrEmpty()) {
                    return fallbackParts.map { part ->
                        com.example.watchorderengine.network.gemini.RawMediaItem(
                            itemId      = buildMediaId(part.id, "movie"),
                            title       = part.title,
                            overview    = part.overview ?: "",
                            contentType = "MOVIE",
                            releaseDate = part.releaseDate,
                            tmdbId      = part.id,
                            source      = "TMDB_SEARCH_COLLECTION",
                            posterPath  = part.posterPath
                        )
                    }
                }
            }

            // Final fallback: standalone movie, no franchise
            Log.d(TAG, "No franchise found for '${entity.title}' — using single-item list")
            listOf(
                com.example.watchorderengine.network.gemini.RawMediaItem(
                    itemId      = mediaId,
                    title       = entity.title,
                    overview    = entity.overview,
                    contentType = "MOVIE",
                    releaseDate = entity.releaseDate,
                    tmdbId      = entity.tmdbId,
                    source      = "TMDB_MOVIE",
                    posterPath  = entity.posterUrl
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Movie franchise expansion failed for '${entity.title}': ${e.message}")
            listOf(
                com.example.watchorderengine.network.gemini.RawMediaItem(
                    itemId      = mediaId,
                    title       = entity.title,
                    overview    = entity.overview,
                    contentType = "MOVIE",
                    releaseDate = entity.releaseDate,
                    tmdbId      = entity.tmdbId,
                    source      = "TMDB_MOVIE",
                    posterPath  = entity.posterUrl
                )
            )
        }
    }

    // ─── TV raw-item builder (cross-series season aggregation) ────────────────

    private suspend fun buildTvRawItems(
        entity: MediaEntity,
        mediaId: String
    ): List<com.example.watchorderengine.network.gemini.RawMediaItem> {
        val baseTitle = stripTitleSuffix(entity.title)
        Log.d(TAG, "TV franchise search: '${entity.title}' → base keyword '$baseTitle'")

        val searchResults = try {
            val response = apiService.searchTv(query = baseTitle)
            response.body()?.results
                ?.filter { it.mediaType == null || it.mediaType == "tv" }
                ?.take(5)
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "TV franchise search failed for '$baseTitle': ${e.message}")
            emptyList()
        }

        val showsToProcess: List<Pair<Int, String>> = buildList {
            add(entity.tmdbId to entity.title)
            for (result in searchResults) {
                if (result.id != entity.tmdbId) {
                    add(result.id to (result.title ?: result.name ?: ""))
                }
            }
        }.distinctBy { it.first }.take(6)

        Log.d(TAG, "TV franchise shows to process: ${showsToProcess.map { it.second }}")

        val allSeasonItems = mutableListOf<com.example.watchorderengine.network.gemini.RawMediaItem>()

        for ((tmdbId, showTitle) in showsToProcess) {
            val showMediaId = buildMediaId(tmdbId, "tv")

            var dbEntity = db.mediaDao().getById(showMediaId)
            if (dbEntity == null) {
                Log.d(TAG, "Fetching & caching new TV franchise entry: '$showTitle' (tmdb=$tmdbId)")
                val cached = fetchAndCacheTv(tmdbId, showMediaId)
                if (!cached) {
                    Log.w(TAG, "Could not cache '$showTitle' — skipping from franchise list")
                    continue
                }
                dbEntity = db.mediaDao().getById(showMediaId)
            }

            val seasons = db.seasonDao().getSeasonsByMedia(showMediaId).sortedBy { it.seasonNumber }
            if (seasons.isEmpty()) {
                Log.d(TAG, "No seasons cached for '$showTitle' — skipping")
                continue
            }

            for (season in seasons) {
                allSeasonItems.add(
                    com.example.watchorderengine.network.gemini.RawMediaItem(
                        itemId       = season.id,
                        title        = "$showTitle — ${season.name}",
                        overview     = season.overview.ifBlank { dbEntity?.overview ?: "" },
                        contentType  = "SERIES",
                        seasonNumber = season.seasonNumber,
                        episodeCount = season.episodeCount,
                        releaseDate  = season.airDate,
                        tmdbId       = tmdbId,
                        source       = if (tmdbId == entity.tmdbId) "TMDB_SEASON" else "TMDB_RELATED_SEASON",
                        posterPath   = season.posterUrl ?: dbEntity?.posterUrl
                    )
                )
            }
        }

        if (allSeasonItems.isEmpty()) {
            Log.d(TAG, "Cross-series search empty — falling back to original seasons for '${entity.title}'")
            return db.seasonDao().getSeasonsByMedia(mediaId).sortedBy { it.seasonNumber }.map { season ->
                com.example.watchorderengine.network.gemini.RawMediaItem(
                    itemId       = season.id,
                    title        = "${entity.title} — ${season.name}",
                    overview     = season.overview.ifBlank { entity.overview },
                    contentType  = "SERIES",
                    seasonNumber = season.seasonNumber,
                    episodeCount = season.episodeCount,
                    releaseDate  = season.airDate,
                    tmdbId       = entity.tmdbId,
                    source       = "TMDB_SEASON",
                    posterPath   = season.posterUrl ?: entity.posterUrl
                )
            }
        }

        Log.d(TAG, "Built ${allSeasonItems.size} season items across ${showsToProcess.size} TV shows for Gemini")
        return allSeasonItems
    }

    // ─── Title suffix stripper ─────────────────────────────────────────────────

    private fun stripTitleSuffix(title: String): String {
        var base = title.split(":", "/").first().trim()

        val suffixes = listOf(
            "shippuden", "brotherhood", "super", "zero", "gt", "z",
            "kai", "next generation", "evolution", "uprising", "origins",
            "season", "part", "volume", "chapter", "arc",
            "and the", "of the", "in the"
        )
        for (suffix in suffixes) {
            if (base.lowercase().endsWith(" $suffix")) {
                base = base.dropLast(suffix.length + 1).trim()
            }
        }

        return if (base.length >= 3) base else title
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
            val now = System.currentTimeMillis()
            val current = db.userProgressDao().getProgress(mediaId)
            
            val entity = current?.copy(
                trackingState = state.name,
                updatedAt = now
            ) ?: UserProgressEntity(
                mediaId = mediaId,
                trackingState = state.name,
                updatedAt = now
            )

            db.userProgressDao().upsert(entity)

            val tmdbId = extractTmdbId(mediaId)

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

            // Ensure MediaEntity knows it is in the watchlist
            db.mediaDao().updateWatchlistStatus(mediaId, true)
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

        // Set inWatchlist to false
        db.mediaDao().updateWatchlistStatus(mediaId, false)
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
    suspend fun syncAllFromCloud(
        onProgress: (SyncProgress) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        fun updateProgress(stage: String, progress: Float) {
            onProgress(SyncProgress(stage, progress, MOVIE_FACTS.random()))
        }

        try {
            updateProgress("Connecting to Engine...", 0.05f)
            
            // 0. IMPORTANT: Start with a clean slate to prevent account pollution
            db.userProgressDao().clearAll()

            // 1. Sync Watchlist
            try {
                updateProgress("Syncing Watchlist...", 0.20f)
                val watchlistSnap = firestore.collection("users").document(uid)
                    .collection("watchlist").get().await()
                
                watchlistSnap.documents.forEach { doc ->
                    try {
                        val mId = doc.getString("mediaId") ?: doc.getString("media_id") ?: doc.id
                        val state = doc.getString("trackingState") ?: doc.getString("tracking_state") ?: "PLANNED"
                        
                        val entity = UserProgressEntity(
                            mediaId = mId,
                            trackingState = state,
                            currentSeasonNumber = doc.getLong("currentSeasonNumber")?.toInt() ?: doc.getLong("current_season_number")?.toInt() ?: 0,
                            currentEpisodeNumber = doc.getLong("currentEpisodeNumber")?.toInt() ?: doc.getLong("current_episode_number")?.toInt() ?: 0,
                            userRating = doc.getDouble("userRating")?.toFloat() ?: doc.getDouble("user_rating")?.toFloat(),
                            userNotes = doc.getString("userNotes") ?: doc.getString("user_notes") ?: "",
                            priorityTag = doc.getString("priorityTag") ?: doc.getString("priority_tag") ?: "NONE",
                            updatedAt = doc.getLong("updatedAt") ?: doc.getLong("updated_at") ?: System.currentTimeMillis()
                        )
                        db.userProgressDao().upsert(entity)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse watchlist item ${doc.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watchlist sync failed: ${e.message}")
            }

            // 1.2 Sync Graph/Universe Progress (Updates existing entities with graph data)
            try {
                updateProgress("Restoring Graph Progress...", 0.40f)
                val progressSnap = firestore.collection("users").document(uid)
                    .collection("progress").get().await()
                
                progressSnap.documents.forEach { doc ->
                    try {
                        val mId = doc.getString("mediaId") ?: doc.getString("media_id") ?: doc.id
                        if (mId.isBlank()) return@forEach

                        val existing = db.userProgressDao().getByMediaId(mId)
                        
                        // If it's a graph-only show, we create it. 
                        // If it's already in watchlist, we just add the graph specific fields.
                        val updatedEntity = (existing ?: UserProgressEntity(mediaId = mId)).copy(
                            completedNodeIds = doc.get("completed_node_ids") as? List<String> ?: emptyList(),
                            activeRoute = doc.getString("active_route"),
                            spoilerShieldEnabled = doc.getBoolean("spoiler_shield_enabled") ?: false
                        )
                        
                        // Preserve existing trackingState if it's more specific than Firestore's generic progress record
                        if (existing != null) {
                            val cloudState = doc.getString("trackingState") ?: doc.getString("tracking_state")
                            if (cloudState != null) {
                                updatedEntity.trackingState = cloudState
                            }
                        } else {
                            updatedEntity.trackingState = doc.getString("trackingState") ?: doc.getString("tracking_state") ?: "PLANNED"
                        }

                        db.userProgressDao().upsert(updatedEntity)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse universe progress ${doc.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Graph progress sync failed: ${e.message}")
            }

            // 1.5 Backfill missing media metadata
            try {
                val trackedItems = db.userProgressDao().getAll()
                val watchlistIds = trackedItems.map { it.mediaId }
                
                if (watchlistIds.isNotEmpty()) {
                    val totalToBackfill = watchlistIds.size
                    watchlistIds.chunked(8).forEachIndexed { chunkIndex, batch ->
                        val currentProgress = 0.50f + (chunkIndex.toFloat() / (watchlistIds.size / 8f).coerceAtLeast(1f)) * 0.35f
                        updateProgress("Fetching Metadata ($totalToBackfill items)...", currentProgress)
                        
                        supervisorScope {
                            batch.map { mediaId ->
                                async {
                                    // Ensure it's a real media ID (starts with tmdb_ or anilist_)
                                    if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) {
                                        if (db.mediaDao().getById(mediaId) == null) {
                                            val tmdbId = extractTmdbId(mediaId)
                                            if (tmdbId != null) {
                                                fetchAndCacheMediaOnly(tmdbId, mediaId)
                                            }
                                        }
                                    } else {
                                        // Cleanup invalid/wrong IDs that shouldn't be here
                                        db.userProgressDao().deleteByMediaId(mediaId)
                                    }
                                }
                            }.forEach { it.await() }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Metadata backfill failed: ${e.message}")
            }

            // 2. Sync Episode Progress
            try {
                updateProgress("Syncing Episode History...", 0.85f)
                val episodeSnap = firestore.collection("users").document(uid)
                    .collection("episode_progress").get().await()
                val episodes = episodeSnap.documents.mapNotNull { doc ->
                    val epId = doc.id
                    val mediaId = doc.getString("media_id") ?: ""
                    val watched = doc.getBoolean("watched") ?: false
                    if (watched) EpisodeWatchedEntity(epId, mediaId) else null
                }
                Log.d(TAG, "Sync: found ${episodes.size} watched episodes in cloud")
                db.episodeWatchedDao().markWatchedAll(episodes)
            } catch (e: Exception) {
                Log.e(TAG, "Episode progress sync failed: ${e.message}")
            }

            // 3. Sync Profile Data
            try {
                updateProgress("Finalizing Profile...", 0.95f)
                
                // 3.1 Private Metadata (Streak, Taste Done)
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

                // 3.2 Public Profile (Username, Avatar)
                val publicProfileSnap = firestore.collection("user_profiles").document(uid).get().await()
                if (publicProfileSnap.exists()) {
                    val cloudName = publicProfileSnap.getString("username")
                    val cloudAvatar = publicProfileSnap.getString("avatarUrl")
                    if (!cloudName.isNullOrBlank()) {
                        userPrefs.updateUsername(cloudName)
                    }
                    if (!cloudAvatar.isNullOrBlank()) {
                        userPrefs.updateAvatarUrl(cloudAvatar)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Profile metadata sync failed: ${e.message}")
            }

            updateProgress("Sync Complete!", 1.0f)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync from cloud failed completely: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Optimized fetcher that only gets the basic Media metadata without heavy season/episode detail. */
    suspend fun fetchAndCacheMediaOnly(tmdbId: Int, mediaId: String): Boolean {
        return try {
            if (isMovieId(mediaId)) {
                fetchAndCacheMovie(tmdbId, mediaId)
            } else {
                val response = com.example.watchorderengine.util.retry { apiService.getTvShow(tmdbId) }
                if (!response.isSuccessful || response.body() == null) return false
                val body = response.body()!!
                val entity = body.toMediaEntity(mediaId)
                db.mediaDao().upsert(entity)
                true
            }
        } catch (e: Exception) { 
            Log.w(TAG, "fetchAndCacheMediaOnly failed for $mediaId: ${e.message}")
            false 
        }
    }

    fun observeListByStatePaged(
        trackingState: TrackingState
    ): Flow<androidx.paging.PagingData<MediaSummary>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { db.userProgressDao().getByStatePaging(trackingState.name) }
        ).flow.map { pagingData ->
            pagingData.map { joined ->
                val progress = joined.progress
                val entity = joined.media
                val tmdbId = extractTmdbId(progress.mediaId) ?: entity?.tmdbId ?: 0

                if (entity == null && tmdbId > 0 && !pendingFetches.contains(progress.mediaId)) {
                    // Auto-repair missing metadata in the background
                    repositoryScope.launch {
                        pendingFetches.add(progress.mediaId)
                        try {
                            fetchAndCacheMediaOnly(tmdbId, progress.mediaId)
                        } finally {
                            pendingFetches.remove(progress.mediaId)
                        }
                    }
                }
                
                entity?.toSummary(
                    trackingState, 
                    PriorityTag.valueOf(progress.priorityTag)
                ) ?: MediaSummary(
                    id = progress.mediaId,
                    tmdbId = tmdbId,
                    title = "Unknown",
                    posterUrl = null,
                    backdropUrl = null,
                    mediaCategory = MediaCategory.TV_SHOW,
                    voteAverage = 0f,
                    releaseYear = "",
                    trackingState = trackingState,
                    ageRating = "NR"
                )
            }
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

    fun observeCountByState(state: TrackingState): Flow<Int> =
        db.userProgressDao().observeCountByState(state.name)

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
            val now = System.currentTimeMillis()
            // 1. Optimized Room update
            db.episodeWatchedDao().markAllPreviousAsWatched(mediaId, upToAbsoluteNumber - 1, now)

            // 2. Optimized Firestore sync (still needs IDs for bulk update)
            val episodes = db.episodeDao().getEpisodesInRange(mediaId, 1, upToAbsoluteNumber - 1)
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
        val now = System.currentTimeMillis()
        // 1. Optimized Room update
        db.episodeWatchedDao().markAllAsWatched(mediaId, now)
        
        // 2. Optimized Firestore sync
        val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
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
                supervisorScope {
                    val d1 = async { apiService.getTrending(page = 1) }
                    val d2 = async { apiService.getTrending(page = 2) }
                    val d3 = async { apiService.getTrending(page = 3) }
                    
                    val r1 = d1.await()
                    val r2 = d2.await()
                    val r3 = d3.await()
                    
                    if (r1.isSuccessful) r1.body()?.results?.let { results.addAll(it) }
                    if (r2.isSuccessful) r2.body()?.results?.let { results.addAll(it) }
                    if (r3.isSuccessful) r3.body()?.results?.let { results.addAll(it) }
                }
            } else {
                val providersStr = providerIds.joinToString("|")
                // Fetch popular on these platforms
                val mResp = apiService.discoverMovies(providerIds = providersStr, page = 1)
                val tResp = apiService.discoverTvShows(providerIds = providersStr, page = 1)
                
                // Add mediaType manually since discover results don't have it (it's implicit)
                mResp.body()?.results?.forEach { results.add(it.copy(mediaType = "movie")) }
                tResp.body()?.results?.forEach { results.add(it.copy(mediaType = "tv")) }
            }
            
            // Filter out future releases from trending to avoid "coming soon" clutter
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            
            results.forEach { result ->
                if (result.mediaType == "movie" || result.mediaType == "tv") {
                    val mediaId = buildMediaId(result.id, result.mediaType)
                    if (db.mediaDao().getById(mediaId) == null)
                        db.mediaDao().upsert(result.toMinimalEntity(mediaId))
                }
            }

            results.filter { 
                (it.mediaType == "movie" || it.mediaType == "tv") && 
                !it.posterPath.isNullOrBlank() && // Must have a poster
                (it.releaseDate ?: it.firstAirDate ?: "").let { date -> date.isNotBlank() && date <= todayStr }
            }
                .mapNotNull { it.toSummary() }
                .distinctBy { it.id }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getRecentlyReleased(): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val now = java.util.Calendar.getInstance()
            val todayStr = sdf.format(now.time)
            
            // Recency: last 3 months (Narrower window for better quality)
            now.add(java.util.Calendar.MONTH, -3)
            val threeMonthsAgoStr = sdf.format(now.time)

            supervisorScope {
                // Fetch 2 pages to ensure we don't miss anything popular that was recently released
                for (page in 1..2) {
                    val mResp = async { 
                        apiService.discoverMovies(
                            sortBy = "popularity.desc", // Popular first, but within date range
                            releaseDateGte = threeMonthsAgoStr,
                            releaseDateLte = todayStr,
                            page = page
                        ) 
                    }
                    val tResp = async { 
                        apiService.discoverTvShows(
                            sortBy = "popularity.desc",
                            airDateGte = threeMonthsAgoStr,
                            airDateLte = todayStr,
                            page = page
                        ) 
                    }
                    
                    mResp.await().body()?.results?.forEach { results.add(it.copy(mediaType = "movie")) }
                    tResp.await().body()?.results?.forEach { results.add(it.copy(mediaType = "tv")) }
                }
            }
            
            results.forEach { result ->
                val mediaId = buildMediaId(result.id, result.mediaType)
                if (db.mediaDao().getById(mediaId) == null)
                    db.mediaDao().upsert(result.toMinimalEntity(mediaId))
            }

            results.distinctBy { it.id }
                .filter { !it.posterPath.isNullOrBlank() }
                .sortedByDescending { it.releaseDate ?: it.firstAirDate ?: "" } // Sort by date for the "Recently Released" feel
                .take(25)
                .mapNotNull { it.toSummary() }
        } catch (e: Exception) {
            Log.e(TAG, "getRecentlyReleased failed", e)
            emptyList()
        }
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

            val movieSummaries = movieResults
                .filter { !it.posterPath.isNullOrBlank() }
                .mapNotNull { it.toSummary(explicitIsMovie = true) }
                .distinctBy { it.id }
            val tvSummaries    = tvResults
                .filter { !it.posterPath.isNullOrBlank() }
                .mapNotNull  { it.toSummary(explicitIsMovie = false) }
                .distinctBy { it.id }
            
            // Interleave and ensure we have a good number of results
            val results = movieSummaries.zip(tvSummaries) { m, t -> listOf(m, t) }.flatten() +
                movieSummaries.drop(tvSummaries.size) + tvSummaries.drop(movieSummaries.size)
            
            // If we have very few results for a specific platform, try to fetch generic popular
            if (results.size < 10 && providerIds.isNotEmpty()) {
                val genericTrending = getTrending(providerIds)
                (results + genericTrending).distinctBy { it.id }.take(40)
            } else {
                results.take(40)
            }
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

    suspend fun countUserReviews(): Int = withContext(Dispatchers.IO) {
        try { db.reviewDao().countAll() } catch (e: Exception) { 0 }
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
                mediaId = mediaId, trackingState = "PLANNED", userRating = rating, updatedAt = now
            ))
        }

        // SYNC TO FIRESTORE
        if (userPrefs.cloudSyncEnabled.first()) {
            try {
                val uid = auth.currentUser?.uid ?: return@withContext
                val progress = db.userProgressDao().getProgress(mediaId)
                if (progress != null) {
                    firestore.collection("users").document(uid)
                        .collection("watchlist").document(mediaId)
                        .set(progress).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync rating to cloud: ${e.message}")
            }
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

    // ─── Jikan filler enrichment ──────────────────────────────────────────────

    /**
     * Returns true when a cached [MediaEntity] is likely an anime AND has not yet
     * been enriched with Jikan filler data.
     */
    suspend fun isAnimeEligibleForJikan(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext false
        if (entity.jikanFillerSynced) return@withContext false   // already done
        entity.originalLanguage == "ja" || entity.genres.contains("Animation")
    }

    /**
     * Launches Jikan filler enrichment in the **repository scope**, which is
     * independent of any ViewModel lifecycle.
     *
     * Safe to call from any coroutine context — it returns immediately and lets
     * the background job run to completion even if the caller is cancelled.
     * The [jikanFillerSynced] Room flag prevents duplicate runs across app sessions.
     */
    fun launchJikanEnrichmentIfNeeded(mediaId: String, showTitle: String) {
        repositoryScope.launch {
            if (!isAnimeEligibleForJikan(mediaId)) return@launch
            enrichEpisodesWithJikanFiller(mediaId, showTitle)
        }
    }

    /**
     * Fetches filler episode data from Jikan and writes it into the local Room
     * `episodes` table.  Call via [launchJikanEnrichmentIfNeeded] — never call
     * this directly from a ViewModel.
     */
    private suspend fun enrichEpisodesWithJikanFiller(mediaId: String, showTitle: String) {
        Log.d("JikanStatus", "Enrichment started for: $showTitle (mediaId: $mediaId)")
        try {
            // Step 1: Resolve MAL ID (type="tv" filter is applied by JikanApiService)
            val searchResponse = jikanApiService.searchAnime(showTitle)
            Log.d("JikanStatus", "Search Response for '$showTitle': Code=${searchResponse.code()}, IsSuccessful=${searchResponse.isSuccessful}")
            
            if (!searchResponse.isSuccessful) {
                Log.w(TAG, "Jikan search failed for '$showTitle': HTTP ${searchResponse.code()}")
                Log.e("JikanStatus", "Search Failed: ${searchResponse.errorBody()?.string()}")
                return
            }

            val malId = searchResponse.body()?.data?.firstOrNull()?.malId
            Log.d("JikanStatus", "Resolved MAL ID for '$showTitle': $malId")
            
            if (malId == null) {
                Log.w(TAG, "Jikan: no MAL entry found for '$showTitle'")
                return
            }
            Log.d(TAG, "Jikan: resolved '$showTitle' → mal_id=$malId")

            // Step 2: Paginate all episode pages, collecting filler numbers
            val fillerEpisodeNumbers = mutableSetOf<Int>()
            var page = 1
            var hasNextPage = true

            while (hasNextPage) {
                Log.d("JikanStatus", "Fetching episodes for malId=$malId, Page=$page")
                // First attempt
                var epResponse = jikanApiService.getEpisodes(malId, page)
                Log.d("JikanStatus", "Episode Response (Page $page): Code=${epResponse.code()}")

                // Exponential back-off on 429 (Jikan 3 req/sec limit)
                if (epResponse.code() == 429) {
                    Log.w(TAG, "Jikan 429 on page $page — waiting 4 s")
                    kotlinx.coroutines.delay(4000L)
                    epResponse = jikanApiService.getEpisodes(malId, page)
                    Log.d("JikanStatus", "Episode Response (Page $page Retry 1): Code=${epResponse.code()}")
                }
                if (epResponse.code() == 429) {
                    Log.w(TAG, "Jikan 429 again on page $page — waiting 10 s")
                    kotlinx.coroutines.delay(10000L)
                    epResponse = jikanApiService.getEpisodes(malId, page)
                    Log.d("JikanStatus", "Episode Response (Page $page Retry 2): Code=${epResponse.code()}")
                }

                if (!epResponse.isSuccessful) {
                    Log.w(TAG, "Jikan episodes failed page $page: HTTP ${epResponse.code()}")
                    Log.e("JikanStatus", "Episode Fetch Failed (Page $page): ${epResponse.errorBody()?.string()}")
                    break
                }
                val body = epResponse.body() ?: break

                body.data.forEach { ep ->
                    // ep.malId is the 1-based sequential episode number within the series
                    if (ep.filler && ep.malId > 0) {
                        fillerEpisodeNumbers.add(ep.malId)
                    }
                }

                hasNextPage = body.pagination?.hasNextPage == true
                page++

                // 1.1 s inter-page delay keeps us safely under Jikan's 3 req/sec limit
                if (hasNextPage) kotlinx.coroutines.delay(1100L)
            }

            Log.d("JikanStatus", "Filler Enrichment Results for $showTitle: Found ${fillerEpisodeNumbers.size} filler episodes")
            Log.d(TAG, "Jikan: found ${fillerEpisodeNumbers.size} filler episodes for '$showTitle'")

            if (fillerEpisodeNumbers.isEmpty()) {
                // Mark synced even on empty result so we don't retry on every launch
                db.mediaDao().markJikanSynced(mediaId)
                return
            }

            // Step 3: Match against Room episodes using absoluteEpisodeNumber
            val allEpisodes = db.episodeDao().getAllEpisodesByMedia(mediaId)

            val toUpdate = allEpisodes.filter { entity ->
                entity.seasonNumber > 0 &&                              // never tag Season 0 specials
                entity.episodeType == EpisodeType.CANON.name &&         // only tag previously-canon eps
                entity.absoluteEpisodeNumber in fillerEpisodeNumbers    // Jikan match
            }.map { entity ->
                entity.copy(episodeType = EpisodeType.FILLER.name)
            }

            if (toUpdate.isNotEmpty()) {
                db.episodeDao().upsertAll(toUpdate)
                Log.d(TAG, "Jikan: tagged ${toUpdate.size} episodes as FILLER for $mediaId")
            }

            // Step 4: Persist the "done" flag — prevents 44-second re-runs on every visit
            db.mediaDao().markJikanSynced(mediaId)

        } catch (e: Exception) {
            Log.w(TAG, "Jikan enrichment failed for '$showTitle': ${e.message}")
            // Do NOT mark synced on exception — let it retry next time the user opens the show
        }
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
            originalLanguage = originalLanguage,
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
