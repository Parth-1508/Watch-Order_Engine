package com.example.watchorderengine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.Universe
import com.example.watchorderengine.data.WatchOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UniverseListUiState {
    data object Loading : UniverseListUiState
    data class Success(val universes: List<Universe>) : UniverseListUiState
    data class Error(val message: String) : UniverseListUiState
}

@HiltViewModel
class UniverseListViewModel @Inject constructor(
    private val repository: WatchOrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UniverseListUiState>(UniverseListUiState.Loading)
    val uiState: StateFlow<UniverseListUiState> = _uiState.asStateFlow()

    init {
        loadUniverses()
    }

    fun loadUniverses() {
        viewModelScope.launch {
            repository.getUniverses()
                .catch { _uiState.value = UniverseListUiState.Error(it.message ?: "Unknown error") }
                .collect { universes ->
                    _uiState.value = UniverseListUiState.Success(universes)
                }
        }
    }
}
