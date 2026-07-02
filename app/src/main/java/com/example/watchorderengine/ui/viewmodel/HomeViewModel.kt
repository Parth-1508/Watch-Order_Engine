package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.recommendation.Recommendation
import com.example.watchorderengine.data.recommendation.RecommendationEngine
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val db: WatchOrderDatabase,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _watchingList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val watchingList: StateFlow<List<MediaSummary>> = _watchingList.asStateFlow()

    private val _plannedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val plannedList: StateFlow<List<MediaSummary>> = _plannedList.asStateFlow()

    private val _completedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val completedList: StateFlow<List<MediaSummary>> = _completedList.asStateFlow()

    private val _droppedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val droppedList: StateFlow<List<MediaSummary>> = _droppedList.asStateFlow()

    private val _pausedList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val pausedList: StateFlow<List<MediaSummary>> = _pausedList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _trendingList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val trendingList: StateFlow<List<MediaSummary>> = _trendingList.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    private val _nextUp = MutableStateFlow<com.example.watchorderengine.ui.screens.home.NextUpItem?>(null)
    val nextUp: StateFlow<com.example.watchorderengine.ui.screens.home.NextUpItem?> = _nextUp.asStateFlow()

    val avatarUrl: StateFlow<String?> = userPrefs.avatarUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        refresh()
        updateDailyStreak()
    }

    private fun updateDailyStreak() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastActive = userPrefs.lastActiveDate.first()
            val currentStreak = userPrefs.currentStreak.first()
            
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            val todayMillis = today.timeInMillis

            if (lastActive == 0L) {
                userPrefs.updateStreak(todayMillis, 1)
                return@launch
            }

            if (lastActive == todayMillis) return@launch

            val yesterday = today.clone() as Calendar
            yesterday.add(Calendar.DATE, -1)
            val yesterdayMillis = yesterday.timeInMillis

            when {
                lastActive == yesterdayMillis -> userPrefs.updateStreak(todayMillis, currentStreak + 1)
                lastActive < yesterdayMillis -> userPrefs.updateStreak(todayMillis, 1)
            }
        }
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

                generateRecommendations()
                updateNextUp(watching)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateNextUp(watching: List<MediaSummary>) {
        viewModelScope.launch(Dispatchers.IO) {
            val mostRecent = watching.firstOrNull() ?: return@launch
            val mediaId = mostRecent.id
            val isMovie = mostRecent.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE

            if (isMovie) {
                _nextUp.value = com.example.watchorderengine.ui.screens.home.NextUpItem(
                    internalId      = mediaId,
                    showTitle       = mostRecent.title,
                    episodeLabel    = "Movie",
                    posterUrl       = mostRecent.posterUrl,
                    backdropUrl     = mostRecent.backdropUrl,
                    progressPercent = 0
                )
            } else {
                val episodes = db.episodeDao().getAllEpisodesByMedia(mediaId)
                val watchedNormalized = repository.getNormalizedWatchedIds(mediaId)

                // FIX: Exclude Season 0 (Specials / OVAs / Trailers) so they are
                // never surfaced as the "next" episode before S1E1 proper.
                val nextEp = episodes
                    .filter { it.seasonNumber > 0 }
                    .find { ep ->
                        val normalizedId = ep.id
                            .removePrefix("tmdb_m_")
                            .removePrefix("tmdb_t_")
                            .removePrefix("tmdb_")
                            .removePrefix("anilist_")
                        normalizedId !in watchedNormalized
                    }

                if (nextEp != null) {
                    // UPGRADE IMAGE QUALITY: If the stored stillUrl is low-res (w185), 
                    // swap it for HD (w780) specifically for this prominent home card.
                    val highResBackdrop = nextEp.stillUrl?.replace("/w185/", "/w780/") 
                        ?: mostRecent.backdropUrl

                    _nextUp.value = com.example.watchorderengine.ui.screens.home.NextUpItem(
                        internalId = mediaId,
                        showTitle = mostRecent.title,
                        episodeLabel = "S${nextEp.seasonNumber} E${nextEp.episodeNumber} — ${nextEp.title}",
                        posterUrl = mostRecent.posterUrl,
                        backdropUrl = highResBackdrop,
                        progressPercent = (watchedNormalized.size * 100 / episodes
                            .filter { it.seasonNumber > 0 }
                            .size.coerceAtLeast(1))
                    )
                } else {
                    _nextUp.value = null
                }
            }
        }
    }

    private fun generateRecommendations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // PRIMARY filter: exact Room ID match
                val trackedIds = repository.getAllTrackedMediaIds()

                // SECONDARY filter: tmdbId-level match to catch legacy/typed ID mismatches.
                // e.g. progress stored as "tmdb_123" would NOT block "tmdb_m_123" from
                // appearing in candidates without this cross-reference.
                val trackedTmdbIds: Set<Int> = trackedIds
                    .mapNotNull { id -> db.mediaDao().getById(id)?.tmdbId }
                    .toSet()

                // Build the "taste profile" from what the user is actively tracking
                val trackedPairs = trackedIds.mapNotNull { id ->
                    val entity   = db.mediaDao().getById(id)             ?: return@mapNotNull null
                    val progress = db.userProgressDao().getByMediaId(id) ?: return@mapNotNull null
                    entity to progress
                }

                // Candidates: exclude by both ID string AND raw tmdbId
                val allMedia   = db.mediaDao().getAll()
                val candidates = allMedia.filter { media ->
                    media.id    !in trackedIds    &&
                    media.tmdbId !in trackedTmdbIds
                }

                val results = RecommendationEngine.generateRecommendations(
                    completedMedia = trackedPairs,
                    candidates     = candidates,
                    topK           = 10
                )
                _recommendations.value = results

            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error generating recommendations", e)
            }
        }
    }
}
