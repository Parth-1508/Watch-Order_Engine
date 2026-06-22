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
    fun findUniverseForMedia(tmdbId: Int, anilistId: Int?) = watchOrderRepository.findUniverseForMedia(tmdbId, anilistId)
    private val castType = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }
    private val arcsType = Types.newParameterizedType(List::class.java, StoryArc::class.java)
    private val arcsAdapter by lazy { moshi.adapter<List<StoryArc>>(arcsType) }

    // ─── Media Detail ─────────────────────────────────────────────────────────

    fun getMediaDetailFlow(mediaId: String): Flow<MediaDetail?> = flow {
        val cached = buildMediaDetail(mediaId)
        emit(cached)

        // Force a refresh if there's no cache OR if the cache is just a "Skeleton" from watch order generation.
        val isSkeleton = db.mediaDao().getById(mediaId)?.status == "Skeleton"
        val refreshed = refreshDetail(mediaId)
        
        if (refreshed || cached == null || isSkeleton) {
            emit(buildMediaDetail(mediaId))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun refreshDetail(mediaId: String): Boolean {
        val tmdbId = mediaId.removePrefix("tmdb_").toIntOrNull() ?: return false
        val cachedEntity = db.mediaDao().getById(mediaId)

        Log.d(TAG, "refreshDetail: Attempting refresh for $mediaId (Category: ${cachedEntity?.mediaCategory}, Status: ${cachedEntity?.status})")

        // Robust routing: 
        // 1. If we know the category and it's NOT a skeleton, use it.
        // 2. If it's a skeleton or category unknown, try TV then Movie.
        if (cachedEntity != null && cachedEntity.status != "Skeleton") {
            return when (cachedEntity.mediaCategory) {
                "MOVIE"  -> fetchAndCacheMovie(tmdbId, mediaId)
                "TV_SHOW" -> fetchAndCacheTv(tmdbId, mediaId)
                else -> false
            }
        }

        // It's a skeleton or unknown. Try both.
        Log.d(TAG, "refreshDetail: $mediaId is a skeleton or unknown category. Trying TV then Movie.")
        val tvSuccess = fetchAndCacheTv(tmdbId, mediaId)
        if (tvSuccess) return true
        
        return fetchAndCacheMovie(tmdbId, mediaId)
    }

    private suspend fun fetchAndCacheMovie(tmdbId: Int, mediaId: String): Boolean {
        return try {
            val response = apiService.getMovie(tmdbId)
            if (!response.isSuccessful || response.body() == null) return false
            val body = response.body()!!
            // toMediaEntity() builds a brand-new row from TMDB data only and hardcodes
            // arcsJson/recommendationsJson back to "[]". Without carrying these forward,
            // every refresh (including the one that fires right after "Generate Watch
            // Order" via loadMediaDetail(forceRefresh=true)) silently wipes any
            // locally-generated chronology before the user ever sees it.
            val existing = db.mediaDao().getById(mediaId)
            val entity = body.toMediaEntity(mediaId).copy(
                arcsJson = existing?.arcsJson ?: "[]",
                recommendationsJson = existing?.recommendationsJson ?: "[]"
            )
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
            // Same reasoning as fetchAndCacheMovie() above.
            val existing = db.mediaDao().getById(mediaId)
            val entity = body.toMediaEntity(mediaId).copy(
                arcsJson = existing?.arcsJson ?: "[]",
                recommendationsJson = existing?.recommendationsJson ?: "[]"
            )
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
                CastMember(c.id, c.name, c.character ?: "", TmdbConfig.buildImageUrl(c.profilePath), c.order ?: 99)
            } ?: emptyList()
        } else {
            body.aggregateCredits?.cast?.take(15)?.map { c ->
                CastMember(c.id, c.name, c.roles?.firstOrNull()?.character ?: "", TmdbConfig.buildImageUrl(c.profilePath), c.order ?: 99)
            } ?: emptyList()
        }
        return castAdapter.toJson(cast) ?: "[]"
    }

    private suspend fun buildMediaDetail(mediaId: String): MediaDetail? {
        val entity = db.mediaDao().getById(mediaId) ?: return null
        val seasons = db.seasonDao().getSeasonsByMedia(mediaId)
        val progress = db.userProgressDao().getProgress(mediaId)
        val cast = runCatching { castAdapter.fromJson(entity.castJson) }.getOrDefault(emptyList())

        val arcs = runCatching { arcsAdapter.fromJson(entity.arcsJson) }.getOrDefault(emptyList())

        return MediaDetail(
            id              = entity.id,
            tmdbId          = entity.tmdbId,
            anilistId       = entity.anilistId,
            title           = entity.title,
            originalTitle   = entity.originalTitle,
            overview        = entity.overview,
            tagline         = entity.tagline,
            status          = entity.status,
            posterUrl       = entity.posterUrl,
            backdropUrl     = entity.backdropUrl,
            mediaCategory   = MediaCategory.valueOf(entity.mediaCategory),
            genres          = entity.genres,
            ageRating       = entity.ageRating.ifBlank { "NR" },
            voteAverage     = entity.voteAverage,
            voteCount       = entity.voteCount,
            runtime         = entity.runtime,
            numberOfSeasons = entity.numberOfSeasons,
            numberOfEpisodes = entity.numberOfEpisodes,
            releaseDate     = entity.releaseDate,
            releaseYear     = entity.releaseDate?.take(4) ?: "",
            trailerKey      = entity.trailerKey,
            cast            = cast ?: emptyList(),
            recommendations = emptyList(),
            seasons         = seasons.map { it.toDomain() },
            arcs            = arcs ?: emptyList(),
            userProgress    = progress?.toDomain()
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

                // 1. Resolve TMDB IDs for each node.
                val mediaNodes = supervisorScope {
                    watchOrder.nodes.map { node ->
                        async {
                            Log.d(TAG, "NODE_RESOLVE: Starting for title='${node.title}', suggested_id=${node.tmdbId}")
                            
                            var resolvedId = 0
                            var resolvedType = if (node.contentType == "MOVIE") "movie" else "tv"

                            // TIER 1: Verify suggested ID if provided
                            if (node.tmdbId > 0) {
                                try {
                                    val response = if (resolvedType == "movie") apiService.getMovie(node.tmdbId) else apiService.getTvShow(node.tmdbId)
                                    if (response.isSuccessful && response.body() != null) {
                                        val body = response.body()!!
                                        val remoteTitle = body.title ?: body.name ?: ""
                                        if (isTitleMatch(remoteTitle, node.title)) {
                                            resolvedId = node.tmdbId
                                            Log.d(TAG, "NODE_RESOLVE: Suggested ID ${node.tmdbId} verified!")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "NODE_RESOLVE: Verification failed for suggested ID ${node.tmdbId}")
                                }
                            }

                            // TIER 2: Search if TIER 1 failed or wasn't applicable
                            if (resolvedId <= 0 && (node.contentType == "MOVIE" || node.contentType == "SERIES")) {
                                val query = node.searchQuery ?: node.title
                                val searchResults = searchMedia(query)
                                
                                val bestMatch = searchResults.find { 
                                    isTitleMatch(it.title, node.title)
                                } ?: searchResults.find {
                                    !node.searchQuery.isNullOrBlank() && it.tmdbId != entity.tmdbId && isTitleMatch(it.title, node.title)
                                }

                                if (bestMatch != null) {
                                    resolvedId = bestMatch.tmdbId
                                    resolvedType = if (bestMatch.mediaCategory == MediaCategory.MOVIE) "movie" else "tv"
                                    Log.d(TAG, "NODE_RESOLVE: Found and validated ID via search! query='$query', id=$resolvedId")
                                }
                            }

                            // FINAL RESOLUTION: Map to global tmdb_ ID or keep as local nodeId
                            val finalId = if (resolvedId > 0) "tmdb_$resolvedId" else node.nodeId
                            idMap[node.nodeId] = finalId

                            // CRITICAL: Upsert skeleton media if it's a new external movie/show
                            if (finalId.startsWith("tmdb_") && finalId != mediaId) {
                                val existing = db.mediaDao().getById(finalId)
                                if (existing == null || existing.status == "Skeleton") {
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
                                        castJson = "[]", recommendationsJson = "[]", arcsJson = "[]",
                                        lastUpdated = System.currentTimeMillis()
                                    ))
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

                // 2. Mirror locally FIRST and unconditionally. This is what the
                //    Chronology tab actually reads (MediaDetail.arcs <- entity.arcsJson),
                //    so it must not depend on the Firestore publish below succeeding.
                //
                //    Only nodes that stayed mapped to THIS show (i.e. the resolver above
                //    did NOT redirect them to a different tmdb_ entity) belong in its own
                //    arc list — a node's start/end season+episode only makes sense against
                //    this show's own episode table. Redirected nodes are a different
                //    movie/show entirely and are handled by the skeleton-creation +
                //    refreshDetail() calls below instead.
                Log.d(TAG, "DB_WRITE: Building local arc list + updating episode types for ${watchOrder.nodes.size} nodes")
                val ownArcs = watchOrder.nodes
                    .filter { node -> !(idMap[node.nodeId] ?: node.nodeId).startsWith("tmdb_") }
                    .map { node ->
                        StoryArc(
                            name = node.title,
                            startSeason = node.startSeason,
                            startEpisode = node.startEpisode,
                            endSeason = node.endSeason,
                            endEpisode = node.endEpisode,
                            startAbsoluteEpisode = null,
                            endAbsoluteEpisode = null,
                            synopsis = node.synopsis
                        )
                    }
                val arcsJson = arcsAdapter.toJson(ownArcs) ?: "[]"
                // Re-read in case the skeleton-creation step above already touched this row.
                val latestEntity = db.mediaDao().getById(mediaId) ?: entity
                db.mediaDao().upsert(latestEntity.copy(arcsJson = arcsJson))
                Log.d(TAG, "DB_WRITE: Wrote ${ownArcs.size} arc(s) into $mediaId.arcsJson")

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

                // 3. Publish to Firestore as a best-effort sync for the shared/universe
                //    timeline view. This can fail (auth, rules, network) without the
                //    Chronology tab being affected at all — it already works locally.
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
                    Log.w(TAG, "FIRESTORE: Push failed: ${publishResult.exceptionOrNull()?.message}")
                    "Generated successfully, but syncing to the shared timeline failed: ${publishResult.exceptionOrNull()?.message}"
                } else {
                    null
                }
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
    }

    suspend fun toggleEpisodeWatched(episodeId: String, mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val isWatched = db.episodeWatchedDao().isWatched(episodeId)
        if (isWatched) db.episodeWatchedDao().unmarkWatched(episodeId)
        else db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(episodeId, mediaId))
        !isWatched
    }

    suspend fun getWatchingList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(TrackingState.WATCHING.name)
        val summaries = progressList.mapNotNull { progress ->
            db.mediaDao().getById(progress.mediaId)?.toSummary(
                TrackingState.WATCHING, PriorityTag.valueOf(progress.priorityTag)
            )
        }
        sortSummaries(summaries, sortType)
    }

    suspend fun getPlannedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(TrackingState.PLANNED.name)
        val summaries = progressList.mapNotNull { progress ->
            db.mediaDao().getById(progress.mediaId)?.toSummary(
                TrackingState.PLANNED, PriorityTag.valueOf(progress.priorityTag)
            )
        }
        sortSummaries(summaries, sortType)
    }

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

    private fun isTitleMatch(searchTitle: String, targetTitle: String): Boolean {
        // 1. Clean strings: lowercase, remove special chars, remove "part"
        fun clean(s: String) = s.lowercase()
            .replace("part", "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val s1 = clean(searchTitle)
        val s2 = clean(targetTitle)
        
        // Exact match after cleaning
        if (s1 == s2) return true
        
        // 2. Year Check: If both strings contain a 4-digit year, they MUST match.
        val year1 = extractYear(searchTitle)
        val year2 = extractYear(targetTitle)
        if (year1 != null && year2 != null && year1 != year2) {
            Log.d(TAG, "TITLE_MATCH: Year mismatch ($year1 vs $year2) for '$searchTitle' vs '$targetTitle'")
            return false
        }

        // 3. Token Check: If one is a significant subset of the other
        val words1 = s1.split(" ").toSet()
        val words2 = s2.split(" ").toSet()
        val intersection = words1.intersect(words2)
        
        // If they share most of their words, it's a match (e.g. "Krrish 3" vs "Krrish 3: The Game")
        val minSize = minOf(words1.size, words2.size)
        return minSize > 0 && intersection.size.toFloat() / minSize >= 0.8f
    }

    private fun extractYear(s: String): String? {
        val match = Regex("\\b(19|20)\\d{2}\\b").find(s)
        return match?.value
    }

    private fun com.example.watchorderengine.network.model.TmdbDetailResponse.toMediaEntity(
        mediaId: String
    ): MediaEntity {
        val isMovie = title != null
        val category = if (isMovie) "MOVIE" else "TV_SHOW"
        val trailerKey = videos?.results
            ?.filter { it.site == "YouTube" && it.type == "Trailer" && it.official }
            ?.maxByOrNull { it.publishedAt ?: "" }?.key

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
            castJson = "[]",
            recommendationsJson = "[]",
            arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toMinimalEntity(
        mediaId: String
    ): MediaEntity {
        val isMovie = mediaType == "movie"
        return MediaEntity(
            id = mediaId, tmdbId = id,
            anilistId = null,
            title = title ?: name ?: "",
            originalTitle = title ?: name ?: "",
            overview = "", tagline = "", status = "",
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (isMovie) "MOVIE" else "TV_SHOW",
            genres = emptyList(), ageRating = "NR",
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
        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (mediaType == "movie") MediaCategory.MOVIE else MediaCategory.TV_SHOW,
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = (releaseDate ?: firstAirDate)?.take(4) ?: "",
            trackingState = null,
            ageRating = "NR",
            genres = emptyList(),
            releaseDate = releaseDate ?: firstAirDate
        )
    }
}
