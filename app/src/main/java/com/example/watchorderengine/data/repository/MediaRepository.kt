package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.gemini.GeminiResult
import com.example.watchorderengine.network.gemini.GeminiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
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
    private val anilistApiService: AnilistApiService,
    private val geminiService: GeminiService,
    private val watchOrderRepository: com.example.watchorderengine.data.WatchOrderRepository
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
        if (mediaId.startsWith("anilist_")) {
            return refreshAnilistDetail(mediaId)
        }
        val tmdbId = mediaId.removePrefix("tmdb_").toIntOrNull() ?: return false
        val cachedEntity = db.mediaDao().getById(mediaId)

        return when (cachedEntity?.mediaCategory) {
            "MOVIE"  -> fetchAndCacheMovie(tmdbId, mediaId)
            "TV_SHOW" -> fetchAndCacheTv(tmdbId, mediaId)
            else -> fetchAndCacheTv(tmdbId, mediaId) || fetchAndCacheMovie(tmdbId, mediaId)
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

    private suspend fun refreshAnilistDetail(mediaId: String): Boolean {
        val aniId = mediaId.removePrefix("anilist_").toIntOrNull() ?: return false
        try {
            val query = """
                query (${'$'}id: Int) {
                  Media (id: ${'$'}id, type: ANIME) {
                    id idMal description bannerImage averageScore genres
                    title { english romaji }
                    coverImage { large }
                    episodes
                  }
                }
            """.trimIndent()
            
            val response = anilistApiService.query(AnilistRequest(query, mapOf("id" to aniId)))
            if (!response.isSuccessful) return false
            val anime = response.body()?.data?.media ?: return false
            
            val entity = MediaEntity(
                id = mediaId,
                tmdbId = anime.idMal ?: anime.id,
                anilistId = anime.id,
                title = anime.title?.english ?: anime.title?.romaji ?: "",
                originalTitle = anime.title?.romaji ?: "",
                overview = anime.description ?: "",
                tagline = "", status = "",
                posterUrl = anime.coverImage?.large,
                backdropUrl = anime.bannerImage,
                mediaCategory = "ANIME",
                genres = anime.genres ?: emptyList(),
                ageRating = "NR",
                voteAverage = (anime.averageScore ?: 0) / 10f,
                voteCount = 0, runtime = null,
                numberOfSeasons = 1,
                numberOfEpisodes = anime.episodes,
                releaseDate = null, trailerKey = null,
                castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
            )
            db.mediaDao().upsert(entity)
            
            val seasonId = "${mediaId}_s1"
            db.seasonDao().upsertAll(listOf(SeasonEntity(
                id = seasonId, mediaId = mediaId, seasonNumber = 1,
                name = "Season 1", overview = "", posterUrl = entity.posterUrl,
                airDate = null, episodeCount = anime.episodes ?: 0
            )))
            
            return true
        } catch (e: Exception) {
            Log.w(TAG, "AniList detail failed: ${e.message}")
            return false
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
        val tmdbDeferred = async { searchTmdb(query) }
        val anilistDeferred = async { searchAnilist(query) }
        (tmdbDeferred.await() + anilistDeferred.await()).distinctBy { it.id }
    }

    private suspend fun searchTmdb(query: String): List<MediaSummary> {
        return try {
            val response = apiService.searchMulti(query)
            if (!response.isSuccessful) return emptyList()
            val results = response.body()?.results?.filter { it.mediaType == "movie" || it.mediaType == "tv" } ?: return emptyList()
            results.forEach { result ->
                val mediaId = "tmdb_${result.id}"
                if (db.mediaDao().getById(mediaId) == null) db.mediaDao().upsert(result.toMinimalEntity(mediaId))
            }
            results.mapNotNull { it.toSummary() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchAnilist(searchQuery: String): List<MediaSummary> {
        return try {
            val query = """
                query (${'$'}search: String) {
                  Page (perPage: 10) {
                    media (search: ${'$'}search, type: ANIME) {
                      id idMal bannerImage averageScore genres
                      title { english romaji }
                      coverImage { large }
                    }
                  }
                }
            """.trimIndent()
            val response = anilistApiService.query(AnilistRequest(query, mapOf("search" to searchQuery)))
            if (!response.isSuccessful) return emptyList()
            val results = response.body()?.data?.page?.media ?: return emptyList()
            results.map { anime ->
                MediaSummary(
                    id = "anilist_${anime.id}",
                    tmdbId = anime.idMal ?: anime.id,
                    title = anime.title?.english ?: anime.title?.romaji ?: "Unknown",
                    posterUrl = anime.coverImage?.large,
                    backdropUrl = anime.bannerImage,
                    mediaCategory = MediaCategory.ANIME,
                    voteAverage = (anime.averageScore ?: 0) / 10f,
                    releaseYear = "",
                    trackingState = null, ageRating = "NR",
                    genres = anime.genres ?: emptyList()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun generateWatchOrder(mediaId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.mediaDao().getById(mediaId) ?: return@withContext "Show not found."
        val seasonCounts = db.seasonDao().getSeasonsByMedia(mediaId).sortedBy { it.seasonNumber }.map { it.episodeCount }

        val result = geminiService.generateWatchOrder(
            showTitle = entity.title,
            overview = entity.overview,
            seasonEpisodeCounts = seasonCounts,
            mediaType = if (entity.mediaCategory == "MOVIE") "movie" else "tv"
        )

        when (result) {
            is GeminiResult.Error -> result.message
            is GeminiResult.Success -> {
                val arcs = result.watchOrder.arcs.map { arc ->
                    StoryArc(
                        name = arc.arcName,
                        startSeason = arc.startSeason,
                        startEpisode = arc.startEpisode,
                        endSeason = arc.endSeason,
                        endEpisode = arc.endEpisode,
                        startAbsoluteEpisode = null,
                        endAbsoluteEpisode = null,
                        synopsis = arc.synopsis
                    )
                }
                val arcsType = Types.newParameterizedType(List::class.java, StoryArc::class.java)
                val arcsJson = moshi.adapter<List<StoryArc>>(arcsType).toJson(arcs) ?: "[]"
                db.mediaDao().upsert(entity.copy(arcsJson = arcsJson))

                result.watchOrder.arcs.forEach { arc ->
                    val arcEpisodes = db.episodeDao().getEpisodesBySeason("${mediaId}_s${arc.startSeason}")
                        .filter { it.episodeNumber in arc.startEpisode..arc.endEpisode }
                    arcEpisodes.forEach { ep ->
                        db.episodeDao().upsertAll(listOf(ep.copy(
                            episodeType = arc.classification,
                            arcName = arc.arcName
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
            voteAverage = voteAverage, releaseYear = releaseDate?.take(4) ?: "",
            trackingState = state, ageRating = ageRating, priorityTag = priority
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
            releaseDate = null, trailerKey = null,
            castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
        )
    }

    private fun com.example.watchorderengine.network.model.TmdbMediaResult.toSummary(): MediaSummary? {
        if (mediaType == null || (mediaType != "movie" && mediaType != "tv")) return null
        val mediaId = "tmdb_$id"
        
        // Basic genre mapping for common IDs to enable Discovery filters
        val mappedGenres = when (mediaType) {
            "movie" -> listOfNotNull(
                if (genreIds?.contains(28) == true) "Action" else null,
                if (genreIds?.contains(12) == true) "Adventure" else null,
                if (genreIds?.contains(16) == true) "Animation" else null,
                if (genreIds?.contains(35) == true) "Comedy" else null,
                if (genreIds?.contains(27) == true) "Horror" else null,
                if (genreIds?.contains(878) == true) "Sci-Fi" else null
            )
            else -> listOfNotNull(
                if (genreIds?.contains(10759) == true) "Action" else null,
                if (genreIds?.contains(16) == true) "Animation" else null,
                if (genreIds?.contains(35) == true) "Comedy" else null,
                if (genreIds?.contains(10765) == true) "Sci-Fi" else null
            )
        }

        return MediaSummary(
            id = mediaId, tmdbId = id,
            title = title ?: name ?: return null,
            posterUrl = TmdbConfig.buildImageUrl(posterPath),
            backdropUrl = TmdbConfig.buildImageUrl(backdropPath, TmdbConfig.PosterSize.HD),
            mediaCategory = if (mediaType == "movie") MediaCategory.MOVIE else MediaCategory.TV_SHOW,
            voteAverage = voteAverage?.toFloat() ?: 0f,
            releaseYear = "",
            trackingState = null,
            ageRating = "NR",
            genres = mappedGenres
        )
    }
}
