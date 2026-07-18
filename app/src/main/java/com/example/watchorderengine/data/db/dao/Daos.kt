package com.example.watchorderengine.data.db.dao

import androidx.room.*
import com.example.watchorderengine.data.db.entity.*
import kotlinx.coroutines.flow.Flow

// ─── MediaDao ────────────────────────────────────────────────────────────────

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE anilistId = :anilistId LIMIT 1")
    suspend fun getByAnilistId(anilistId: Int): MediaEntity?

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId AND mediaCategory IN (:categories) LIMIT 1")
    suspend fun getByTmdbIdAndCategory(tmdbId: Int, categories: List<String>): MediaEntity?

    @Query("SELECT * FROM media ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE mediaCategory = :category")
    suspend fun getByCategory(category: String): List<MediaEntity>

    @Upsert
    suspend fun upsert(entity: MediaEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE media SET inWatchlist = :inWatchlist WHERE id = :mediaId")
    suspend fun updateWatchlistStatus(mediaId: String, inWatchlist: Boolean)

    @Query("UPDATE media SET jikanFillerSynced = 1 WHERE id = :mediaId")
    suspend fun markJikanSynced(mediaId: String)

    @Query("SELECT jikanFillerSynced FROM media WHERE id = :mediaId")
    suspend fun isJikanSynced(mediaId: String): Boolean
}

// ─── SeasonDao ───────────────────────────────────────────────────────────────

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons WHERE mediaId = :mediaId ORDER BY seasonNumber ASC")
    suspend fun getSeasonsByMedia(mediaId: String): List<SeasonEntity>

    @Upsert
    suspend fun upsertAll(entities: List<SeasonEntity>)
}

// ─── EpisodeDao ──────────────────────────────────────────────────────────────

@Dao
interface EpisodeDao {
    @Query("""
        SELECT * FROM episodes 
        WHERE mediaId = :mediaId 
          AND absoluteEpisodeNumber BETWEEN :start AND :end 
        ORDER BY absoluteEpisodeNumber ASC
    """)
    suspend fun getEpisodesInRange(mediaId: String, start: Int, end: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    suspend fun getEpisodesBySeason(seasonId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun observeEpisodesBySeason(seasonId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM episodes WHERE mediaId = :mediaId AND episodeType = :type")
    suspend fun countByType(mediaId: String, type: String): Int

    @Query("DELETE FROM episodes")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertAll(entities: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY absoluteEpisodeNumber ASC")
    suspend fun getAllEpisodesByMedia(mediaId: String): List<EpisodeEntity>

    @Query("""
        SELECT MAX(e.absoluteEpisodeNumber) FROM episodes e
        INNER JOIN episode_watched w ON e.id = w.episodeId
        WHERE e.mediaId = :mediaId
    """)
    fun observeMaxWatchedAbsoluteEpisode(mediaId: String): Flow<Int?>
}

// ─── UserProgressDao ─────────────────────────────────────────────────────────

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE mediaId = :mediaId")
    suspend fun getProgress(mediaId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE trackingState = :state ORDER BY updatedAt DESC")
    suspend fun getByState(state: String): List<UserProgressEntity>

    @Transaction
    @Query("SELECT * FROM user_progress WHERE trackingState = :state ORDER BY updatedAt DESC")
    fun getByStatePaging(state: String): androidx.paging.PagingSource<Int, JoinedProgressMedia>

    @Query("SELECT mediaId FROM user_progress WHERE trackingState = 'COMPLETED'")
    fun observeCompletedMediaIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM user_progress WHERE trackingState = :state")
    fun observeCountByState(state: String): Flow<Int>

    @Query("SELECT * FROM user_progress")
    suspend fun getAll(): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: String): UserProgressEntity?

    @Query("DELETE FROM user_progress")
    suspend fun clearAll()

    @Upsert
    suspend fun upsert(entity: UserProgressEntity)

    @Query("UPDATE user_progress SET trackingState = :state, updatedAt = :timestamp WHERE mediaId = :mediaId")
    suspend fun updateState(mediaId: String, state: String, timestamp: Long): Int

    @Query("UPDATE user_progress SET userRating = :rating, updatedAt = :timestamp WHERE mediaId = :mediaId")
    suspend fun updateRating(mediaId: String, rating: Float, timestamp: Long): Int

    @Query("UPDATE user_progress SET userNotes = :notes, updatedAt = :timestamp WHERE mediaId = :mediaId")
    suspend fun updateNotes(mediaId: String, notes: String, timestamp: Long): Int

    @Query("UPDATE user_progress SET priorityTag = :tag, updatedAt = :timestamp WHERE mediaId = :mediaId")
    suspend fun updatePriority(mediaId: String, tag: String, timestamp: Long): Int

    @Query("DELETE FROM user_progress WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)
}

// ─── EpisodeWatchedDao ───────────────────────────────────────────────────────

@Dao
interface EpisodeWatchedDao {
    @Query("SELECT episodeId FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun getWatchedIds(mediaId: String): List<String>

    @Query("SELECT COUNT(*) FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun countWatchedForMedia(mediaId: String): Int

    @Query("SELECT COUNT(*) FROM episode_watched")
    suspend fun countAllWatchedRaw(): Int

    @Query("SELECT episodeId FROM episode_watched")
    suspend fun getAllWatchedIds(): List<String>

    @Query("""
        SELECT SUM(e.runtime) FROM episodes e
        INNER JOIN episode_watched w ON e.id = w.episodeId
    """)
    suspend fun sumWatchedRuntimeMinutesTypeSafe(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM episode_watched WHERE episodeId = :episodeId)")
    suspend fun isWatched(episodeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(entity: EpisodeWatchedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatchedAll(entities: List<EpisodeWatchedEntity>)

    @Query("DELETE FROM episode_watched WHERE episodeId = :episodeId")
    suspend fun unmarkWatched(episodeId: String)

    @Query("DELETE FROM episode_watched WHERE mediaId = :mediaId AND episodeId LIKE :pattern")
    suspend fun unmarkSeasonWatched(mediaId: String, pattern: String)

    @Query("DELETE FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)

    @Query("SELECT watchedAt FROM episode_watched ORDER BY watchedAt DESC")
    suspend fun getAllWatchedTimestamps(): List<Long>

    @Transaction
    @Query("""
        INSERT OR REPLACE INTO episode_watched (episodeId, mediaId, watchedAt)
        SELECT id, mediaId, :timestamp FROM episodes
        WHERE mediaId = :mediaId AND absoluteEpisodeNumber <= :upToAbsoluteNumber
    """)
    suspend fun markAllPreviousAsWatched(mediaId: String, upToAbsoluteNumber: Int, timestamp: Long)

    @Transaction
    @Query("""
        INSERT OR REPLACE INTO episode_watched (episodeId, mediaId, watchedAt)
        SELECT id, mediaId, :timestamp FROM episodes
        WHERE mediaId = :mediaId
    """)
    suspend fun markAllAsWatched(mediaId: String, timestamp: Long)
}

// ─── ReviewDao ───────────────────────────────────────────────────────────────

@Dao
interface ReviewDao {
    @Query("SELECT * FROM user_reviews WHERE mediaId = :mediaId ORDER BY createdAt DESC")
    fun observeReviewsForMedia(mediaId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM user_reviews WHERE mediaId = :mediaId ORDER BY createdAt DESC")
    suspend fun getReviewsForMedia(mediaId: String): List<ReviewEntity>

    @Query("SELECT * FROM user_reviews WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeReviewsByUser(userId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM user_reviews WHERE isSynced = 0")
    suspend fun getPendingSyncReviews(): List<ReviewEntity>

    @Query("SELECT AVG(rating) FROM user_reviews WHERE mediaId = :mediaId")
    suspend fun getAverageRating(mediaId: String): Float?

    @Query("SELECT AVG(rating) FROM user_reviews")
    fun observeGlobalAverageRating(): Flow<Float?>

    @Query("SELECT * FROM user_reviews WHERE id = :reviewId")
    suspend fun getById(reviewId: String): ReviewEntity?

    @Query("SELECT COUNT(*) FROM user_reviews")
    suspend fun countAll(): Int

    @Query("DELETE FROM user_reviews")
    suspend fun clearAll()

    @Upsert
    suspend fun upsert(entity: ReviewEntity)

    @Query("UPDATE user_reviews SET isSynced = 1 WHERE id = :reviewId")
    suspend fun markSynced(reviewId: String)

    @Query("DELETE FROM user_reviews WHERE id = :reviewId")
    suspend fun deleteById(reviewId: String)
}

// ─── DiscoverySkippedDao ────────────────────────────────────────────────────

@Dao
interface DiscoverySkippedDao {
    @Query("SELECT mediaId FROM discovery_skipped")
    suspend fun getAllSkippedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markSkipped(entity: DiscoverySkippedEntity)

    @Query("DELETE FROM discovery_skipped WHERE mediaId = :mediaId")
    suspend fun removeSkipped(mediaId: String)

    @Query("DELETE FROM discovery_skipped")
    suspend fun clearAllSkipped()
}
