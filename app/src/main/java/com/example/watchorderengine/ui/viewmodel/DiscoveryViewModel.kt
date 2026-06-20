package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _discoveryDeck = MutableStateFlow<List<MediaSummary>>(emptyList())
    val discoveryDeck: StateFlow<List<MediaSummary>> = _discoveryDeck.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDiscovery()
    }

    fun loadDiscovery() {
        viewModelScope.launch {
            _isLoading.value = true
            val trending = repository.getTrending()
            _discoveryDeck.value = trending
            _isLoading.value = false
        }
    }

    fun handleSwipe(media: MediaSummary, state: TrackingState) {
        viewModelScope.launch {
            repository.updateTrackingState(media.id, state)
            _discoveryDeck.value = _discoveryDeck.value.filter { it.id != media.id }
        }
    }

    fun resetDeck() {
        loadDiscovery()
    }
}
