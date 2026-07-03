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
    val repository: MediaRepository,
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

    private val _isLoading = MutableStateFlow(true)
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
        viewModelScope.launch(Dispatchers.IO) {
            refreshData()
            updateDailyStreak()
        }
        observeTasteProfile()
    }

    private fun observeTasteProfile() {
        viewModelScope.launch {
            userPrefs.selectedGenres.collect {
                generateRecommendations()
            }
        }
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

            val isTasteDone = userPrefs.isTasteProfileCompleted.first()
            val genres = userPrefs.selectedGenres.first()

            when {
                lastActive == yesterdayMillis -> {
                    userPrefs.updateStreak(todayMillis, currentStreak + 1)
                    repository.syncProfileToCloud(isTasteDone, todayMillis, currentStreak + 1, genres)
                }
                lastActive < yesterdayMillis -> {
                    userPrefs.updateStreak(todayMillis, 1)
                    repository.syncProfileToCloud(isTasteDone, todayMillis, 1, genres)
                }
            }
        }
    }

    private var hasAttemptedSync = false

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        _isLoading.value = true
        try {
            // Load trending immediately to show SOMETHING
            _trendingList.value = repository.getTrending()
            
            val watching = repository.getWatchingList()
            val planned = repository.getPlannedList()
            val completed = repository.getCompletedList()
            val dropped = repository.getDroppedList()
            val paused = repository.getPausedList()

            // Update UI with what we have locally
            _watchingList.value = watching
            _plannedList.value = planned
            _completedList.value = completed
            _droppedList.value = dropped
            _pausedList.value = paused
            
            // Generate initial recs from cache
            generateRecommendations()
            updateNextUp(watching)

            // If everything is empty but user is logged in, try a sync once
            if (!hasAttemptedSync && watching.isEmpty() && planned.isEmpty() && completed.isEmpty()) {
                hasAttemptedSync = true
                
                // This call was optimized to only fetch Media metadata, not episodes.
                repository.syncAllFromCloud()
                
                // Refresh lists after sync
                _watchingList.value = repository.getWatchingList()
                _plannedList.value = repository.getPlannedList()
                _completedList.value = repository.getCompletedList()
                _droppedList.value = repository.getDroppedList()
                _pausedList.value = repository.getPausedList()
                
                generateRecommendations()
                updateNextUp(_watchingList.value)
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Refresh failed", e)
        } finally {
            _isLoading.value = false
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
                val preferredGenres = userPrefs.selectedGenres.first()
                
                // PRIMARY filter: exact Room ID match
                val trackedIds = repository.getAllTrackedMediaIds().toList()
                if (trackedIds.isEmpty() && preferredGenres.isEmpty()) return@launch

                // Fetch tracked entities in one go
                val trackedEntities = db.mediaDao().getByIds(trackedIds).associateBy { it.id }
                val trackedTmdbIds: Set<Int> = trackedEntities.values.map { it.tmdbId }.toSet()

                // Build the "taste profile" from what the user is actively tracking
                val trackedPairs = trackedIds.mapNotNull { id ->
                    val entity   = trackedEntities[id]                   ?: return@mapNotNull null
                    val progress = db.userProgressDao().getByMediaId(id) ?: return@mapNotNull null
                    entity to progress
                }

                // Candidates: exclude by both ID string AND raw tmdbId
                // OPTIMIZATION: Instead of getAll(), maybe we just need some diversity?
                // But the engine uses the candidates list.
                val allMedia   = db.mediaDao().getAll()
                val candidates = allMedia.filter { media ->
                    media.id    !in trackedEntities.keys &&
                    media.tmdbId !in trackedTmdbIds
                }

                val results = RecommendationEngine.generateRecommendations(
                    completedMedia  = trackedPairs,
                    candidates      = candidates,
                    preferredGenres = preferredGenres,
                    topK            = 15
                )
                _recommendations.value = results

            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error generating recommendations", e)
            }
        }
    }
}
