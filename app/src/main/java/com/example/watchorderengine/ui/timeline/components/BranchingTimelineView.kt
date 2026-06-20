package com.example.watchorderengine.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 *
 * VISUAL RESULT:
 *  - Single-path stretches render as a straight vertical line of cards.
 *  - Branch points split the cards left/right with diagonal connector curves.
 *  - Merge points bring the diagonal curves back together.
 *  - Completed connections are solid green; pending ones are dashed grey.
 *
 * @param rows        The ordered list of [TimelineRow]s from the ViewModel.
 * @param onNodeToggle Called when the user taps a node's checkbox.
 * @param onNodeClick  Called when the user taps a node's card body.
 */
@Composable
fun BranchingTimelineView(
    rows: List<TimelineRow>,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve density once here; pass as a parameter where needed to avoid
    // capturing LocalDensity inside Canvas lambda (which runs in DrawScope).
    val density = LocalDensity.current
    val columnWidthPx = with(density) { COLUMN_WIDTH.toPx() }
    val columnGapPx = with(density) { COLUMN_GAP.toPx() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start  = 16.dp,
            end    = 16.dp,
            top    = 16.dp,
            bottom = 64.dp  // Extra bottom padding for FAB or tab bar clearance
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp) // We control spacing manually
    ) {
        itemsIndexed(
            items = rows,
            key   = { _, row -> row.level }   // Stable key: prevents recompose on scroll
        ) { index, row ->

            // ── Timeline Row (the node cards) ─────────────────────────────────
            TimelineRowView(
                row          = row,
                onNodeToggle = onNodeToggle,
                onNodeClick  = onNodeClick
            )

            // ── Connector Strip (bezier lines to the next row) ─────────────
            // Only draw connectors if there IS a next row. The last row has no
            // outgoing connections to render.
            if ((index < rows.lastIndex) && row.outgoing.isNotEmpty()) {
                ConnectorStrip(
                    connections  = row.outgoing,
                    totalColumns = row.totalColumns,
                    columnWidthPx = columnWidthPx,
                    columnGapPx   = columnGapPx,
                    modifier     = Modifier
                        .fillMaxWidth()
                        .height(CONNECTOR_STRIP_HEIGHT)
                        .padding(horizontal = 0.dp)
                )
            } else if (index < rows.lastIndex) {
                // Even with no edge data, leave a small gap between rows
                Spacer(Modifier.height(CONNECTOR_STRIP_HEIGHT / 2))
            }
        }
    }
}

// ─── Row View ─────────────────────────────────────────────────────────────────

/**
 * Renders a single "row" in the timeline — one topological level.
 *
 * If the row has multiple nodes (a branch), they appear side-by-side.
 * Single-node rows are centered within the full column width to maintain
 * visual alignment with branching rows above/below.
 *
 * The "column" index of each node dictates its horizontal position.
 * Empty column slots (gaps between branches) are rendered as transparent
 * spacers to keep card positions consistent across rows.
 */
@Composable
private fun TimelineRowView(
    row: TimelineRow,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit
) {
    // Build a sparse map of column → DisplayNode for O(1) lookup below
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
                    // ── Populated column: render the node card ─────────────────
                    TimelineNodeCard(
                        displayNode = displayNode,
                        onCheckToggle = { onNodeToggle(displayNode) },
                        onCardClick = { onNodeClick(displayNode) },
                        modifier = Modifier.width(COLUMN_WIDTH)
                    )
                } else {
                    // ── Empty column: transparent spacer ──────────────────────
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

/**
 * Draws the DAG edge connections between two consecutive timeline rows using Canvas.
 *
 * For each [GraphEngine.OutgoingConnection], draws a cubic Bezier curve from the
 * center of the source column (at y=0, the top of this strip) to the center of
 * the destination column (at y=height, the bottom of this strip).
 *
 * VISUAL DESIGN:
 *   - COMPLETED edges: solid green line, slightly thicker.
 *   - PENDING edges:   dashed grey line, thinner.
 *   - Straight lines (same column): degenerate bezier — renders as a straight line.
 *   - Diagonal lines (different columns): smooth S-curve via bezier control points
 *     at 40% and 60% of the strip height, creating a "branch and merge" look.
 *   - A small filled circle is drawn at both ends of each connection for polish.
 *
 * @param connections    The outgoing connections for the row above this strip.
 * @param totalColumns   Total columns in the graph (used to compute x-centers).
 * @param columnWidthPx  Width of a single node card column in pixels.
 * @param columnGapPx    Gap between columns in pixels.
 */
@Composable
private fun ConnectorStrip(
    connections: List<GraphEngine.OutgoingConnection>,
    totalColumns: Int,
    columnWidthPx: Float,
    columnGapPx: Float,
    modifier: Modifier = Modifier
) {
    // Pre-resolve color values outside the Canvas lambda.
    // Canvas's DrawScope does NOT have access to CompositionLocals,
    // so colors must be captured in the composable's scope.
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
                // Glow effect for active connections
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