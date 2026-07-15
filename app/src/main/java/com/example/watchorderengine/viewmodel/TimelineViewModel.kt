package com.example.watchorderengine.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.data.cache.TmdbMetadataCache
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.data.repository.TmdbRepository
import com.example.watchorderengine.network.model.TmdbMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI Layer Models ──────────────────────────────────────────────────────────

/**
 * A fully-resolved node, ready to render. Bridges the domain model [MediaNode]
 * with ephemeral UI state (completion, spoiler, column position).
 */
data class DisplayNode(
    val node: MediaNode,
    val column: Int,
    val isCompleted: Boolean,
    val isSpoilerBlurred: Boolean,
    val metadata: TmdbFetchState
)

/**
 * A single horizontal "row" in the visual timeline — one topological level.
 * May contain multiple nodes if the graph branches at this level.
 */
data class TimelineRow(
    val level: Int,
    val nodes: List<DisplayNode>,
    val totalColumns: Int,
    val outgoing: List<GraphEngine.OutgoingConnection>
)

/** The sealed UI state hierarchy emitted by the ViewModel's StateFlow. */
sealed interface TimelineUiState {
    data object Loading : TimelineUiState

    data class Error(
        val message: String,
        val retryAction: (() -> Unit)? = null
    ) : TimelineUiState

    data class Success(
        val universe: Universe,
        val nodes: List<MediaNode>,
        val edges: List<Edge>,
        val rows: List<TimelineRow>,
        val availableTags: List<ContextTag>,
        val activeRouteTag: String,
        val spoilerShieldEnabled: Boolean,
        val completedCount: Int,
        val totalNodeCount: Int
    ) : TimelineUiState {
        val progressFraction: Float
            get() = if (totalNodeCount == 0) 0f
            else completedCount.toFloat() / totalNodeCount
    }
}

/** One-shot side effects (toasts, navigation). */
sealed interface TimelineEvent {
    data class ShowSnackbar(val message: String) : TimelineEvent
    data class NavigateToDetail(val mediaId: String) : TimelineEvent
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WatchOrderRepository,
    private val mediaRepository: MediaRepository,
    private val tmdbRepo: TmdbRepository,
    private val tmdbCache: TmdbMetadataCache
) : ViewModel() {

    private val optimisticOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _uiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _events = Channel<TimelineEvent>(Channel.BUFFERED)
    val events: Flow<TimelineEvent> = _events.receiveAsFlow()

    private var currentUniverseId: String = ""
    private var initializationJob: Job? = null

    // ─── Initialization ───────────────────────────────────────────────────────

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun initialize(universeId: String) {
        if (currentUniverseId == universeId && initializationJob?.isActive == true) return

        initializationJob?.cancel()
        currentUniverseId = universeId
        _uiState.value = TimelineUiState.Loading

        initializationJob = viewModelScope.launch {
            val graphDataFlow = combine(
                repository.getUniverse(universeId),
                repository.getNodes(universeId),
                repository.getEdges(universeId)
            ) { universe, nodes, edges -> Triple(universe, nodes, edges) }

            combine(
                graphDataFlow,
                repository.getUserProgress(universeId),
                repository.getContextTags(universeId),
                mediaRepository.observeCompletedMediaIds(),
                optimisticOverrides
            ) { (universe, nodes, edges), progress, tags, localCompleted, overrides ->
                DataSnapshot(universe, nodes, edges, progress, tags, localCompleted, overrides)
            }
                .flatMapLatest { snapshot ->
                    flow {
                        emit(computeUiState(snapshot))
                        val uncached = tmdbCache.filterUncached(snapshot.nodes)
                        if (uncached.isNotEmpty()) {
                            tmdbRepo.fetchAndCache(snapshot.nodes)
                            emit(computeUiState(snapshot))
                        }
                    }
                }
                .catch { throwable ->
                    _uiState.value = TimelineUiState.Error(
                        message = throwable.message ?: "An unknown error occurred.",
                        retryAction = { initialize(universeId) }
                    )
                }
                .collect { state -> _uiState.value = state }
        }
    }

    private data class DataSnapshot(
        val universe: Universe,
        val nodes: List<MediaNode>,
        val edges: List<Edge>,
        val progress: UserProgress,
        val tags: List<ContextTag>,
        val localCompleted: Set<String>,
        val overrides: Map<String, Boolean>
    )

    private fun computeUiState(snapshot: DataSnapshot) = computeUiState(
        snapshot.universe, snapshot.nodes, snapshot.edges,
        snapshot.progress, snapshot.tags, snapshot.localCompleted, snapshot.overrides
    )

    // ─── Core Computation ─────────────────────────────────────────────────────

    private fun computeUiState(
        universe: Universe,
        nodes: List<MediaNode>,
        edges: List<Edge>,
        progress: UserProgress,
        tags: List<ContextTag>,
        localCompleted: Set<String>,
        overrides: Map<String, Boolean>
    ): TimelineUiState {
        if (nodes.isEmpty()) {
            return TimelineUiState.Error("No content found in this universe.")
        }

        val filteredNodes = GraphEngine.applyRouteFilter(nodes, progress.active_route ?: "ALL")
        val filteredNodeIds = filteredNodes.map { it.id }.toSet()
        val filteredEdges = edges.filter {
            it.from_node_id in filteredNodeIds && it.to_node_id in filteredNodeIds
        }

        val layout = try {
            GraphEngine.computeLayout(filteredNodes, filteredEdges)
        } catch (e: Exception) {
            return TimelineUiState.Error("Layout error: ${e.message}")
        }
        
        if (layout.isCycleDetected) {
            viewModelScope.launch {
                _events.send(TimelineEvent.ShowSnackbar(
                    "Note: AI generated a complex timeline with cycles. Some connections were automatically adjusted."
                ))
            }
        }

        val nodeById = filteredNodes.associateBy { it.id }
        val sortedNodes = layout.sortedIds.mapNotNull { nodeById[it] }

        val effectiveCompleted = buildEffectiveCompleted(
            firestoreCompleted = progress.completed_node_ids.toSet(),
            localCompleted = localCompleted,
            nodes = nodes,
            overrides = overrides
        )

        val blurredIds = if (progress.spoiler_shield_enabled) {
            GraphEngine.computeSpoilerShield(sortedNodes, filteredEdges, effectiveCompleted)
        } else emptySet()

        val displayNodes = sortedNodes.map { mediaNode ->
            val metadata = tmdbCache.getOrLoading(mediaNode.tmdb_id)
            
            // Refined Fallback: handle blank or "null" strings
            val rawTitle = mediaNode.title.trim()
            val titleWithFallback = if ((rawTitle.isBlank() || rawTitle.lowercase() == "null") && metadata is TmdbFetchState.Success) {
                metadata.detail.title
            } else {
                rawTitle
            }

            val updatedNode = if (mediaNode.posterUrl.isNullOrBlank() && metadata is TmdbFetchState.Success) {
                mediaNode.copy(
                    posterUrl = metadata.detail.posterUrl,
                    title = titleWithFallback
                )
            } else {
                mediaNode.copy(title = titleWithFallback)
            }

            // Ensure tmdb_media_type is also fixed if missing
            val fixedNode = if (updatedNode.tmdb_media_type.isBlank() && metadata is TmdbFetchState.Success) {
                updatedNode.copy(tmdb_media_type = if (metadata.detail.mediaType == TmdbMediaType.MOVIE) "movie" else "tv")
            } else updatedNode

            DisplayNode(
                node = fixedNode,
                column = layout.columnMap[mediaNode.id] ?: 0,
                isCompleted = mediaNode.id in effectiveCompleted,
                isSpoilerBlurred = mediaNode.id in blurredIds,
                metadata = metadata
            )
        }

        val enrichedNodes = displayNodes.map { it.node }

        val displayNodeMeta = displayNodes.associate { it.node.id to (it.column to it.isCompleted) }
        val connections = GraphEngine.computeConnections(
            displayNodeMap = displayNodeMeta,
            edges = filteredEdges,
            levelMap = layout.levelMap
        )

        val rows = displayNodes
            .groupBy { layout.levelMap[it.node.id] ?: 0 }
            .entries
            .sortedBy { it.key }
            .map { (level, nodesAtLevel) ->
                TimelineRow(
                    level = level,
                    nodes = nodesAtLevel.sortedBy { it.column },
                    totalColumns = layout.maxColumns,
                    outgoing = connections[level] ?: emptyList()
                )
            }

        return TimelineUiState.Success(
            universe = universe,
            nodes = enrichedNodes,
            edges = edges,
            rows = rows,
            availableTags = tags,
            activeRouteTag = progress.active_route ?: "ALL",
            spoilerShieldEnabled = progress.spoiler_shield_enabled,
            completedCount = effectiveCompleted.intersect(filteredNodeIds).size,
            totalNodeCount = filteredNodes.size
        )
    }

    private fun buildEffectiveCompleted(
        firestoreCompleted: Set<String>,
        localCompleted: Set<String>,
        nodes: List<MediaNode>,
        overrides: Map<String, Boolean>
    ): Set<String> = buildSet {
        // 1. Start with Firestore state
        addAll(firestoreCompleted)

        // 2. Add anything that is marked COMPLETED in local Room DB.
        // We check both the node.id (Firestore) and resolveMediaId(node) (Room).
        nodes.forEach { node ->
            val resolvedId = resolveMediaId(node)
            if (node.id in localCompleted || resolvedId in localCompleted) {
                add(node.id)
            }
        }

        // 3. Apply optimistic UI overrides
        overrides.forEach { (id, isCompleted) ->
            if (isCompleted) add(id) else remove(id)
        }
    }

    // ─── User Actions ─────────────────────────────────────────────────────────

    fun toggleNodeCompletion(nodeId: String, currentlyCompleted: Boolean) {
        val newState = !currentlyCompleted
        optimisticOverrides.update { it + (nodeId to newState) }

        // Find the node to resolve its Room mediaId
        val successState = uiState.value as? TimelineUiState.Success
        val targetNode = successState?.rows?.flatMap { it.nodes }?.find { it.node.id == nodeId }?.node
        val resolvedMediaId = targetNode?.let { resolveMediaId(it) }

        viewModelScope.launch {
            try {
                if (newState) _isSyncing.value = true

                // FIX: Ensure metadata is cached locally before marking as completed
                // to avoid "Unknown Movie" labels in the watchlist.
                if (newState && targetNode != null) {
                    mediaRepository.ensureMetadataCached(targetNode)
                }
                
                // 1. Sync to Firestore (Watch Order status)
                val result = repository.setNodeCompletion(currentUniverseId, nodeId, newState, context)
                
                // 2. Sync to Room (Media Tracking status)
                resolvedMediaId?.let { mediaId ->
                    if (newState) {
                        mediaRepository.updateTrackingState(mediaId, TrackingState.COMPLETED)
                        mediaRepository.markAllAsWatched(mediaId)
                    } else {
                        mediaRepository.removeFromWatchlist(mediaId)
                    }
                }

                optimisticOverrides.update { it - nodeId }

                if (result.isFailure) {
                    _events.send(
                        TimelineEvent.ShowSnackbar("Couldn't save progress. Queued for offline sync.")
                    )
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun setActiveRoute(routeTag: String) {
        viewModelScope.launch {
            repository.setActiveRoute(currentUniverseId, routeTag).onFailure {
                _events.send(TimelineEvent.ShowSnackbar("Couldn't save route preference."))
            }
        }
    }

    fun toggleSpoilerShield() {
        val currentEnabled = (_uiState.value as? TimelineUiState.Success)
            ?.spoilerShieldEnabled ?: true
        viewModelScope.launch {
            repository.setSpoilerShieldEnabled(currentUniverseId, !currentEnabled).onFailure {
                _events.send(TimelineEvent.ShowSnackbar("Couldn't save spoiler shield preference."))
            }
        }
    }

    /**
     * BUG FIX: Navigate using tmdb_id + tmdb_media_type, NOT node.id.
     */
    fun onNodeClick(node: MediaNode) {
        val mediaId = resolveMediaId(node)
        viewModelScope.launch {
            _events.send(TimelineEvent.NavigateToDetail(mediaId))
        }
    }

    companion object {
        /**
         * Builds the Room-compatible media ID from a MediaNode's TMDB fields.
         */
        fun resolveMediaId(node: MediaNode): String {
            if (node.tmdb_id <= 0) return node.id

            val prefix = when (node.tmdb_media_type.lowercase().trim()) {
                "movie" -> "tmdb_m_"
                "tv", "anime", "ova", "ona", "special" -> "tmdb_t_"
                else -> {
                    when (node.content_type.uppercase()) {
                        "MOVIE", "SHORT" -> "tmdb_m_"
                        else             -> "tmdb_t_"
                    }
                }
            }
            return "$prefix${node.tmdb_id}"
        }
    }
}
