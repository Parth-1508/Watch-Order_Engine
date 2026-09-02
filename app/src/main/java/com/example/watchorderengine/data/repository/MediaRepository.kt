package com.example.watchorderengine.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.graph.FranchiseAnchors
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.sync.SyncWorker
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.JikanApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.gemini.GeminiResult
import com.example.watchorderengine.network.gemini.GeminiService
import com.example.watchorderengine.network.model.TmdbWatchProvider
import com.example.watchorderengine.network.model.TmdbWatchProviderCountry
import androidx.paging.map
import com.example.watchorderengine.util.retry
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val db: WatchOrderDatabase,
    private val moshi: Moshi,
    private val apiService: TmdbApiService,
    private val jikanApiService: JikanApiService,
    private val anilistApi: com.example.watchorderengine.network.AnilistApiService,
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
        val cachedEntity = db.mediaDao().getById(mediaId)
            ?: db.mediaDao().getByTmdbId(tmdbId)

        return when (cachedEntity?.mediaCategory) {
            "MOVIE"   -> fetchAndCacheMovie(tmdbId, mediaId)
            "TV_SHOW",
            "ANIME"   -> fetchAndCacheTv(tmdbId, mediaId)
            else -> {
                when {
                    isMovieId(mediaId) -> fetchAndCacheMovie(tmdbId, mediaId) || fetchAndCacheTv(tmdbId, mediaId)
                    isTvId(mediaId)    -> fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
                    else -> fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
                }
            }
        }
    }

    private suspend fun buildMediaDetail(mediaId: String): MediaDetail? {
        val tmdbId = extractTmdbId(mediaId)

        val typedCategories = when {
            isMovieId(mediaId) -> listOf("MOVIE")
            isTvId(mediaId)    -> listOf("TV_SHOW", "ANIME")
            else               -> listOf("MOVIE", "TV_SHOW", "ANIME")
        }
        val entity = db.mediaDao().getById(mediaId)
            ?: tmdbId?.let { db.mediaDao().getByTmdbIdAndCategory(it, typedCategories) }
            ?: tmdbId?.let { db.mediaDao().getByTmdbId(it) }
            ?: tmdbId?.let { 
                db.mediaDao().getById("tmdb_$tmdbId") ?: db.mediaDao().getById(tmdbId.toString())
            }

        if (entity == null) {
            return null
        }

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
                    absoluteEpisodeNumber = if (seasonNumber > 0) offset + idx + 1 else 0,
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

    /**
     * Every episode across every season, watched-status resolved — used by
     * the Binge & Catch-Up calculator, which needs the complete picture
     * (not just whichever season tab happens to be open) to sum "what's left
     * to reach the latest release." Missing seasons are fetched and cached
     * first via [ensureEpisodesCached], same as [markAllAsWatched] already
     * does, so this is accurate even if the person has never opened every
     * season tab.
     */
    suspend fun getAllEpisodesForCatchUp(mediaId: String): List<EpisodeItem> =
        withContext(Dispatchers.IO) {
            if (mediaId.startsWith("tmdb_t_")) {
                ensureEpisodesCached(mediaId)
            }
            val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)

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

    /**
     * Powers the "Binge & Catch-Up" time calculator badge on the show detail page.
     *
     * Sums the exact runtime of every unwatched episode that has already aired
     * (season > 0, airDate <= today or unknown) to reach the latest release.
     * Both totals — with and without filler — are computed in one pass so the
     * UI's "exclude filler" toggle is an instant local flip, no re-query.
     */
    fun observeCatchUpSummary(mediaId: String): Flow<CatchUpSummary> {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

        return combine(
            db.episodeDao().observeAllEpisodesByMedia(mediaId),
            db.episodeWatchedDao().observeWatchedIds(mediaId)
        ) { episodes, watchedIds ->
            val watchedSet = watchedIds.toSet()
            val remaining = episodes.filter { ep ->
                ep.seasonNumber > 0 &&
                ep.id !in watchedSet &&
                (ep.airDate.isNullOrBlank() || ep.airDate <= todayStr)
            }

            val fillerCount = remaining.count { it.episodeType == EpisodeType.FILLER.name }
            val totalRuntime = remaining.sumOf { it.runtime ?: 0 }
            val nonFillerRuntime = remaining
                .filter { it.episodeType != EpisodeType.FILLER.name }
                .sumOf { it.runtime ?: 0 }

            CatchUpSummary(
                remainingEpisodeCount = remaining.size,
                remainingFillerCount = fillerCount,
                remainingRuntimeMinutes = totalRuntime,
                remainingRuntimeMinutesExcludingFiller = nonFillerRuntime,
                hasIncompleteRuntimeData = remaining.any { it.runtime == null }
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Drives the progress-aware [com.example.watchorderengine.ui.components.SpoilerShield]
     * on the Character Detail screen.
     *
     * Neither TMDB, AniList, nor Jikan expose "which episode first reveals this
     * character" — so rather than fabricate that data, the shield uses the same
     * signal already trusted for episode-level spoilers: the user's global
     * "Spoiler Shield" preference plus their real watch progress on *this* show.
     * The shield is active whenever the toggle is on AND the user hasn't caught
     * up to every episode that has already aired.
     */
    fun observeSpoilerShieldActive(mediaId: String): Flow<Boolean> {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

        return combine(
            userPrefs.hideUnwatchedSpoilers,
            db.episodeDao().observeAllEpisodesByMedia(mediaId),
            db.episodeWatchedDao().observeWatchedIds(mediaId)
        ) { hideSpoilers, episodes, watchedIds ->
            if (!hideSpoilers) return@combine false
            val watchedSet = watchedIds.toSet()
            val hasUnwatchedAiredEpisode = episodes.any { ep ->
                ep.seasonNumber > 0 &&
                ep.id !in watchedSet &&
                (ep.airDate.isNullOrBlank() || ep.airDate <= todayStr)
            }
            hasUnwatchedAiredEpisode
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    /**
     * Whether the current user has already generated a watch order for
     * [mediaId] — checked directly against the DAG (Firestore nodes), not
     * against [MediaDetail.arcs]. [MediaDetail.arcs] is derived from
     * episode-level `arcName` tagging (see [generateWatchOrder]'s
     * classification loop, which is gated on `raw.seasonNumber` and so never
     * fires for movies), so it stays empty for movies even after a
     * successful generation. This check works for both movies and TV shows.
     */
    suspend fun hasGeneratedWatchOrder(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        val uniqueUniverseId = "${uid}_$mediaId"
        watchOrderRepository.getNodes(uniqueUniverseId).first().isNotEmpty()
    }

    /**
     * "Ask AI about this order" — fetches the DAG timeline this user already
     * generated for [mediaId] (via [generateWatchOrder]) and asks Gemini for a
     * spoiler-free editorial explanation of why the titles are ordered that way.
     *
     * Returns null if the user hasn't generated a watch order for this show yet
     * (nothing to explain) or if Gemini couldn't produce an explanation.
     */
    suspend fun explainWatchOrder(mediaId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext null
        val uid = auth.currentUser?.uid ?: return@withContext null
        val uniqueUniverseId = "${uid}_$mediaId"

        val nodes = watchOrderRepository.getNodes(uniqueUniverseId).first()
        if (nodes.isEmpty()) return@withContext null
        val edges = watchOrderRepository.getEdges(uniqueUniverseId).first()

        val result = geminiService.explainWatchOrder(universeName = entity.title, nodes = nodes, edges = edges)
        if (result is com.example.watchorderengine.network.gemini.GeminiExplanationResult.Success) {
            result.explanation
        } else null
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

                val uid = auth.currentUser?.uid ?: return@withContext "User not authenticated."
                val uniqueUniverseId = "${uid}_$mediaId"

                // Resolves a raw item back to its OWN show/movie's Room mediaId.
                // Used both for Firestore node navigation targets and for locating
                // the right local episodes to tag below — must stay the same
                // function in both places, or cross-franchise items (raw.tmdbId !=
                // entity.tmdbId) get episode-classified under the wrong show's
                // season id (BUG FIX: this loop used to always rebuild seasonId
                // from the currently-open show's mediaId, silently mis-tagging
                // any related show pulled in via buildTvRawItems' cross-search).
                fun resolveRawItemMediaId(raw: com.example.watchorderengine.network.gemini.RawMediaItem): Pair<String, String> {
                    return if (raw.tmdbId == entity.tmdbId) {
                        mediaId to (if (isMovie) "movie" else "tv")
                    } else {
                        val type = if (raw.contentType == "MOVIE") "movie" else "tv"
                        buildMediaId(raw.tmdbId, type) to type
                    }
                }

                watchOrderRepository.clearGeneratedUniverse(uniqueUniverseId)
                val publishResult = watchOrderRepository.publishSortedUniverse(
                    universeId     = uniqueUniverseId,
                    universeName   = entity.title,
                    coverUrl       = entity.posterUrl ?: "",
                    rawItems       = rawItems,
                    sortedNodes    = sortedNodes,
                    sortedEdges    = sortedEdges,
                    resolveMediaId = ::resolveRawItemMediaId
                )
                if (publishResult.isFailure) {
                    return@withContext "Firestore push failed: ${publishResult.exceptionOrNull()?.message}"
                }

                supervisorScope {
                    sortedNodes.forEach { node ->
                        val raw = rawItems.find { it.itemId == node.itemId } ?: return@forEach
                        val classification = if (node.filler) "FILLER" else "CANON"
                        val (rawMediaId, rawType) = resolveRawItemMediaId(raw)
                        if (rawType != "tv") return@forEach // movies have no season/episode rows to tag

                        val episodesToTag = if (raw.startAbsoluteEpisode != null && raw.endAbsoluteEpisode != null) {
                            // Arc-segment item — its range may span a season boundary,
                            // so tag by absolute episode number across the whole show
                            // rather than by a single seasonId.
                            db.episodeDao().getAllEpisodesByMedia(rawMediaId).filter {
                                it.absoluteEpisodeNumber in raw.startAbsoluteEpisode..raw.endAbsoluteEpisode
                            }
                        } else {
                            val seasonNumber = raw.seasonNumber ?: return@forEach
                            db.episodeDao().getEpisodesBySeason("${rawMediaId}_s$seasonNumber")
                        }

                        if (episodesToTag.isNotEmpty()) {
                            db.episodeDao().upsertAll(episodesToTag.map {
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

    /**
     * Movie raw-item builder. TMDB's "collection" concept (used by
     * [buildMovieRawItemsFromCollections] below) is movie-only — a collection
     * can never contain a TV show, so a franchise's parent series or sibling
     * TV-format entries can never surface from that path alone, no matter
     * which of its three tiers succeeds. For anime movies with a known
     * AniList ID, this supplements whatever the collection logic found with
     * real relations (the parent TV series, other films, OVAs) — the same
     * source used for the TV-first path in [buildTvRawItems].
     */
    private suspend fun buildMovieRawItems(
        entity: MediaEntity,
        mediaId: String
    ): List<com.example.watchorderengine.network.gemini.RawMediaItem> {
        val baseItems = buildMovieRawItemsFromCollections(entity, mediaId)

        if (entity.genres.contains("Animation") && entity.anilistId != null) {
            val alreadyIncludedTmdbIds = baseItems.map { it.tmdbId }.toSet()
            val relatedNodes = fetchAnimeFranchiseRelations(entity.anilistId)
            Log.d(TAG, "AniList relations for movie '${entity.title}': ${relatedNodes.map { it.title?.english ?: it.title?.romaji }}")

            val resolvedItems = supervisorScope {
                relatedNodes.map { node -> async { resolveAnilistRelationToTmdb(node) } }.awaitAll()
            }
            val newItems = resolvedItems.filterNotNull().filter { it.tmdbId !in alreadyIncludedTmdbIds }

            if (newItems.isNotEmpty()) {
                Log.d(TAG, "Movie-first generation found ${newItems.size} additional entries via AniList (e.g. parent TV series)")
                return baseItems + newItems
            }
        }

        return baseItems
    }

    private suspend fun buildMovieRawItemsFromCollections(
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

    /** Shows with at least this many total episodes get arc-level DAG nodes instead of season-level ones. */
    private val ARC_SEGMENTATION_MIN_EPISODES = 30

    /**
     * Segments a long-running show's episodes into story arcs via
     * [GeminiService.segmentIntoArcs] and converts each arc into a
     * [RawMediaItem] carrying its absolute episode range — so the resulting
     * DAG node can deep-link to the season it starts in (see [MediaNode.seasonNumber])
     * and the classification loop in [generateWatchOrder] can tag exactly the
     * right episodes, even when an arc spans a season boundary.
     *
     * Returns null (never throws) on any failure, so callers can fall back to
     * plain season-level items rather than block generation entirely.
     */
    private suspend fun tryBuildArcRawItems(
        showMediaId: String,
        showTitle: String,
        tmdbId: Int,
        dbEntity: com.example.watchorderengine.data.db.entity.MediaEntity?,
        seasons: List<com.example.watchorderengine.data.db.entity.SeasonEntity>
    ): List<com.example.watchorderengine.network.gemini.RawMediaItem>? {
        val episodes = db.episodeDao().getAllEpisodesByMedia(showMediaId)
            .filter { it.seasonNumber > 0 }
            .sortedBy { it.absoluteEpisodeNumber }
        if (episodes.isEmpty()) return null

        val episodeInputs = episodes.map {
            com.example.watchorderengine.network.gemini.ArcSegmentEpisodeInput(
                absoluteEpisode = it.absoluteEpisodeNumber,
                title = it.title
            )
        }
        val arcs = geminiService.segmentIntoArcs(showTitle, episodeInputs) ?: return null

        val seasonByAbsoluteEpisode = episodes.associate { it.absoluteEpisodeNumber to it.seasonNumber }

        return arcs.mapIndexed { index, arc ->
            val startSeason = seasonByAbsoluteEpisode[arc.startAbsoluteEpisode] ?: seasons.first().seasonNumber
            com.example.watchorderengine.network.gemini.RawMediaItem(
                itemId               = "${showMediaId}_arc$index",
                title                = "$showTitle — ${arc.arcName}",
                overview             = "",
                contentType          = "SERIES",
                seasonNumber         = startSeason,
                episodeCount         = arc.endAbsoluteEpisode - arc.startAbsoluteEpisode + 1,
                releaseDate          = null,
                tmdbId               = tmdbId,
                source               = "ARC_SEGMENT",
                posterPath           = dbEntity?.posterUrl,
                startAbsoluteEpisode = arc.startAbsoluteEpisode,
                endAbsoluteEpisode   = arc.endAbsoluteEpisode
            )
        }
    }

    /**
     * AniList relation types worth pulling into a watch-order DAG. Deliberately
     * excludes ADAPTATION/SOURCE/CHARACTER/OTHER/CONTAINS, which mostly point
     * at manga/light-novel source material or loosely-related media that
     * doesn't belong in a video watch order.
     */
    private val RELEVANT_RELATION_TYPES = setOf(
        "SEQUEL", "PREQUEL", "SIDE_STORY", "ALTERNATIVE", "SPIN_OFF", "PARENT", "SUMMARY", "COMPILATION"
    )

    /**
     * Fetches real franchise relations from AniList (movies, OVAs, spin-offs,
     * sequels/prequels) for an anime, instead of guessing via TMDB title search.
     *
     * This is what actually finds Naruto's canon films and Shippuden split —
     * TMDB's search/tv endpoint can't return movies at all, and title-stripping
     * heuristics don't know a spin-off exists unless its title happens to be a
     * substring match.
     */
    private suspend fun fetchAnimeFranchiseRelations(
        anilistId: Int,
        maxDepth: Int = 2,
        visitedIds: MutableSet<Int> = mutableSetOf()
    ): List<com.example.watchorderengine.network.AnilistMedia> {
        if (anilistId in visitedIds || maxDepth <= 0) return emptyList()
        visitedIds.add(anilistId)

        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                relations {
                  edges {
                    relationType
                    node {
                      id
                      format
                      title { romaji english }
                      episodes
                      coverImage { extraLarge large }
                      startDate { year }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response = anilistApi.query(
                com.example.watchorderengine.network.AnilistRequest(query, mapOf("id" to anilistId))
            )
            val edges = response.body()?.data?.media?.relations?.edges.orEmpty()

            Log.d(TAG, "AniList relations for anilistId=$anilistId (depth=$maxDepth, all, pre-filter): " +
                edges.joinToString { "${it.relationType}:${it.node?.format}:${it.node?.title?.english ?: it.node?.title?.romaji}" })

            val directNodes = edges.filter { edge ->
                edge.relationType in RELEVANT_RELATION_TYPES ||
                    (edge.relationType == "OTHER" && edge.node?.format == "MOVIE")
            }.mapNotNull { it.node }

            val resultList = mutableListOf<com.example.watchorderengine.network.AnilistMedia>()
            resultList.addAll(directNodes)

            // Multi-hop traversal: for main structural relations (SEQUEL, PREQUEL, PARENT),
            // fetch their relations as well so movies attached to subsequent generations (e.g. Pokémon AG, DP, BW, XY, SM)
            // are brought into the raw items list.
            if (maxDepth > 1) {
                val nextHopEdges = edges.filter { edge ->
                    edge.relationType in setOf("SEQUEL", "PREQUEL", "PARENT") &&
                        edge.node?.id != null &&
                        edge.node.id !in visitedIds
                }
                for (edge in nextHopEdges) {
                    val nextId = edge.node?.id ?: continue
                    val subNodes = fetchAnimeFranchiseRelations(nextId, maxDepth - 1, visitedIds)
                    resultList.addAll(subNodes)
                }
            }

            resultList.distinctBy { it.id }
        } catch (e: Exception) {
            Log.w(TAG, "AniList relations fetch failed for anilistId=$anilistId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves an AniList relation node to a cached Room [MediaEntity] via TMDB
     * title search — anchored to AniList's exact title/format (not a stripped
     * guess), so this is a precise lookup rather than fuzzy matching. Movies
     * search `search/movie`; TV/OVA/ONA/special formats search `search/tv`
     * (TMDB has no distinct OVA type). Pre-caches a minimal entity on first
     * resolution so timeline navigation is instant, same as the movie
     * franchise-expansion path in [buildMovieRawItems].
     */
    private suspend fun resolveAnilistRelationToTmdb(
        node: com.example.watchorderengine.network.AnilistMedia
    ): com.example.watchorderengine.network.gemini.RawMediaItem? {
        val title = node.title?.english ?: node.title?.romaji ?: return null
        val isMovieFormat = node.format == "MOVIE"

        val searchResult = try {
            if (isMovieFormat) {
                apiService.searchMovie(query = title).body()?.results?.firstOrNull()
                    ?: apiService.searchTv(query = title).body()?.results?.firstOrNull()
            } else {
                apiService.searchTv(query = title).body()?.results?.firstOrNull()
                    ?: apiService.searchMovie(query = title).body()?.results?.firstOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB resolution failed for AniList relation '$title': ${e.message}")
            null
        } ?: return null

        val resolvedType = if (isMovieFormat) "movie" else "tv"
        val relatedMediaId = buildMediaId(searchResult.id, resolvedType)

        if (db.mediaDao().getById(relatedMediaId) == null) {
            db.mediaDao().upsert(
                MediaEntity(
                    id = relatedMediaId, tmdbId = searchResult.id, anilistId = node.id,
                    title = title, originalTitle = title,
                    overview = "", tagline = "", status = "",
                    posterUrl   = node.coverImage?.extraLarge ?: node.coverImage?.large,
                    backdropUrl = null,
                    mediaCategory = if (isMovieFormat) "MOVIE" else "TV_SHOW",
                    genres = emptyList(), ageRating = "NR",
                    voteAverage = 0f, voteCount = 0, runtime = null,
                    numberOfSeasons = null, numberOfEpisodes = node.episodes,
                    releaseDate = node.startDate?.year?.let { "$it-01-01" },
                    releaseYear = node.startDate?.year?.toString() ?: "",
                    trailerKey = null,
                    watchProvidersJson = "[]", castJson = "[]",
                    recommendationsJson = "[]", arcsJson = "[]"
                )
            )
        }

        return com.example.watchorderengine.network.gemini.RawMediaItem(
            itemId       = relatedMediaId,
            title        = title,
            overview     = "",
            contentType  = if (isMovieFormat) "MOVIE" else "SERIES",
            seasonNumber = null,
            episodeCount = node.episodes,
            releaseDate  = node.startDate?.year?.let { "$it-01-01" },
            tmdbId       = searchResult.id,
            source       = "ANILIST_RELATION",
            posterPath   = node.coverImage?.extraLarge ?: node.coverImage?.large
        )
    }

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
            val isPrimaryShow = tmdbId == entity.tmdbId

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

            var seasons = db.seasonDao().getSeasonsByMedia(showMediaId).sortedBy { it.seasonNumber }
            if (seasons.isEmpty() && isPrimaryShow) {
                Log.d(TAG, "Primary show '$showTitle' has no cached seasons — fetching now")
                fetchAndCacheTv(tmdbId, showMediaId)
                seasons = db.seasonDao().getSeasonsByMedia(showMediaId).sortedBy { it.seasonNumber }
            }
            if (seasons.isEmpty()) {
                Log.d(TAG, "No seasons cached for '$showTitle' — skipping")
                continue
            }

            val totalEpisodes = seasons.sumOf { it.episodeCount }
            if (isPrimaryShow && totalEpisodes >= ARC_SEGMENTATION_MIN_EPISODES) {
                val arcItems = tryBuildArcRawItems(showMediaId, showTitle, tmdbId, dbEntity, seasons)
                if (arcItems != null) {
                    allSeasonItems.addAll(arcItems)
                    continue
                }
                Log.d(TAG, "Arc segmentation unavailable for '$showTitle' — falling back to season-level items")
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

        // ANIME FRANCHISE RELATIONS & TMDB MOVIE CROSS-SEARCH:
        // TMDB's search/tv endpoint above can only ever find other TV shows,
        // and only by fuzzy title match — it will never surface a franchise's canon
        // movies at all, and can miss spin-offs whose titles don't happen to
        // contain the base keyword. For anime/animation with a known AniList ID or category:
        // 1. Pull real relations from AniList (with multi-hop traversal for sequels/prequels)
        // 2. Perform a TMDB movie search for baseTitle to catch franchise feature films on TMDB.
        if (entity.genres.contains("Animation") || entity.mediaCategory == "ANIME" || entity.anilistId != null) {
            val alreadyIncludedTmdbIds = showsToProcess.map { it.first }.toMutableSet()

            if (entity.anilistId != null) {
                val relatedNodes = fetchAnimeFranchiseRelations(entity.anilistId)
                Log.d(TAG, "AniList relations for '${entity.title}': ${relatedNodes.map { it.title?.english ?: it.title?.romaji }}")

                val resolvedItems = supervisorScope {
                    relatedNodes.map { node -> async { resolveAnilistRelationToTmdb(node) } }.awaitAll()
                }

                for (item in resolvedItems) {
                    if (item == null) continue
                    if (item.tmdbId in alreadyIncludedTmdbIds) continue // avoid duplicating a show already found
                    alreadyIncludedTmdbIds.add(item.tmdbId)
                    allSeasonItems.add(item)
                }
            }

            // Supplement with TMDB movie search for baseTitle
            try {
                val tmdbMovies = apiService.searchMovie(query = baseTitle).body()?.results?.take(10).orEmpty()
                for (movie in tmdbMovies) {
                    if (movie.id !in alreadyIncludedTmdbIds) {
                        val movieTitle = movie.title ?: movie.name ?: continue
                        if (isTitleMatch(movieTitle, baseTitle) || movieTitle.contains(baseTitle, ignoreCase = true)) {
                            alreadyIncludedTmdbIds.add(movie.id)
                            val movieMediaId = buildMediaId(movie.id, "movie")
                            if (db.mediaDao().getById(movieMediaId) == null) {
                                db.mediaDao().upsert(
                                    MediaEntity(
                                        id = movieMediaId, tmdbId = movie.id, anilistId = null,
                                        title = movieTitle, originalTitle = movieTitle,
                                        overview = "", tagline = "", status = "",
                                        posterUrl   = TmdbConfig.buildImageUrl(movie.posterPath),
                                        backdropUrl = null, mediaCategory = "MOVIE",
                                        genres = emptyList(), ageRating = "NR",
                                        voteAverage = movie.voteAverage?.toFloat() ?: 0f,
                                        voteCount = 0, runtime = null,
                                        numberOfSeasons = null, numberOfEpisodes = 1,
                                        releaseDate = movie.releaseDate,
                                        releaseYear = movie.releaseDate?.take(4) ?: "",
                                        trailerKey = null,
                                        watchProvidersJson = "[]", castJson = "[]",
                                        recommendationsJson = "[]", arcsJson = "[]"
                                    )
                                )
                            }
                            allSeasonItems.add(
                                com.example.watchorderengine.network.gemini.RawMediaItem(
                                    itemId       = movieMediaId,
                                    title        = movieTitle,
                                    overview     = "",
                                    contentType  = "MOVIE",
                                    seasonNumber = null,
                                    episodeCount = 1,
                                    releaseDate  = movie.releaseDate,
                                    tmdbId       = movie.id,
                                    source       = "TMDB_RELATED_MOVIE",
                                    posterPath   = movie.posterPath
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TMDB movie cross-search failed for '$baseTitle': ${e.message}")
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
            
            com.example.watchorderengine.widget.WidgetUpdater.refreshAll(appContext)

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
            if (state == TrackingState.COMPLETED) {
                // Also ensure all episodes are marked as watched
                markAllAsWatched(mediaId)

                if (tmdbId != null) {
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
        
        com.example.watchorderengine.widget.WidgetUpdater.refreshAll(appContext)

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
                val response = retry { apiService.getTvShow(tmdbId) }
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

    /**
     * The "Continue Watching" carousel's exact data — up to [limit] most
     * recently-updated Watching titles, each resolved to its next unwatched
     * episode. This is the single source of truth for that carousel: both
     * [com.example.watchorderengine.ui.viewmodel.HomeViewModel] (in-app) and
     * the Continue Watching Glance widget call this same function, so they
     * can never drift out of sync with each other.
     */
    suspend fun getContinueWatchingItems(limit: Int = 5): List<ContinueWatchingItem> = withContext(Dispatchers.IO) {
        val watching = getWatchingList()
        if (watching.isEmpty()) return@withContext emptyList()

        watching.take(limit).mapNotNull { recent ->
            val mediaId = recent.id
            val isMovie = recent.mediaCategory == MediaCategory.MOVIE

            if (isMovie) {
                ContinueWatchingItem(
                    mediaId         = mediaId,
                    showTitle       = recent.title,
                    episodeLabel    = "Movie",
                    posterUrl       = recent.posterUrl,
                    backdropUrl     = recent.backdropUrl,
                    progressPercent = 0,
                    targetSeason    = null,
                    nextEpisodeId   = null
                )
            } else {
                val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
                val watchedNormalized = getNormalizedWatchedIds(mediaId)

                val nextEp = episodes
                    .filter { it.seasonNumber > 0 }
                    .find { ep ->
                        val normalizedId = ep.id
                            .removePrefix("tmdb_m_")
                            .removePrefix("tmdb_t_")
                            .removePrefix("tmdb_")
                            .removePrefix("anilist_")
                        normalizedId !in watchedNormalized
                    }

                nextEp?.let {
                    val highResBackdrop = it.stillUrl?.replace("/w185/", "/w780/") ?: recent.backdropUrl
                    ContinueWatchingItem(
                        mediaId = mediaId,
                        showTitle = recent.title,
                        episodeLabel = "S${it.seasonNumber} E${it.episodeNumber} — ${it.title}",
                        posterUrl = recent.posterUrl,
                        backdropUrl = highResBackdrop,
                        progressPercent = (watchedNormalized.size * 100 / episodes
                            .filter { ep -> ep.seasonNumber > 0 }
                            .size.coerceAtLeast(1)),
                        targetSeason = it.seasonNumber,
                        nextEpisodeId = it.id
                    )
                }
            }
        }
    }

    // ─── Release Calendar ───────────────────────────────────────────────────

    /**
     * For every WATCHING show, determines its currently-active season (via
     * TMDB's next_episode_to_air / last_episode_to_air — one cheap call,
     * already made elsewhere) and re-fetches THAT season's full episode list,
     * writing every episode's airDate to Room — not just the next one.
     *
     * A show whose season already has 6 announced air dates will surface all
     * 6 in the calendar after this runs, not just the soonest.
     *
     * Movies are skipped (movie/TV TMDB IDs are separate ID spaces and can collide).
     *
     * @return how many shows were successfully refreshed.
     */
    suspend fun refreshCurrentSeasonForWatchingShows(): Int = withContext(Dispatchers.IO) {
        val watching = db.userProgressDao().getByState("WATCHING")
        if (watching.isEmpty()) return@withContext 0

        val tvShows = watching
            .mapNotNull { db.mediaDao().getById(it.mediaId) }
            .filter { it.mediaCategory != "MOVIE" }
        if (tvShows.isEmpty()) return@withContext 0

        val results = supervisorScope {
            tvShows.map { entity ->
                async {
                    try {
                        val showResponse = retry { apiService.getTvShow(entity.tmdbId) }
                        if (!showResponse.isSuccessful) return@async false
                        val body = showResponse.body() ?: return@async false

                        // Still keep the quick-glance fields current — cheap,
                        // same response, used by show cards elsewhere in the app.
                        db.mediaDao().upsert(
                            entity.copy(
                                nextAirDate             = body.nextEpisodeToAir?.airDate,
                                nextEpisodeNumber       = body.nextEpisodeToAir?.episodeNumber,
                                nextEpisodeSeasonNumber = body.nextEpisodeToAir?.seasonNumber,
                                nextEpisodeName         = body.nextEpisodeToAir?.name?.takeIf { it.isNotBlank() },
                                lastUpdated             = System.currentTimeMillis(),
                            )
                        )

                        // Which season is "active"? Prefer the season with an
                        // unaired next episode; fall back to the season that
                        // most recently aired (covers "between episodes, next
                        // one just not announced yet"); fall back to the last
                        // season TMDB knows about at all.
                        val activeSeason = body.nextEpisodeToAir?.seasonNumber
                            ?: body.lastEpisodeToAir?.seasonNumber
                            ?: body.numberOfSeasons
                            ?: return@async true   // no seasons at all — nothing to refresh, not an error

                        refreshSeasonAirDatesOnly(entity.tmdbId, entity.id, activeSeason)
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Calendar refresh failed for ${entity.title}: ${e.message}")
                        false
                    }
                }
            }.awaitAll()
        }
        com.example.watchorderengine.widget.WidgetUpdater.refreshUpcomingCalendar(appContext)
        results.count { it }
    }

    /**
     * Re-fetches ONE season's episode list and upserts airDate/title/overview
     * for each — WITHOUT touching absoluteEpisodeNumber bookkeeping owned by
     * the main fetchAndCacheTv() flow, and WITHOUT wiping episodeType/arcName
     * (filler classification) that the Jikan enrichment pass may have already
     * set on these rows.
     *
     * Offset derivation: if this season already has at least one cached
     * episode, reuse ITS (absoluteEpisodeNumber - episodeNumber) as the
     * offset for the whole season — exact, and avoids re-deriving from every
     * prior season (the drift trap fetchAndCacheTv()'s own comments warn
     * about for long-running shows). Only falls back to summing
     * SeasonEntity.episodeCount when this exact season has never been cached
     * before (a brand-new, never-opened season) — a rare case that
     * self-corrects to exact the next time the user opens the Detail screen.
     */
    private suspend fun refreshSeasonAirDatesOnly(tmdbId: Int, mediaId: String, seasonNumber: Int) {
        val seasonId  = "${mediaId}_s$seasonNumber"
        val existing  = db.episodeDao().getEpisodesBySeason(seasonId)
        val existingByNumber = existing.associateBy { it.episodeNumber }

        val offset = existing.firstOrNull()?.let { it.absoluteEpisodeNumber - it.episodeNumber }
            ?: db.seasonDao().getSeasonsByMedia(mediaId)
                .filter { it.seasonNumber in 1 until seasonNumber }
                .sumOf { it.episodeCount }

        val response = retry { apiService.getTvSeason(tmdbId, seasonNumber) }
        if (!response.isSuccessful) return
        val episodes = response.body()?.episodes ?: return

        val refreshed = episodes.map { ep ->
            val prior = existingByNumber[ep.episodeNumber]
            EpisodeEntity(
                id = "${mediaId}_s${seasonNumber}e${ep.episodeNumber}",
                seasonId = seasonId, mediaId = mediaId,
                episodeNumber = ep.episodeNumber, seasonNumber = seasonNumber,
                absoluteEpisodeNumber = offset + ep.episodeNumber,
                title = ep.name?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episodeNumber}",
                overview = ep.overview ?: prior?.overview ?: "",
                airDate = ep.airDate,
                runtime = ep.runtime ?: prior?.runtime,
                stillUrl = com.example.watchorderengine.network.TmdbConfig.buildImageUrl(ep.stillPath) ?: prior?.stillUrl,
                voteAverage = ep.voteAverage?.toFloat() ?: prior?.voteAverage ?: 0f,
                // Preserve filler classification — an @Upsert replaces the
                // whole row, so without this a refresh would silently erase
                // Jikan's CANON/FILLER tagging on every affected episode.
                episodeType = prior?.episodeType ?: "CANON",
                arcName = prior?.arcName,
            )
        }
        db.episodeDao().upsertAll(refreshed)
    }

    /**
     * Reads the current calendar from Room — fast, local, no network.
     * Sources every future-dated episode already cached for any WATCHING
     * show, across all seasons — not just "the next one." Call
     * [refreshCurrentSeasonForWatchingShows] first (or after, then re-call
     * this) to pick up dates announced since the last cache.
     */
    /**
     * Expanded calendar: sources every future-dated episode already cached for
     * any tracked show (except DROPPED), plus dates up to 14 days in the past,
     * plus current Trending titles from both TMDB and AniList.
     */
    suspend fun getUpcomingEpisodes(): List<UpcomingEpisode> = withContext(Dispatchers.IO) {
        val today = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        // 14 days in past
        val startCal = today.clone() as java.util.Calendar
        startCal.add(java.util.Calendar.DATE, -14)
        val startDateIso = sdf.format(startCal.time)

        // 1. My Tracked Shows (expanded)
        val episodes = db.episodeDao().getUpcomingExpanded(startDateIso)
        
        val mediaMap = db.mediaDao()
            .getByIds(episodes.map { it.mediaId }.distinct())
            .associateBy { it.id }

        val myEpisodes = episodes.mapNotNull { ep ->
            val media = mediaMap[ep.mediaId] ?: return@mapNotNull null
            UpcomingEpisode(
                mediaId       = ep.mediaId,
                showTitle     = media.title,
                posterUrl     = media.posterUrl,
                mediaCategory = media.mediaCategory,
                seasonNumber  = ep.seasonNumber,
                episodeNumber = ep.episodeNumber,
                episodeName   = ep.title.ifBlank { "Episode ${ep.episodeNumber}" },
                airDate       = ep.airDate!!,
            )
        }

        // 2. Trending Shows (TMDB)
        val trendingTmdb = runCatching { getTrending() }.getOrDefault(emptyList())
        val trendingTmdbEpisodes = trendingTmdb.mapNotNull { summary ->
            val date = summary.releaseDate
            if (date != null && date >= startDateIso) {
                UpcomingEpisode(
                    mediaId = summary.id,
                    showTitle = summary.title,
                    posterUrl = summary.posterUrl,
                    mediaCategory = summary.mediaCategory.name,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    episodeName = if (summary.mediaCategory == MediaCategory.MOVIE) "Theater Release" else "Series Premiere",
                    airDate = date
                )
            } else null
        }

        // 3. Trending/Airing Anime (AniList)
        val trendingAnilist = fetchAiringTrendingAnime(startDateIso)

        (myEpisodes + trendingTmdbEpisodes + trendingAnilist)
            .distinctBy { it.mediaId + it.airDate + it.episodeNumber }
            .sortedBy { it.airDate } // Ascending (Past to Future)
    }

    private suspend fun fetchAiringTrendingAnime(startDateIso: String): List<UpcomingEpisode> {
        val query = """
            query (${'$'}page: Int) {
              Page (page: ${'$'}page, perPage: 15) {
                media (status: RELEASING, sort: TRENDING_DESC, type: ANIME) {
                  id
                  title { english romaji }
                  coverImage { large }
                  format
                  nextAiringEpisode {
                    airingAt
                    episode
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response = anilistApi.query(AnilistRequest(query, mapOf("page" to 1)))
            if (!response.isSuccessful) return emptyList()

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val animeList = response.body()?.data?.page?.media.orEmpty()

            animeList.mapNotNull { anime ->
                val nextEp = anime.nextAiringEpisode ?: return@mapNotNull null
                val date = sdf.format(java.util.Date(nextEp.airingAt * 1000L))
                if (date < startDateIso) return@mapNotNull null

                val title = anime.title?.english ?: anime.title?.romaji ?: return@mapNotNull null

                // ── Resolve to a REAL tmdb_t_{id} mediaId ──────────────────────
                val existing = db.mediaDao().getByAnilistId(anime.id)

                val resolvedMediaId = existing?.id ?: run {
                    val searchResult = try {
                        apiService.searchTv(query = title).body()?.results?.firstOrNull()
                    } catch (e: Exception) {
                        Log.w(TAG, "TMDB resolve failed for AniList '$title': ${e.message}")
                        null
                    } ?: return@mapNotNull null

                    val newMediaId = buildMediaId(searchResult.id, "tv")
                    val existingByTmdbId = db.mediaDao().getById(newMediaId)
                    when {
                        existingByTmdbId == null -> {
                            db.mediaDao().upsert(
                                searchResult.toMinimalEntity(newMediaId, explicitIsMovie = false)
                                    .copy(anilistId = anime.id)
                            )
                        }
                        existingByTmdbId.anilistId == null -> {
                            db.mediaDao().upsert(existingByTmdbId.copy(anilistId = anime.id))
                        }
                    }
                    newMediaId
                }

                UpcomingEpisode(
                    mediaId       = resolvedMediaId,
                    showTitle     = title,
                    posterUrl     = anime.coverImage?.large,
                    mediaCategory = "ANIME",
                    seasonNumber  = 1,
                    episodeNumber = nextEp.episode,
                    episodeName   = "Upcoming Episode",
                    airDate       = date,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniList trending fetch failed: ${e.message}")
            emptyList()
        }
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
        
        com.example.watchorderengine.widget.WidgetUpdater.refreshContinueWatching(appContext)
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
        val now = System.currentTimeMillis()
        // 1. Optimized Room update using bulk SQL
        db.episodeWatchedDao().markBulkPreviousAsWatched(mediaId, targetSeason, targetEpisode, now)
        
        // 2. Sync to Firestore (still needs IDs for bulk update)
        val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
            .filter { it.seasonNumber < targetSeason ||
                (it.seasonNumber == targetSeason && it.episodeNumber < targetEpisode) }
        
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
    }

    suspend fun markAllAsWatched(mediaId: String) = withContext(Dispatchers.IO) {
        if (mediaId.startsWith("tmdb_t_")) {
            ensureEpisodesCached(mediaId)
        }
        val now = System.currentTimeMillis()
        // 1. Optimized Room update
        db.episodeWatchedDao().markAllAsWatched(mediaId, now)
        
        // 2. Optimized Firestore sync
        val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
        syncEpisodesToFirestore(mediaId, episodes.map { it.id }, true)
    }

    /**
     * Ensures all episodes for a TV show are in the local database.
     * Checks count against TMDB header and fetches missing seasons if needed.
     */
    private suspend fun ensureEpisodesCached(mediaId: String) {
        try {
            val tmdbId = mediaId.substringAfter("tmdb_t_").toIntOrNull() ?: return
            val currentCount = db.episodeDao().getCountByMedia(mediaId)
            
            // 1. Fetch show detail to get the total number of episodes from TMDB
            val response = retry { apiService.getTvShow(tmdbId) }
            val body = response.body() ?: return
            val totalEpisodes = body.numberOfEpisodes ?: 0
            val seasons = body.seasons ?: emptyList()
            
            if (currentCount < totalEpisodes) {
                Log.d(TAG, "Syncing missing episodes for $mediaId (local: $currentCount, tmdb: $totalEpisodes)")
                
                var cumulativeOffset = 0
                seasons.sortedBy { it.seasonNumber }.forEach { seasonSummary ->
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure episodes cached for $mediaId", e)
        }
    }

    suspend fun markSeasonAsWatched(mediaId: String, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 1. Optimized Room update
        db.episodeWatchedDao().markSeasonAsWatchedBulk(mediaId, seasonNumber, now)
        
        // 2. Sync to Firestore
        val seasonId = "${mediaId}_s$seasonNumber"
        val episodes = db.episodeDao().getEpisodesBySeason(seasonId)
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
        db.episodeDao().hasUnwatchedEpisodesBefore(mediaId, targetSeason, targetEpisode)
    }

    // ─── Trending / Discovery ─────────────────────────────────────────────────

    fun getDiscoveryStream(
        category: TmdbConfig.DiscoveryCategory?,
        providerIds: Set<Int>,
        originalLanguage: String? = null
    ): Flow<PagingData<MediaSummary>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { DiscoveryPagingSource(this, category, providerIds, originalLanguage) }
        ).flow
    }

    suspend fun getTrendingPaged(
        providerIds: Set<Int> = emptySet(),
        page: Int = 1,
        originalLanguage: String? = null
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            
            // TMDB's /trending endpoint has no language-filter param at all — only
            // discover/movie and discover/tv support with_original_language — so
            // a language filter forces the discover-endpoint path even when no
            // provider filter is active, same as the existing provider-filter branch.
            if (providerIds.isEmpty() && originalLanguage == null) {
                val response = apiService.getTrending(page = page)
                if (response.isSuccessful) response.body()?.results?.let { results.addAll(it) }
            } else {
                val providersStr = providerIds.joinToString("|").takeIf { it.isNotEmpty() }
                val mResp = apiService.discoverMovies(providerIds = providersStr, originalLanguage = originalLanguage, page = page)
                val tResp = apiService.discoverTvShows(providerIds = providersStr, originalLanguage = originalLanguage, page = page)
                
                mResp.body()?.results?.forEach { results.add(it.copy(mediaType = "movie")) }
                tResp.body()?.results?.forEach { results.add(it.copy(mediaType = "tv")) }
            }
            
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
                !it.posterPath.isNullOrBlank() && 
                (it.releaseDate ?: it.firstAirDate ?: "").let { date -> date.isNotBlank() && date <= todayStr }
            }
                .mapNotNull { it.toSummary() }
                .distinctBy { it.id }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun discoverByGenrePaged(
        category: TmdbConfig.DiscoveryCategory,
        providerIds: Set<Int> = emptySet(),
        page: Int = 1,
        originalLanguage: String? = null
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val providersStr = providerIds.joinToString("|").takeIf { it.isNotBlank() }
            
            val movieResults = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            val tvResults = mutableListOf<com.example.watchorderengine.network.model.TmdbMediaResult>()
            
            val mResp = apiService.discoverMovies(genreId = category.movieGenreId.toString(), providerIds = providersStr, originalLanguage = originalLanguage, page = page)
            if (mResp.isSuccessful) mResp.body()?.results?.let { movieResults.addAll(it) }
            
            val tResp = apiService.discoverTvShows(genreId = category.tvGenreId.toString(), providerIds = providersStr, originalLanguage = originalLanguage, page = page)
            if (tResp.isSuccessful) tResp.body()?.results?.let { tvResults.addAll(it) }

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
            val tvSummaries    = tvResults
                .filter { !it.posterPath.isNullOrBlank() }
                .mapNotNull  { it.toSummary(explicitIsMovie = false) }

            (movieSummaries + tvSummaries).sortedByDescending { it.voteAverage }.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrending(providerIds: Set<Int> = emptySet(), originalLanguage: String? = null): List<MediaSummary> = withContext(Dispatchers.IO) {
        // Legacy fallback for 3 pages
        (1..3).flatMap { getTrendingPaged(providerIds, it, originalLanguage) }.distinctBy { it.id }
    }

    /**
     * "Trending, filtered to one original-production language" — backs the
     * Home screen's language carousels (Japanese Shows & Anime, Korean Dramas & Movies, ...)
     * and reuses the exact same discover-endpoint path Language Filters uses
     * elsewhere.
     */
    suspend fun getTrendingByLanguage(languageCode: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        getTrendingPaged(originalLanguage = languageCode, page = 1)
    }

    suspend fun discoverByGenre(
        category: TmdbConfig.DiscoveryCategory,
        providerIds: Set<Int> = emptySet(),
        originalLanguage: String? = null
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        // Legacy fallback for 2 pages
        (1..2).flatMap { discoverByGenrePaged(category, providerIds, it, originalLanguage) }.distinctBy { it.id }.take(40)
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
        // Legacy fallback for 2 pages
        (1..2).flatMap { discoverByGenrePaged(category, providerIds, it) }.distinctBy { it.id }.take(40)
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

    fun syncProfileToCloud(isTasteDone: Boolean, lastActive: Long, streak: Int, genres: Set<String> = emptySet()) {
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
        try {
            // Step 1: Resolve MAL ID (type="tv" filter is applied by JikanApiService)
            val searchResponse = jikanApiService.searchAnime(showTitle)
            
            if (!searchResponse.isSuccessful) {
                Log.w(TAG, "Jikan search failed for '$showTitle': HTTP ${searchResponse.code()}")
                return
            }

            val malId = searchResponse.body()?.data?.firstOrNull()?.malId
            
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
                // First attempt
                var epResponse = jikanApiService.getEpisodes(malId, page)

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

    private suspend fun com.example.watchorderengine.network.model.TmdbDetailResponse.toMediaEntity(
        mediaId: String
    ): MediaEntity {
        val existing = db.mediaDao().getById(mediaId)
        val genresList = genres?.map { it.name } ?: emptyList()
        val isMovie    = if (isMovieId(mediaId)) true else if (isTvId(mediaId)) false else title != null
        val category   = if (isMovie) "MOVIE" else "TV_SHOW"

        // Always honour the typed prefix passed in — never compute a new ID here.
        val trailerKey = videos?.results
            ?.filter { it.site == "YouTube" && it.type == "Trailer" && it.official }
            ?.maxByOrNull { it.publishedAt ?: "" }?.key

        val providers     = resolveWatchProviders(watchProviders?.results)
        val providersJson = runCatching { wpAdapter.toJson(providers) }.getOrDefault("[]")

        return MediaEntity(
            id = mediaId, tmdbId = this.id,
            anilistId = existing?.anilistId,
            title = title ?: name ?: "",
            originalTitle = originalTitle ?: originalName ?: "",
            overview = overview ?: "", tagline = tagline ?: "", status = status ?: "",
            posterUrl   = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = category,
            genres = genresList, 
            ageRating = contentRatings?.results?.find { it.countryCode == "US" }?.rating
                ?: releaseDates?.results?.find { it.countryCode == "US" }?.releaseDates?.firstOrNull()?.certification
                ?: existing?.ageRating ?: "NR",
            voteAverage = voteAverage.toFloat(), voteCount = voteCount,
            runtime = runtime ?: episodeRunTime?.firstOrNull(),
            numberOfSeasons = numberOfSeasons, numberOfEpisodes = numberOfEpisodes,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = trailerKey,
            originalLanguage = originalLanguage,
            watchProvidersJson = providersJson,
            castJson = existing?.castJson ?: "[]",
            recommendationsJson = existing?.recommendationsJson ?: "[]",
            arcsJson = existing?.arcsJson ?: "[]",
            jikanFillerSynced = existing?.jikanFillerSynced ?: false,
            nextAirDate = this.nextEpisodeToAir?.airDate ?: existing?.nextAirDate,
            nextEpisodeNumber = this.nextEpisodeToAir?.episodeNumber ?: existing?.nextEpisodeNumber,
            nextEpisodeSeasonNumber = this.nextEpisodeToAir?.seasonNumber ?: existing?.nextEpisodeSeasonNumber,
            nextEpisodeName = this.nextEpisodeToAir?.name?.takeIf { it.isNotBlank() } ?: existing?.nextEpisodeName
        )
    }

    private fun resolveWatchProviders(
        results: Map<String, TmdbWatchProviderCountry>?
    ): List<WatchProviderItem> {
        if (results.isNullOrEmpty()) return emptyList()
        // Prefer the device's actual configured region (Settings > System >
        // Languages & region) — this is "the user's region" the /watch/providers
        // response should be read for. Only fall back to the static priority
        // list, then to whatever's available, when TMDB has no data for it
        // (e.g. a region with no reported streaming availability yet).
        val deviceRegion = appContext.resources.configuration.locales.get(0)?.country
            ?.takeIf { it.isNotBlank() }
        val countryCode = deviceRegion?.takeIf { results.containsKey(it) }
            ?: TmdbConfig.PROVIDER_COUNTRY_PRIORITY.firstOrNull { results.containsKey(it) }
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
            genres = genresList, releaseDate = releaseDate ?: firstAirDate,
            originalLanguage = originalLanguage
        )
    }
}
