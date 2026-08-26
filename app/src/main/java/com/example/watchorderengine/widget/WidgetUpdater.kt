package com.example.watchorderengine.widget

import android.content.Context
import android.util.Log

/**
 * Central place [com.example.watchorderengine.data.repository.MediaRepository]
 * calls into after a mutation that should show up on the home screen widgets
 * promptly, rather than waiting out Android's own ~30-minute widget update
 * cadence. Both widgets read fresh from Room on every [androidx.glance.appwidget.GlanceAppWidget.provideGlance]
 * call, so "refresh" here just means "ask Glance to recompose now."
 *
 * Safe to call with zero placed widget instances (a no-op) and failures are
 * swallowed — a home screen widget lagging a few minutes behind is a minor
 * inconvenience, never worth surfacing as an error on whatever mutation
 * triggered this.
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    suspend fun refreshContinueWatching(context: Context) {
        try {
            ContinueWatchingWidget.refreshAll(context)
        } catch (e: Exception) {
            Log.w(TAG, "Continue Watching widget refresh failed: ${e.message}")
        }
    }

    suspend fun refreshUpcomingCalendar(context: Context) {
        try {
            UpcomingCalendarWidget.refreshAll(context)
        } catch (e: Exception) {
            Log.w(TAG, "Release Calendar widget refresh failed: ${e.message}")
        }
    }

    /** Both widgets — for mutations like a tracking-state change that could affect either. */
    suspend fun refreshAll(context: Context) {
        refreshContinueWatching(context)
        refreshUpcomingCalendar(context)
    }
}
