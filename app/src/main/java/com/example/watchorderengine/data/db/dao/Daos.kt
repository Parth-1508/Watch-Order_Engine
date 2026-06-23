package com.example.watchorderengine.data.db.dao

import androidx.room.*
import com.example.watchorderengine.data.db.entity.*
import kotlinx.coroutines.flow.Flow

// ─── MediaDao ────────────────────────────────────────────────────────────────

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity?

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
          AND absoluteEpisodeNumber BETWEEN :fromAbsolute AND :toAbsolute
        ORDER BY absoluteEpisodeNumber ASC
    """)
    suspend fun getEpisodesInRange(
        mediaId: String,
        fromAbsolute: Int,
        toAbsolute: Int
    ): List<EpisodeEntity>

    /**
     * One-shot read for a specific season.
     *
     * ERROR #3 FIX: this function is now `suspend` so the coroutine dispatcher
     * (Dispatchers.IO in the repository) actually suspends while Room reads the
     * database, rather than blocking the calling thread.  The original non-suspend
     * version ran synchronously on whatever thread called it — if that happened to
     * be the main thread (during a LaunchedEffect) the query would ANR.
     */
    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    suspend fun getEpisodesBySeason(seasonId: String): List<EpisodeEntity>

    /**
     * Reactive variant: returns a [Flow] that re-emits whenever any row in
     * the matching season changes.
     *
     * ERROR #3 FIX: the ViewModel uses this to collect episodes reactively.
     * When [MediaRepository.refreshSeasonEpisodes] writes rows to Room, Room
     * notifies all active observers of this query automatically — no polling,
     * no manual re-trigger from the ViewModel needed.
     */
    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun observeEpisodesBySeason(seasonId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM episodes WHERE mediaId = :mediaId AND episodeType = :type")
    suspend fun countByType(mediaId: String, type: String): Int

    @Upsert
    suspend fun upsertAll(entities: List<EpisodeEntity>)
}

// ─── UserProgressDao ─────────────────────────────────────────────────────────

@Dao
interface UserProgressDao {

    @Query("SELECT * FROM user_progress WHERE mediaId = :mediaId")
    suspend fun getProgress(mediaId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE trackingState = :state")
    suspend fun getByState(state: String): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<UserProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProgressEntity)

    @Query("UPDATE user_progress SET trackingState = :state, updatedAt = :now WHERE mediaId = :mediaId")
    suspend fun updateState(mediaId: String, state: String, now: Long): Int

    @Query("UPDATE user_progress SET userRating = :rating, updatedAt = :now WHERE mediaId = :mediaId")
    suspend fun updateRating(mediaId: String, rating: Float, now: Long): Int

    @Query("UPDATE user_progress SET userNotes = :notes, updatedAt = :now WHERE mediaId = :mediaId")
    suspend fun updateNotes(mediaId: String, notes: String, now: Long): Int

    @Query("UPDATE user_progress SET priorityTag = :priority, updatedAt = :now WHERE mediaId = :mediaId")
    suspend fun updatePriority(mediaId: String, priority: String, now: Long): Int
}

// ─── EpisodeWatchedDao ───────────────────────────────────────────────────────

@Dao
interface EpisodeWatchedDao {

    @Query("SELECT episodeId FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun getWatchedIds(mediaId: String): List<String>

    /**
     * Per-show count (used by progress ring on MediaDetail).
     * Pass an empty string for [mediaId] to count ALL watched episodes globally.
     *
     * ERROR #4 FIX: ProfileViewModel previously multiplied list sizes by a
     * magic number.  Now it calls `countWatched("")` to get the real global
     * total from the database.
     *
     * Note: passing "" matches no mediaId rows in SQLite's LIKE comparison,
     * so we use two separate queries to keep the logic explicit and correct.
     */
    @Query("SELECT COUNT(*) FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun countWatchedForMedia(mediaId: String): Int

    /** Global watched episode count across ALL shows — used by ProfileViewModel. */
    @Query("SELECT COUNT(*) FROM episode_watched")
    suspend fun countAllWatched(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM episode_watched WHERE episodeId = :episodeId)")
    suspend fun isWatched(episodeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(entity: EpisodeWatchedEntity)

    @Query("DELETE FROM episode_watched WHERE episodeId = :episodeId")
    suspend fun unmarkWatched(episodeId: String)

    /**
     * Returns all watchedAt timestamps, used by [MediaRepository.computeWatchStreak]
     * to calculate consecutive calendar-day streaks.
     */
    @Query("SELECT watchedAt FROM episode_watched ORDER BY watchedAt DESC")
    suspend fun getAllWatchedTimestamps(): List<Long>
}

// ─── DiscoverySkippedDao ─────────────────────────────────────────────────────

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
