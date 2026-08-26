package com.example.watchorderengine.data.model

import com.example.watchorderengine.data.db.entity.DiscoverySkippedEntity
import com.example.watchorderengine.data.db.entity.EpisodeWatchedEntity
import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.example.watchorderengine.data.db.entity.UserProgressEntity
import kotlinx.serialization.Serializable

/** Bump this whenever a change to one of the 4 backed-up entities would break parsing an older export file. */
const val CURRENT_BACKUP_SCHEMA_VERSION = 1

/**
 * The complete contents of a local backup file — the JSON on disk is
 * exactly this object, kotlinx.serialization-encoded with pretty-printing.
 */
@Serializable
data class WatchOrderBackup(
    val schemaVersion: Int = CURRENT_BACKUP_SCHEMA_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val userProgress: List<UserProgressEntity> = emptyList(),
    val episodeWatched: List<EpisodeWatchedEntity> = emptyList(),
    val discoverySkipped: List<DiscoverySkippedEntity> = emptyList(),
    val reviews: List<ReviewEntity> = emptyList(),
) {
    /** Total row count across all 4 tables. */
    val totalItemCount: Int
        get() = userProgress.size + episodeWatched.size + discoverySkipped.size + reviews.size
}
