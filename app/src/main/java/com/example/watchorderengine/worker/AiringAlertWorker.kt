package com.example.watchorderengine.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.NotifiedEpisodeEntity
import com.example.watchorderengine.data.model.UpcomingEpisode
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "AiringAlertWorker"
private const val WORK_NAME = "airing_alert_check"
private const val WORK_TAG = "airing_alerts"

private fun UpcomingEpisode.notificationKey() = "${mediaId}_s${seasonNumber}e${episodeNumber}"

@HiltWorker
class AiringAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val db: WatchOrderDatabase,
    private val userPrefs: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!userPrefs.airingAlertsEnabled.first()) {
            Log.d(TAG, "Airing alerts disabled — skipping this run.")
            return Result.success()
        }

        return try {
            mediaRepository.refreshCurrentSeasonForWatchingShows()

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val airingToday = mediaRepository.getUpcomingEpisodes().filter { it.airDate == today }

            if (airingToday.isEmpty()) {
                Log.d(TAG, "No episodes airing today.")
                return Result.success()
            }

            val alreadyNotified = db.notifiedEpisodeDao().getAllNotifiedIds().toSet()
            val freshEpisodes = airingToday.filter { it.notificationKey() !in alreadyNotified }

            if (freshEpisodes.isEmpty()) {
                Log.d(TAG, "${airingToday.size} episode(s) airing today, already notified about all of them.")
                return Result.success()
            }

            notificationHelper.showAiringAlert(freshEpisodes)

            db.notifiedEpisodeDao().insertAll(
                freshEpisodes.map { NotifiedEpisodeEntity(episodeId = it.notificationKey()) }
            )
            db.notifiedEpisodeDao().deleteOlderThan(
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            )

            Log.i(TAG, "Notified about ${freshEpisodes.size} new episode(s).")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Airing check failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AiringAlertWorker>(
                12, TimeUnit.HOURS,
                4, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

            Log.d(TAG, "Airing alert checks scheduled (every ~12h, ±4h flex).")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Airing alert checks cancelled.")
        }
    }
}
