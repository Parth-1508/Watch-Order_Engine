package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.ReviewRepository
import com.example.watchorderengine.util.ConnectivityObserver
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val reviewRepository: ReviewRepository,
    private val auth: FirebaseAuth,
    private val connectivityObserver: ConnectivityObserver,
    private val db: com.example.watchorderengine.data.db.WatchOrderDatabase
) : ViewModel() {

    val connectivityStatus: StateFlow<ConnectivityObserver.Status> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Available)

    fun syncDataOnLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = auth.currentUser
            if (user != null) {
                // Sync user profile info to DataStore
                val currentUsername = userPrefs.username.first()
                if (currentUsername == "Player One" || currentUsername == "Guest") {
                    user.displayName?.let { userPrefs.updateUsername(it) }
                }
                user.photoUrl?.toString()?.let { userPrefs.updateAvatarUrl(it) }
            }
            mediaRepository.syncAllFromCloud()
            reviewRepository.syncReviewsFromFirestore()
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
