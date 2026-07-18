package com.example.watchorderengine.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.prefs.LayoutStyle
import com.example.watchorderengine.data.prefs.ThemeMode
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Outcome of the Danger Zone "wipe my cloud data" action, surfaced to the UI as a one-shot result. */
sealed interface WipeAccountState {
    data object Idle : WipeAccountState
    data object InProgress : WipeAccountState
    data object Success : WipeAccountState
    data class Failure(val message: String) : WipeAccountState
}

sealed interface ChangePasswordState {
    data object Idle : ChangePasswordState
    data object Loading : ChangePasswordState
    data object Success : ChangePasswordState
    data class Error(val message: String) : ChangePasswordState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefsRepository: UserPreferencesRepository,
    private val db: WatchOrderDatabase,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
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

    private val _wipeAccountState = MutableStateFlow<WipeAccountState>(WipeAccountState.Idle)
    val wipeAccountState: StateFlow<WipeAccountState> = _wipeAccountState.asStateFlow()

    private val _changePasswordState = MutableStateFlow<ChangePasswordState>(ChangePasswordState.Idle)
    val changePasswordState: StateFlow<ChangePasswordState> = _changePasswordState.asStateFlow()

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
            // 1. Reset local profile data
            prefsRepository.setTasteProfileCompleted(false)
            prefsRepository.setSelectedGenres(emptySet())
            prefsRepository.updateUsername("Guest")
            prefsRepository.updateAvatarUrl(null)
            prefsRepository.updateStreak(0, 0)
            
            // 2. Sign out from Firebase
            auth.signOut()
            
            // 3. Clear local cache for security/privacy
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    fun changePassword(newPassword: String) {
        val user = auth.currentUser
        if (user == null) {
            _changePasswordState.value = ChangePasswordState.Error("User not logged in.")
            return
        }
        
        _changePasswordState.value = ChangePasswordState.Loading
        user.updatePassword(newPassword).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _changePasswordState.value = ChangePasswordState.Success
            } else {
                val msg = task.exception?.message ?: "Failed to update password."
                // Check if it's a "requires recent login" error
                if (msg.contains("recent login", ignoreCase = true)) {
                    _changePasswordState.value = ChangePasswordState.Error("Security check: Please log out and back in to change your password.")
                } else {
                    _changePasswordState.value = ChangePasswordState.Error(msg)
                }
            }
        }
    }

    fun acknowledgePasswordResult() {
        _changePasswordState.value = ChangePasswordState.Idle
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearMetadataCache()
        }
    }

    /**
     * Danger Zone: Permanently deletes all user data from Firestore AND local Room,
     * then signs the user out.
     */
    fun wipeAllAccountData() {
        viewModelScope.launch {
            _wipeAccountState.value = WipeAccountState.InProgress
            try {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    // 1. Wipe User-Nested Firestore data
                    val nestedCollections = listOf("watchlist", "progress", "episode_progress", "profile", "reviews")
                    for (collection in nestedCollections) {
                        val docs = firestore.collection("users").document(uid)
                            .collection(collection).get().await()
                        
                        docs.documents.chunked(450).forEach { chunk ->
                            val batch = firestore.batch()
                            chunk.forEach { batch.delete(it.reference) }
                            batch.commit().await()
                        }
                    }
                    
                    // 2. Wipe Top-Level Collections linked to this UID
                    val topLevelWipes = listOf(
                        firestore.collection("notifications").whereEqualTo("userId", uid),
                        firestore.collection("reviews").whereEqualTo("userId", uid),
                        firestore.collection("universes").whereEqualTo("owner_id", uid)
                    )

                    for (query in topLevelWipes) {
                        val docs = query.get().await()
                        docs.documents.chunked(450).forEach { chunk ->
                            val batch = firestore.batch()
                            chunk.forEach { batch.delete(it.reference) }
                            batch.commit().await()
                        }
                    }

                    // 3. Delete Public Profile & Root User Doc
                    val batch = firestore.batch()
                    batch.delete(firestore.collection("user_profiles").document(uid))
                    batch.delete(firestore.collection("users").document(uid))
                    batch.commit().await()
                }

                // 4. Clear local Room database
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                }

                // 5. Reset local preferences
                prefsRepository.setTasteProfileCompleted(false)
                prefsRepository.setSelectedGenres(emptySet())
                prefsRepository.updateUsername("Guest")
                prefsRepository.updateAvatarUrl(null)
                prefsRepository.updateStreak(0, 0)

                // 6. Sign out
                auth.signOut()

                _wipeAccountState.value = WipeAccountState.Success
            } catch (e: Exception) {
                Log.e("SettingsVM", "Wipe failed", e)
                _wipeAccountState.value = WipeAccountState.Failure(e.message ?: "Unknown error.")
            }
        }
    }

    /** Lets the UI dismiss/reset the one-shot result after it's been shown. */
    fun acknowledgeWipeResult() {
        _wipeAccountState.value = WipeAccountState.Idle
    }
}
