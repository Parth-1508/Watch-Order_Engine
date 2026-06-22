package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.MediaDetail
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _mediaDetail = MutableStateFlow<MediaDetail?>(null)
    val mediaDetail: StateFlow<MediaDetail?> = _mediaDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _episodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    val episodes: StateFlow<List<EpisodeItem>> = _episodes.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isEpisodesLoading = MutableStateFlow(false)
    val isEpisodesLoading: StateFlow<Boolean> = _isEpisodesLoading.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _generationSuccess = MutableStateFlow(false)
    val generationSuccess: StateFlow<Boolean> = _generationSuccess.asStateFlow()

    private val _universe = MutableStateFlow<com.example.watchorderengine.data.model.Universe?>(null)
    val universe: StateFlow<com.example.watchorderengine.data.model.Universe?> = _universe.asStateFlow()

    private var loadJob: Job? = null
    private var episodesJob: Job? = null

    fun loadMediaDetail(mediaId: String, forceRefresh: Boolean = false) {
        val sanitizedId = when {
            mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_") -> mediaId
            mediaId.all { it.isDigit() } -> "tmdb_$mediaId"
            else -> mediaId // Pass through arc_... or other internal IDs
        }

        if (!forceRefresh && _mediaDetail.value?.id == sanitizedId) return 
        
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _isAnalyzing.value = false
                _generationError.value = null
                // Reset state for new media
                if (!forceRefresh) {
                    _mediaDetail.value = null
                    _episodes.value = emptyList()
                    _isEpisodesLoading.value = false
                }

                repository.getMediaDetailFlow(sanitizedId).collect { detail ->
                    _mediaDetail.value = detail
                    if (detail != null) {
                        _isLoading.value = false
                        
                        // Only trigger episode load if the list is currently empty or for a new show
                        if (_episodes.value.isEmpty() || _episodes.value.firstOrNull()?.mediaId != mediaId || forceRefresh) {
                            val initialSeason = detail.seasons.find { it.seasonNumber > 0 }?.seasonNumber 
                                                ?: detail.seasons.firstOrNull()?.seasonNumber 
                                                ?: 1
                            loadEpisodes(mediaId, initialSeason)
                        }
                        
                        // Look for universe
                        launch {
                            repository.findUniverseForMedia(detail.tmdbId, detail.anilistId).collect {
                                _universe.value = it
                            }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEpisodes(mediaId: String, seasonNumber: Int) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            try {
                _isEpisodesLoading.value = true
                android.util.Log.d("MediaDetailVM", "Requesting episodes for $mediaId S$seasonNumber")
                val eps = repository.getEpisodesBySeason(mediaId, seasonNumber)
                android.util.Log.d("MediaDetailVM", "Received ${eps.size} episodes")
                _episodes.value = eps
            } finally {
                _isEpisodesLoading.value = false
            }
        }
    }

    fun generateWatchOrder(mediaId: String) {
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _generationError.value = null
                _generationSuccess.value = false
                val errorMessage = repository.generateWatchOrder(mediaId)
                if (errorMessage != null) {
                    _generationError.value = errorMessage
                } else {
                    _generationSuccess.value = true
                    // Reload to reflect changes in episode types and universe banner
                    loadMediaDetail(mediaId, forceRefresh = true)
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun dismissGenerationSuccess() {
        _generationSuccess.value = false
    }

    fun dismissGenerationError() {
        _generationError.value = null
    }

    fun updateTrackingState(mediaId: String, state: TrackingState) {
        viewModelScope.launch {
            repository.updateTrackingState(mediaId, state)
            // Reload to reflect changes
            loadMediaDetail(mediaId, forceRefresh = true)
        }
    }

    fun toggleEpisodeWatched(episodeId: String, mediaId: String) {
        viewModelScope.launch {
            repository.toggleEpisodeWatched(episodeId, mediaId)
            // Refresh detail to update progress
            loadMediaDetail(mediaId, forceRefresh = true)
        }
    }

    suspend fun getPersonBiography(personId: Int): String? {
        return repository.getPersonBiography(personId)
    }
}
