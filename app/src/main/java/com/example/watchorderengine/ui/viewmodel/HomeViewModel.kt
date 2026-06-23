package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _watchingList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val watchingList: StateFlow<List<MediaSummary>> = _watchingList

    private val _plannedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val plannedList: StateFlow<List<MediaSummary>> = _plannedList

    private val _completedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val completedList: StateFlow<List<MediaSummary>> = _completedList

    private val _droppedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val droppedList: StateFlow<List<MediaSummary>> = _droppedList

    private val _pausedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val pausedList: StateFlow<List<MediaSummary>> = _pausedList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _trendingList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val trendingList: StateFlow<List<MediaSummary>> = _trendingList

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val watching = repository.getWatchingList()
                val planned = repository.getPlannedList()
                val completed = repository.getCompletedList()
                val dropped = repository.getDroppedList()
                val paused = repository.getPausedList()
                val trending = repository.getTrending()

                _watchingList.value = watching
                _plannedList.value = planned
                _completedList.value = completed
                _droppedList.value = dropped
                _pausedList.value = paused
                _trendingList.value = trending

                android.util.Log.d("HomeViewModel", "Refresh: watching=${watching.size}, planned=${planned.size}, completed=${completed.size}, dropped=${dropped.size}, paused=${paused.size}, trending=${trending.size}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
