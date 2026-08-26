package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.UpcomingEpisode
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        refresh(showSpinner = false)
    }

    fun refresh(showSpinner: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (showSpinner) _isRefreshing.value = true
            if (_uiState.value !is CalendarUiState.Success) _uiState.value = CalendarUiState.Loading
            
            try {
                // First, do a background network refresh to catch any newly
                // announced air dates for the user's watching list.
                repository.refreshCurrentSeasonForWatchingShows()

                // Then read the full local calendar from Room.
                val episodes = repository.getUpcomingEpisodes()
                _uiState.value = CalendarUiState.Success(episodes)
            } catch (e: Exception) {
                if (_uiState.value !is CalendarUiState.Success) {
                    _uiState.value = CalendarUiState.Error(e.message ?: "Unknown error")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun goToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _selectedMonth.value = YearMonth.from(today)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        // If the user taps a date in an adjacent month shown in the grid,
        // sync the header month to match.
        val month = YearMonth.from(date)
        if (month != _selectedMonth.value) {
            _selectedMonth.value = month
        }
    }
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(val episodes: List<UpcomingEpisode>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}
