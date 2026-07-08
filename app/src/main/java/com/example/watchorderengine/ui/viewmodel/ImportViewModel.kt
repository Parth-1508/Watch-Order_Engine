package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.import_list.AnimeListImportRepository
import com.example.watchorderengine.data.import_list.ImportedAnimeEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportSource {
    object AniList : ImportSource
    object MAL     : ImportSource
}

sealed interface ImportUiState {
    object Idle    : ImportUiState
    object Loading : ImportUiState
    data class Syncing(val current: Int, val total: Int) : ImportUiState
    data class Preview(
        val entries: List<ImportedAnimeEntry>,
        val source: ImportSource
    ) : ImportUiState
    data class Success(
        val written: Int, 
        val total: Int
    )  : ImportUiState
    data class Error(val message: String)                 : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importRepository: AnimeListImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Step 1: fetch and preview entries without writing to Room. */
    fun fetchPreview(username: String, source: ImportSource) {
        if (username.isBlank()) {
            _uiState.value = ImportUiState.Error("Please enter a username.")
            return
        }
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading
            try {
                val entries = when (source) {
                    is ImportSource.AniList -> importRepository.fetchAniListEntries(username.trim())
                    is ImportSource.MAL     -> importRepository.fetchMalEntries(username.trim())
                }
                if (entries.isEmpty()) {
                    _uiState.value = ImportUiState.Error(
                        "No anime found on this ${if (source is ImportSource.AniList) "AniList" else "MAL"} profile.\n" +
                        "Make sure the list is public and the username is correct."
                    )
                } else {
                    _uiState.value = ImportUiState.Preview(entries = entries, source = source)
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error(e.message ?: "Unknown error.")
            }
        }
    }

    /** Step 2: confirm import — writes the previewed entries to Room. */
    fun confirmImport(entries: List<ImportedAnimeEntry>, overwrite: Boolean) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Syncing(0, entries.size)
            try {
                val written = importRepository.persistEntriesToRoom(
                    entries = entries, 
                    overwrite = overwrite,
                    onProgress = { current, total ->
                        _uiState.value = ImportUiState.Syncing(current, total)
                    }
                )
                _uiState.value = ImportUiState.Success(written = written, total = entries.size)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error(e.message ?: "Import failed.")
            }
        }
    }

    fun reset() { _uiState.value = ImportUiState.Idle }
}
