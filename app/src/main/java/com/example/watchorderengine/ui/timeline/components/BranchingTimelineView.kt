package com.example.watchorderengine.ui.timeline.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.viewmodel.DisplayNode
import com.example.watchorderengine.viewmodel.TimelineRow

// ─── Dimension Constants ──────────────────────────────────────────────────────

private val COLUMN_WIDTH: Dp = 92.dp
private val COLUMN_GAP: Dp = 20.dp
private val CONNECTOR_STRIP_HEIGHT: Dp = 64.dp
private val ROW_VERTICAL_PADDING: Dp = 12.dp

private const val CONNECTOR_STROKE_IDLE = 1.5f
private const val CONNECTOR_STROKE_DONE = 2.5f
private const val CONNECTOR_STROKE_ON_PATH = 3f
private const val CONNECTOR_ALPHA_DIMMED = 0.12f
private const val CONNECTOR_CORNER_PX = 14f

// ─── Main Composable ──────────────────────────────────────────────────────────

/**
 * Renders the watch-order DAG as a simple, fixed-scale vertical timeline.
 * No pinch-zoom, no pan gestures to fight with — the graph is always laid
 * out at a legible size and scrolls naturally (vertically, and horizontally
 * only if a row genuinely has more branches than fit on screen).
 *
 * ## Path Highlight
 * Tapping a node traces its direct prerequisite chain backward through the
 * DAG ([GraphEngine.computeAncestorPath]) and dims everything outside that
 * chain, so a massive branching universe collapses down to "here's exactly
 * what you need to watch to reach this title." Tapping the same node again
 * (or the "Clear" action on the banner that appears) returns to normal.
 * Navigating to the tapped node's detail page happens via that banner's
 * "Open" action — [onNodeClick] fires only from there, never from a bare
 * tap, since a bare tap's job is now to focus/unfocus the path.
 * Long-press still marks a node watched regardless of focus state.
 */
@Composable
fun BranchingTimelineView(
    rows: List<TimelineRow>,
    edges: List<Edge>,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val columnWidthPx = with(density) { COLUMN_WIDTH.toPx() }
    val columnGapPx = with(density) { COLUMN_GAP.toPx() }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    var focusedNodeId by remember { mutableStateOf<String?>(null) }

    // Reset focus if the underlying graph changes shape (route filter
    // switched, a node got removed, etc.) so we never highlight a path
    // against edges that no longer describe the visible graph.
    LaunchedEffect(rows) {
        if (focusedNodeId != null && rows.flatMap { it.nodes }.none { it.node.id == focusedNodeId }) {
            focusedNodeId = null
        }
    }

    val pathHighlight = remember(focusedNodeId, edges) {
        focusedNodeId?.let { GraphEngine.computeAncestorPath(it, edges) }
    }

    val focusedDisplayNode = remember(focusedNodeId, rows) {
        focusedNodeId?.let { id -> rows.flatMap { it.nodes }.find { it.node.id == id } }
    }

    fun pathStateFor(nodeId: String): NodePathState = when {
        pathHighlight == null -> NodePathState.NORMAL
        nodeId == focusedNodeId -> NodePathState.FOCUSED
        nodeId in pathHighlight.nodeIds -> NodePathState.ON_PATH
        else -> NodePathState.DIMMED
    }

    fun handleNodeTap(node: DisplayNode) {
        focusedNodeId = if (focusedNodeId == node.node.id) null else node.node.id
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Column(
            modifier = Modifier
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            rows.forEachIndexed { index, row ->
                TimelineRowView(
                    row          = row,
                    pathStateFor = ::pathStateFor,
                    onNodeToggle = onNodeToggle,
                    onNodeClick  = ::handleNodeTap
                )

                if (index < rows.lastIndex && row.outgoing.isNotEmpty()) {
                    ConnectorStrip(
                        connections   = row.outgoing,
                        totalColumns  = row.totalColumns,
                        columnWidthPx = columnWidthPx,
                        columnGapPx   = columnGapPx,
                        pathEdgeIds   = pathHighlight?.edgeIds,
                        modifier      = Modifier
                            .width(rowContentWidth(row.totalColumns))
                            .height(CONNECTOR_STRIP_HEIGHT)
                    )
                } else if (index < rows.lastIndex) {
                    Spacer(Modifier.height(CONNECTOR_STRIP_HEIGHT / 2))
                }
            }
        }

        FocusedPathBanner(
            focusedNode = focusedDisplayNode,
            onOpen = { node -> onNodeClick(node) },
            onClear = { focusedNodeId = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private fun rowContentWidth(totalColumns: Int): Dp {
    if (totalColumns <= 0) return COLUMN_WIDTH
    return COLUMN_WIDTH * totalColumns + COLUMN_GAP * (totalColumns - 1)
}

// ─── Focused Path Banner ──────────────────────────────────────────────────────

/**
 * Small pill that appears while a node is focused, naming it and offering
 * the two actions a bare tap no longer performs: opening its detail page,
 * or clearing the focus to return the timeline to normal.
 */
@Composable
private fun FocusedPathBanner(
    focusedNode: DisplayNode?,
    onOpen: (DisplayNode) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    AnimatedVisibility(
        visible = focusedNode != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.padding(top = 12.dp)
    ) {
        if (focusedNode == null) return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.4f)),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Path to ${focusedNode.node.title.ifBlank { "this title" }}",
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = theme.accent,
                    modifier = Modifier.clickable { onOpen(focusedNode) }
                ) {
                    Text(
                        "Open",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClickLabel = "Clear focused path") { onClear() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Row View ─────────────────────────────────────────────────────────────────

@Composable
private fun TimelineRowView(
    row: TimelineRow,
    pathStateFor: (String) -> NodePathState,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit
) {
    val nodeByColumn = row.nodes.associateBy { it.column }

    Row(
        modifier = Modifier.padding(vertical = ROW_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        verticalAlignment = Alignment.Top
    ) {
        for (columnIndex in 0 until row.totalColumns) {
            val displayNode = nodeByColumn[columnIndex]

            if (displayNode != null) {
                key(displayNode.node.id) {
                    TimelineNodeCard(
                        displayNode = displayNode,
                        onCheckToggle = { onNodeToggle(displayNode) },
                        onCardClick = { onNodeClick(displayNode) },
                        pathState = pathStateFor(displayNode.node.id),
                        modifier = Modifier.width(COLUMN_WIDTH)
                    )
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .width(COLUMN_WIDTH)
                        .height(1.dp)
                )
            }
        }
    }
}

// ─── Connector Strip Canvas ───────────────────────────────────────────────────

/**
 * Draws clean orthogonal (elbow) connectors between rows instead of crossing
 * bezier curves — a straight drop, a rounded corner, a straight run, another
 * rounded corner, then a straight drop into the next node. Reads clearly even
 * when several branches merge or split at once.
 *
 * When Path Highlight is active ([pathEdgeIds] non-null), connectors on the
 * traced path draw thicker and in a solid accent color; everything else
 * fades to near-invisible so the prerequisite chain reads as a single clear
 * line through the graph instead of competing with every other branch.
 */
@Composable
private fun ConnectorStrip(
    connections: List<GraphEngine.OutgoingConnection>,
    totalColumns: Int,
    columnWidthPx: Float,
    columnGapPx: Float,
    pathEdgeIds: Set<Pair<String, String>>?,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val colorCompleted = theme.statusCanon
    val colorPending    = theme.textSecondary.copy(alpha = 0.3f)
    val colorOnPath     = theme.accent
    val dotCompleted    = theme.statusCanon
    val dotPending      = theme.border.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        if (totalColumns == 0) return@Canvas

        fun columnCenterX(columnIndex: Int): Float =
            columnIndex * (columnWidthPx + columnGapPx) + columnWidthPx / 2f

        val midY = size.height / 2f

        for (connection in connections) {
            val isOnPath = pathEdgeIds?.contains(connection.fromNodeId to connection.toNodeId) == true
            val isDimmed = pathEdgeIds != null && !isOnPath

            val startX = columnCenterX(connection.fromColumn)
            val endX   = columnCenterX(connection.toColumn)
            val startY = 0f
            val endY   = size.height

            val baseColor = if (connection.isFromNodeCompleted) colorCompleted else colorPending
            val lineColor = when {
                isOnPath -> colorOnPath
                isDimmed -> baseColor.copy(alpha = CONNECTOR_ALPHA_DIMMED)
                else     -> baseColor
            }
            val strokeWidth = when {
                isOnPath -> CONNECTOR_STROKE_ON_PATH
                connection.isFromNodeCompleted -> CONNECTOR_STROKE_DONE
                else -> CONNECTOR_STROKE_IDLE
            }

            val path = Path().apply {
                moveTo(startX, startY)
                if (startX == endX) {
                    lineTo(endX, endY)
                } else {
                    val dir = if (endX > startX) 1f else -1f
                    val corner = CONNECTOR_CORNER_PX
                    lineTo(startX, midY - corner)
                    quadraticTo(startX, midY, startX + corner * dir, midY)
                    lineTo(endX - corner * dir, midY)
                    quadraticTo(endX, midY, endX, midY + corner)
                    lineTo(endX, endY)
                }
            }

            drawPath(
                path  = path,
                color = lineColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val dotColor  = when {
                isOnPath -> colorOnPath
                isDimmed -> (if (connection.isFromNodeCompleted) dotCompleted else dotPending).copy(alpha = CONNECTOR_ALPHA_DIMMED)
                connection.isFromNodeCompleted -> dotCompleted
                else -> dotPending
            }
            val dotRadius = if (isOnPath || connection.isFromNodeCompleted) 4f else 2.5f

            drawCircle(color = dotColor, radius = dotRadius, center = Offset(startX, startY))
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(endX, endY))
        }
    }
}
