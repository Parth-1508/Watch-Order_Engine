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
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val connectivityStatus: StateFlow<ConnectivityObserver.Status> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Available)

    fun syncDataOnLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            mediaRepository.syncAllFromCloud()
            reviewRepository.syncReviewsFromFirestore()
        }
    }

    suspend fun getInitialRoute(): String {
        val isLoggedIn = auth.currentUser != null
        if (!isLoggedIn) return "login"
        
        val isTasteDone = userPrefs.isTasteProfileCompleted.first()
        return if (!isTasteDone) "taste_profile_setup" else "home"
    }
}
