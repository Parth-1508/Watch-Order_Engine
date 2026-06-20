package com.example.watchorderengine.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.viewmodel.DisplayNode
import com.example.watchorderengine.viewmodel.TimelineRow

// ─── Dimension Constants ──────────────────────────────────────────────────────

/** Width of each node card column in the branching layout. */
private val COLUMN_WIDTH: Dp = 100.dp
private val COLUMN_GAP: Dp = 24.dp
private val CONNECTOR_STRIP_HEIGHT: Dp = 80.dp
private val ROW_VERTICAL_PADDING: Dp = 16.dp

/** Stroke width for pending (uncompleted) connector lines. */
private const val CONNECTOR_STROKE_IDLE = 1.5f

/** Stroke width for completed connector lines. Slightly bolder for emphasis. */
private const val CONNECTOR_STROKE_DONE = 2.5f

/** Dash intervals for the pending dashed connector line: 10px on, 6px off. */
private val DASH_INTERVALS = floatArrayOf(10f, 6f)

// ─── Main Composable ──────────────────────────────────────────────────────────

/**
 * Renders the full branching timeline as a vertical list of rows.
 *
 * LAYOUT ALGORITHM:
 * Each [TimelineRow] occupies a horizontal "slot" in the LazyColumn. Within a
 * slot, [DisplayNode]s are arranged side-by-side according to their column index.
 * Between consecutive row slots, a [ConnectorStrip] Canvas draws smooth bezier
 * curves representing the DAG edges.
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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
            contentPadding = PaddingValues(
                start  = 32.dp,
                end    = 32.dp,
                top    = 16.dp,
                bottom = 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = rows,
                key   = { _, row -> row.level }
            ) { index, row ->
                TimelineRowView(
                    row          = row,
                    onNodeToggle = onNodeToggle,
                    onNodeClick  = onNodeClick
                )

                if ((index < rows.lastIndex) && row.outgoing.isNotEmpty()) {
                    ConnectorStrip(
                        connections  = row.outgoing,
                        totalColumns = row.totalColumns,
                        columnWidthPx = columnWidthPx,
                        columnGapPx   = columnGapPx,
                        modifier     = Modifier
                            .fillMaxWidth()
                            .height(CONNECTOR_STRIP_HEIGHT)
                    )
                } else if (index < rows.lastIndex) {
                    Spacer(Modifier.height(CONNECTOR_STRIP_HEIGHT / 2))
                }
            }
        }
    }
}

// ─── Row View ─────────────────────────────────────────────────────────────────

@Composable
private fun TimelineRowView(
    row: TimelineRow,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit
) {
    val nodeByColumn = row.nodes.associateBy { it.column }
    val usedColumns = (0 until row.totalColumns)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .padding(vertical = ROW_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (columnIndex in usedColumns) {
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
}

// ─── Connector Strip Canvas ───────────────────────────────────────────────────

@Composable
private fun ConnectorStrip(
    connections: List<GraphEngine.OutgoingConnection>,
    totalColumns: Int,
    columnWidthPx: Float,
    columnGapPx: Float,
    modifier: Modifier = Modifier
) {
    val colorCompleted  = WatchOrderColors.ConnectorActive
    val colorPending    = WatchOrderColors.ConnectorIdle
    val colorDotActive  = WatchOrderColors.CompletedGreen
    val colorDotIdle    = WatchOrderColors.CardBorder

    Canvas(modifier = modifier) {
        if (totalColumns == 0) return@Canvas

        val totalGraphWidth = totalColumns * columnWidthPx + (totalColumns - 1) * columnGapPx
        val horizontalOffset = (size.width - totalGraphWidth) / 2f

        fun columnCenterX(columnIndex: Int): Float =
            horizontalOffset + columnIndex * (columnWidthPx + columnGapPx) + columnWidthPx / 2f

        for (connection in connections) {
            val startX = columnCenterX(connection.fromColumn)
            val endX   = columnCenterX(connection.toColumn)
            val startY = 0f
            val endY   = size.height

            val lineColor   = if (connection.isFromNodeCompleted) colorCompleted else colorPending
            val strokeWidth = if (connection.isFromNodeCompleted) CONNECTOR_STROKE_DONE
            else CONNECTOR_STROKE_IDLE

            val controlPoint1 = Offset(startX, endY * 0.40f)
            val controlPoint2 = Offset(endX,   endY * 0.60f)

            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(
                    controlPoint1.x, controlPoint1.y,
                    controlPoint2.x, controlPoint2.y,
                    endX, endY
                )
            }

            if (connection.isFromNodeCompleted) {
                drawPath(
                    path = path,
                    color = colorCompleted.copy(alpha = 0.2f),
                    style = Stroke(width = strokeWidth * 3f, cap = StrokeCap.Round)
                )
            }

            drawPath(
                path  = path,
                color = lineColor,
                style = Stroke(
                    width      = strokeWidth,
                    cap        = StrokeCap.Round,
                    join       = StrokeJoin.Round,
                    pathEffect = if (!connection.isFromNodeCompleted) {
                        PathEffect.dashPathEffect(DASH_INTERVALS, phase = 0f)
                    } else null
                )
            )

            val dotColor = if (connection.isFromNodeCompleted) colorDotActive else colorDotIdle
            val dotRadius = if (connection.isFromNodeCompleted) 5f else 3f

            drawCircle(color = dotColor, radius = dotRadius, center = Offset(startX, startY))
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(endX, endY))
        }
    }
}
