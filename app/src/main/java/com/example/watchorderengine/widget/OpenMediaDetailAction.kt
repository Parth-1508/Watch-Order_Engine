package com.example.watchorderengine.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.watchorderengine.MainActivity

class OpenMediaDetailAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetId = parameters[WidgetParams.MEDIA_ID] ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("targetId", targetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
