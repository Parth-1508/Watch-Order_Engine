package com.example.watchorderengine.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LayoutStyle { COMFORT, COMPACT }

@Singleton
class UserPreferencesRepository @Inject constructor(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_STYLE = stringPreferencesKey("layout_style")
        val HIDE_FILLER = booleanPreferencesKey("hide_filler")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val layoutStyle: Flow<LayoutStyle> = context.dataStore.data.map { preferences ->
        LayoutStyle.valueOf(preferences[PreferencesKeys.LAYOUT_STYLE] ?: LayoutStyle.COMFORT.name)
    }

    val hideFiller: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HIDE_FILLER] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setLayoutStyle(style: LayoutStyle) {
        context.dataStore.edit { it[PreferencesKeys.LAYOUT_STYLE] = style.name }
    }

    suspend fun setHideFiller(hide: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIDE_FILLER] = hide }
    }
}
