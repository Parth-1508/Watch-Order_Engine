package com.example.watchorderengine.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.TaskType
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val SYNC_TAG = "OfflineSync"
private const val MAX_RETRY_COUNT = 5

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: WatchOrderDatabase,
    private val watchOrderRepository: WatchOrderRepository,
    private val userPrefs: UserPreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncEnabled = userPrefs.cloudSyncEnabled.first()
        if (!syncEnabled) {
            Log.i(SYNC_TAG, "Cloud sync disabled — worker exiting without draining queue.")
            return Result.success()
        }

        val tasks = db.pendingSyncTaskDao().getAll()
        if (tasks.isEmpty()) return Result.success()

        Log.i(SYNC_TAG, "Draining ${tasks.size} pending sync task(s).")

        var allSucceeded = true

        taskLoop@for (task in tasks) {
            try {
                when (task.taskType) {
                    TaskType.NODE_COMPLETION -> {
                        val nodeId = task.nodeId
                            ?: run {
                                Log.e(SYNC_TAG, "NODE_COMPLETION task ${task.id} has null nodeId — deleting.")
                                db.pendingSyncTaskDao().deleteById(task.id)
                                continue@taskLoop
                            }
                        watchOrderRepository.setNodeCompletionDirect(
                            universeId = task.universeId,
                            nodeId     = nodeId,
                            completed  = task.completed,
                        ).getOrThrow()
                        db.pendingSyncTaskDao().deleteById(task.id)
                        Log.d(SYNC_TAG, "Flushed NODE_COMPLETION $nodeId ✓")
                    }

                    TaskType.EPISODE_WATCHED -> {
                        val episodeId = task.episodeId
                        val mediaId   = task.mediaId
                        if (episodeId == null || mediaId == null) {
                            Log.e(SYNC_TAG, "EPISODE_WATCHED task ${task.id} missing episodeId/mediaId — deleting.")
                            db.pendingSyncTaskDao().deleteById(task.id)
                            continue@taskLoop
                        }
                        watchOrderRepository.mirrorEpisodeWatchedToFirestore(
                            episodeId = episodeId,
                            mediaId   = mediaId,
                            watched   = task.completed,
                        ).getOrThrow()
                        db.pendingSyncTaskDao().deleteById(task.id)
                        Log.d(SYNC_TAG, "Flushed EPISODE_WATCHED $episodeId ✓")
                    }

                    else -> {
                        Log.w(SYNC_TAG, "Unknown task type '${task.taskType}' (id=${task.id}) — deleting.")
                        db.pendingSyncTaskDao().deleteById(task.id)
                    }
                }
            } catch (e: Exception) {
                Log.w(SYNC_TAG, "Task ${task.id} (${task.taskType}) failed: ${e.message}")
                if (task.retryCount >= MAX_RETRY_COUNT) {
                    Log.e(SYNC_TAG, "Task ${task.id} exceeded max retries — dropping permanently.")
                    db.pendingSyncTaskDao().deleteById(task.id)
                } else {
                    db.pendingSyncTaskDao().incrementRetry(task.id)
                }
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "WatchOrderSyncWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .addTag(SYNC_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
