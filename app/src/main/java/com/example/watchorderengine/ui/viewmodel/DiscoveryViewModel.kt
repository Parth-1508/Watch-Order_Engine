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
    // Standard TMDB image fetcher
    private fun logo(path: String) = "https://image.tmdb.org/t/p/w92$path"

    // Fallback dynamic icon fetcher
    private fun favicon(domain: String) = "https://www.google.com/s2/favicons?domain=$domain&sz=128"

    val ALL: List<StreamingPlatform> = listOf(
        // Stable TMDB hashes
        StreamingPlatform(listOf(8),    "Netflix",      logo("/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg")),
        StreamingPlatform(listOf(119),  "Prime Video",  logo("/dQeAar5H991VYporEjUspolDarG.jpg")),
        StreamingPlatform(listOf(350),  "Apple TV+",    logo("/peURlLlr8jggOwK53fJ5wdQl05y.jpg")),
        StreamingPlatform(listOf(220, 122, 337), "JioCinema",    logo("/boMYreJ9JWNDnXiHUfoix4oYhBc.jpg")),

        // Stable Google Favicon links to completely bypass TMDB's 404 errors
        StreamingPlatform(listOf(283),  "Crunchyroll",  favicon("crunchyroll.com")),
        StreamingPlatform(listOf(232),  "Zee5",         favicon("zee5.com"))
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
    val discoveryDeck: StateFlow<List<MediaSummary>> = _rawDeck.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Null = "All" / trending. Non-null = a genre category chip is active. */
    private val _activeCategory = MutableStateFlow<TmdbConfig.DiscoveryCategory?>(null)
    val activeCategory: StateFlow<TmdbConfig.DiscoveryCategory?> = _activeCategory.asStateFlow()

    val categories: List<TmdbConfig.DiscoveryCategory> = TmdbConfig.DISCOVERY_CATEGORIES

    private val _platformFilter = MutableStateFlow(PlatformFilterState())
    val platformFilter: StateFlow<PlatformFilterState> = _platformFilter.asStateFlow()

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
            // Extract raw TMDB IDs to ensure legacy/prefixed IDs are caught
            val trackedTmdbIds = trackedIds.mapNotNull { it.substringAfterLast("_").toIntOrNull() }.toSet()
            
            val skippedIds = repository.getSkippedMediaIds()

            val filtered = raw.filter { 
                it.id !in trackedIds && 
                it.tmdbId !in trackedTmdbIds &&
                it.id !in skippedIds 
            }

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
            // Immediately remove from UI list
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
