package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.MediaDetail
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.CharacterRepository
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val characterRepository: CharacterRepository
) : ViewModel() {

    private val _mediaDetail = MutableStateFlow<MediaDetail?>(null)
    val mediaDetail: StateFlow<MediaDetail?> = _mediaDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _episodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    val episodes: StateFlow<List<EpisodeItem>> = _episodes.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isEpisodesLoading = MutableStateFlow(false)
    val isEpisodesLoading: StateFlow<Boolean> = _isEpisodesLoading.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _universes = MutableStateFlow<List<com.example.watchorderengine.data.model.Universe>>(emptyList())
    val universes: StateFlow<List<com.example.watchorderengine.data.model.Universe>> = _universes.asStateFlow()

    // character name (any case) -> AniList character art URL, for the whole show in one
    // batched lookup — lets the Characters tab show fictional-character art (Luffy, Zoro,
    // Naruto, ...) next to/instead of the voice actor's real-life TMDB headshot.
    private val _characterArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val characterArt: StateFlow<Map<String, String>> = _characterArt.asStateFlow()

    private var loadJob: Job? = null
    private var episodesJob: Job? = null
    private var characterArtJob: Job? = null

    fun loadMediaDetail(mediaId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _mediaDetail.value?.id == mediaId) return 
        
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _isAnalyzing.value = false
                _generationError.value = null
                // Reset state for new media
                if (!forceRefresh) {
                    _mediaDetail.value = null
                    _episodes.value = emptyList()
                    _isEpisodesLoading.value = false
                }

                repository.getMediaDetailFlow(mediaId).collect { detail ->
                    _mediaDetail.value = detail
                    if (detail != null) {
                        _isLoading.value = false
                        
                        // Only trigger episode load if the list is currently empty or for a new show
                        if (_episodes.value.isEmpty() || _episodes.value.firstOrNull()?.mediaId != mediaId || forceRefresh) {
                            val initialSeason = detail.seasons.find { it.seasonNumber > 0 }?.seasonNumber 
                                                ?: detail.seasons.firstOrNull()?.seasonNumber 
                                                ?: 1
                            loadEpisodes(mediaId, initialSeason)
                        }
                        
                        // Look for ALL universes containing this media — a
                        // show can legitimately belong to more than one
                        // generated universe (e.g. a crossover entry)
                        launch {
                            repository.findUniversesForMedia(detail.tmdbId).collect {
                                _universes.value = it
                            }
                        }

                        loadCharacterArt(
                            anilistId = detail.anilistId,
                            showTitle = detail.title,
                            isAnime = detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.ANIME
                        )
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEpisodes(mediaId: String, seasonNumber: Int) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            try {
                _isEpisodesLoading.value = true
                android.util.Log.d("MediaDetailVM", "Requesting episodes for $mediaId S$seasonNumber")
                val eps = repository.getEpisodesBySeason(mediaId, seasonNumber)
                android.util.Log.d("MediaDetailVM", "Received ${eps.size} episodes")
                _episodes.value = eps
            } finally {
                _isEpisodesLoading.value = false
            }
        }
    }

    fun generateWatchOrder(mediaId: String) {
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _generationError.value = null
                val errorMessage = repository.generateWatchOrder(mediaId)
                if (errorMessage != null) {
                    _generationError.value = errorMessage
                } else {
                    // Reload to reflect changes in episode types
                    loadMediaDetail(mediaId, forceRefresh = true)
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun dismissGenerationError() {
        _generationError.value = null
    }

    fun updateTrackingState(mediaId: String, state: TrackingState) {
        viewModelScope.launch {
            repository.updateTrackingState(mediaId, state)
            // Reload to reflect changes
            loadMediaDetail(mediaId, forceRefresh = true)
        }
    }

    fun toggleEpisodeWatched(episodeId: String, mediaId: String) {
        viewModelScope.launch {
            repository.toggleEpisodeWatched(episodeId, mediaId)
            // Refresh detail to update progress
            loadMediaDetail(mediaId, forceRefresh = true)
        }
    }

    suspend fun getPersonBiography(personId: Int): String? {
        return repository.getPersonBiography(personId)
    }

    /** Batched, one-shot AniList lookup of every character's art for the current show. */
    private fun loadCharacterArt(anilistId: Int?, showTitle: String, isAnime: Boolean) {
        characterArtJob?.cancel()
        if (!isAnime) {
            _characterArt.value = emptyMap()
            return
        }
        characterArtJob = viewModelScope.launch {
            _characterArt.value = characterRepository.getCharacterArtMap(anilistId, showTitle, isAnime)
        }
    }

    /** Fuzzy-matches a TMDB cast member's character name against the batched AniList art map. */
    fun characterArtFor(characterName: String): String? =
        characterRepository.matchCharacterArt(characterArt.value, characterName)
}
