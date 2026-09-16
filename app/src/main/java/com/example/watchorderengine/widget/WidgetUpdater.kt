package com.example.watchorderengine.widget

import android.content.Context
import android.util.Log

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

    suspend fun refreshWatchStreak(context: Context) {
        try {
            WatchStreakWidget.refreshAll(context)
        } catch (e: Exception) {
            Log.w(TAG, "Watch Streak widget refresh failed: ${e.message}")
        }
    }

    suspend fun refreshAll(context: Context) {
        refreshContinueWatching(context)
        refreshUpcomingCalendar(context)
        refreshWatchStreak(context)
    }
}
