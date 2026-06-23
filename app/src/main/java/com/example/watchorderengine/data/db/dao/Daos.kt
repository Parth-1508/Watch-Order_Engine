package com.example.watchorderengine.data.db.dao

import androidx.room.*
import com.example.watchorderengine.data.db.entity.*

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id")
    fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media ORDER BY lastUpdated DESC")
    fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%'")
    fun search(query: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE mediaCategory = :category")
    fun getByCategory(category: String): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id = :id")
    fun deleteById(id: String)
}

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons WHERE mediaId = :mediaId ORDER BY seasonNumber ASC")
    fun getSeasonsByMedia(mediaId: String): List<SeasonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<SeasonEntity>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId AND absoluteEpisodeNumber BETWEEN :fromAbsolute AND :toAbsolute ORDER BY absoluteEpisodeNumber ASC")
    fun getEpisodesInRange(mediaId: String, fromAbsolute: Int, toAbsolute: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun getEpisodesBySeason(seasonId: String): List<EpisodeEntity>

    @Query("SELECT COUNT(*) FROM episodes WHERE mediaId = :mediaId AND episodeType = :type")
    fun countByType(mediaId: String, type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<EpisodeEntity>)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE mediaId = :mediaId")
    fun getProgress(mediaId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE trackingState = :state")
    fun getByState(state: String): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress ORDER BY updatedAt DESC")
    fun getAll(): List<UserProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: UserProgressEntity)

    @Query("UPDATE user_progress SET trackingState = :state, updatedAt = :now WHERE mediaId = :mediaId")
    fun updateState(mediaId: String, state: String, now: Long): Int

    @Query("UPDATE user_progress SET userRating = :rating, updatedAt = :now WHERE mediaId = :mediaId")
    fun updateRating(mediaId: String, rating: Float, now: Long): Int

    @Query("UPDATE user_progress SET userNotes = :notes, updatedAt = :now WHERE mediaId = :mediaId")
    fun updateNotes(mediaId: String, notes: String, now: Long): Int

    @Query("UPDATE user_progress SET priorityTag = :priority, updatedAt = :now WHERE mediaId = :mediaId")
    fun updatePriority(mediaId: String, priority: String, now: Long): Int
}

@Dao
interface EpisodeWatchedDao {
    @Query("SELECT episodeId FROM episode_watched WHERE mediaId = :mediaId")
    fun getWatchedIds(mediaId: String): List<String>

    @Query("SELECT COUNT(*) FROM episode_watched WHERE mediaId = :mediaId")
    fun countWatched(mediaId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM episode_watched WHERE episodeId = :episodeId)")
    fun isWatched(episodeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun markWatched(entity: EpisodeWatchedEntity)

    @Query("DELETE FROM episode_watched WHERE episodeId = :episodeId")
    fun unmarkWatched(episodeId: String)
}

@Dao
interface DiscoverySkippedDao {
    @Query("SELECT mediaId FROM discovery_skipped")
    fun getAllSkippedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun markSkipped(entity: DiscoverySkippedEntity)

    @Query("DELETE FROM discovery_skipped WHERE mediaId = :mediaId")
    fun removeSkipped(mediaId: String)

    @Query("DELETE FROM discovery_skipped")
    fun clearAllSkipped()
}
