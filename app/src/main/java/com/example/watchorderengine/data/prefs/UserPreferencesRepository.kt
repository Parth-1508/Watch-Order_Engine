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
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK, COMIC, MANGA, FUNK, DEFAULT }
enum class LayoutStyle { COMFORT, COMPACT }

@Singleton
class UserPreferencesRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_STYLE = stringPreferencesKey("layout_style")
        val HIDE_FILLER = booleanPreferencesKey("hide_filler")
        val HIDE_UNWATCHED_SPOILERS = booleanPreferencesKey("hide_unwatched_spoilers")
        val USERNAME = stringPreferencesKey("username")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val DYNAMIC_SHOW_THEMING = booleanPreferencesKey("dynamic_show_theming")
        val SELECTED_GENRES = stringSetPreferencesKey("selected_genres")
        val IS_TASTE_PROFILE_COMPLETED = booleanPreferencesKey("is_taste_profile_completed")
        val LAST_ACTIVE_DATE = longPreferencesKey("last_active_date")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val LAST_SMART_NOTIF_TRIGGER = longPreferencesKey("last_smart_notif_trigger")
        val AIRING_ALERTS_ENABLED = booleanPreferencesKey("airing_alerts_enabled")
        val PREFERRED_HOME_LANGUAGES = stringSetPreferencesKey("preferred_home_languages")
    }

    val isTasteProfileCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_TASTE_PROFILE_COMPLETED] ?: false
    }

    val lastActiveDate: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_ACTIVE_DATE] ?: 0L
    }

    val currentStreak: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CURRENT_STREAK] ?: 0
    }

    val lastSmartNotifTrigger: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_SMART_NOTIF_TRIGGER] ?: 0L
    }

    val airingAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AIRING_ALERTS_ENABLED] ?: false
    }

    suspend fun setTasteProfileCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.IS_TASTE_PROFILE_COMPLETED] = completed }
    }

    suspend fun updateStreak(date: Long, streak: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.LAST_ACTIVE_DATE] = date
            it[PreferencesKeys.CURRENT_STREAK] = streak
        }
    }

    suspend fun setLastSmartNotifTrigger(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_SMART_NOTIF_TRIGGER] = time }
    }

    suspend fun setAiringAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AIRING_ALERTS_ENABLED] = enabled }
    }

    val username: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USERNAME] ?: "Player One"
    }.stateIn(scope, SharingStarted.Eagerly, "Player One")

    val avatarUrl: StateFlow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AVATAR_URL]
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DEFAULT.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DEFAULT)
    }

    val layoutStyle: Flow<LayoutStyle> = context.dataStore.data.map { preferences ->
        LayoutStyle.valueOf(preferences[PreferencesKeys.LAYOUT_STYLE] ?: LayoutStyle.COMFORT.name)
    }

    val hideFiller: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HIDE_FILLER] ?: false
    }

    val hideUnwatchedSpoilers: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HIDE_UNWATCHED_SPOILERS] ?: false
    }

    val cloudSyncEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLOUD_SYNC_ENABLED] ?: true
    }

    /** Whether the Media Detail screen should tint itself from the show's poster/backdrop art. */
    val dynamicShowTheming: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_SHOW_THEMING] ?: true
    }

    val selectedGenres: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SELECTED_GENRES] ?: emptySet()
    }

    val preferredHomeLanguages: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PREFERRED_HOME_LANGUAGES] ?: setOf("ja", "ko", "hi", "es")
    }

    suspend fun setPreferredHomeLanguages(languages: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.PREFERRED_HOME_LANGUAGES] = languages }
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

    suspend fun setHideUnwatchedSpoilers(hide: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIDE_UNWATCHED_SPOILERS] = hide }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setDynamicShowTheming(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_SHOW_THEMING] = enabled }
    }

    suspend fun setSelectedGenres(genres: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.SELECTED_GENRES] = genres }
    }

    suspend fun updateUsername(name: String) {
        context.dataStore.edit { it[PreferencesKeys.USERNAME] = name }
    }

    suspend fun updateAvatarUrl(url: String?) {
        context.dataStore.edit { 
            if (url == null) it.remove(PreferencesKeys.AVATAR_URL)
            else it[PreferencesKeys.AVATAR_URL] = url 
        }
    }
}
