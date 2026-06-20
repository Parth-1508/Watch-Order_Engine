package com.example.watchorderengine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.*
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.data.cache.TmdbMetadataCache
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI Layer Models ──────────────────────────────────────────────────────────

/**
 * A fully-resolved node, ready to render. Bridges the domain model [MediaNode]
 * with ephemeral UI state (completion, spoiler, column position).
 * This is deliberately NOT a Parcelable — it lives only in memory.
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
 * Also carries the outgoing connector lines TO the next row, used by Canvas.
 */
data class TimelineRow(
    val level: Int,
    val nodes: List<DisplayNode>,                          // Ordered by column index
    val totalColumns: Int,                                 // Total graph width (for layout)
    val outgoing: List<GraphEngine.OutgoingConnection>     // Lines drawn below this row
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

/** One-shot side effects (toasts, navigation). Separate from persistent state to avoid
 *  the "replay on recompose" problem inherent in StateFlow for events. */
sealed interface TimelineEvent {
    data class ShowSnackbar(val message: String) : TimelineEvent
    data class NavigateToDetail(val nodeId: String) : TimelineEvent
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Timeline screen.
 *
 * COMBINING STREAMS:
 * We combine 4 Firestore real-time streams + 1 in-memory optimistic override
 * stream using a nested combine approach (Kotlin's combine supports 5 flows,
 * but splitting into (3 → combined) then (+ 2) avoids the array-lambda form).
 *
 * Every time ANY stream emits a new value, [computeUiState] runs and produces
 * a fresh [TimelineUiState.Success]. This computation is pure and fast (~1ms
 * for 500-node universes), so no debouncing is needed.
 *
 * OPTIMISTIC UPDATES:
 * User taps checkbox → optimisticOverrides emits immediately → combine fires →
 * UI shows new state before Firestore confirms. On Firestore write completion,
 * override is cleared (success) or kept to revert (failure path clears too,
 * reverting to the Firestore-confirmed state).
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: WatchOrderRepository,
    private val tmdbRepo: TmdbRepository,
    private val tmdbCache: TmdbMetadataCache
) : ViewModel() {

    // ─── State ────────────────────────────────────────────────────────────────

    /** Holds in-flight user actions that haven't been confirmed by Firestore yet.
     *  nodeId → true (optimistically checked) or false (optimistically unchecked). */
    private val optimisticOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _uiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _events = Channel<TimelineEvent>(Channel.BUFFERED)
    val events: Flow<TimelineEvent> = _events.receiveAsFlow()

    // Cached universeId for use in action functions without re-passing it
    private var currentUniverseId: String = ""
    private var initializationJob: Job? = null

    // ─── Initialization ───────────────────────────────────────────────────────

    /**
     * Starts the observation pipeline. Call once from a LaunchedEffect(universeId)
     * in the composable. Safe to call multiple times — the previous job will
     * be cancelled if this is called with a different universeId.
     */
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
                optimisticOverrides
            ) { (universe, nodes, edges), progress, tags, overrides ->
                DataSnapshot(universe, nodes, edges, progress, tags, overrides)
            }
                .flatMapLatest { snapshot ->
                    flow {
                        // Emit initial state (with shimmers for uncached)
                        emit(computeUiState(snapshot))

                        // Fetch uncached metadata
                        val uncached = tmdbCache.filterUncached(snapshot.nodes)
                        if (uncached.isNotEmpty()) {
                            tmdbRepo.fetchAndCache(snapshot.nodes)
                            // Emit updated state with loaded metadata
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
        val overrides: Map<String, Boolean>
    )

    private fun computeUiState(snapshot: DataSnapshot): TimelineUiState {
        return computeUiState(
            snapshot.universe,
            snapshot.nodes,
            snapshot.edges,
            snapshot.progress,
            snapshot.tags,
            snapshot.overrides
        )
    }

    // ─── Core Computation ─────────────────────────────────────────────────────

    /**
     * Transforms raw Firestore data into the complete [TimelineUiState.Success].
     *
     * This is intentionally a PURE FUNCTION with no side effects.
     * It runs on every stream emission — keep it fast.
     */
    private fun computeUiState(
        universe: Universe,
        nodes: List<MediaNode>,
        edges: List<Edge>,
        progress: UserProgress,
        tags: List<ContextTag>,
        overrides: Map<String, Boolean>
    ): TimelineUiState {
        if (nodes.isEmpty()) {
            return TimelineUiState.Error("No content found in this universe.")
        }

        // ── STEP 1: Apply route filter ────────────────────────────────────────
        // Remove nodes not matching the active route (e.g., non-canon nodes
        // when the user has selected "Canon Only").
        val filteredNodes = GraphEngine.applyRouteFilter(nodes, progress.active_route ?: "ALL")
        val filteredNodeIds = filteredNodes.map { it.id }.toSet()

        // Also prune edges that reference nodes removed by the filter.
        // Dangling edges would confuse the topological sort.
        val filteredEdges = edges.filter {
            it.from_node_id in filteredNodeIds && it.to_node_id in filteredNodeIds
        }

        // ── STEP 2: Compute DAG layout ────────────────────────────────────────
        val layout = try {
            GraphEngine.computeLayout(filteredNodes, filteredEdges)
        } catch (e: IllegalStateException) {
            return TimelineUiState.Error("Data integrity error: ${e.message}")
        }

        // ── STEP 3: Sort nodes into topological order ─────────────────────────
        val nodeById = filteredNodes.associateBy { it.id }
        val sortedNodes = layout.sortedIds.mapNotNull { nodeById[it] }

        // ── STEP 4: Merge Firestore state with optimistic overrides ───────────
        val effectiveCompleted = buildEffectiveCompleted(
            firestoreCompleted = progress.completed_node_ids.toSet(),
            overrides = overrides
        )

        // ── STEP 5: Compute spoiler blur set ──────────────────────────────────
        val blurredIds = if (progress.spoiler_shield_enabled) {
            GraphEngine.computeSpoilerShield(sortedNodes, filteredEdges, effectiveCompleted)
        } else emptySet()

        // ── STEP 6: Build DisplayNode list ────────────────────────────────────
        val displayNodes = sortedNodes.map { mediaNode ->
            DisplayNode(
                node = mediaNode,
                column = layout.columnMap[mediaNode.id] ?: 0,
                isCompleted = mediaNode.id in effectiveCompleted,
                isSpoilerBlurred = mediaNode.id in blurredIds,
                metadata = tmdbCache.getOrLoading(mediaNode.tmdb_id)
            )
        }

        // ── STEP 7: Compute connector lines between levels ────────────────────
        val displayNodeMeta = displayNodes.associate { it.node.id to (it.column to it.isCompleted) }
        val connections = GraphEngine.computeConnections(
            displayNodeMap = displayNodeMeta,
            edges = filteredEdges,
            levelMap = layout.levelMap
        )

        // ── STEP 8: Group DisplayNodes into TimelineRows ──────────────────────
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
            rows = rows,
            availableTags = tags,
            activeRouteTag = progress.active_route ?: "ALL",
            spoilerShieldEnabled = progress.spoiler_shield_enabled,
            completedCount = effectiveCompleted.intersect(filteredNodeIds).size,
            totalNodeCount = filteredNodes.size
        )
    }

    /**
     * Merges Firestore-confirmed completions with in-flight optimistic overrides.
     * The override always wins — that's the point of optimistic updates.
     */
    private fun buildEffectiveCompleted(
        firestoreCompleted: Set<String>,
        overrides: Map<String, Boolean>
    ): Set<String> = buildSet {
        addAll(firestoreCompleted)
        overrides.forEach { (id, isCompleted) ->
            if (isCompleted) add(id) else remove(id)
        }
    }

    // ─── User-Initiated Actions ───────────────────────────────────────────────

    /**
     * Toggles a node's completion state with full optimistic update support.
     *
     * FLOW:
     *   1. Immediately add override → combine fires → UI shows new state (instant).
     *   2. Fire Firestore write in the background.
     *   3a. Success: clear the override. Firestore stream will emit the confirmed
     *       value, and removing the override is then a no-op (values agree).
     *   3b. Failure: clear the override, which reverts the UI to the Firestore
     *       (unchanged) value. Show an error snackbar.
     */
    fun toggleNodeCompletion(nodeId: String, currentlyCompleted: Boolean) {
        val newState = !currentlyCompleted

        // STEP 1: Optimistic update (zero latency)
        optimisticOverrides.update { current -> current + (nodeId to newState) }

        viewModelScope.launch {
            // STEP 2: Persist
            val result = repository.setNodeCompletion(currentUniverseId, nodeId, newState)

            // STEP 3: Clean up regardless of outcome
            // On success: Firestore stream has/will emit the confirmed value.
            // On failure: removing the override reverts the UI to the old Firestore value.
            optimisticOverrides.update { current -> current - nodeId }

            if (result.isFailure) {
                _events.send(
                    TimelineEvent.ShowSnackbar("Couldn't save progress. Check your connection.")
                )
            }
        }
    }

    /** Changes the active route filter and persists the preference. */
    fun setActiveRoute(routeTag: String) {
        viewModelScope.launch {
            repository.setActiveRoute(currentUniverseId, routeTag).onFailure {
                _events.send(TimelineEvent.ShowSnackbar("Couldn't save route preference."))
            }
        }
    }

    /** Toggles the spoiler shield on/off and persists the preference. */
    fun toggleSpoilerShield() {
        val currentEnabled = (_uiState.value as? TimelineUiState.Success)
            ?.spoilerShieldEnabled ?: true
        viewModelScope.launch {
            repository.setSpoilerShieldEnabled(currentUniverseId, !currentEnabled).onFailure {
                _events.send(TimelineEvent.ShowSnackbar("Couldn't save spoiler shield preference."))
            }
        }
    }

    /** Navigates to a node's detail screen (handled by nav host). */
    fun onNodeClick(nodeId: String) {
        val state = _uiState.value as? TimelineUiState.Success
        val node = state?.rows?.flatMap { it.nodes }?.find { it.node.id == nodeId }?.node
        val targetId = if (node != null && node.tmdb_id > 0) "tmdb_${node.tmdb_id}" else nodeId
        
        viewModelScope.launch {
            _events.send(TimelineEvent.NavigateToDetail(targetId))
        }
    }
}