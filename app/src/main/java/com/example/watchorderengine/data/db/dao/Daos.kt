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
     * Type-safe TMDB ID lookup filtered by media category.
     *
     * ALWAYS pass a category list matching the ID prefix:
     *   "tmdb_m_{id}" → listOf("MOVIE")
     *   "tmdb_t_{id}" → listOf("TV_SHOW", "ANIME")
     *
     * The previous untyped query (WHERE tmdbId = X LIMIT 1) was
     * non-deterministic when a movie AND a TV show shared the same numeric
     * TMDB ID — SQLite LIMIT 1 with no ORDER BY is undefined, producing the
     * "half the time opens the wrong show" symptom.
     */
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
    suspend fun countAllWatchedRaw(): Int

    @Query("SELECT episodeId FROM episode_watched")
    suspend fun getAllWatchedIds(): List<String>

    /**
     * Accurate runtime summing that handles dual-ID mapping (legacy vs current).
     * Prevents One Piece / Naruto stats from being missing or glitched.
     */
    @Query("""
        SELECT COALESCE(SUM(e.runtime), 0) FROM episodes e
        WHERE EXISTS (
            SELECT 1 FROM episode_watched w 
            WHERE w.episodeId = e.id 
               OR w.episodeId = REPLACE(REPLACE(e.id, 'tmdb_m_', 'tmdb_'), 'tmdb_t_', 'tmdb_')
               OR w.episodeId = REPLACE(REPLACE(e.id, 'tmdb_m_', ''), 'tmdb_t_', '')
        )
    """)
    suspend fun sumWatchedRuntimeMinutesTypeSafe(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM episode_watched WHERE episodeId = :episodeId)")
    suspend fun isWatched(episodeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(entity: EpisodeWatchedEntity)

    @Query("DELETE FROM episode_watched WHERE episodeId = :episodeId")
    suspend fun unmarkWatched(episodeId: String)

    @Query("DELETE FROM episode_watched WHERE mediaId = :mediaId AND episodeId LIKE :seasonPrefix")
    suspend fun unmarkSeasonWatched(mediaId: String, seasonPrefix: String)

    @Query("DELETE FROM episode_watched WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)

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
