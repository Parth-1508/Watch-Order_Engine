package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.network.TmdbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the user did to a card — distinct from the swipe gesture itself, see [SwipeAction]. */
enum class SwipeAction { WATCH, SKIP, PLAN, PAUSE }

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _discoveryDeck = MutableStateFlow<List<MediaSummary>>(emptyList())
    val discoveryDeck: StateFlow<List<MediaSummary>> = _discoveryDeck.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Null = "All" / trending. Non-null = a genre category chip is active. */
    private val _activeCategory = MutableStateFlow<TmdbConfig.DiscoveryCategory?>(null)
    val activeCategory: StateFlow<TmdbConfig.DiscoveryCategory?> = _activeCategory.asStateFlow()

    val categories: List<TmdbConfig.DiscoveryCategory> = TmdbConfig.DISCOVERY_CATEGORIES

    init {
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

            val raw = _activeCategory.value?.let { repository.discoverByGenre(it) }
                ?: repository.getTrending()

            // Exclude anything the user has already made a real decision about
            // (any of the 5 tracking states) AND anything they've temporarily
            // skipped this session — previously NOTHING was excluded, so
            // already-tracked shows kept resurfacing in the deck.
            val trackedIds = repository.getAllTrackedMediaIds()
            val skippedIds = repository.getSkippedMediaIds()

            _discoveryDeck.value = raw.filter { it.id !in trackedIds && it.id !in skippedIds }
            _isLoading.value = false
        }
    }

    /**
     * Applies a swipe decision. WATCH/PLAN/PAUSE write a real, permanent
     * TrackingState. SKIP is intentionally NOT a tracking state — it only
     * goes into the temporary skip table, so it can resurface after
     * [resetDeck] instead of being gone forever like a real decision.
     */
    fun handleSwipe(media: MediaSummary, action: SwipeAction) {
        viewModelScope.launch {
            when (action) {
                SwipeAction.WATCH -> repository.updateTrackingState(media.id, TrackingState.WATCHING)
                SwipeAction.PLAN  -> repository.updateTrackingState(media.id, TrackingState.PLANNED)
                SwipeAction.PAUSE -> repository.updateTrackingState(media.id, TrackingState.PAUSED)
                SwipeAction.SKIP  -> repository.markSkipped(media.id)
            }
            _discoveryDeck.value = _discoveryDeck.value.filter { it.id != media.id }
        }
    }

    /**
     * Explicit permanent dismissal ("Not Interested" / X button) — distinct
     * from a left-swipe Skip. This DOES write TrackingState.DROPPED, so
     * unlike Skip it survives [resetDeck] and never resurfaces here, though
     * it's still visible/manageable from the Dropped section on Home.
     */
    fun dismissPermanently(media: MediaSummary) {
        viewModelScope.launch {
            repository.updateTrackingState(media.id, TrackingState.DROPPED)
            _discoveryDeck.value = _discoveryDeck.value.filter { it.id != media.id }
        }
    }

    /** Clears temporary skips and reloads — the only way skipped shows resurface, by design (no auto-resurface). */
    fun resetDeck() {
        viewModelScope.launch {
            repository.clearSkipped()
            loadDiscovery()
        }
    }
}
