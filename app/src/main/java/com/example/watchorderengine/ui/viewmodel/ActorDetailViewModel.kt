package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.ActorDetail
import com.example.watchorderengine.data.model.ActorSummary
import com.example.watchorderengine.data.repository.ActorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ActorDetailUiState {
    data object Loading : ActorDetailUiState
    data class Loaded(val actor: ActorDetail, val isFavorite: Boolean) : ActorDetailUiState
    data class Error(val message: String) : ActorDetailUiState
}

@HiltViewModel
class ActorDetailViewModel @Inject constructor(
    private val actorRepository: ActorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val personId: Int = checkNotNull(savedStateHandle.get<Int>("tmdbPersonId") ?: savedStateHandle.get<String>("tmdbPersonId")?.toIntOrNull()) {
        "ActorDetailScreen requires a tmdbPersonId"
    }

    private val _uiState = MutableStateFlow<ActorDetailUiState>(ActorDetailUiState.Loading)
    val uiState: StateFlow<ActorDetailUiState> = _uiState.asStateFlow()

    init {
        loadActor()
        observeFavorite()
    }

    fun loadActor() {
        viewModelScope.launch {
            _uiState.value = ActorDetailUiState.Loading
            val detail = actorRepository.getActorDetail(personId)
            if (detail != null) {
                val isFav = actorRepository.isFavorite(personId)
                _uiState.value = ActorDetailUiState.Loaded(detail, isFav)
            } else {
                _uiState.value = ActorDetailUiState.Error("Could not load actor profile.")
            }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            actorRepository.observeIsFavorite(personId).collect { isFav ->
                val current = _uiState.value
                if (current is ActorDetailUiState.Loaded) {
                    _uiState.value = current.copy(isFavorite = isFav)
                }
            }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value as? ActorDetailUiState.Loaded ?: return
        val actor = current.actor
        viewModelScope.launch {
            actorRepository.toggleFavorite(
                ActorSummary(
                    id = actor.id,
                    name = actor.name,
                    profilePath = actor.profilePath,
                    knownForDepartment = actor.knownForDepartment,
                    popularity = null
                )
            )
        }
    }
}
