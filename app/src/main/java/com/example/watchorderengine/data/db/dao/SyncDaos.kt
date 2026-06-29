package com.example.watchorderengine.data.db.dao

import androidx.room.*
import com.example.watchorderengine.data.db.entity.PendingSyncTaskEntity

@Dao
interface PendingSyncTaskDao {

    @Query("SELECT * FROM pending_sync_tasks ORDER BY id ASC")
    suspend fun getAll(): List<PendingSyncTaskEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_tasks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: PendingSyncTaskEntity): Long

    @Query("DELETE FROM pending_sync_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_sync_tasks SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM pending_sync_tasks WHERE taskType = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM pending_sync_tasks")
    suspend fun deleteAll()
}
