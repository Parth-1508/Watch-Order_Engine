package com.example.watchorderengine.ui.timeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.viewmodel.DisplayNode
import com.example.watchorderengine.viewmodel.TimelineRow

// ─── Dimension Constants ──────────────────────────────────────────────────────

private val COLUMN_WIDTH: Dp = 100.dp
private val COLUMN_GAP: Dp = 24.dp
private val CONNECTOR_STRIP_HEIGHT: Dp = 100.dp
private val ROW_VERTICAL_PADDING: Dp = 24.dp

private const val CONNECTOR_STROKE_IDLE = 1.5f
private const val CONNECTOR_STROKE_DONE = 2.5f
private val DASH_INTERVALS = floatArrayOf(10f, 6f)

// ─── Zoom constraints ─────────────────────────────────────────────────────────

private const val ZOOM_MIN = 0.4f
private const val ZOOM_MAX = 3.0f

// ─── Main Composable ──────────────────────────────────────────────────────────

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

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(ZOOM_MIN, ZOOM_MAX)
        
        // Direct panning for stability and immediate response.
        // Clamp the offset to keep the graph within a playable area.
        val targetOffset = offset + panChange
        val limit = 3000f // Shared limit for all zooms to prevent "lost in space"
        offset = Offset(
            x = targetOffset.x.coerceIn(-limit, limit),
            y = targetOffset.y.coerceIn(-limit, limit)
        )
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformableState)
        ) {
            TransformableTimelineContent(
                rows          = rows,
                scale         = scale,
                offset        = offset,
                columnWidthPx = columnWidthPx,
                columnGapPx   = columnGapPx,
                onNodeToggle  = onNodeToggle,
                onNodeClick   = onNodeClick,
                modifier      = Modifier.fillMaxSize()
            )
        }

        ZoomControls(
            scale      = scale,
            onZoomIn   = { scale = (scale * 1.25f).coerceIn(ZOOM_MIN, ZOOM_MAX) },
            onZoomOut  = { scale = (scale / 1.25f).coerceIn(ZOOM_MIN, ZOOM_MAX) },
            onReset    = { scale = 1f; offset = Offset.Zero },
            modifier   = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun TransformableTimelineContent(
    rows: List<TimelineRow>,
    scale: Float,
    offset: Offset,
    columnWidthPx: Float,
    columnGapPx: Float,
    onNodeToggle: (DisplayNode) -> Unit,
    onNodeClick: (DisplayNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                transformOrigin = TransformOrigin.Center
            }
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 64.dp)
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
private fun ZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = com.example.watchorderengine.ui.theme.LocalAppTheme.current
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        color     = theme.surface.copy(alpha = 0.95f),
        border    = BorderStroke(1.dp, theme.border.copy(alpha = 0.2f)),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier            = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick  = onZoomIn,
                enabled  = scale < ZOOM_MAX,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom in",
                    tint     = if (scale < ZOOM_MAX) theme.textPrimary else theme.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text     = "${(scale * 100).toInt()}%",
                color    = theme.accent,
                style    = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick  = onZoomOut,
                enabled  = scale > ZOOM_MIN,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom out",
                    tint     = if (scale > ZOOM_MIN) theme.textPrimary else theme.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color     = theme.border.copy(alpha = 0.1f),
                thickness = 1.dp
            )

            IconButton(
                onClick  = onReset,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset view",
                    tint     = theme.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

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
            verticalAlignment = Alignment.Top
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
    val theme = com.example.watchorderengine.ui.theme.LocalAppTheme.current
    val colorCompleted  = theme.accent
    val colorPending    = theme.textSecondary.copy(alpha = 0.3f)
    val colorDotActive  = theme.statusCanon
    val colorDotIdle    = theme.border.copy(alpha = 0.2f)

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
