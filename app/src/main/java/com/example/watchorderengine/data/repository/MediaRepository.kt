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
    fun findUniversesForMedia(tmdbId: Int) = watchOrderRepository.findUniversesForMedia(tmdbId)

    /**
     * One-time repair for legacy universes seeded before posters were stored
     * on the universe document at all (the old Node.js seed scripts had no
     * image field whatsoever). Resolves a poster from the universe's first
     * node via TMDB and writes it back, so the Graph list's rectangular
     * tabs stop rendering blank for old universes while new Gemini-generated
     * ones already work. Safe to call repeatedly — universes that already
     * have a poster are skipped, so this becomes a fast no-op after the
     * first successful run.
     */
    suspend fun backfillMissingUniversePosters(universes: List<com.example.watchorderengine.data.model.Universe>) {
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

    private val castType = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }

    // ─── Media Detail ─────────────────────────────────────────────────────────

    fun getMediaDetailFlow(mediaId: String): Flow<MediaDetail?> = flow {
        val cached = buildMediaDetail(mediaId)
        emit(cached)

        val refreshed = refreshDetail(mediaId)
        if (refreshed || cached == null) {
            emit(buildMediaDetail(mediaId))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun refreshDetail(mediaId: String): Boolean {
        val tmdbId = mediaId.removePrefix("tmdb_").toIntOrNull() ?: return false
        val cachedEntity = db.mediaDao().getById(mediaId)

        // Robust routing: 
        // 1. If we know the category, use it.
        // 2. If it's a new ID (no cached category), try TV then Movie (TV is more complex to fetch).
        return when (cachedEntity?.mediaCategory) {
            "MOVIE"  -> fetchAndCacheMovie(tmdbId, mediaId)
            "TV_SHOW" -> fetchAndCacheTv(tmdbId, mediaId)
            "ANIME" -> fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
            else -> {
                // Try to resolve category via TMDB search if unknown
                val searchMatch = searchMedia(cachedEntity?.title ?: "").firstOrNull { it.tmdbId == tmdbId }
                if (searchMatch?.mediaCategory == MediaCategory.ANIME) {
                    fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
                } else if (searchMatch?.mediaCategory == MediaCategory.MOVIE) {
                    fetchAndCacheMovie(tmdbId, mediaId)
                } else {
                    fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
                }
            }
        }
    }

    private suspend fun fetchAndCacheMovie(tmdbId: Int, mediaId: String): Boolean {
        return try {
            val response = apiService.getMovie(tmdbId)
            if (!response.isSuccessful || response.body() == null) return false
            val body = response.body()!!
            val entity = body.toMediaEntity(mediaId)
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
        tmdbId: Int,
        mediaId: String,
        seasonNumber: Int,
        episodeCount: Int
    ) {
        Log.d(TAG, "Refreshing episodes for $mediaId season $seasonNumber")
        try {
            val response = apiService.getTvSeason(tmdbId, seasonNumber)
            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "Season $seasonNumber response failed: ${response.code()} ${response.errorBody()?.string()}")
                return
            }

            val seasonBody = response.body()!!
            val seasonId = "${mediaId}_s$seasonNumber"

            val seasonEntity = SeasonEntity(
                id = seasonId, mediaId = mediaId, seasonNumber = seasonNumber,
                name = seasonBody.name, overview = seasonBody.overview ?: "",
                posterUrl = TmdbConfig.buildImageUrl(seasonBody.posterPath),
                airDate = seasonBody.airDate, episodeCount = episodeCount
            )
            db.seasonDao().upsertAll(listOf(seasonEntity))

            val previousEpisodes = db.episodeDao()
                .getEpisodesInRange(mediaId, 0, (seasonNumber - 1) * 1000)
                .size

            val existingEpisodes = db.episodeDao().getEpisodesBySeason(seasonId)
                .associateBy { it.episodeNumber }

            val episodeEntities = seasonBody.episodes?.mapIndexed { idx, ep ->
                val existing = existingEpisodes[ep.episodeNumber]
                EpisodeEntity(
                    id = "${mediaId}_s${seasonNumber}e${ep.episodeNumber}",
                    seasonId = seasonId, mediaId = mediaId,
                    episodeNumber = ep.episodeNumber, seasonNumber = seasonNumber,
                    absoluteEpisodeNumber = previousEpisodes + idx + 1,
                    title = ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview ?: "",
                    airDate = ep.airDate, runtime = ep.runtime,
                    stillUrl = TmdbConfig.buildImageUrl(ep.stillPath, TmdbConfig.PosterSize.SMALL),
                    voteAverage = ep.voteAverage?.toFloat() ?: 0f,
                    episodeType = existing?.episodeType ?: "CANON",
                    arcName = existing?.arcName
                )
            } ?: run {
                Log.w(TAG, "No episodes found in response for $mediaId S$seasonNumber")
                return
            }
            if (episodeEntities.isNotEmpty()) {
                Log.d(TAG, "Upserting ${episodeEntities.size} episodes for $seasonId")
                db.episodeDao().upsertAll(episodeEntities)
            }
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

    private suspend fun buildMediaDetail(mediaId: String): MediaDetail? {
        val entity   = db.mediaDao().getById(mediaId) ?: return null
        val seasons  = db.seasonDao().getSeasonsByMedia(mediaId)
        val progress = db.userProgressDao().getProgress(mediaId)

        val castType    = Types.newParameterizedType(List::class.java, CastMember::class.java)
        val castAdapter = moshi.adapter<List<CastMember>>(castType)
        val cast        = runCatching { castAdapter.fromJson(entity.castJson) }.getOrDefault(emptyList())

        val arcsType    = Types.newParameterizedType(List::class.java, StoryArc::class.java)
        val arcsAdapter = moshi.adapter<List<StoryArc>>(arcsType)
        val arcs        = runCatching { arcsAdapter.fromJson(entity.arcsJson) }.getOrDefault(emptyList())

        // ERROR #5 — deserialise watch providers from the cached JSON column
        val wpType      = Types.newParameterizedType(List::class.java, WatchProviderItem::class.java)
        val wpAdapter   = moshi.adapter<List<WatchProviderItem>>(wpType)
        val providers   = runCatching {
            wpAdapter.fromJson(entity.watchProvidersJson)
        }.getOrDefault(emptyList())

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
            mediaCategory    = MediaCategory.valueOf(entity.mediaCategory),
            genres           = entity.genres,
            ageRating        = entity.ageRating.ifBlank { "NR" },
            voteAverage      = entity.voteAverage,
            voteCount        = entity.voteCount,
            runtime          = entity.runtime,
            numberOfSeasons  = entity.numberOfSeasons,
            numberOfEpisodes = entity.numberOfEpisodes,
            releaseDate      = entity.releaseDate,
            releaseYear      = entity.releaseDate?.take(4) ?: "",
            trailerKey       = entity.trailerKey,
            watchProviders   = providers ?: emptyList(),
            cast             = cast ?: emptyList(),
            recommendations  = emptyList(),
            seasons          = seasons.map { it.toDomain() },
            arcs             = arcs ?: emptyList(),
            userProgress     = progress?.toDomain()
        )
    }

    suspend fun getEpisodesBySeason(mediaId: String, seasonNumber: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val sanitizedId = if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) mediaId else "tmdb_$mediaId"
        val seasonId = "${sanitizedId}_s$seasonNumber"
        
        Log.d(TAG, "Querying episodes for seasonId: $seasonId")
        val episodes = db.episodeDao().getEpisodesBySeason(seasonId)
        Log.d(TAG, "Found ${episodes.size} episodes in DB for $seasonId")
        
        val watchedIds = db.episodeWatchedDao().getWatchedIds(sanitizedId).toSet()
        episodes.map { it.toDomain(watchedIds) }
    }

    fun observeEpisodesBySeason(mediaId: String, seasonNumber: Int): Flow<List<EpisodeItem>> {
        val sanitizedId = if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) mediaId else "tmdb_$mediaId"
        val seasonId = "${sanitizedId}_s$seasonNumber"

        return db.episodeDao().observeEpisodesBySeason(seasonId).map { episodes ->
            val watchedIds = db.episodeWatchedDao().getWatchedIds(sanitizedId).toSet()
            episodes.map { it.toDomain(watchedIds) }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun searchMedia(query: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = apiService.searchMulti(query)
            if (!response.isSuccessful) return@withContext emptyList()
            val results = response.body()?.results?.filter { it.mediaType == "movie" || it.mediaType == "tv" } ?: return@withContext emptyList()
            results.forEach { result ->
                val mediaId = "tmdb_${result.id}"
                if (db.mediaDao().getById(mediaId) == null) db.mediaDao().upsert(result.toMinimalEntity(mediaId))
            }
            results.mapNotNull { it.toSummary() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun generateWatchOrder(mediaId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext "Show not found."
        val isMovie = entity.mediaCategory == "MOVIE"
        val seasonCounts = db.seasonDao().getSeasonsByMedia(mediaId).sortedBy { it.seasonNumber }.map { it.episodeCount }

        val result = geminiService.generateWatchOrder(
            showTitle = entity.title,
            overview = entity.overview,
            seasonEpisodeCounts = seasonCounts,
            mediaType = if (isMovie) "movie" else "tv"
        )

        when (result) {
            is GeminiResult.Error -> result.message
            is GeminiResult.Success -> {
                val watchOrder = result.watchOrder
                val universeId = mediaId
                val idMap = java.util.concurrent.ConcurrentHashMap<String, String>()

                Log.d(TAG, "GENERATION: Gemini returned ${watchOrder.nodes.size} nodes and ${watchOrder.edges.size} edges.")

                // 1. Resolve TMDB IDs for each node. Use direct ID from Gemini if provided.
                val mediaNodes = supervisorScope {
                    watchOrder.nodes.map { node ->
                        async {
                            Log.d(TAG, "NODE_RESOLVE: Starting for title='${node.title}', gemini_tmdb_id=${node.tmdbId}")
                            
                            // If Gemini provided a direct TMDB ID, use it. 
                            // Otherwise, fallback to the parent show's ID (for episode arcs).
                            var resolvedId = if (node.tmdbId > 0) node.tmdbId else entity.tmdbId
                            var resolvedType = if (node.contentType == "MOVIE") "movie" else "tv"

                            // Safety check: if Gemini ID is 0 or same as parent, but it's a
                            // standalone MOVIE/SERIES, we can try a title search as a last
                            // resort — but ONLY when the node's title is actually different
                            // from the parent show. If the titles match (e.g. a "Toy Story"
                            // node generated while viewing "Toy Story" itself), this node IS
                            // the parent — searching anyway and grabbing whatever TMDB
                            // returns is how an unrelated movie ends up attached to the
                            // correct title (the old fallback `find { it.tmdbId != entity.tmdbId }`
                            // had no title-matching at all, so it would pick literally any
                            // other search result once the first two stricter matches missed).
                            if (resolvedId == entity.tmdbId && (node.contentType == "MOVIE" || node.contentType == "SERIES")) {
                                val isSameTitleAsParent = node.title.equals(entity.title, ignoreCase = true) ||
                                                        (entity.title.length > 5 && node.title.startsWith(entity.title, ignoreCase = true))

                                if (!isSameTitleAsParent) {
                                    val query = node.searchQuery ?: node.title
                                    val searchResults = searchMedia(query)

                                    val bestMatch = searchResults.find {
                                        it.title.equals(node.title, ignoreCase = true)
                                    } ?: searchResults.find {
                                        it.title.contains(node.title, ignoreCase = true) &&
                                        node.title.contains(it.title, ignoreCase = true)
                                    }
                                    // Removed: the old third fallback matched ANY search result
                                    // that wasn't the parent's exact ID, with no title check at
                                    // all — that's what let an unrelated title slip in. If the
                                    // two title-based matches above don't find anything, we now
                                    // correctly fall through and keep resolvedId = entity.tmdbId
                                    // (i.e. treat the node as belonging to the parent show)
                                    // rather than guessing.

                                    if (bestMatch != null) {
                                        resolvedId = bestMatch.tmdbId
                                        resolvedType = if (bestMatch.mediaCategory == MediaCategory.MOVIE) "movie" else "tv"
                                        Log.d(TAG, "NODE_RESOLVE: Found via search! query='$query', id=$resolvedId")
                                    }
                                }
                            }

                            val finalId = if (resolvedId != entity.tmdbId && (node.contentType == "MOVIE" || node.contentType == "SERIES")) {
                                "tmdb_$resolvedId"
                            } else {
                                node.nodeId
                            }
                            idMap[node.nodeId] = finalId

                            Log.d(TAG, "NODE_RESOLVE: Result for '${node.title}' -> finalId=$finalId, resolvedId=$resolvedId")

                            // CRITICAL: Upsert skeleton media if it's a new standalone movie/show
                            if (finalId.startsWith("tmdb_")) {
                                val existing = db.mediaDao().getById(finalId)
                                if (existing == null) {
                                    Log.d(TAG, "DB_WRITE: Creating skeleton for $finalId ('${node.title}')")
                                    db.mediaDao().upsert(MediaEntity(
                                        id = finalId, tmdbId = resolvedId, 
                                        anilistId = null, title = node.title, originalTitle = node.title,
                                        overview = "", tagline = "", status = "Skeleton",
                                        posterUrl = null, backdropUrl = null,
                                        mediaCategory = if (resolvedType == "movie") "MOVIE" else "TV_SHOW",
                                        genres = emptyList(), ageRating = "NR", voteAverage = 0f, voteCount = 0,
                                        runtime = null, numberOfSeasons = null, numberOfEpisodes = null,
                                        releaseDate = null, releaseYear = "", trailerKey = null,
                                        castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
                                    ))
                                } else {
                                    Log.d(TAG, "DB_WRITE: $finalId already exists in DB.")
                                }
                            }

                            com.example.watchorderengine.data.model.MediaNode(
                                id = finalId,
                                title = node.title,
                                tmdb_id = resolvedId,
                                tmdb_media_type = resolvedType,
                                content_type = node.contentType,
                                type = if (resolvedType == "movie") MediaCategory.MOVIE else MediaCategory.TV_SHOW,
                                chrono_order = node.chronoOrder,
                                release_order = node.releaseOrder,
                                phase = node.phase,
                                tags = node.tags
                            )
                        }
                    }.awaitAll()
                }

                val mediaEdges = watchOrder.edges.map { edge ->
                    com.example.watchorderengine.data.model.Edge(
                        from_node_id = idMap[edge.fromNodeId] ?: edge.fromNodeId,
                        to_node_id = idMap[edge.toNodeId] ?: edge.toNodeId,
                        type = edge.type
                    )
                }

                // 2. Publish to Firestore
                Log.d(TAG, "FIRESTORE: Clearing and publishing universe=$universeId")
                watchOrderRepository.clearGeneratedUniverse(universeId)
                val publishResult = watchOrderRepository.publishGeneratedUniverse(
                    universeId = universeId,
                    universeName = entity.title,
                    coverUrl = entity.posterUrl ?: "",
                    nodes = mediaNodes,
                    edges = mediaEdges
                )

                if (publishResult.isFailure) {
                    Log.e(TAG, "FIRESTORE: Push failed: ${publishResult.exceptionOrNull()?.message}")
                    return@withContext "Saved locally, but Firestore push failed: ${publishResult.exceptionOrNull()?.message}"
                }

                // 3. Update episode types and refresh from TMDB
                Log.d(TAG, "DB_WRITE: Updating episode types for ${watchOrder.nodes.size} nodes")
                supervisorScope {
                    watchOrder.nodes.forEach { node ->
                        val finalNodeId = idMap[node.nodeId] ?: node.nodeId
                        
                        // If it's a new movie/show, trigger a full refresh to get its episodes/details
                        if (finalNodeId.startsWith("tmdb_") && finalNodeId != mediaId) {
                            launch {
                                Log.d(TAG, "FETCH: Triggering detail refresh for $finalNodeId")
                                refreshDetail(finalNodeId)
                            }
                        }

                        val classification = when {
                            "FILLER" in node.tags -> "FILLER"
                            "MIXED" in node.tags -> "MIXED"
                            else -> "CANON"
                        }
                        val seasonId = "${finalNodeId}_s${node.startSeason}"
                        val nodeEpisodes = db.episodeDao().getEpisodesBySeason(seasonId).filter { ep ->
                            ep.episodeNumber in node.startEpisode..node.endEpisode
                        }
                        
                        if (nodeEpisodes.isNotEmpty()) {
                            Log.d(TAG, "DB_WRITE: Mapping ${nodeEpisodes.size} episodes to arc '${node.title}'")
                            nodeEpisodes.forEach { ep ->
                                db.episodeDao().upsertAll(listOf(ep.copy(
                                    episodeType = classification,
                                    arcName = node.title
                                )))
                            }
                        }
                    }
                }
                null
            }
        }
    }

    suspend fun getPersonBiography(personId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPerson(personId)
            if (response.isSuccessful) response.body()?.biography
            else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTrending(): List<MediaSummary> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrending()
            if (!response.isSuccessful) return@withContext emptyList()
            val results = response.body()?.results ?: return@withContext emptyList()
            results.forEach { result ->
                if (result.mediaType == "movie" || result.mediaType == "tv") {
                    val mediaId = "tmdb_${result.id}"
                    if (db.mediaDao().getById(mediaId) == null) db.mediaDao().upsert(result.toMinimalEntity(mediaId))
                }
            }
            results.filter { it.mediaType == "movie" || it.mediaType == "tv" }.mapNotNull { it.toSummary() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateTrackingState(mediaId: String, state: TrackingState) = withContext(Dispatchers.IO) {
        val current = db.userProgressDao().getProgress(mediaId)
        db.userProgressDao().upsert(UserProgressEntity(
            mediaId = mediaId, trackingState = state.name,
            userNotes = current?.userNotes ?: "",
            priorityTag = current?.priorityTag ?: "NONE"
        ))
        // A show that's now permanently tracked shouldn't still occupy a
        // "temporarily skipped" slot — remove just this one id, leaving
        // every other skip untouched.
        db.discoverySkippedDao().removeSkipped(mediaId)
    }

    /** All media IDs currently in ANY of the 5 tracking states — used to exclude already-decided shows from Discovery. */
    suspend fun getAllTrackedMediaIds(): Set<String> = withContext(Dispatchers.IO) {
        db.userProgressDao().getAll().map { it.mediaId }.toSet()
    }

    /** Media IDs the user swiped left on ("skip") — excluded from Discovery until [clearSkipped]. */
    suspend fun getSkippedMediaIds(): Set<String> = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().getAllSkippedIds().toSet()
    }

    suspend fun markSkipped(mediaId: String) = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().markSkipped(DiscoverySkippedEntity(mediaId))
    }

    /** Clears all temporary skips, letting them resurface in Discovery again. Does NOT touch real tracking states. */
    suspend fun clearSkipped() = withContext(Dispatchers.IO) {
        db.discoverySkippedDao().clearAllSkipped()
    }

    suspend fun toggleEpisodeWatched(episodeId: String, mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val isWatched = db.episodeWatchedDao().isWatched(episodeId)
        if (isWatched) db.episodeWatchedDao().unmarkWatched(episodeId)
        else db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(episodeId, mediaId))
        !isWatched
    }

    /**
     * Generic fetch by tracking state — fixes the bug where Completed/Dropped/
     * Paused never had their own query path (only Watching/Planned did),
     * so shows marked into those states would vanish from Home entirely.
     */
    suspend fun getListByState(
        state: TrackingState,
        sortType: SortType = SortType.DATE_ADDED
    ): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(state.name)
        val summaries = progressList.mapNotNull { progress ->
            db.mediaDao().getById(progress.mediaId)?.toSummary(
                state, PriorityTag.valueOf(progress.priorityTag)
            )
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
        SortType.ALPHABETICAL   -> list.sortedBy { it.title }
        SortType.USER_RATING,
        SortType.GLOBAL_SCORE   -> list.sortedByDescending { it.voteAverage }
        SortType.DATE_ADDED     -> list
    }

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

    private fun UserProgressEntity.toDomain() = UserProgress(
        mediaId = mediaId, trackingState = TrackingState.valueOf(trackingState),
        currentSeasonNumber = currentSeasonNumber, currentEpisodeNumber = currentEpisodeNumber,
        totalEpisodesWatched = 0, userRating = userRating,
        startedDate = startedDate, completedDate = completedDate, updatedAt = updatedAt,
        userNotes = userNotes, priorityTag = PriorityTag.valueOf(priorityTag)
    )

    private fun MediaEntity.toSummary(state: TrackingState? = null, priority: PriorityTag = PriorityTag.NONE) =
        MediaSummary(
            id = id, tmdbId = tmdbId, title = title, posterUrl = posterUrl,
            backdropUrl = backdropUrl, mediaCategory = MediaCategory.valueOf(mediaCategory),
            voteAverage = voteAverage, releaseYear = releaseYear,
            trackingState = state, ageRating = ageRating, priorityTag = priority,
            releaseDate = releaseDate
        )

    private fun com.example.watchorderengine.network.model.TmdbDetailResponse.toMediaEntity(
        mediaId: String
    ): MediaEntity {
        val isMovie = title != null
        val genresList = genres?.map { it.name } ?: emptyList()
        val isAnimation = genresList.contains("Animation")
        
        val category = when {
            isAnimation -> "ANIME"
            isMovie -> "MOVIE"
            else -> "TV_SHOW"
        }

        val trailerKey = videos?.results
            ?.filter { it.site == "YouTube" && it.type == "Trailer" && it.official }
            ?.maxByOrNull { it.publishedAt ?: "" }?.key

        // Resolve providers at write-time so reads are instant.
        val providers    = resolveWatchProviders(watchProviders?.results)
        val wpType       = Types.newParameterizedType(List::class.java, WatchProviderItem::class.java)
        val wpAdapter    = moshi.adapter<List<WatchProviderItem>>(wpType)
        val providersJson = runCatching { wpAdapter.toJson(providers) }.getOrDefault("[]")

        return MediaEntity(
            id = mediaId, tmdbId = this.id,
            anilistId = null,
            title = title ?: name ?: "",
            originalTitle = originalTitle ?: originalName ?: "",
            overview = overview ?: "",
            tagline = tagline ?: "",
            status = status ?: "",
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = category,
            genres = genres?.map { it.name } ?: emptyList(),
            ageRating = "NR",
            voteAverage = voteAverage.toFloat(),
            voteCount = voteCount,
            runtime = runtime ?: episodeRunTime?.firstOrNull(),
            numberOfSeasons = numberOfSeasons,
            numberOfEpisodes = numberOfEpisodes,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = trailerKey,
            watchProvidersJson = providersJson,
            castJson = "[]",
            recommendationsJson = "[]",
            arcsJson = "[]"
        )
    }

    private fun resolveWatchProviders(
        results: Map<String, TmdbWatchProviderCountry>?
    ): List<WatchProviderItem> {
        if (results.isNullOrEmpty()) return emptyList()

        val countryCode = TmdbConfig.PROVIDER_COUNTRY_PRIORITY
            .firstOrNull { results.containsKey(it) }
            ?: results.keys.firstOrNull()
            ?: return emptyList()

        val country      = results[countryCode] ?: return emptyList()
        val justWatchUrl = country.link

        fun List<TmdbWatchProvider>?.toItems(type: String) =
            this?.sortedBy { it.displayPriority }?.map { p ->
                WatchProviderItem(
                    providerId   = p.providerId,
                    providerName = TmdbConfig.PROVIDER_SHORT_NAMES[p.providerId] ?: p.providerName,
                    logoUrl      = TmdbConfig.buildImageUrl(p.logoPath, TmdbConfig.PosterSize.THUMBNAIL),
                    offerType    = type,
                    justWatchUrl = justWatchUrl
                )
            } ?: emptyList()

        return (country.flatrate.toItems("stream") +
                country.free.toItems("free") +
                country.rent.toItems("rent") +
                country.buy.toItems("buy"))
            .distinctBy { it.providerId }
    }

    suspend fun countWatchedEpisodes(): Int = withContext(Dispatchers.IO) {
        try { db.episodeWatchedDao().countAllWatched() } catch (e: Exception) { 0 }
    }

    suspend fun getAllRatedMedia(): List<Pair<String, Float>> = withContext(Dispatchers.IO) {
        try {
            db.userProgressDao().getAll()
                .mapNotNull { p -> p.userRating?.let { Pair(p.mediaId, it) } }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun updateRating(mediaId: String, rating: Float) = withContext(Dispatchers.IO) {
        val now     = System.currentTimeMillis()
        val updated = db.userProgressDao().updateRating(mediaId, rating, now)
        if (updated == 0) {
            db.userProgressDao().upsert(
                UserProgressEntity(
                    mediaId       = mediaId,
                    trackingState = "PLANNED",
                    userRating    = rating
                )
            )
        }
    }

    suspend fun computeWatchStreak(): Int = withContext(Dispatchers.IO) {
        try {
            val timestamps = db.episodeWatchedDao().getAllWatchedTimestamps()
            if (timestamps.isEmpty()) return@withContext 0

            // Midnight-align each timestamp to a calendar day.
            val days = timestamps.map { ts ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = ts
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }.toSortedSet(reverseOrder())   // descending, unique days

            val oneDayMs = 24L * 60L * 60L * 1000L
            val todayMidnight = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0);       set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Walk backwards from today; stop at the first missing day.
            var streak   = 0
            var expected = todayMidnight
            for (day in days) {
                if (day == expected) { streak++; expected -= oneDayMs }
                else if (day < expected) break
                // day > expected means today's entries appeared but yesterday was missing
                // — don't count a partial day at the head
            }
            streak
        } catch (e: Exception) {
            Log.w(TAG, "computeWatchStreak failed: ${e.message}")
            0
        }
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toMinimalEntity(
        mediaId: String
    ): MediaEntity {
        val isMovie = mediaType == "movie"
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        val isAnimation = genresList.contains("Animation")
        
        return MediaEntity(
            id = mediaId, tmdbId = id,
            anilistId = null,
            title = title ?: name ?: "",
            originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                isAnimation -> "ANIME"
                isMovie -> "MOVIE"
                else -> "TV_SHOW"
            },
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage?.toFloat() ?: 0f,
            voteCount = 0, runtime = null,
            numberOfSeasons = null, numberOfEpisodes = null,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = null,
            castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(): MediaSummary? {
        if (mediaType == null || (mediaType != "movie" && mediaType != "tv")) return null
        val mediaId = "tmdb_$id"
        val isMovie = mediaType == "movie"
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        val isAnimation = genresList.contains("Animation")

        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                isAnimation -> MediaCategory.ANIME
                isMovie -> MediaCategory.MOVIE
                else -> MediaCategory.TV_SHOW
            },
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trackingState = null,
            ageRating = "NR",
            genres = genresList,
            releaseDate = releaseDate ?: firstAirDate
        )
    }

    /**
     * Same mapping as [toSummary], but for `/discover/movie` and `/discover/tv`
     * results, which carry NO `media_type` field at all (unlike search/trending) —
     * the endpoint itself already tells us the type, so it's passed in explicitly
     * instead of being read from (missing) response data. Without this, every
     * discover result would be silently dropped by the `mediaType == null` guard
     * in [toSummary], which is exactly why genre category screens showed nothing.
     */
    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(
        isMovie: Boolean
    ): MediaSummary? {
        val mediaId = "tmdb_$id"
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        val isAnimation = genresList.contains("Animation")

        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                isAnimation -> MediaCategory.ANIME
                isMovie -> MediaCategory.MOVIE
                else -> MediaCategory.TV_SHOW
            },
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trackingState = null,
            ageRating = "NR",
            genres = genresList,
            releaseDate = releaseDate ?: firstAirDate
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toMinimalEntity(
        mediaId: String, isMovie: Boolean
    ): MediaEntity {
        val genresList = TmdbConfig.genreNamesFor(genreIds, isMovie)
        val isAnimation = genresList.contains("Animation")

        return MediaEntity(
            id = mediaId, tmdbId = id,
            anilistId = null,
            title = title ?: name ?: "",
            originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = when {
                isAnimation -> "ANIME"
                isMovie -> "MOVIE"
                else -> "TV_SHOW"
            },
            genres = genresList, ageRating = "NR",
            voteAverage = voteAverage?.toFloat() ?: 0f,
            voteCount = 0, runtime = null,
            numberOfSeasons = null, numberOfEpisodes = null,
            releaseDate = releaseDate ?: firstAirDate,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trailerKey = null,
            castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    /**
     * Discovers media by genre for the Discovery screen's category chips.
     * Queries both movie and TV discover endpoints (TMDB has no combined
     * "discover/multi") and interleaves the results.
     */
    suspend fun discoverByGenre(category: TmdbConfig.DiscoveryCategory): List<MediaSummary> =
        withContext(Dispatchers.IO) {
            try {
                val movieResponse = apiService.discoverMovies(genreId = category.movieGenreId)
                val tvResponse = apiService.discoverTvShows(genreId = category.tvGenreId)

                val movies = if (movieResponse.isSuccessful) movieResponse.body()?.results ?: emptyList() else emptyList()
                val shows = if (tvResponse.isSuccessful) tvResponse.body()?.results ?: emptyList() else emptyList()

                // Cache to Room so tapping a card opens Detail instantly,
                // same pattern as searchMedia()/getTrending().
                movies.forEach { result ->
                    val mediaId = "tmdb_${result.id}"
                    if (db.mediaDao().getById(mediaId) == null) {
                        db.mediaDao().upsert(result.toMinimalEntity(mediaId, isMovie = true))
                    }
                }
                shows.forEach { result ->
                    val mediaId = "tmdb_${result.id}"
                    if (db.mediaDao().getById(mediaId) == null) {
                        db.mediaDao().upsert(result.toMinimalEntity(mediaId, isMovie = false))
                    }
                }

                val movieSummaries = movies.mapNotNull { it.toSummary(isMovie = true) }
                val tvSummaries = shows.mapNotNull { it.toSummary(isMovie = false) }

                // Interleave rather than concatenate so the deck isn't "all
                // movies, then all shows" — more variety swipe-to-swipe.
                movieSummaries.zip(tvSummaries) { m, t -> listOf(m, t) }.flatten() +
                    movieSummaries.drop(tvSummaries.size) + tvSummaries.drop(movieSummaries.size)
            } catch (e: Exception) {
                Log.w(TAG, "discoverByGenre failed for ${category.label}: ${e.message}")
                emptyList()
            }
        }
}
