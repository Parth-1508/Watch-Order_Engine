package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.Notification
import com.example.watchorderengine.data.repository.NotificationRepository
import com.example.watchorderengine.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationUiState {
    object Loading : NotificationUiState
    data class Success(val notifications: List<Notification>) : NotificationUiState
    data class Error(val message: String) : NotificationUiState
}

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Loading)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            repository.observeNotifications().collect { result ->
                result.onSuccess { list ->
                    _uiState.value = NotificationUiState.Success(list)
                }.onFailure { e ->
                    _uiState.value = NotificationUiState.Error(e.message ?: "Failed to load notifications.")
                }
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun getAvatarModel(url: String?): Any? = userProfileRepository.getAvatarModel(url)
}
