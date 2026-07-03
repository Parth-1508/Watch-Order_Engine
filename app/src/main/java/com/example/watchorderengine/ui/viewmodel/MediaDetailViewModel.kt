package com.example.watchorderengine.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.MediaDetail
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.CharacterRepository
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val characterRepository: CharacterRepository,
    private val userPrefs: UserPreferencesRepository,
    val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _mediaDetail = MutableStateFlow<MediaDetail?>(null)
    val mediaDetail: StateFlow<MediaDetail?> = _mediaDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _episodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    val episodes: StateFlow<List<EpisodeItem>> = _episodes.asStateFlow()

    private val _reviews = MutableStateFlow<List<ReviewEntity>>(emptyList())
    val reviews: StateFlow<List<ReviewEntity>> = _reviews.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isEpisodesLoading = MutableStateFlow(false)
    val isEpisodesLoading: StateFlow<Boolean> = _isEpisodesLoading.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _generationSuccess = MutableStateFlow(false)
    val generationSuccess: StateFlow<Boolean> = _generationSuccess.asStateFlow()

    private val _universes = MutableStateFlow<List<com.example.watchorderengine.data.model.Universe>>(emptyList())
    val universes: StateFlow<List<com.example.watchorderengine.data.model.Universe>> = _universes.asStateFlow()

    // character name (any case) -> AniList character art URL, for the whole show in one
    // batched lookup — lets the Characters tab show fictional-character art (Luffy, Zoro,
    // Naruto, ...) next to/instead of the voice actor's real-life TMDB headshot.
    private val _characterArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val characterArt: StateFlow<Map<String, String>> = _characterArt.asStateFlow()

    private val _bulkMarkPrompt = MutableStateFlow<EpisodeItem?>(null)
    val bulkMarkPrompt: StateFlow<EpisodeItem?> = _bulkMarkPrompt.asStateFlow()

    private val _isBulkSyncing = MutableStateFlow(false)
    val isBulkSyncing: StateFlow<Boolean> = _isBulkSyncing.asStateFlow()

    private val _showWelcomeTip = MutableStateFlow(true)
    val showWelcomeTip: StateFlow<Boolean> = _showWelcomeTip.asStateFlow()

    private var loadJob: Job? = null
    private var episodesJob: Job? = null
    private var reviewsJob: Job? = null
    private var characterArtJob: Job? = null

    fun loadMediaDetail(mediaId: String, forceRefresh: Boolean = false) {
        val sanitizedMediaId = if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) mediaId else "tmdb_$mediaId"
        if (!forceRefresh && _mediaDetail.value?.id == sanitizedMediaId) return 
        
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

                observeReviews(sanitizedMediaId)

                repository.getMediaDetailFlow(sanitizedMediaId).collect { detail ->
                    _mediaDetail.value = detail
                    if (detail != null) {
                        _isLoading.value = false
                        
                        // Only trigger episode load if the list is currently empty or for a new show
                        val currentEps = _episodes.value
                        val needsLoad = currentEps.isEmpty() || currentEps.firstOrNull()?.mediaId != sanitizedMediaId || forceRefresh

                        if (needsLoad) {
                            val currentSeason = _episodes.value.firstOrNull()?.seasonNumber
                            val initialSeason = currentSeason ?: detail.seasons.find { it.seasonNumber > 0 }?.seasonNumber
                                                ?: detail.seasons.firstOrNull()?.seasonNumber 
                                                ?: 1
                            loadEpisodes(sanitizedMediaId, initialSeason)
                        }
                        
                        // Look for ALL universes containing this media
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
                // Once the detail flow is finished, if we are still "loading episodes" 
                // but have none, we should stop the shimmer so the user sees the "No episodes" message.
                if (_episodes.value.isEmpty()) {
                    _isEpisodesLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDetailVM", "loadMediaDetail error", e)
                _isEpisodesLoading.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun observeReviews(mediaId: String) {
        reviewsJob?.cancel()
        reviewsJob = viewModelScope.launch {
            reviewRepository.observeReviewsForMedia(mediaId).collect {
                _reviews.value = it
            }
        }
    }

    fun submitReview(mediaId: String, rating: Float, text: String, hasSpoilers: Boolean) {
        viewModelScope.launch {
            reviewRepository.submitReview(mediaId, rating, text, hasSpoilers, context)
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            reviewRepository.deleteReview(reviewId)
        }
    }

    fun loadEpisodes(mediaId: String, seasonNumber: Int) {
        val sanitizedId = if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) mediaId else "tmdb_$mediaId"
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            _isEpisodesLoading.value = true
            try {
                combine(
                    repository.observeEpisodesBySeason(sanitizedId, seasonNumber),
                    userPrefs.hideFiller,
                    userPrefs.hideUnwatchedSpoilers,
                    repository.observeMaxWatchedAbsoluteEpisode(sanitizedId)
                ) { eps, hideFillers, hideSpoilers, maxWatchedAbs ->
                    eps.filter { ep -> 
                        if (hideFillers) ep.episodeType != com.example.watchorderengine.data.model.EpisodeType.FILLER 
                        else true 
                    }.map { ep ->
                        val isBlurred = hideSpoilers && !ep.isWatched && ep.absoluteEpisodeNumber > (maxWatchedAbs + 1)
                        if (isBlurred) {
                            ep.copy(
                                title = "Episode ${ep.episodeNumber}",
                                overview = "Spoiler shielded. Watch previous episodes to reveal.",
                                isSpoilerBlurred = true
                            )
                        } else ep
                    }
                }.collect { filtered ->
                    _episodes.value = filtered
                    _isEpisodesLoading.value = false
                }
            } catch (e: Exception) {
                _isEpisodesLoading.value = false
            }
        }
    }

    fun generateWatchOrder(mediaId: String) {
        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _generationError.value = null
                _generationSuccess.value = false
                val errorMessage = repository.generateWatchOrder(mediaId)
                if (errorMessage != null) {
                    _generationError.value = errorMessage
                } else {
                    _generationSuccess.value = true
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

    fun dismissGenerationSuccess() {
        _generationSuccess.value = false
    }

    fun updateTrackingState(mediaId: String, state: TrackingState?) {
        viewModelScope.launch {
            try {
                if (state == TrackingState.COMPLETED) _isBulkSyncing.value = true
                if (state == null) {
                    repository.removeFromWatchlist(mediaId)
                } else {
                    repository.updateTrackingState(mediaId, state)
                    if (state == TrackingState.COMPLETED) {
                        repository.markAllAsWatched(mediaId)
                    }
                }
                // Reload to reflect changes
                loadMediaDetail(mediaId, forceRefresh = true)
            } finally {
                _isBulkSyncing.value = false
            }
        }
    }

    fun toggleEpisodeWatched(episode: EpisodeItem, mediaId: String) {
        val sanitizedId = if (mediaId.startsWith("tmdb_") || mediaId.startsWith("anilist_")) mediaId else "tmdb_$mediaId"
        viewModelScope.launch {
            val wasWatched = episode.isWatched
            
            // Log for Boruto debugging
            if (sanitizedId.contains("70881")) {
                android.util.Log.d("BorutoDebug", "Toggling episode ${episode.id} for $sanitizedId. WasWatched=$wasWatched")
            }

            repository.toggleEpisodeWatched(episode.id, sanitizedId, context)
            
            // If we just marked it as watched, check if there are previous unwatched episodes
            if (!wasWatched) {
                val hasUnwatched = repository.hasUnwatchedEpisodesBefore(sanitizedId, episode.seasonNumber, episode.episodeNumber)
                if (hasUnwatched) {
                    _bulkMarkPrompt.value = episode
                }
            }

            // Refresh detail and check for auto-completion
            loadMediaDetail(sanitizedId, forceRefresh = true)
            checkAutoCompletion(sanitizedId)
        }
    }

    fun confirmBulkMark(mediaId: String) {
        val episode = _bulkMarkPrompt.value ?: return
        viewModelScope.launch {
            try {
                _isBulkSyncing.value = true
                repository.markPreviousEpisodesAsWatchedSequentially(mediaId, episode.seasonNumber, episode.episodeNumber)
                _bulkMarkPrompt.value = null
                loadMediaDetail(mediaId, forceRefresh = true)
                checkAutoCompletion(mediaId)
            } finally {
                _isBulkSyncing.value = false
            }
        }
    }

    fun dismissBulkMark() {
        _bulkMarkPrompt.value = null
    }

    fun dismissWelcomeTip() {
        _showWelcomeTip.value = false
    }

    fun markSeasonAsWatched(mediaId: String, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                _isBulkSyncing.value = true
                repository.markSeasonAsWatched(mediaId, seasonNumber)
                loadMediaDetail(mediaId, forceRefresh = true)
                checkAutoCompletion(mediaId)
            } finally {
                _isBulkSyncing.value = false
            }
        }
    }

    fun unmarkSeasonAsWatched(mediaId: String, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                _isBulkSyncing.value = true
                repository.unmarkSeasonAsWatched(mediaId, seasonNumber)
                loadMediaDetail(mediaId, forceRefresh = true)
                // If we unmark, it shouldn't be completed anymore
                checkAutoCompletion(mediaId)
            } finally {
                _isBulkSyncing.value = false
            }
        }
    }

    private suspend fun checkAutoCompletion(mediaId: String) {
        // We can't rely on _mediaDetail.value yet as loadMediaDetail is asynchronous
        // Let's grab the fresh state from repository
        repository.getMediaDetailFlow(mediaId).collect { detail ->
            if (detail != null) {
                val watched = detail.userProgress?.totalEpisodesWatched ?: 0
                val total = detail.numberOfEpisodes ?: 0
                val currentState = detail.userProgress?.trackingState

                if (total > 0 && watched >= total && currentState != TrackingState.COMPLETED) {
                    repository.updateTrackingState(mediaId, TrackingState.COMPLETED)
                    // One final reload to sync the UI state
                    loadMediaDetail(mediaId, forceRefresh = true)
                } else if (total > 0 && watched < total && currentState == TrackingState.COMPLETED) {
                    // If it was completed but now has unmarked episodes, move back to Watching
                    repository.updateTrackingState(mediaId, TrackingState.WATCHING)
                    loadMediaDetail(mediaId, forceRefresh = true)
                }
            }
            // Stop after first emission (cached or refreshed)
            return@collect
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
