package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.ReviewRepository
import com.example.watchorderengine.util.ConnectivityObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    suspend fun syncDataOnLogin() {
        val user = auth.currentUser
        if (user != null) {
            // 1. Check local username
            val currentUsername = userPrefs.username.first()
            
            // 2. Try to sync from Firestore first (Our primary source of truth)
            try {
                val doc = firestore.collection("users").document(user.uid)
                    .collection("profile").document("metadata").get().await()
                val cloudName = doc.getString("username")
                if (!cloudName.isNullOrBlank()) {
                    userPrefs.updateUsername(cloudName!!)
                } else if (!user.displayName.isNullOrBlank() && (currentUsername == "Player One" || currentUsername == "Guest")) {
                    // Fallback to Firebase displayName only if Firestore is empty
                    userPrefs.updateUsername(user.displayName!!)
                }
            } catch (e: Exception) {
                // Firestore fetch failed, fallback to displayName if local is default
                if (!user.displayName.isNullOrBlank() && (currentUsername == "Player One" || currentUsername == "Guest")) {
                    userPrefs.updateUsername(user.displayName!!)
                }
            }

            user.photoUrl?.toString()?.let { userPrefs.updateAvatarUrl(it) }
        }
        mediaRepository.syncAllFromCloud()
        reviewRepository.syncReviewsFromFirestore()
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
