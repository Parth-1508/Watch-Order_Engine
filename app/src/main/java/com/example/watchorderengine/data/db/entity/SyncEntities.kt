package com.example.watchorderengine.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Discriminator values for [PendingSyncTaskEntity.taskType]. */
object TaskType {
    const val NODE_COMPLETION = "NODE_COMPLETION"
    const val EPISODE_WATCHED = "EPISODE_WATCHED"
}

/**
 * Persists a single Firestore mutation that failed due to network unavailability.
 */
@Entity(
    tableName = "pending_sync_tasks",
    indices = [Index("taskType")]
)
data class PendingSyncTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Discriminator for the mutation type.
     * "NODE_COMPLETION" | "EPISODE_WATCHED"
     */
    val taskType: String,

    // -- NODE_COMPLETION fields --
    val universeId: String = "",
    val nodeId: String? = null,
    val completed: Boolean = true,

    // -- EPISODE_WATCHED fields --
    val episodeId: String? = null,
    val mediaId: String? = null,

    // -- Generic fields --
    val payload: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)
