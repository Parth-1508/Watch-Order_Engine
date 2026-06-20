package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.MediaDetail
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _universe = MutableStateFlow<com.example.watchorderengine.data.model.Universe?>(null)
    val universe: StateFlow<com.example.watchorderengine.data.model.Universe?> = _universe.asStateFlow()

    fun loadMediaDetail(mediaId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getMediaDetailFlow(mediaId).collect { detail ->
                _mediaDetail.value = detail
                if (detail != null) {
                    _isLoading.value = false
                    if (_episodes.value.isEmpty() && detail.seasons.isNotEmpty()) {
                        loadEpisodes(detail.id, detail.seasons.first().seasonNumber)
                    }
                    
                    // Look for universe
                    launch {
                        repository.findUniverseForMedia(detail.tmdbId).collect {
                            _universe.value = it
                        }
                    }
                }
            }
        }
    }

    fun loadEpisodes(mediaId: String, seasonNumber: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val eps = repository.getEpisodesBySeason(mediaId, seasonNumber)
            _episodes.value = eps
            _isLoading.value = false
        }
    }

    fun generateWatchOrder(mediaId: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _generationError.value = null
            val errorMessage = repository.generateWatchOrder(mediaId)
            if (errorMessage != null) {
                _generationError.value = errorMessage
            } else {
                // Reload to reflect changes in episode types
                loadMediaDetail(mediaId)
            }
            _isAnalyzing.value = false
        }
    }

    fun dismissGenerationError() {
        _generationError.value = null
    }

    fun updateTrackingState(mediaId: String, state: TrackingState) {
        viewModelScope.launch {
            repository.updateTrackingState(mediaId, state)
            // Reload to reflect changes
            loadMediaDetail(mediaId)
        }
    }

    fun toggleEpisodeWatched(episodeId: String, mediaId: String) {
        viewModelScope.launch {
            repository.toggleEpisodeWatched(episodeId, mediaId)
            // Refresh detail to update progress
            loadMediaDetail(mediaId)
        }
    }

    suspend fun getPersonBiography(personId: Int): String? {
        return repository.getPersonBiography(personId)
    }
}
