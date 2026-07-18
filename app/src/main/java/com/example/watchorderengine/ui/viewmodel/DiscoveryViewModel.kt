package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Null = "All" / trending. Non-null = a genre category chip is active. */
    private val _activeCategory = MutableStateFlow<TmdbConfig.DiscoveryCategory?>(null)
    val activeCategory: StateFlow<TmdbConfig.DiscoveryCategory?> = _activeCategory.asStateFlow()

    val categories: List<TmdbConfig.DiscoveryCategory> = TmdbConfig.DISCOVERY_CATEGORIES

    private val _platformFilter = MutableStateFlow(PlatformFilterState())
    val platformFilter: StateFlow<PlatformFilterState> = _platformFilter.asStateFlow()

    private val _swipedIds = MutableStateFlow<Set<String>>(emptySet())
    val swipedIds: StateFlow<Set<String>> = _swipedIds.asStateFlow()

    val pagingData: Flow<PagingData<MediaSummary>> = combine(
        _activeCategory,
        _platformFilter,
        _swipedIds
    ) { category, filter, swiped ->
        repository.getDiscoveryStream(category, filter.selectedProviderIds)
            .map { pagingData ->
                pagingData.filter { it.id !in swiped }
            }
    }.flattenMerge()
        .cachedIn(viewModelScope)

    // A small buffer for the UI to show the "top" of the deck as a list for swiping
    // In a real tinder-like app with Paging 3, you'd usually transform the PagingData
    // to filter out swiped items.
    private val _currentDeck = MutableStateFlow<List<MediaSummary>>(emptyList())
    val discoveryDeck: StateFlow<List<MediaSummary>> = _currentDeck.asStateFlow()

    fun togglePlatform(platform: StreamingPlatform) {
        val current = _platformFilter.value.selectedProviderIds
        val platformIds = platform.providerIds.toSet()
        
        val updated = if (current.containsAll(platformIds)) {
            current - platformIds
        } else {
            current + platformIds
        }

        _platformFilter.value = _platformFilter.value.copy(selectedProviderIds = updated)
    }

    fun selectCategory(category: TmdbConfig.DiscoveryCategory?) {
        if (_activeCategory.value == category) return
        _activeCategory.value = category
    }

    fun handleSwipe(media: MediaSummary, action: SwipeAction) {
        viewModelScope.launch {
            when (action) {
                SwipeAction.WATCH -> repository.updateTrackingState(media.id, TrackingState.WATCHING)
                SwipeAction.PLAN  -> repository.updateTrackingState(media.id, TrackingState.PLANNED)
                SwipeAction.PAUSE -> repository.updateTrackingState(media.id, TrackingState.PAUSED)
                SwipeAction.SKIP  -> repository.markSkipped(media.id)
            }
            _swipedIds.value = _swipedIds.value + media.id
        }
    }

    fun dismissPermanently(media: MediaSummary) {
        viewModelScope.launch {
            repository.updateTrackingState(media.id, TrackingState.DROPPED)
            _swipedIds.value = _swipedIds.value + media.id
        }
    }

    fun resetDeck() {
        viewModelScope.launch {
            repository.clearSkipped()
            _swipedIds.value = emptySet()
            // Triggers a refresh of the paging source by changing a state
            val current = _activeCategory.value
            _activeCategory.value = null
            _activeCategory.value = current
        }
    }
}
