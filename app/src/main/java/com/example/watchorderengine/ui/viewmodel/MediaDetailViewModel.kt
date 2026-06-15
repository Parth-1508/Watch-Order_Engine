package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun loadMediaDetail(mediaId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getMediaDetailFlow(mediaId).collect {
                _mediaDetail.value = it
                _isLoading.value = false
            }
        }
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
}
