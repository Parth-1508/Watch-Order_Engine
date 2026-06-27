package com.example.watchorderengine.data.db.dao

import androidx.room.*
import com.example.watchorderengine.data.db.entity.*
import kotlinx.coroutines.flow.Flow

// ─── MediaDao ────────────────────────────────────────────────────────────────

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity?

    /**
     * Type-safe lookup by raw TMDB numeric ID and category.
     * Prevents collisions between Movies and TV shows that share the same TMDB ID.
     */
    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId AND mediaCategory = :category LIMIT 1")
    suspend fun getByTmdbIdAndCategory(tmdbId: Int, category: String): MediaEntity?

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

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    suspend fun getEpisodesBySeason(seasonId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun observeEpisodesBySeason(seasonId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM episodes WHERE mediaId = :mediaId AND episodeType = :type")
    suspend fun countByType(mediaId: String, type: String): Int

    @Upsert
    suspend fun upsertAll(entities: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY absoluteEpisodeNumber ASC")
    suspend fun getAllEpisodesByMedia(mediaId: String): List<EpisodeEntity>
}

// ─── UserProgressDao ─────────────────────────────────────────────────────────

@Dao
interface UserProgressDao {

    @Query("SELECT * FROM user_progress WHERE mediaId = :mediaId")
    suspend fun getProgress(mediaId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE trackingState = :state ORDER BY updatedAt DESC")
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
    suspend fun countAllWatched(): Int

    @Query("""
        SELECT COALESCE(SUM(e.runtime), 0) FROM episode_watched w
        INNER JOIN episodes e ON e.id = w.episodeId
        WHERE e.runtime IS NOT NULL
    """)
    suspend fun sumWatchedRuntimeMinutes(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM episode_watched WHERE episodeId = :episodeId)")
    suspend fun isWatched(episodeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(entity: EpisodeWatchedEntity)

    @Query("DELETE FROM episode_watched WHERE episodeId = :episodeId")
    suspend fun unmarkWatched(episodeId: String)

    @Query("DELETE FROM episode_watched WHERE mediaId = :mediaId AND episodeId LIKE :seasonPrefix")
    suspend fun unmarkSeasonWatched(mediaId: String, seasonPrefix: String)

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
