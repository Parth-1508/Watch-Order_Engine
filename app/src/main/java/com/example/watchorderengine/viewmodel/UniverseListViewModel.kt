package com.example.watchorderengine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.Universe
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UniverseListUiState {
    data object Loading : UniverseListUiState
    data class Success(
        val universes: List<Universe>,
        val regeneratingUniverseIds: Set<String> = emptySet()
    ) : UniverseListUiState
    data class Error(val message: String) : UniverseListUiState
}

@HiltViewModel
class UniverseListViewModel @Inject constructor(
    private val repository: WatchOrderRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UniverseListUiState>(UniverseListUiState.Loading)
    val uiState: StateFlow<UniverseListUiState> = _uiState.asStateFlow()

    private var hasRunBackfill = false

    init {
        loadUniverses()
    }

    fun loadUniverses() {
        viewModelScope.launch {
            repository.getUniverses()
                .catch { _uiState.value = UniverseListUiState.Error(it.message ?: "Unknown error") }
                .collect { universes ->
                    _uiState.value = UniverseListUiState.Success(universes)

                    // One-time repair for legacy universes with no poster at
                    // all — runs once per app session, after data is already
                    // showing, so it never blocks or delays the list render.
                    if (!hasRunBackfill) {
                        hasRunBackfill = true
                        launch {
                            mediaRepository.backfillMissingUniversePosters(universes)
                        }
                    }
                }
        }
    }

    fun deleteUniverse(universeId: String) {
        viewModelScope.launch {
            repository.deleteUniverse(universeId)
        }
    }

    fun regenerateUniverse(universeId: String) {
        val currentState = _uiState.value
        if (currentState is UniverseListUiState.Success) {
            _uiState.value = currentState.copy(
                regeneratingUniverseIds = currentState.regeneratingUniverseIds + universeId
            )
        }

        viewModelScope.launch {
            try {
                mediaRepository.generateWatchOrder(universeId)
            } finally {
                val endState = _uiState.value
                if (endState is UniverseListUiState.Success) {
                    _uiState.value = endState.copy(
                        regeneratingUniverseIds = endState.regeneratingUniverseIds - universeId
                    )
                }
            }
        }
    }

    fun toggleUniverseCompletion(universeId: String, markAsCompleted: Boolean) {
        viewModelScope.launch {
            val nodes = repository.getNodes(universeId).first()
            nodes.forEach { node ->
                val mediaId = TimelineViewModel.resolveMediaId(node)
                
                // FIX: Ensure metadata is cached locally so the "Complete Watchlist" 
                // doesn't show "Unknown Movie".
                if (markAsCompleted) {
                    launch {
                        mediaRepository.ensureMetadataCached(node)
                    }
                }

                if (markAsCompleted) {
                    mediaRepository.updateTrackingState(mediaId, com.example.watchorderengine.data.model.TrackingState.COMPLETED)
                    mediaRepository.markAllAsWatched(mediaId)
                    repository.setNodeCompletionDirect(universeId, node.id, true)
                } else {
                    mediaRepository.removeFromWatchlist(mediaId)
                    repository.setNodeCompletionDirect(universeId, node.id, false)
                }
            }
        }
    }
}
