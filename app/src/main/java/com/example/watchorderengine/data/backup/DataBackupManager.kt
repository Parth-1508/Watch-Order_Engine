package com.example.watchorderengine.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.model.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.watchorderengine.data.model.WatchOrderBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DataBackupManager"

enum class ImportStrategy {
    MERGE,
    REPLACE_ALL,
}

data class ImportSummary(
    val userProgressCount: Int,
    val episodeWatchedCount: Int,
    val discoverySkippedCount: Int,
    val reviewsCount: Int,
    val exportedAtMillis: Long,
) {
    val totalCount: Int get() = userProgressCount + episodeWatchedCount + discoverySkippedCount + reviewsCount
}

@Singleton
class DataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WatchOrderDatabase,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportBackup(destinationUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = WatchOrderBackup(
                userProgress     = db.userProgressDao().getAllProgress(),
                episodeWatched   = db.episodeWatchedDao().getAllWatched(),
                discoverySkipped = db.discoverySkippedDao().getAll(),
                reviews          = db.reviewDao().getAllReviews(),
            )

            val jsonText = json.encodeToString(WatchOrderBackup.serializer(), backup)

            val stream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Could not open output stream")

            stream.use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }

            Log.i(TAG, "Exported ${backup.totalItemCount} items")
            backup.totalItemCount
        }
    }

    suspend fun importBackup(
        sourceUri: Uri,
        strategy: ImportStrategy = ImportStrategy.MERGE,
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IOException("Could not open input stream")

            val backup = try {
                json.decodeFromString(WatchOrderBackup.serializer(), jsonText)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid backup file format", e)
            }

            if (backup.schemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
                throw IllegalArgumentException("Backup from newer app version")
            }

            db.withTransaction {
                if (strategy == ImportStrategy.REPLACE_ALL) {
                    db.userProgressDao().deleteAll()
                    db.episodeWatchedDao().deleteAll()
                    db.discoverySkippedDao().clearAllSkipped()
                    db.reviewDao().deleteAll()
                }

                db.userProgressDao().upsertAll(backup.userProgress)
                db.episodeWatchedDao().insertAllWatched(backup.episodeWatched)
                db.discoverySkippedDao().insertAll(backup.discoverySkipped)
                db.reviewDao().upsertAllReviews(backup.reviews)
            }

            ImportSummary(
                userProgressCount     = backup.userProgress.size,
                episodeWatchedCount   = backup.episodeWatched.size,
                discoverySkippedCount = backup.discoverySkipped.size,
                reviewsCount          = backup.reviews.size,
                exportedAtMillis      = backup.exportedAtMillis,
            )
        }
    }
}
