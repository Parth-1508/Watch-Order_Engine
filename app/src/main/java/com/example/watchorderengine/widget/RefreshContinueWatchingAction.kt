package com.example.watchorderengine.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class RefreshContinueWatchingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Just force a Glance recomposition. provideGlance will re-read from Room.
        ContinueWatchingWidget.refreshAll(context)
    }
}
