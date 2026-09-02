package com.example.watchorderengine.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Search-sheet state for the "add a title" flow. */
sealed interface AddNodeSearchState {
    object Idle : AddNodeSearchState
    object Loading : AddNodeSearchState
    data class Results(val items: List<MediaSummary>) : AddNodeSearchState
    data class Error(val message: String) : AddNodeSearchState
}

/**
 * Everything GraphBuilderScreen needs to render the live DAG, in one place —
 * recomputed reactively whenever nodes or edges change.
 */
data class GraphBuilderState(
    val nodes: List<MediaNode> = emptyList(),
    val edges: List<Edge> = emptyList(),
    val nodePositions: Map<String, Offset> = emptyMap(),
) {
    val layout: GraphEngine.GraphLayout get() = GraphEngine.computeLayout(nodes, edges)

    val canPublish: Boolean
        get() = nodes.size >= 2 && edges.isNotEmpty() && !layout.isCycleDetected
}

@HiltViewModel
class GraphBuilderViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _nodes = MutableStateFlow<List<MediaNode>>(emptyList())
    private val _edges = MutableStateFlow<List<Edge>>(emptyList())
    private val _nodePositions = MutableStateFlow<Map<String, Offset>>(emptyMap())

    val state: StateFlow<GraphBuilderState> = combine(
        _nodes, _edges, _nodePositions
    ) { nodes, edges, positions ->
        GraphBuilderState(nodes, edges, positions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphBuilderState())

    // ── Link mode (connect-two-nodes-by-tapping) ────────────────────────────

    private val _linkModeActive = MutableStateFlow(false)
    val linkModeActive: StateFlow<Boolean> = _linkModeActive.asStateFlow()

    private val _pendingFromNodeId = MutableStateFlow<String?>(null)
    val pendingFromNodeId: StateFlow<String?> = _pendingFromNodeId.asStateFlow()

    fun toggleLinkMode() {
        _linkModeActive.value = !_linkModeActive.value
        _pendingFromNodeId.value = null
    }

    fun onNodeTappedForLink(nodeId: String) {
        val pending = _pendingFromNodeId.value
        when {
            pending == null -> _pendingFromNodeId.value = nodeId
            pending == nodeId -> _pendingFromNodeId.value = null
            else -> {
                addEdge(pending, nodeId)
                _pendingFromNodeId.value = null
            }
        }
    }

    private fun addEdge(fromId: String, toId: String) {
        val exists = _edges.value.any { it.from_node_id == fromId && it.to_node_id == toId }
        if (!exists) {
            _edges.value = _edges.value + Edge(from_node_id = fromId, to_node_id = toId)
        }
    }

    fun removeEdge(edge: Edge) {
        _edges.value = _edges.value - edge
    }

    // ── Node positions (drag-to-arrange) ─────────────────────────────────────

    fun updateNodePosition(nodeId: String, offset: Offset) {
        _nodePositions.value = _nodePositions.value + (nodeId to offset)
    }

    /**
     * Applies one incremental drag step for [nodeId].
     * Reads state fresh inside ViewModel to avoid stale captured closures.
     */
    fun applyNodeDrag(
        nodeId: String,
        delta: Offset,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        nodeWidthPx: Float,
        nodeHeightPx: Float,
    ) {
        val current = _nodePositions.value[nodeId] ?: Offset.Zero
        val newX = (current.x + delta.x).coerceIn(0f, (canvasWidthPx - nodeWidthPx).coerceAtLeast(0f))
        val newY = (current.y + delta.y).coerceIn(0f, (canvasHeightPx - nodeHeightPx).coerceAtLeast(0f))
        _nodePositions.value = _nodePositions.value + (nodeId to Offset(newX, newY))
    }

    // ── Adding / removing nodes ───────────────────────────────────────────────

    private fun nextDefaultPosition(): Offset {
        val index = _nodes.value.size
        val col = index % 3
        val row = index / 3
        return Offset(x = 24f + col * 300f, y = 24f + row * 260f)
    }

    fun addNode(summary: MediaSummary) {
        if (_nodes.value.any { it.id == summary.id }) return

        val node = MediaNode(
            id              = summary.id,
            title           = summary.title,
            content_type    = if (summary.mediaCategory.name == "MOVIE") "MOVIE" else "SERIES",
            type            = summary.mediaCategory,
            tmdb_id         = summary.tmdbId,
            tmdb_media_type = if (summary.mediaCategory.name == "MOVIE") "movie" else "tv",
            releaseYear     = summary.releaseYear.toIntOrNull() ?: 0,
            posterUrl       = summary.posterUrl,
            parentMediaId   = summary.id,
            seasonNumber    = -1,
        )
        _nodes.value = _nodes.value + node
        _nodePositions.value = _nodePositions.value + (node.id to nextDefaultPosition())
    }

    fun removeNode(nodeId: String) {
        _nodes.value = _nodes.value.filterNot { it.id == nodeId }
        _edges.value = _edges.value.filterNot { it.from_node_id == nodeId || it.to_node_id == nodeId }
        _nodePositions.value = _nodePositions.value - nodeId
        if (_pendingFromNodeId.value == nodeId) _pendingFromNodeId.value = null
    }

    fun clearBoard() {
        _nodes.value = emptyList()
        _edges.value = emptyList()
        _nodePositions.value = emptyMap()
        _pendingFromNodeId.value = null
        _linkModeActive.value = false
    }

    // ── Add-node search sheet ───────────────────────────────────────────────

    private val _searchState = MutableStateFlow<AddNodeSearchState>(AddNodeSearchState.Idle)
    val searchState: StateFlow<AddNodeSearchState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchState.value = AddNodeSearchState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            _searchState.value = AddNodeSearchState.Loading
            delay(350)
            try {
                val results = mediaRepository.searchMedia(query)
                _searchState.value = AddNodeSearchState.Results(results)
            } catch (e: Exception) {
                _searchState.value = AddNodeSearchState.Error(e.message ?: "Search failed.")
            }
        }
    }

    fun resetSearch() {
        searchJob?.cancel()
        _searchState.value = AddNodeSearchState.Idle
    }
}
