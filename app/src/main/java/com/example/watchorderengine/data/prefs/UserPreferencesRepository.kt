package com.example.watchorderengine.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.watchorderengine.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK, COMIC, MANGA, RETRO, BOLLYWOOD, NARUTO }
enum class LayoutStyle { COMFORT, COMPACT }

@Singleton
class UserPreferencesRepository @Inject constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_STYLE = stringPreferencesKey("layout_style")
        val HIDE_FILLER = booleanPreferencesKey("hide_filler")
        val USERNAME = stringPreferencesKey("username")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
    }

    val username: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USERNAME] ?: "Player One"
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "Player One")

    val avatarUrl: StateFlow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AVATAR_URL]
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name)
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

    suspend fun updateUsername(name: String) {
        context.dataStore.edit { it[PreferencesKeys.USERNAME] = name }
    }

    suspend fun updateAvatarUrl(url: String) {
        context.dataStore.edit { it[PreferencesKeys.AVATAR_URL] = url }
    }
}
