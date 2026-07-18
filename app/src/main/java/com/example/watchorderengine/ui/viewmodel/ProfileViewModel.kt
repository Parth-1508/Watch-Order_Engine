package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.UserStats
import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.ReviewRepository
import com.example.watchorderengine.data.repository.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val reviewRepository: ReviewRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val auth: FirebaseAuth,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats: StateFlow<UserStats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userReviews = MutableStateFlow<List<ReviewEntity>>(emptyList())
    val userReviews: StateFlow<List<ReviewEntity>> = _userReviews.asStateFlow()

    val username: StateFlow<String> = userPrefs.username
    val avatarUrl: StateFlow<String?> = userPrefs.avatarUrl
    val userEmail: String? = auth.currentUser?.email

    fun getAvatarModel(url: String?): Any? = userProfileRepository.getAvatarModel(url)

    private val _liveAverageRating = MutableStateFlow<Float?>(null)
    private var hasAttemptedSync = false

    init {
        loadStats()
        observeUserReviews()
        observeLiveAverageRating()
        observeTasteProfile()
    }

    private fun observeTasteProfile() {
        viewModelScope.launch {
            userPrefs.selectedGenres.collect {
                loadStats()
            }
        }
    }

    private fun observeLiveAverageRating() {
        viewModelScope.launch {
            reviewRepository.observeGlobalAverageRating().collect { avg ->
                _liveAverageRating.value = avg
                _stats.value = _stats.value?.copy(averageRating = avg)
            }
        }
    }

    private fun observeUserReviews() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            reviewRepository.observeReviewsByUser(uid).collect {
                _userReviews.value = it
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch all tracking buckets in parallel
                val watchingDef = async { repository.getListByState(TrackingState.WATCHING) }
                val plannedDef = async { repository.getListByState(TrackingState.PLANNED) }
                val completedDef = async { repository.getListByState(TrackingState.COMPLETED) }
                val droppedDef = async { repository.getListByState(TrackingState.DROPPED) }
                val pausedDef = async { repository.getListByState(TrackingState.PAUSED) }
                
                // Real DB metrics
                val totalWatchedDef = async { repository.countWatchedEpisodes() }
                val totalMinutesDef = async { repository.getTotalWatchedMinutes() }
                val streakDef       = async { repository.computeWatchStreak() }

                val watching  = watchingDef.await()
                val planned   = plannedDef.await()
                val completed = completedDef.await()
                val dropped   = droppedDef.await()
                val paused    = pausedDef.await()
                
                // If everything is empty but user is logged in, try a sync once
                if (!hasAttemptedSync && watching.isEmpty() && completed.isEmpty() && planned.isEmpty()) {
                    hasAttemptedSync = true
                    repository.syncAllFromCloud()
                    // Re-run this function to pick up synced data
                    loadStats()
                    return@launch
                }

                val totalWatched = totalWatchedDef.await()
                val totalMinutes = totalMinutesDef.await()
                val streak       = streakDef.await()

                // Top genres from all tracked media + onboarding choices
                val preferredGenres = userPrefs.selectedGenres.first()
                val allMedia = watching + completed + planned + paused + dropped
                
                val genreCounts = allMedia.flatMap { it.genres }
                    .groupingBy { it }
                    .eachCount()
                    .toMutableMap()
                
                // Onboarding genres get a virtual "weight" so they show up even with empty watchlist
                preferredGenres.forEach { genre ->
                    genreCounts[genre] = (genreCounts[genre] ?: 0) + 5 
                }

                val topGenres = genreCounts.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }

                // Recently watched: union of Watching and Completed, sorted by updated date (implied by repository)
                val recentlyWatched = (watching + completed).take(6)

                // ── Profile score ────────────────────────────────────────────────
                // Update: Count both Movie category AND Anime films (identified by TMDB movie ID prefix)
                val totalMovies = completed.count { 
                    it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE || 
                    it.id.contains("_m_") 
                }
                val score = computeProfileScore(
                    episodes = totalWatched,
                    movies   = totalMovies,
                    reviews  = _userReviews.value.size,
                    streak   = streak
                )

                _stats.value = UserStats(
                    totalMinutesWatched  = totalMinutes.toLong(),
                    totalEpisodesWatched = totalWatched,
                    totalMoviesWatched   = totalMovies,
                    showsCompleted       = completed.size,
                    showsDropped         = dropped.size,
                    showsWatching        = watching.size,
                    showsPlanned         = planned.size,
                    showsPaused          = paused.size,
                    topGenres            = topGenres,
                    averageRating        = _liveAverageRating.value,
                    recentlyWatched      = recentlyWatched,
                    favoriteGenre        = topGenres.firstOrNull(),
                    streakDays           = streak,
                    profileScore         = score,
                    profileRank          = getRankForScore(score)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun computeProfileScore(
        episodes: Int,
        movies: Int,
        reviews: Int,
        streak: Int
    ): Int = (episodes * 1) + (movies * 10) + (reviews * 25) + (streak * 5)

    private fun getRankForScore(score: Int): String = when {
        score >= 10000 -> "Legend"
        score >= 5000  -> "Grandmaster"
        score >= 2000  -> "Master"
        score >= 1000  -> "Expert"
        score >= 500   -> "Cinephile"
        score >= 100   -> "Explorer"
        else           -> "Novice"
    }

    companion object {
        // Multipliers removed as they are now inline in computeProfileScore for clarity
    }

    fun updateUsername(newName: String) {
        viewModelScope.launch {
            userPrefs.updateUsername(newName)
            
            // Sync to Firestore metadata
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users").document(uid)
                    .collection("profile").document("metadata")
                    .set(mapOf("username" to newName), com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun updateAvatarUrl(url: String) {
        viewModelScope.launch {
            userPrefs.updateAvatarUrl(url)
        }
    }

    fun updateAvatarUrlFromBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            userProfileRepository.processAvatarToBase64(bitmap).onSuccess { dataUri ->
                userPrefs.updateAvatarUrl(dataUri)
            }
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            reviewRepository.deleteReview(reviewId)
        }
    }
}
