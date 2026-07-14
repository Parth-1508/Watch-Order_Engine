package com.example.watchorderengine.data.cache

import androidx.compose.runtime.mutableStateMapOf
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.network.model.TmdbMediaDetail
import javax.inject.Inject
import javax.inject.Singleton

// ─── Cache State ──────────────────────────────────────────────────────────────

/**
 * Represents the state of a single TMDB metadata fetch for one node.
 */
sealed interface TmdbFetchState {
    data object Loading : TmdbFetchState
    data class Success(val detail: TmdbMediaDetail) : TmdbFetchState
    data class Error(val message: String, val tmdbId: Int) : TmdbFetchState
}

// ─── Cache ────────────────────────────────────────────────────────────────────

/**
 * Thread-safe in-memory cache for TMDB metadata, scoped to the application
 * lifetime (Hilt @Singleton).
 */
@Singleton
class TmdbMetadataCache @Inject constructor() {

    private val store = mutableStateMapOf<Int, TmdbFetchState>()

    /** Returns the current state for [tmdbId], or null if never fetched. */
    fun get(tmdbId: Int): TmdbFetchState? = store[tmdbId]

    /** Returns the current state, defaulting to [TmdbFetchState.Loading] if absent. */
    fun getOrLoading(tmdbId: Int): TmdbFetchState = store[tmdbId] ?: TmdbFetchState.Loading

    /** Stores a fetch result (Loading, Success, or Error) for the given [tmdbId]. */
    fun put(tmdbId: Int, state: TmdbFetchState) { store[tmdbId] = state }

    /**
     * Returns true if [tmdbId] has a [TmdbFetchState.Success] entry.
     */
    fun isSuccessfullyCached(tmdbId: Int): Boolean = store[tmdbId] is TmdbFetchState.Success

    /**
     * Filters [nodes] to only those that do NOT have a successful cache entry.
     */
    fun filterUncached(nodes: List<MediaNode>): List<MediaNode> =
        nodes.filter { !isSuccessfullyCached(it.tmdb_id) }

    /**
     * Builds a snapshot map of tmdbId → [TmdbFetchState] for a given list of nodes.
     */
    fun snapshotFor(nodes: List<MediaNode>): Map<Int, TmdbFetchState> =
        nodes.associate { it.tmdb_id to getOrLoading(it.tmdb_id) }

    /** Clears all cached entries. */
    fun invalidateAll() = store.clear()

    /** Clears a specific entry. */
    fun invalidate(tmdbId: Int) = store.remove(tmdbId)
}
