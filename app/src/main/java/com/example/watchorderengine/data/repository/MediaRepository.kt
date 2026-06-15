package com.example.watchorderengine.data.repository

import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.*
import com.example.watchorderengine.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val db: WatchOrderDatabase,
    private val firestore: FirebaseFirestore,
    private val moshi: Moshi
) {
    private val castType = Types.newParameterizedType(List::class.java, CastMember::class.java)
    private val castAdapter by lazy { moshi.adapter<List<CastMember>>(castType) }

    // ─── Media Detail ─────────────────────────────────────────────────────────

    fun getMediaDetailFlow(mediaId: String): Flow<MediaDetail?> = flow {
        val cached = buildMediaDetail(mediaId)
        emit(cached)

        refreshFromFirestore(mediaId)

        emit(buildMediaDetail(mediaId))
    }.flowOn(Dispatchers.IO)

    private suspend fun buildMediaDetail(mediaId: String): MediaDetail? {
        val entity = db.mediaDao().getById(mediaId) ?: return null
        val seasons = db.seasonDao().getSeasonsByMedia(mediaId)
        val progress = db.userProgressDao().getProgress(mediaId)

        val cast = runCatching { castAdapter.fromJson(entity.castJson) }.getOrDefault(emptyList())

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
            ageRating       = entity.ageRating,
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
            arcs            = emptyList(),
            userProgress    = progress?.toDomain()
        )
    }

    private suspend fun refreshFromFirestore(mediaId: String) {
        try {
            val snap = firestore.collection("catalog").document(mediaId).get().await()
            val data = snap.data ?: return
            val entity = firestoreDocToEntity(mediaId, data)
            db.mediaDao().upsert(entity)

            val seasonsSnap = firestore.collection("catalog").document(mediaId)
                .collection("seasons").get().await()
            val seasonEntities = seasonsSnap.documents.mapNotNull { doc ->
                doc.data?.let { firestoreSeasonToEntity(mediaId, doc.id, it) }
            }
            if (seasonEntities.isNotEmpty()) db.seasonDao().upsertAll(seasonEntities)
        } catch (e: Exception) {
            android.util.Log.w("MediaRepository", "Firestore refresh failed for $mediaId: ${e.message}")
        }
    }

    // ─── Episode Chunk Loading ────────────────────────────────────────────────

    suspend fun getEpisodeChunk(mediaId: String, fromAbsolute: Int, toAbsolute: Int): List<EpisodeItem> =
        withContext(Dispatchers.IO) {
            val episodes = db.episodeDao().getEpisodesInRange(mediaId, fromAbsolute, toAbsolute)
            val watchedIds = db.episodeWatchedDao().getWatchedIds(mediaId).toSet()

            if (episodes.isEmpty()) {
                refreshEpisodesFromFirestore(mediaId, fromAbsolute, toAbsolute)
                return@withContext db.episodeDao().getEpisodesInRange(mediaId, fromAbsolute, toAbsolute)
                    .map { it.toDomain(watchedIds) }
            }

            episodes.map { it.toDomain(watchedIds) }
        }

    private suspend fun refreshEpisodesFromFirestore(mediaId: String, from: Int, to: Int) {
        try {
            val snap = firestore.collection("catalog").document(mediaId)
                .collection("episodes")
                .whereGreaterThanOrEqualTo("absoluteEpisodeNumber", from)
                .whereLessThanOrEqualTo("absoluteEpisodeNumber", to)
                .get().await()

            val entities = snap.documents.mapNotNull { doc ->
                doc.data?.let { firestoreEpisodeToEntity(doc.id, mediaId, it) }
            }
            if (entities.isNotEmpty()) db.episodeDao().upsertAll(entities)
        } catch (e: Exception) {
            android.util.Log.w("MediaRepository", "Episode refresh failed: ${e.message}")
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    suspend fun searchMedia(query: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        db.mediaDao().search(query).map { it.toSummary() }
    }

    // ─── User Progress ────────────────────────────────────────────────────────

    suspend fun updateTrackingState(mediaId: String, state: TrackingState) =
        withContext(Dispatchers.IO) {
            val current = db.userProgressDao().getProgress(mediaId)
            db.userProgressDao().upsert(
                UserProgressEntity(
                    mediaId = mediaId, 
                    trackingState = state.name,
                    userNotes = current?.userNotes ?: "",
                    priorityTag = current?.priorityTag ?: "NONE"
                )
            )
        }

    suspend fun updateNotes(mediaId: String, notes: String) = withContext(Dispatchers.IO) {
        db.userProgressDao().updateNotes(mediaId, notes, System.currentTimeMillis())
    }

    suspend fun updatePriority(mediaId: String, priority: PriorityTag) = withContext(Dispatchers.IO) {
        db.userProgressDao().updatePriority(mediaId, priority.name, System.currentTimeMillis())
    }

    suspend fun toggleEpisodeWatched(episodeId: String, mediaId: String): Boolean =
        withContext(Dispatchers.IO) {
            val isCurrentlyWatched = db.episodeWatchedDao().isWatched(episodeId)
            if (isCurrentlyWatched) {
                db.episodeWatchedDao().unmarkWatched(episodeId)
            } else {
                db.episodeWatchedDao().markWatched(EpisodeWatchedEntity(episodeId, mediaId))
            }
            !isCurrentlyWatched
        }

    // ─── Home Dashboard ───────────────────────────────────────────────────────

    suspend fun getWatchingList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(TrackingState.WATCHING.name)
        val summaries = progressList.mapNotNull { progress ->
            db.mediaDao().getById(progress.mediaId)?.toSummary(
                TrackingState.WATCHING, 
                PriorityTag.valueOf(progress.priorityTag)
            )
        }
        sortSummaries(summaries, sortType)
    }

    suspend fun getPlannedList(sortType: SortType = SortType.DATE_ADDED): List<MediaSummary> = withContext(Dispatchers.IO) {
        val progressList = db.userProgressDao().getByState(TrackingState.PLANNED.name)
        val summaries = progressList.mapNotNull { progress ->
            db.mediaDao().getById(progress.mediaId)?.toSummary(
                TrackingState.PLANNED,
                PriorityTag.valueOf(progress.priorityTag)
            )
        }
        sortSummaries(summaries, sortType)
    }

    private fun sortSummaries(list: List<MediaSummary>, sortType: SortType): List<MediaSummary> = when(sortType) {
        SortType.ALPHABETICAL -> list.sortedBy { it.title }
        SortType.USER_RATING -> list.sortedByDescending { it.voteAverage } 
        SortType.GLOBAL_SCORE -> list.sortedByDescending { it.voteAverage }
        SortType.DATE_ADDED -> list
    }

    // ─── Profile Stats ────────────────────────────────────────────────────────

    suspend fun getUserStats(): UserStats = withContext(Dispatchers.IO) {
        val allProgress = db.userProgressDao().getAll()
        val watchedCount = allProgress.sumOf { p ->
            db.episodeWatchedDao().countWatched(p.mediaId)
        }
        UserStats(
            totalMinutesWatched  = 0L,
            totalEpisodesWatched = watchedCount,
            totalMoviesWatched   = allProgress.count { p ->
                p.trackingState == TrackingState.COMPLETED.name &&
                db.mediaDao().getById(p.mediaId)?.mediaCategory == "MOVIE"
            },
            showsCompleted = allProgress.count { it.trackingState == TrackingState.COMPLETED.name },
            showsDropped   = allProgress.count { it.trackingState == TrackingState.DROPPED.name },
            showsWatching  = allProgress.count { it.trackingState == TrackingState.WATCHING.name },
            showsPlanned   = allProgress.count { it.trackingState == TrackingState.PLANNED.name },
            showsPaused    = allProgress.count { it.trackingState == TrackingState.PAUSED.name },
            topGenres      = emptyList(),
            averageRating  = allProgress.mapNotNull { it.userRating }.average().toFloat()
                .takeIf { !it.isNaN() }
        )
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private fun SeasonEntity.toDomain() = SeasonSummary(
        id = this.id, mediaId = this.mediaId, seasonNumber = this.seasonNumber,
        name = this.name, overview = this.overview, posterUrl = this.posterUrl,
        airDate = this.airDate, episodeCount = this.episodeCount
    )

    private fun EpisodeEntity.toDomain(watchedIds: Set<String>) = EpisodeItem(
        id = this.id, seasonId = this.seasonId, mediaId = this.mediaId,
        episodeNumber = this.episodeNumber, seasonNumber = this.seasonNumber,
        absoluteEpisodeNumber = this.absoluteEpisodeNumber,
        title = this.title, overview = this.overview, airDate = this.airDate,
        runtime = this.runtime, stillUrl = this.stillUrl, voteAverage = this.voteAverage,
        episodeType = EpisodeType.entries.find { it.name == this.episodeType } ?: EpisodeType.CANON,
        arcName = this.arcName, isWatched = this.id in watchedIds
    )

    private fun UserProgressEntity.toDomain() = UserProgress(
        mediaId = this.mediaId,
        trackingState = TrackingState.valueOf(this.trackingState),
        currentSeasonNumber = this.currentSeasonNumber,
        currentEpisodeNumber = this.currentEpisodeNumber,
        totalEpisodesWatched = 0,
        userRating = this.userRating,
        startedDate = this.startedDate,
        completedDate = this.completedDate,
        updatedAt = this.updatedAt,
        userNotes = this.userNotes,
        priorityTag = PriorityTag.valueOf(this.priorityTag)
    )

    private fun MediaEntity.toSummary(state: TrackingState? = null, priority: PriorityTag = PriorityTag.NONE) = MediaSummary(
        id = this.id, tmdbId = this.tmdbId, title = this.title,
        posterUrl = this.posterUrl, backdropUrl = this.backdropUrl,
        mediaCategory = MediaCategory.valueOf(this.mediaCategory),
        voteAverage = this.voteAverage,
        releaseYear = this.releaseDate?.take(4) ?: "",
        trackingState = state, ageRating = this.ageRating,
        priorityTag = priority
    )

    private fun firestoreDocToEntity(mediaId: String, data: Map<String, Any>): MediaEntity =
        MediaEntity(
            id = mediaId,
            tmdbId = (data["tmdbId"] as? Long)?.toInt() ?: 0,
            anilistId = (data["anilistId"] as? Long)?.toInt(),
            title = data["title"] as? String ?: "",
            originalTitle = data["originalTitle"] as? String ?: "",
            overview = data["overview"] as? String ?: "",
            tagline = data["tagline"] as? String ?: "",
            status = data["status"] as? String ?: "",
            posterUrl = data["posterUrl"] as? String,
            backdropUrl = data["backdropUrl"] as? String,
            mediaCategory = data["mediaType"] as? String ?: "TV_SHOW",
            genres = (data["genres"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            ageRating = data["ageRating"] as? String ?: "NR",
            voteAverage = (data["voteAverage"] as? Double)?.toFloat() ?: 0f,
            voteCount = (data["voteCount"] as? Long)?.toInt() ?: 0,
            runtime = (data["runtime"] as? Long)?.toInt(),
            numberOfSeasons = (data["numberOfSeasons"] as? Long)?.toInt(),
            numberOfEpisodes = (data["numberOfEpisodes"] as? Long)?.toInt(),
            releaseDate = data["releaseDate"] as? String,
            trailerKey = data["trailerKey"] as? String,
            castJson = moshi.adapter<Any>(List::class.java).toJson(data["cast"]) ?: "[]",
            recommendationsJson = "[]",
            arcsJson = moshi.adapter<Any>(List::class.java).toJson(data["arcs"]) ?: "[]"
        )

    private fun firestoreSeasonToEntity(mediaId: String, docId: String, data: Map<String, Any>) =
        SeasonEntity(
            id = "${mediaId}_${docId}",
            mediaId = mediaId,
            seasonNumber = (data["seasonNumber"] as? Long)?.toInt() ?: 0,
            name = data["name"] as? String ?: "",
            overview = data["overview"] as? String ?: "",
            posterUrl = data["posterUrl"] as? String,
            airDate = data["airDate"] as? String,
            episodeCount = (data["episodeCount"] as? Long)?.toInt() ?: 0
        )

    private fun firestoreEpisodeToEntity(docId: String, mediaId: String, data: Map<String, Any>) =
        EpisodeEntity(
            id = "${mediaId}_${docId}",
            seasonId = "${mediaId}_s${(data["seasonNumber"] as? Long)?.toInt() ?: 0}",
            mediaId = mediaId,
            episodeNumber = (data["episodeNumber"] as? Long)?.toInt() ?: 0,
            seasonNumber = (data["seasonNumber"] as? Long)?.toInt() ?: 0,
            absoluteEpisodeNumber = (data["absoluteEpisodeNumber"] as? Long)?.toInt() ?: 0,
            title = data["title"] as? String ?: "",
            overview = data["overview"] as? String ?: "",
            airDate = data["airDate"] as? String,
            runtime = (data["runtime"] as? Long)?.toInt(),
            stillUrl = data["stillUrl"] as? String,
            voteAverage = (data["voteAverage"] as? Double)?.toFloat() ?: 0f,
            episodeType = data["typeTag"] as? String ?: "CANON",
            arcName = data["arcName"] as? String
        )
}
