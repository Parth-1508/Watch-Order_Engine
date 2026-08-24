package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.CharacterDetail
import com.example.watchorderengine.data.repository.CharacterRepository
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CharacterDetailState {
    object Loading : CharacterDetailState()
    data class Success(val detail: CharacterDetail) : CharacterDetailState()
    data class Error(val message: String) : CharacterDetailState()
}

@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CharacterDetailState>(CharacterDetailState.Loading)
    val state: StateFlow<CharacterDetailState> = _state.asStateFlow()

    private var activePhotoIndex = MutableStateFlow(0)
    val photoIndex: StateFlow<Int> = activePhotoIndex.asStateFlow()

    /**
     * Whether the Spoiler Shield should currently blur character
     * description / voice-actor reveal content. False (never blur) when this
     * screen was opened without a [mediaId] — e.g. from a context with no
     * show to measure progress against.
     */
    private val _spoilerShieldActive = MutableStateFlow(false)
    val spoilerShieldActive: StateFlow<Boolean> = _spoilerShieldActive.asStateFlow()

    private var shieldJob: Job? = null

    fun load(
        tmdbPersonId: Int,
        characterName: String,
        showTitle: String,
        isAnime: Boolean,
        anilistId: Int? = null,
        mediaId: String? = null
    ) {
        viewModelScope.launch {
            _state.value = CharacterDetailState.Loading
            activePhotoIndex.value = 0 // Reset gallery index on new load
            val result = repository.getCharacterDetail(
                tmdbPersonId  = tmdbPersonId,
                characterName = characterName,
                showTitle     = showTitle,
                isAnime       = isAnime,
                anilistId     = anilistId
            )
            _state.value = result.fold(
                onSuccess = { CharacterDetailState.Success(it) },
                onFailure = { CharacterDetailState.Error(it.message ?: "Unknown error") }
            )
        }
        observeSpoilerShield(mediaId)
    }

    private fun observeSpoilerShield(mediaId: String?) {
        shieldJob?.cancel()
        if (mediaId.isNullOrBlank()) {
            _spoilerShieldActive.value = false
            return
        }
        shieldJob = viewModelScope.launch {
            mediaRepository.observeSpoilerShieldActive(mediaId).collect { active ->
                _spoilerShieldActive.value = active
            }
        }
    }

    fun setPhotoIndex(index: Int) { activePhotoIndex.value = index }

    fun retry(tmdbPersonId: Int, characterName: String, showTitle: String, isAnime: Boolean, anilistId: Int? = null, mediaId: String? = null) =
        load(tmdbPersonId, characterName, showTitle, isAnime, anilistId, mediaId)
}
