package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<MediaSummary>>(emptyList())
    val searchResults: StateFlow<List<MediaSummary>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    private var lastQuery = ""
    private var searchJob: Job? = null

    fun search(query: String) {
        lastQuery = query
        triggerSearch()
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
        triggerSearch()
    }

    private fun triggerSearch() {
        searchJob?.cancel()
        if (lastQuery.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(500) // Debounce
            try {
                val results = repository.searchMedia(lastQuery)
                val filtered = when (_categoryFilter.value) {
                    "MOVIE" -> results.filter { it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE }
                    "TV" -> results.filter { it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.TV_SHOW }
                    "ANIME" -> results.filter { it.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.ANIME }
                    else -> results
                }
                _searchResults.value = filtered
            } finally {
                _isSearching.value = false
            }
        }
    }
}
