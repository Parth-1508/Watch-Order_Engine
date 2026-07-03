package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.prefs.LayoutStyle
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Outcome of the Danger Zone "wipe my cloud graphs" action, surfaced to the UI as a one-shot result. */
sealed interface WipeGraphsState {
    data object Idle : WipeGraphsState
    data object InProgress : WipeGraphsState
    data object Success : WipeGraphsState
    data class Failure(val message: String) : WipeGraphsState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefsRepository: UserPreferencesRepository,
    private val watchOrderRepository: WatchOrderRepository,
    private val db: WatchOrderDatabase,
    private val auth: FirebaseAuth
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val layoutStyle: StateFlow<LayoutStyle> = prefsRepository.layoutStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutStyle.COMFORT)

    val hideFiller: StateFlow<Boolean> = prefsRepository.hideFiller
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideUnwatchedSpoilers: StateFlow<Boolean> = prefsRepository.hideUnwatchedSpoilers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cloudSyncEnabled: StateFlow<Boolean> = prefsRepository.cloudSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _wipeGraphsState = MutableStateFlow<WipeGraphsState>(WipeGraphsState.Idle)
    val wipeGraphsState: StateFlow<WipeGraphsState> = _wipeGraphsState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun setLayoutStyle(style: LayoutStyle) {
        viewModelScope.launch { prefsRepository.setLayoutStyle(style) }
    }

    fun setHideFiller(hide: Boolean) {
        viewModelScope.launch { prefsRepository.setHideFiller(hide) }
    }

    fun setHideUnwatchedSpoilers(hide: Boolean) {
        viewModelScope.launch { prefsRepository.setHideUnwatchedSpoilers(hide) }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setCloudSyncEnabled(enabled) }
    }

    fun signOut() {
        viewModelScope.launch {
            // 1. Reset onboarding flags and local taste profile
            prefsRepository.setTasteProfileCompleted(false)
            prefsRepository.setSelectedGenres(emptySet())
            // 2. Sign out from Firebase
            auth.signOut()
            // 3. Clear local cache for security/privacy if desired
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    fun changePassword(newPassword: String) {
        val user = auth.currentUser ?: return
        user.updatePassword(newPassword)
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    /**
     * Danger Zone: permanently deletes every AI-generated graph this user
     * has in Firestore, plus their progress on all of them. Irreversible —
     * the UI must confirm with the user before calling this.
     */
    fun wipeAllCloudGraphs() {
        viewModelScope.launch {
            _wipeGraphsState.value = WipeGraphsState.InProgress
            val result = watchOrderRepository.deleteAllGeneratedUniverses()
            _wipeGraphsState.value = if (result.isSuccess) {
                WipeGraphsState.Success
            } else {
                WipeGraphsState.Failure(result.exceptionOrNull()?.message ?: "Unknown error.")
            }
        }
    }

    /** Lets the UI dismiss/reset the one-shot result after it's been shown. */
    fun acknowledgeWipeResult() {
        _wipeGraphsState.value = WipeGraphsState.Idle
    }
}
