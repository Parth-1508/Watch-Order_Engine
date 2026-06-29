package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.WatchProviderItem
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.network.TmdbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Streaming Platform Model ─────────────────────────────────────────────────

data class StreamingPlatform(
    val providerId: Int,
    val displayName: String,
    val logoUrl: String,
)

object StreamingPlatforms {
    private fun logo(path: String) = "https://image.tmdb.org/t/p/w92$path"

    val ALL: List<StreamingPlatform> = listOf(
        StreamingPlatform(8,    "Netflix",      logo("/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg")),
        StreamingPlatform(119,  "Prime Video",  logo("/dQeAar5H991VYporEjUspolDarG.jpg")),
        StreamingPlatform(337,  "Disney+",      logo("/7rwgEs15tFwyR9NPQ5vjkL215Kj.jpg")),
        StreamingPlatform(350,  "Apple TV+",    logo("/peURlLlr8jggOwK53fJ5wdQl05y.jpg")),
        StreamingPlatform(15,   "Hulu",         logo("/zxrVdFjIjLqkfnwyghnfywTn3Lh.jpg")),
        StreamingPlatform(1899, "Max",          logo("/Ajqyt5aNxNx9GEU0Nyo5bJFMnTI.jpg")),
        StreamingPlatform(283,  "Crunchyroll",  logo("/8Gt1iClBlzTeQs8WQm8UrCoInjx.jpg")),
        StreamingPlatform(122,  "Hotstar",      logo("/xbhHHa1YgtpwhC8lb1NQ3ACVcLd.jpg")),
        StreamingPlatform(232,  "Zee5",         logo("/kgd9I4vq3v3pKkMX3s0KQdnZDgw.jpg")),
        StreamingPlatform(307,  "SonyLiv",      logo("/DOBsJLpNq59GNv5GrBUeVFgOSY.jpg")),
    )

    val BY_ID: Map<Int, StreamingPlatform> = ALL.associateBy { it.providerId }
}

// ─── UI Filter State ──────────────────────────────────────────────────────────

data class PlatformFilterState(
    val availablePlatforms: List<StreamingPlatform> = StreamingPlatforms.ALL,
    val selectedProviderIds: Set<Int> = emptySet(),
) {
    val isFilterActive: Boolean get() = selectedProviderIds.isNotEmpty()

    val filterSummary: String
        get() = if (!isFilterActive) "All Platforms"
        else selectedProviderIds
            .mapNotNull { StreamingPlatforms.BY_ID[it]?.displayName }
            .joinToString(", ")
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

    val discoveryDeck: StateFlow<List<MediaSummary>> =
        combine(_rawDeck, _platformFilter) { deck, filter ->
            if (!filter.isFilterActive) {
                deck
            } else {
                deck.filter { media ->
                    val providers = repository.getCachedWatchProviders(media.id)
                    providers.any { it.providerId in filter.selectedProviderIds }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        loadDiscovery()
    }

    fun togglePlatform(providerId: Int) {
        val current = _platformFilter.value.selectedProviderIds
        val updated = if (providerId in current) current - providerId else current + providerId
        _platformFilter.value = _platformFilter.value.copy(selectedProviderIds = updated)
    }

    fun clearPlatformFilters() {
        _platformFilter.value = _platformFilter.value.copy(selectedProviderIds = emptySet())
    }

    fun selectCategory(category: TmdbConfig.DiscoveryCategory?) {
        if (_activeCategory.value == category) return
        _activeCategory.value = category
        loadDiscovery()
    }

    fun loadDiscovery() {
        viewModelScope.launch {
            _isLoading.value = true

            val raw = _activeCategory.value?.let { repository.discoverByGenre(it) }
                ?: repository.getTrending()

            val trackedIds = repository.getAllTrackedMediaIds()
            val skippedIds = repository.getSkippedMediaIds()

            _rawDeck.value = raw.filter { it.id !in trackedIds && it.id !in skippedIds }
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
