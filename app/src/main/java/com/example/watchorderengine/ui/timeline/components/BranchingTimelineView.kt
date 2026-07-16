package com.example.watchorderengine.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.viewmodel.DisplayNode
import com.example.watchorderengine.viewmodel.TimelineRow

// ─── Dimension Constants ──────────────────────────────────────────────────────

private val COLUMN_WIDTH: Dp = 92.dp
private val COLUMN_GAP: Dp = 20.dp
private val CONNECTOR_STRIP_HEIGHT: Dp = 64.dp
private val ROW_VERTICAL_PADDING: Dp = 12.dp

private const val CONNECTOR_STROKE_IDLE = 1.5f
private const val CONNECTOR_STROKE_DONE = 2.5f
private const val CONNECTOR_CORNER_PX = 14f

// ─── Main Composable ──────────────────────────────────────────────────────────

/**
 * Renders the watch-order DAG as a simple, fixed-scale vertical timeline.
 * No pinch-zoom, no pan gestures to fight with — the graph is always laid
 * out at a legible size and scrolls naturally (vertically, and horizontally
 * only if a row genuinely has more branches than fit on screen).
 */
@Composable
fun BranchingTimelineView(
    rows: List<TimelineRow>,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val columnWidthPx = with(density) { COLUMN_WIDTH.toPx() }
    val columnGapPx = with(density) { COLUMN_GAP.toPx() }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

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
                    onNodeToggle = onNodeToggle,
                    onNodeClick  = onNodeClick
                )

                if (index < rows.lastIndex && row.outgoing.isNotEmpty()) {
                    ConnectorStrip(
                        connections   = row.outgoing,
                        totalColumns  = row.totalColumns,
                        columnWidthPx = columnWidthPx,
                        columnGapPx   = columnGapPx,
                        modifier      = Modifier
                            .width(rowContentWidth(row.totalColumns))
                            .height(CONNECTOR_STRIP_HEIGHT)
                    )
                } else if (index < rows.lastIndex) {
                    Spacer(Modifier.height(CONNECTOR_STRIP_HEIGHT / 2))
                }
            }
        }
    }
}

private fun rowContentWidth(totalColumns: Int): Dp {
    if (totalColumns <= 0) return COLUMN_WIDTH
    return COLUMN_WIDTH * totalColumns + COLUMN_GAP * (totalColumns - 1)
}

// ─── Row View ─────────────────────────────────────────────────────────────────

@Composable
private fun TimelineRowView(
    row: TimelineRow,
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
 */
@Composable
private fun ConnectorStrip(
    connections: List<GraphEngine.OutgoingConnection>,
    totalColumns: Int,
    columnWidthPx: Float,
    columnGapPx: Float,
    modifier: Modifier = Modifier
) {
    val theme = com.example.watchorderengine.ui.theme.LocalAppTheme.current
    val colorCompleted = theme.statusCanon
    val colorPending    = theme.textSecondary.copy(alpha = 0.3f)
    val dotCompleted    = theme.statusCanon
    val dotPending      = theme.border.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        if (totalColumns == 0) return@Canvas

        fun columnCenterX(columnIndex: Int): Float =
            columnIndex * (columnWidthPx + columnGapPx) + columnWidthPx / 2f

        val midY = size.height / 2f

        for (connection in connections) {
            val startX = columnCenterX(connection.fromColumn)
            val endX   = columnCenterX(connection.toColumn)
            val startY = 0f
            val endY   = size.height

            val lineColor    = if (connection.isFromNodeCompleted) colorCompleted else colorPending
            val strokeWidth  = if (connection.isFromNodeCompleted) CONNECTOR_STROKE_DONE else CONNECTOR_STROKE_IDLE

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

            val dotColor  = if (connection.isFromNodeCompleted) dotCompleted else dotPending
            val dotRadius = if (connection.isFromNodeCompleted) 4f else 2.5f

            drawCircle(color = dotColor, radius = dotRadius, center = Offset(startX, startY))
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(endX, endY))
        }
    }
}
