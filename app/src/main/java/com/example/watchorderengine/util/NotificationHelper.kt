package com.example.watchorderengine.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.InboxStyle
import androidx.core.app.NotificationManagerCompat
import com.example.watchorderengine.MainActivity
import com.example.watchorderengine.R
import com.example.watchorderengine.data.model.UpcomingEpisode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CHANNEL_ID = "watch_order_notifications"
        private const val CHANNEL_NAME = "Watch Order Notifications"
        private const val CHANNEL_DESC = "Notifications for likes, recommendations, and streaks"

        private const val CHANNEL_ID_AIRING = "watch_order_airing_alerts"
        private const val CHANNEL_NAME_AIRING = "New Episode Alerts"
        private const val CHANNEL_DESC_AIRING = "Notifies you when a show on your Watching list has a new episode available"
        private const val AIRING_DIGEST_NOTIFICATION_ID = 9001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESC
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_AIRING, CHANNEL_NAME_AIRING, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESC_AIRING
                }
            )
        }
    }

    fun showNotification(
        id: Int, title: String, message: String, targetId: String? = null,
        channelId: String = CHANNEL_ID
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (targetId != null) {
                putExtra("targetId", targetId)
            }
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(id, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showAiringAlert(episodes: List<UpcomingEpisode>) {
        if (episodes.isEmpty()) return

        if (episodes.size == 1) {
            val ep = episodes.first()
            showNotification(
                id        = ep.mediaId.hashCode(),
                title     = "New Episode: ${ep.showTitle}",
                message   = "${ep.seasonEpisodeLabel} — ${ep.episodeName} is out today",
                targetId  = ep.mediaId,
                channelId = CHANNEL_ID_AIRING,
            )
            return
        }

        val style = InboxStyle().setBigContentTitle("${episodes.size} shows have new episodes today")
        episodes.forEach { style.addLine("${it.showTitle} — ${it.seasonEpisodeLabel}") }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("targetId", "calendar")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_AIRING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${episodes.size} shows have new episodes today")
            .setContentText(episodes.joinToString(", ") { it.showTitle })
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) { notify(AIRING_DIGEST_NOTIFICATION_ID, builder.build()) }
        } catch (e: SecurityException) { }
    }
}
