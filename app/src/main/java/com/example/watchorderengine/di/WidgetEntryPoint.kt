package com.example.watchorderengine.di

import android.content.Context
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Allows Glance home screen widgets (which are created by the OS and live
 * outside the Activity/Fragment graph) to access Hilt-managed dependencies.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun mediaRepository(): MediaRepository
}

/** Convenience helper for widget classes to bridge into the SingletonComponent. */
fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(this.applicationContext, WidgetEntryPoint::class.java)
