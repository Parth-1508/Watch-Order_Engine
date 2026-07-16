package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.UserProfile
import com.example.watchorderengine.data.repository.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Loaded(val profile: UserProfile) : PublicProfileUiState
    data object NotFound : PublicProfileUiState
    data class Error(val message: String) : PublicProfileUiState
}

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val repository: UserProfileRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val userId: String = java.net.URLDecoder.decode(
        checkNotNull(savedStateHandle.get<String>("userId")) { "PublicProfileScreen requires a userId" },
        "UTF-8"
    )
    val isOwnProfile: Boolean get() = auth.currentUser?.uid == userId

    fun getAvatarModel(url: String?): Any? = repository.getAvatarModel(url)

    private val _uiState = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PublicProfileUiState.Loading
            repository.getProfile(userId)
                .onSuccess { profile ->
                    _uiState.value = if (profile == null) {
                        PublicProfileUiState.NotFound
                    } else {
                        PublicProfileUiState.Loaded(profile)
                    }
                }
                .onFailure { e ->
                    _uiState.value = PublicProfileUiState.Error(e.message ?: "Failed to load profile.")
                }
        }
    }
}
