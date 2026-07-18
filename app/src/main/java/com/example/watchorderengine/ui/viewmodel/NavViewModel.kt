package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.SyncProgress
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.ReviewRepository
import com.example.watchorderengine.util.ConnectivityObserver
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val reviewRepository: ReviewRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val connectivityObserver: ConnectivityObserver,
    private val db: com.example.watchorderengine.data.db.WatchOrderDatabase
) : ViewModel() {

    val connectivityStatus: StateFlow<ConnectivityObserver.Status> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Available)

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    fun syncDataOnLogin(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = auth.currentUser
            if (user != null) {
                try {
                    mediaRepository.syncAllFromCloud { progress ->
                        _syncProgress.value = progress
                    }
                    reviewRepository.syncReviewsFromFirestore()
                } catch (e: Exception) {
                    android.util.Log.e("NavViewModel", "Sync failed", e)
                }
            }
            // Ensure UI thread for navigation
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun logout() {
        auth.signOut()
        viewModelScope.launch(Dispatchers.IO) {
            db.clearAllTables()
            // Reset local prefs as well to clear User A's data
            userPrefs.setTasteProfileCompleted(false)
            userPrefs.updateStreak(0, 0)
            userPrefs.setSelectedGenres(emptySet())
            userPrefs.updateUsername("Guest")
            userPrefs.updateAvatarUrl(null)
        }
    }

    suspend fun getInitialRoute(): String {
        val user = auth.currentUser
        
        // 1. If not logged in at all, go to Login
        if (user == null) return "login"
        
        val isTasteDone = userPrefs.isTasteProfileCompleted.first()

        // 2. If the user is Anonymous and hasn't finished onboarding, 
        // force them to the Login screen. This prevents "Ghost Guests" 
        // from bypassing the sign-in choice.
        if (user.isAnonymous && !isTasteDone) {
            return "login"
        }
        
        // 3. Otherwise, follow standard onboarding flow
        return if (!isTasteDone) "taste_profile_setup" else "home"
    }
}
