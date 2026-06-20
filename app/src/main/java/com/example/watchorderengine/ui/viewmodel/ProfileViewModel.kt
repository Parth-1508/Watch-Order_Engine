package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.model.UserStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats: StateFlow<UserStats?> = _stats.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            // In a real app, this would be a sophisticated join or separate collection.
            // For now, we'll derive it from our local lists.
            val watching = repository.getWatchingList()
            val planned = repository.getPlannedList()
            
            // Mocking some stats based on actual data
            _stats.value = UserStats(
                totalMinutesWatched = watching.size * 240L, // Mock avg
                totalEpisodesWatched = watching.size * 12,
                totalMoviesWatched = watching.count { it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE },
                showsCompleted = 0,
                showsDropped = 0,
                showsWatching = watching.size,
                showsPlanned = planned.size,
                showsPaused = 0,
                topGenres = listOf("Action", "Fantasy", "Sci-Fi"),
                averageRating = 8.5f
            )
        }
    }
}
