package com.example.watchorderengine.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.watchorderengine.di.widgetEntryPoint

class MarkEpisodeWatchedAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val mediaId = parameters[WidgetParams.MEDIA_ID] ?: return
        val episodeId = parameters[WidgetParams.EPISODE_ID] ?: return
        if (episodeId.isBlank()) return

        try {
            val repository = context.widgetEntryPoint().mediaRepository()
            // Advancing a show from the home screen is a core power-user flow.
            repository.toggleEpisodeWatched(episodeId, mediaId, context)
            
            // repository.toggleEpisodeWatched already triggers WidgetUpdater.refreshContinueWatching
        } catch (e: Exception) {
            Log.e("MarkEpisodeWatched", "Failed to mark watched: ${e.message}")
        }
    }
}
