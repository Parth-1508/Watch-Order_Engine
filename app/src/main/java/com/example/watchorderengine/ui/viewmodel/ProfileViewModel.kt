package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.UserStats
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats: StateFlow<UserStats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val username: StateFlow<String> = userPrefs.username
    val avatarUrl: StateFlow<String?> = userPrefs.avatarUrl

    init {
        loadStats()
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
                val ratingsDef      = async { repository.getAllRatedMedia() }
                val streakDef       = async { repository.computeWatchStreak() }

                val watching  = watchingDef.await()
                val planned   = plannedDef.await()
                val completed = completedDef.await()
                val dropped   = droppedDef.await()
                val paused    = pausedDef.await()
                
                val totalWatched = totalWatchedDef.await()
                val totalMinutes = totalMinutesDef.await()
                val ratings      = ratingsDef.await()
                val streak       = streakDef.await()

                // Calculate real average rating
                val avgRating = if (ratings.isNotEmpty()) {
                    ratings.map { it.second }.average().toFloat()
                } else null

                // Top genres from all tracked media
                val allMedia = watching + completed + planned + paused + dropped
                val topGenres = allMedia.flatMap { it.genres }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }

                // Recently watched: union of Watching and Completed, sorted by updated date (implied by repository)
                val recentlyWatched = (watching + completed).take(6)

                _stats.value = UserStats(
                    totalMinutesWatched  = totalMinutes.toLong(),
                    totalEpisodesWatched = totalWatched,
                    totalMoviesWatched   = completed.count { it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE },
                    showsCompleted       = completed.size,
                    showsDropped         = dropped.size,
                    showsWatching        = watching.size,
                    showsPlanned         = planned.size,
                    showsPaused          = paused.size,
                    topGenres            = topGenres,
                    averageRating        = avgRating,
                    recentlyWatched      = recentlyWatched,
                    favoriteGenre        = topGenres.firstOrNull(),
                    streakDays           = streak
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUsername(newName: String) {
        viewModelScope.launch {
            userPrefs.updateUsername(newName)
        }
    }

    fun updateAvatarUrl(url: String) {
        viewModelScope.launch {
            userPrefs.updateAvatarUrl(url)
        }
    }
}
