@file:Suppress("RestrictedApi")

package com.example.watchorderengine.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.watchorderengine.MainActivity
import com.example.watchorderengine.R
import com.example.watchorderengine.di.widgetEntryPoint

private const val TAG = "WatchStreakWidget"

private val WidgetBackground    = ColorProvider(R.color.widget_background)
private val WidgetGlassBorder   = ColorProvider(R.color.widget_glass_border)
private val WidgetGlassFrost    = ColorProvider(R.color.widget_glass_frost)
private val WidgetSurface       = ColorProvider(R.color.widget_surface)
private val WidgetAccent        = ColorProvider(R.color.widget_accent_gold)
private val WidgetTextPrimary   = ColorProvider(R.color.white)
private val WidgetTextSecondary = ColorProvider(R.color.widget_text_secondary)

private const val RING_CYCLE_DAYS = 7

class WatchStreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetEntryPoint().mediaRepository()

        val streak = try {
            repository.computeWatchStreak()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute watch streak: ${e.message}")
            0
        }

        val inProgressCount = try {
            repository.getWatchingList().size
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load watching list count: ${e.message}")
            0
        }

        val ringBitmap = drawStreakRing(context = context, streak = streak, diameterPx = 176)

        provideContent {
            WatchStreakContent(streak = streak, inProgressCount = inProgressCount, ring = ringBitmap)
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            WatchStreakWidget().updateAll(context)
        }
    }
}

class WatchStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchStreakWidget()
}

class OpenAppHomeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}

private fun drawStreakRing(context: Context, streak: Int, diameterPx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val strokeWidthPx = diameterPx * 0.09f
    val inset = strokeWidthPx / 2f + 4f
    val bounds = RectF(inset, inset, diameterPx - inset, diameterPx - inset)

    val trackColor = ContextCompat.getColor(context, R.color.widget_track)
    val accentColor = ContextCompat.getColor(context, R.color.widget_accent_gold)

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = trackColor
    }
    canvas.drawArc(bounds, 0f, 360f, false, trackPaint)

    if (streak > 0) {
        val cyclePosition = ((streak - 1) % RING_CYCLE_DAYS) + 1
        val fraction = cyclePosition / RING_CYCLE_DAYS.toFloat()
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = accentColor
        }
        canvas.drawArc(bounds, -90f, 360f * fraction, false, progressPaint)
    }

    return bitmap
}

@Composable
private fun WatchStreakContent(streak: Int, inProgressCount: Int, ring: Bitmap) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .appWidgetBackground()
    ) {
        GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clickable(actionRunCallback<OpenAppHomeAction>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = GlanceModifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Image(
                        provider = ImageProvider(ring),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥", style = TextStyle(fontSize = 18.sp))
                        Text(
                            "$streak",
                            style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    "DAY STREAK",
                    style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    streakSubtext(streak, inProgressCount),
                    maxLines = 2,
                    style = TextStyle(color = WidgetTextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
                )
            }
        }
    }
}

private fun streakSubtext(streak: Int, inProgressCount: Int): String {
    if (streak == 0) return "Watch an episode today to start a streak."
    val toMilestone = RING_CYCLE_DAYS - (((streak - 1) % RING_CYCLE_DAYS) + 1)
    return when {
        toMilestone == 0 -> "🎉 7-day milestone hit!"
        inProgressCount > 0 -> "$inProgressCount show${if (inProgressCount == 1) "" else "s"} in progress · $toMilestone to go"
        else -> "$toMilestone day${if (toMilestone == 1) "" else "s"} to your next milestone"
    }
}

@Composable
private fun GlassCard(
    modifier: GlanceModifier = GlanceModifier,
    radius: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(WidgetGlassBorder)
            .cornerRadius(radius)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetSurface)
                .cornerRadius(radius)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetGlassFrost)
                    .cornerRadius(radius)
            ) {
                content()
            }
        }
    }
}
