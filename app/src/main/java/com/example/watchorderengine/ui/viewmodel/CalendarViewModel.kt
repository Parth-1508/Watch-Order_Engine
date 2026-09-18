package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.TrackingState
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
import java.time.ZoneId
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

    private val _dailyGlobalSchedule = MutableStateFlow<List<UpcomingEpisode>>(emptyList())
    val dailyGlobalSchedule: StateFlow<List<UpcomingEpisode>> = _dailyGlobalSchedule.asStateFlow()

    private val _isGlobalScheduleLoading = MutableStateFlow(false)
    val isGlobalScheduleLoading: StateFlow<Boolean> = _isGlobalScheduleLoading.asStateFlow()

    private val _reminderEpisodeIds = MutableStateFlow<Set<String>>(emptySet())
    val reminderEpisodeIds: StateFlow<Set<String>> = _reminderEpisodeIds.asStateFlow()

    private val _activeCategoryFilter = MutableStateFlow<String?>(null)
    val activeCategoryFilter: StateFlow<String?> = _activeCategoryFilter.asStateFlow()

    init {
        refresh(showSpinner = false)
        loadGlobalScheduleForDate(_selectedDate.value)
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

    fun loadGlobalScheduleForDate(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGlobalScheduleLoading.value = true
            try {
                val zoneId = ZoneId.systemDefault()
                val startOfDay = date.atStartOfDay(zoneId).toEpochSecond()
                val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toEpochSecond() - 1

                val schedule = repository.fetchGlobalAiringScheduleForDay(startOfDay, endOfDay)
                _dailyGlobalSchedule.value = schedule
            } catch (e: Exception) {
                _dailyGlobalSchedule.value = emptyList()
            } finally {
                _isGlobalScheduleLoading.value = false
            }
        }
    }

    fun goToToday() {
        val today = LocalDate.now()
        selectDate(today)
    }

    fun previousMonth() {
        val newMonth = _selectedMonth.value.minusMonths(1)
        selectMonthYear(newMonth)
    }

    fun nextMonth() {
        val newMonth = _selectedMonth.value.plusMonths(1)
        selectMonthYear(newMonth)
    }

    fun selectMonthYear(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
        val newDate = yearMonth.atDay(1)
        _selectedDate.value = newDate
        loadGlobalScheduleForDate(newDate)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        val month = YearMonth.from(date)
        if (month != _selectedMonth.value) {
            _selectedMonth.value = month
        }
        loadGlobalScheduleForDate(date)
    }

    fun setCategoryFilter(category: String?) {
        _activeCategoryFilter.value = category
    }

    fun toggleNotificationReminder(episodeKey: String) {
        val current = _reminderEpisodeIds.value
        _reminderEpisodeIds.value = if (episodeKey in current) current - episodeKey else current + episodeKey
    }

    fun quickAddToWatchlist(mediaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTrackingState(mediaId, TrackingState.WATCHING)
            refresh(showSpinner = false)
        }
    }
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(val episodes: List<UpcomingEpisode>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}
