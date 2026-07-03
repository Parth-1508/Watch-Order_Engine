package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.network.TmdbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Streaming Platform Model ─────────────────────────────────────────────────

data class StreamingPlatform(
    val providerIds: List<Int>,
    val displayName: String,
    val logoUrl: String,
)

object StreamingPlatforms {
    private fun logo(path: String) = "https://image.tmdb.org/t/p/w92$path"

    val ALL: List<StreamingPlatform> = listOf(
        StreamingPlatform(listOf(8),    "Netflix",      logo("/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg")),
        StreamingPlatform(listOf(119),  "Prime Video",  logo("/dQeAar5H991VYporEjUspolDarG.jpg")),
        StreamingPlatform(listOf(220, 122, 337), "JioCinema", logo("/9A1qXwN7qT3N8pG2pX4WdE2j4gX.png")),
        StreamingPlatform(listOf(350),  "Apple TV+",    logo("/peURlLlr8jggOwK53fJ5wdQl05y.jpg")),
        StreamingPlatform(listOf(283),  "Crunchyroll",  logo("/8Gt1iClBlzTeQs8WQm8UrCoInjx.png")),
        StreamingPlatform(listOf(232),  "Zee5",         logo("/kgd9I4vq3v3pKkMX3s0KQdnZDgw.png")),
    )
}

// ─── UI Filter State ──────────────────────────────────────────────────────────

data class PlatformFilterState(
    val availablePlatforms: List<StreamingPlatform> = StreamingPlatforms.ALL,
    val selectedProviderIds: Set<Int> = emptySet(),
) {
    val isFilterActive: Boolean get() = selectedProviderIds.isNotEmpty()
}

/** What the user did to a card — distinct from the swipe gesture itself, see [SwipeAction]. */
enum class SwipeAction { WATCH, SKIP, PLAN, PAUSE }

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _rawDeck = MutableStateFlow<List<MediaSummary>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Null = "All" / trending. Non-null = a genre category chip is active. */
    private val _activeCategory = MutableStateFlow<TmdbConfig.DiscoveryCategory?>(null)
    val activeCategory: StateFlow<TmdbConfig.DiscoveryCategory?> = _activeCategory.asStateFlow()

    val categories: List<TmdbConfig.DiscoveryCategory> = TmdbConfig.DISCOVERY_CATEGORIES

    private val _platformFilter = MutableStateFlow(PlatformFilterState())
    val platformFilter: StateFlow<PlatformFilterState> = _platformFilter.asStateFlow()

    val discoveryDeck: StateFlow<List<MediaSummary>> = _rawDeck.asStateFlow()

    init {
        loadDiscovery()
    }

    fun togglePlatform(platform: StreamingPlatform) {
        val current = _platformFilter.value.selectedProviderIds
        val platformIds = platform.providerIds.toSet()
        
        val updated = if (current.containsAll(platformIds)) {
            current - platformIds
        } else {
            current + platformIds
        }

        _platformFilter.value = _platformFilter.value.copy(selectedProviderIds = updated)
        loadDiscovery()
    }

    fun selectCategory(category: TmdbConfig.DiscoveryCategory?) {
        if (_activeCategory.value == category) return
        _activeCategory.value = category
        loadDiscovery()
    }

    fun loadDiscovery() {
        viewModelScope.launch {
            _isLoading.value = true

            val selectedProviders = _platformFilter.value.selectedProviderIds
            val raw = _activeCategory.value?.let { repository.discoverByGenre(it, selectedProviders) }
                ?: repository.getTrending(selectedProviders)

            val trackedIds = repository.getAllTrackedMediaIds()
            val skippedIds = repository.getSkippedMediaIds()

            val filtered = raw.filter { it.id !in trackedIds && it.id !in skippedIds }

            _rawDeck.value = filtered
            _isLoading.value = false
        }
    }

    fun handleSwipe(media: MediaSummary, action: SwipeAction) {
        viewModelScope.launch {
            when (action) {
                SwipeAction.WATCH -> repository.updateTrackingState(media.id, TrackingState.WATCHING)
                SwipeAction.PLAN  -> repository.updateTrackingState(media.id, TrackingState.PLANNED)
                SwipeAction.PAUSE -> repository.updateTrackingState(media.id, TrackingState.PAUSED)
                SwipeAction.SKIP  -> repository.markSkipped(media.id)
            }
            _rawDeck.value = _rawDeck.value.filter { it.id != media.id }
        }
    }

    fun dismissPermanently(media: MediaSummary) {
        viewModelScope.launch {
            repository.updateTrackingState(media.id, TrackingState.DROPPED)
            _rawDeck.value = _rawDeck.value.filter { it.id != media.id }
        }
    }

    fun resetDeck() {
        viewModelScope.launch {
            repository.clearSkipped()
            loadDiscovery()
        }
    }
}
