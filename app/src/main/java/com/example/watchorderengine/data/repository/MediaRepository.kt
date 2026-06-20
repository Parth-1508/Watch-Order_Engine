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
    fun findUniverseForMedia(tmdbId: Int) = watchOrderRepository.findUniverseForMedia(tmdbId)
    private val castType = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }

    // ─── Media Detail ─────────────────────────────────────────────────────────

    fun getMediaDetailFlow(mediaId: String): Flow<MediaDetail?> = flow {
        val cached = buildMediaDetail(mediaId)
        emit(cached)

        val refreshed = refreshDetail(mediaId)
        if (refreshed) {
            emit(buildMediaDetail(mediaId))
        } else if (cached == null) {
            emit(null)
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
            else -> {
                // Try to resolve category via TMDB search if unknown
                val searchMatch = searchMedia(cachedEntity?.title ?: "").firstOrNull { it.tmdbId == tmdbId }
                if (searchMatch?.mediaCategory == MediaCategory.MOVIE) {
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
                        .filter { it.seasonNumber > 0 }
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
        try {
            val response = apiService.getTvSeason(tmdbId, seasonNumber)
            if (!response.isSuccessful || response.body() == null) return

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
            } ?: return
            if (episodeEntities.isNotEmpty()) db.episodeDao().upsertAll(episodeEntities)
        } catch (e: Exception) {
            Log.w(TAG, "Season $seasonNumber fetch failed: ${e.message}")
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

        val arcsType = Types.newParameterizedType(List::class.java, StoryArc::class.java)
        val arcsAdapter = moshi.adapter<List<StoryArc>>(arcsType)
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
        val seasonId = "${mediaId}_s$seasonNumber"
        val episodes = db.episodeDao().getEpisodesBySeason(seasonId)
        val watchedIds = db.episodeWatchedDao().getWatchedIds(mediaId).toSet()
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

                // 1. Resolve TMDB IDs for each node. Use direct ID from Gemini if provided.
                val mediaNodes = supervisorScope {
                    watchOrder.nodes.map { node ->
                        async {
                            // If Gemini provided a direct TMDB ID, use it. 
                            // Otherwise, fallback to the parent show's ID (for episode arcs).
                            var resolvedId = if (node.tmdbId > 0) node.tmdbId else entity.tmdbId
                            var resolvedType = if (node.contentType == "MOVIE") "movie" else "tv"

                            // Safety check: if Gemini ID is 0 or same as parent, but it's a standalone MOVIE/SERIES, 
                            // we can still try the title search as a last resort.
                            if (resolvedId == entity.tmdbId && (node.contentType == "MOVIE" || node.contentType == "SERIES")) {
                                // If the title is almost exactly the same as the parent, it's likely just an arc
                                // of the parent show, so we DON'T search to avoid jumping to another movie.
                                val isSameTitleAsParent = node.title.equals(entity.title, ignoreCase = true) || 
                                                        (entity.title.length > 5 && node.title.startsWith(entity.title, ignoreCase = true))

                                if (!isSameTitleAsParent || !node.searchQuery.isNullOrBlank()) {
                                    val query = node.searchQuery ?: node.title
                                    val searchResults = searchMedia(query)
                                    
                                    // Robust matching:
                                    // 1. Exact title match (best)
                                    // 2. Result that contains the node title AND vice versa (strong containment)
                                    // 3. Fallback to first result ONLY if it's not the parent ID we're trying to escape
                                    
                                    val bestMatch = searchResults.find { 
                                        it.title.equals(node.title, ignoreCase = true)
                                    } ?: searchResults.find {
                                        it.title.contains(node.title, ignoreCase = true) && 
                                        node.title.contains(it.title, ignoreCase = true)
                                    } ?: searchResults.find {
                                        // If we have a search query, trust the first result that isn't the parent
                                        !node.searchQuery.isNullOrBlank() && it.tmdbId != entity.tmdbId
                                    }

                                    if (bestMatch != null) {
                                        resolvedId = bestMatch.tmdbId
                                        resolvedType = if (bestMatch.mediaCategory == MediaCategory.MOVIE) "movie" else "tv"
                                    }
                                }
                            }

                            com.example.watchorderengine.data.model.MediaNode(
                                id = node.nodeId,
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
                        from_node_id = edge.fromNodeId,
                        to_node_id = edge.toNodeId,
                        type = edge.type
                    )
                }

                // 2. Publish to Firestore
                watchOrderRepository.clearGeneratedUniverse(universeId)
                val publishResult = watchOrderRepository.publishGeneratedUniverse(
                    universeId = universeId,
                    universeName = entity.title,
                    coverUrl = entity.posterUrl ?: "",
                    nodes = mediaNodes,
                    edges = mediaEdges
                )

                if (publishResult.isFailure) {
                    return@withContext "Saved locally, but Firestore push failed: ${publishResult.exceptionOrNull()?.message}"
                }

                // 3. Mirror locally for Instant Detail
                val arcs = watchOrder.nodes.map { node ->
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
                val arcsType = Types.newParameterizedType(List::class.java, StoryArc::class.java)
                val arcsJson = moshi.adapter<List<StoryArc>>(arcsType).toJson(arcs) ?: "[]"
                db.mediaDao().upsert(entity.copy(arcsJson = arcsJson))

                // Update episode types
                watchOrder.nodes.forEach { node ->
                    val classification = when {
                        "FILLER" in node.tags -> "FILLER"
                        "MIXED" in node.tags -> "MIXED"
                        else -> "CANON"
                    }
                    val seasonId = "${mediaId}_s${node.startSeason}"
                    val nodeEpisodes = db.episodeDao().getEpisodesBySeason(seasonId).filter { ep ->
                        ep.episodeNumber in node.startEpisode..node.endEpisode
                    }
                    nodeEpisodes.forEach { ep ->
                        db.episodeDao().upsertAll(listOf(ep.copy(
                            episodeType = classification,
                            arcName = node.title
                        )))
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
