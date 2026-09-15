package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.UserActivity
import com.example.watchorderengine.data.repository.FriendRepository
import com.example.watchorderengine.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FriendActivityUiState {
    data object Loading : FriendActivityUiState
    data class Loaded(val activity: List<UserActivity>) : FriendActivityUiState
    data class Error(val message: String) : FriendActivityUiState
}

@HiltViewModel
class FriendActivityViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendActivityUiState>(FriendActivityUiState.Loading)
    val uiState: StateFlow<FriendActivityUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = FriendActivityUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetch() {
        friendRepository.getFriendActivity()
            .onSuccess { activity -> _uiState.value = FriendActivityUiState.Loaded(activity) }
            .onFailure { e -> _uiState.value = FriendActivityUiState.Error(e.message ?: "Couldn't load activity.") }
    }

    fun getAvatarModel(url: String?): Any? = userProfileRepository.getAvatarModel(url)
}
