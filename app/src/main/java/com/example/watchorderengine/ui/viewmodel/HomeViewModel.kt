package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.recommendation.Recommendation
import com.example.watchorderengine.data.recommendation.RecommendationEngine
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.NotificationRepository
import com.example.watchorderengine.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Calendar

@HiltViewModel
class HomeViewModel @Inject constructor(
    val repository: MediaRepository,
    private val db: WatchOrderDatabase,
    private val userPrefs: UserPreferencesRepository,
    private val userProfileRepository: UserProfileRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Watching")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _trendingList = MutableStateFlow<List<MediaSummary>>(emptyList())
    val trendingList: StateFlow<List<MediaSummary>> = _trendingList.asStateFlow()

    private val _recentlyReleased = MutableStateFlow<List<MediaSummary>>(emptyList())
    val recentlyReleased: StateFlow<List<MediaSummary>> = _recentlyReleased.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    private val _nextUp = MutableStateFlow<com.example.watchorderengine.ui.screens.home.NextUpItem?>(null)
    val nextUp: StateFlow<com.example.watchorderengine.ui.screens.home.NextUpItem?> = _nextUp.asStateFlow()

    val watchingCount: StateFlow<Int> = repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.WATCHING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val plannedCount: StateFlow<Int> = repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.PLANNED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val completedCount: StateFlow<Int> = repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.COMPLETED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val droppedCount: StateFlow<Int> = repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.DROPPED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val pausedCount: StateFlow<Int> = repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.PAUSED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watchlistPaged: Flow<PagingData<MediaSummary>> = _selectedCategory.flatMapLatest { cat ->
        val state = when (cat) {
            "Watching"  -> com.example.watchorderengine.data.model.TrackingState.WATCHING
            "Planned"   -> com.example.watchorderengine.data.model.TrackingState.PLANNED
            "Completed" -> com.example.watchorderengine.data.model.TrackingState.COMPLETED
            "Dropped"   -> com.example.watchorderengine.data.model.TrackingState.DROPPED
            "Paused"    -> com.example.watchorderengine.data.model.TrackingState.PAUSED
            else        -> com.example.watchorderengine.data.model.TrackingState.WATCHING
        }
        repository.observeListByStatePaged(state)
    }.cachedIn(viewModelScope)

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
        observeNextUp()
    }

    private fun observeNextUp() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeCountByState(com.example.watchorderengine.data.model.TrackingState.WATCHING).collect {
                val watching = repository.getWatchingList()
                updateNextUp(watching)
            }
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun getAvatarModel(url: String?): Any? = userProfileRepository.getAvatarModel(url)

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
            _trendingList.value = repository.getTrending()
            _recentlyReleased.value = repository.getRecentlyReleased()
            generateRecommendations()

            val watching = repository.getWatchingList()
            if (!hasAttemptedSync && watching.isEmpty()) {
                hasAttemptedSync = true
                repository.syncAllFromCloud()
                generateRecommendations()
            }
            
            triggerSmartNotifications()
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Refresh failed", e)
        } finally {
            _isLoading.value = false
        }
    }

    private fun triggerSmartNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastTrigger = userPrefs.lastSmartNotifTrigger.first()
            
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis

            if (lastTrigger >= today) return@launch // Only once per day

            // 1. Streak Notif
            val streak = userPrefs.currentStreak.first()
            if (streak > 0) {
                notificationRepository.sendSystemNotification(
                    type = com.example.watchorderengine.data.model.NotificationType.STREAK,
                    title = "Keep the momentum!",
                    message = "You're on a $streak-day streak. Watch something today to keep it going!"
                )
            }

            // 2. Personalized Recommendation Notif
            val topRec = _recommendations.value.firstOrNull()
            if (topRec != null) {
                notificationRepository.sendSystemNotification(
                    type = com.example.watchorderengine.data.model.NotificationType.RECOMMENDATION,
                    title = "Just for you",
                    message = "Based on your taste, we think you'll love ${topRec.media.title}.",
                    targetId = topRec.media.id,
                    imageUrl = topRec.media.posterUrl
                )
            }

            userPrefs.setLastSmartNotifTrigger(System.currentTimeMillis())
        }
    }

    private fun updateNextUp(watching: List<MediaSummary>) {
        viewModelScope.launch(Dispatchers.IO) {
            val mostRecent = watching.firstOrNull() ?: run {
                _nextUp.value = null
                return@launch
            }
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
                val trackedIds = repository.getAllTrackedMediaIds().toList()
                if (trackedIds.isEmpty() && preferredGenres.isEmpty()) return@launch

                val trackedEntities = db.mediaDao().getByIds(trackedIds).associateBy { it.id }
                val trackedTmdbIds: Set<Int> = trackedEntities.values.map { it.tmdbId }.toSet()

                val trackedPairs = trackedIds.mapNotNull { id ->
                    val entity   = trackedEntities[id]                   ?: return@mapNotNull null
                    val progress = db.userProgressDao().getByMediaId(id) ?: return@mapNotNull null
                    entity to progress
                }

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
