package com.example.watchorderengine.ui.screens.community

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.ui.components.ShareTimelineDialog
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.AddNodeSearchState
import com.example.watchorderengine.ui.viewmodel.CommunityViewModel
import com.example.watchorderengine.ui.viewmodel.GraphBuilderViewModel
import com.example.watchorderengine.ui.viewmodel.ShareTimelineState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val NODE_WIDTH = 96.dp
private val NODE_HEIGHT = 150.dp
// The only part of GraphNodeCard with a truly FIXED height — the title text
// below it grows to up to 2 lines depending on length, so NODE_HEIGHT itself
// is not a reliable anchor point for where the poster actually ends on screen.
private val POSTER_HEIGHT = NODE_HEIGHT * 0.72f
private val CANVAS_HEIGHT = 1000.dp

@Composable
fun GraphBuilderScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    viewModel: GraphBuilderViewModel = hiltViewModel(),
    communityViewModel: CommunityViewModel = hiltViewModel(),
) {
    val theme         = LocalAppTheme.current
    val boardState    by viewModel.state.collectAsStateWithLifecycle()
    val linkModeActive by viewModel.linkModeActive.collectAsStateWithLifecycle()
    val pendingFromId  by viewModel.pendingFromNodeId.collectAsStateWithLifecycle()
    val shareState     by communityViewModel.shareState.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var publishTitle by remember { mutableStateOf("") }
    var publishDescription by remember { mutableStateOf("") }

    LaunchedEffect(shareState) {
        if (shareState is ShareTimelineState.Shared) {
            communityViewModel.resetShareState()
            onPublished()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(theme.background)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textPrimary)
            }
            Text(
                "BUILD TIMELINE",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textPrimary,
                modifier   = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showExportDialog = true },
                enabled = boardState.nodes.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.Share, "Export as image",
                    tint = if (boardState.nodes.isNotEmpty()) theme.textSecondary else theme.textSecondary.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = { viewModel.toggleLinkMode() }) {
                Icon(
                    Icons.Default.Timeline, "Toggle link mode",
                    tint = if (linkModeActive) theme.accent else theme.textSecondary
                )
            }
        }

        AnimatedModeBanner(
            linkModeActive  = linkModeActive,
            pendingFromId   = pendingFromId,
            nodes           = boardState.nodes,
            isCycleDetected = boardState.layout.isCycleDetected,
        )

        if (boardState.nodes.isEmpty()) {
            EmptyBoardState(modifier = Modifier.weight(1f))
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val density = LocalDensity.current
                val canvasWidthPx = with(density) { maxWidth.toPx() }
                val nodeWidthPx = with(density) { NODE_WIDTH.toPx() }
                val nodeHeightPx = with(density) { NODE_HEIGHT.toPx() }
                val posterHeightPx = with(density) { POSTER_HEIGHT.toPx() }
                val canvasHeightPx = with(density) { CANVAS_HEIGHT.toPx() }

                Box(modifier = Modifier.fillMaxWidth().height(CANVAS_HEIGHT)) {

                    EdgesCanvas(
                        edges         = boardState.edges,
                        positions     = boardState.nodePositions,
                        nodeWidthPx   = nodeWidthPx,
                        posterHeightPx = posterHeightPx,
                        accentColor   = theme.accent,
                        isCycleDetected = boardState.layout.isCycleDetected,
                    )

                    boardState.nodes.forEach { node ->
                        val position = boardState.nodePositions[node.id] ?: Offset.Zero
                        GraphNodeCard(
                            node               = node,
                            position           = position,
                            isPendingLinkSource = pendingFromId == node.id,
                            linkModeActive     = linkModeActive,
                            canvasWidthPx      = canvasWidthPx,
                            canvasHeightPx     = canvasHeightPx,
                            nodeWidthPx        = nodeWidthPx,
                            nodeHeightPx       = nodeHeightPx,
                            onDrag             = { delta ->
                                viewModel.applyNodeDrag(
                                    nodeId = node.id,
                                    delta = delta,
                                    canvasWidthPx = canvasWidthPx,
                                    canvasHeightPx = canvasHeightPx,
                                    nodeWidthPx = nodeWidthPx,
                                    nodeHeightPx = nodeHeightPx,
                                )
                            },
                            onLinkTap          = { viewModel.onNodeTappedForLink(node.id) },
                            onRemove           = { viewModel.removeNode(node.id) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ADD TITLE", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Button(
                onClick = {
                    publishTitle = ""
                    publishDescription = ""
                    showPublishDialog = true
                },
                enabled = boardState.canPublish,
                modifier = Modifier.weight(1f),
                colors  = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Icon(Icons.Default.Publish, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("PUBLISH", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }

    if (showAddSheet) {
        AddNodeSheet(
            viewModel = viewModel,
            onDismiss = {
                showAddSheet = false
                viewModel.resetSearch()
            }
        )
    }

    if (showPublishDialog) {
        PublishTimelineDialog(
            title           = publishTitle,
            description     = publishDescription,
            onTitleChange   = { publishTitle = it },
            onDescriptionChange = { publishDescription = it },
            shareState      = shareState,
            onConfirm       = {
                communityViewModel.shareTimeline(
                    title       = publishTitle,
                    description = publishDescription,
                    nodesJson   = SharedTimelineCodec.encode(
                        boardState.nodes, boardState.edges
                    )
                )
            },
            onDismiss = {
                showPublishDialog = false
                communityViewModel.resetShareState()
            }
        )
    }

    if (showExportDialog) {
        ShareTimelineDialog(
            title     = publishTitle.ifBlank { "My Custom Timeline" },
            nodes     = boardState.nodes,
            edges     = boardState.edges,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun AnimatedModeBanner(
    linkModeActive: Boolean,
    pendingFromId: String?,
    nodes: List<MediaNode>,
    isCycleDetected: Boolean,
) {
    val theme = LocalAppTheme.current
    val message = when {
        isCycleDetected -> "This creates a loop — remove a connection before publishing."
        linkModeActive && pendingFromId != null ->
            "Tap the title that comes AFTER \"${nodes.find { it.id == pendingFromId }?.title ?: ""}\"."
        linkModeActive -> "Link mode: tap a title, then tap the one that follows it."
        else -> "Drag titles to arrange them. Long-press to remove."
    }
    val bannerColor = if (isCycleDetected) Color(0xFFFF6B6B) else theme.accent

    Surface(color = bannerColor.copy(alpha = 0.12f)) {
        Text(
            message,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = bannerColor,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EmptyBoardState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AccountTree, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Tap ADD TITLE to start building your timeline",
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EdgesCanvas(
    edges: List<Edge>,
    positions: Map<String, Offset>,
    nodeWidthPx: Float,
    posterHeightPx: Float,
    accentColor: Color,
    isCycleDetected: Boolean,
) {
    val lineColor = if (isCycleDetected) Color(0xFFFF6B6B) else accentColor
    Canvas(modifier = Modifier.fillMaxSize()) {
        edges.forEach { edge ->
            val fromPos = positions[edge.from_node_id] ?: return@forEach
            val toPos = positions[edge.to_node_id] ?: return@forEach
            val start = Offset(fromPos.x + nodeWidthPx / 2f, fromPos.y + posterHeightPx)
            val end = Offset(toPos.x + nodeWidthPx / 2f, toPos.y)
            drawArrow(start = start, end = end, color = lineColor, strokeWidthPx = 3f)
        }
    }
}

private fun DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidthPx: Float,
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLength = 16f
    val arrowAngle = Math.toRadians(28.0)

    val wing1 = Offset(
        x = end.x - (arrowLength * cos(angle - arrowAngle)).toFloat(),
        y = end.y - (arrowLength * sin(angle - arrowAngle)).toFloat(),
    )
    val wing2 = Offset(
        x = end.x - (arrowLength * cos(angle + arrowAngle)).toFloat(),
        y = end.y - (arrowLength * sin(angle + arrowAngle)).toFloat(),
    )
    drawLine(color = color, start = end, end = wing1, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
    drawLine(color = color, start = end, end = wing2, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
}

@Composable
private fun GraphNodeCard(
    node: MediaNode,
    position: Offset,
    isPendingLinkSource: Boolean,
    linkModeActive: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    nodeWidthPx: Float,
    nodeHeightPx: Float,
    onDrag: (Offset) -> Unit,
    onLinkTap: () -> Unit,
    onRemove: () -> Unit,
) {
    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(NODE_WIDTH)
            .pointerInput(node.id, linkModeActive) {
                if (linkModeActive) {
                    detectTapGestures(onTap = { onLinkTap() })
                } else {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(drag)
                    }
                }
            }
            .pointerInput(node.id, linkModeActive) {
                if (!linkModeActive) {
                    detectTapGestures(onLongPress = { onRemove() })
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(NODE_WIDTH, NODE_HEIGHT * 0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surfaceHover)
                .border(
                    width = if (isPendingLinkSource) 2.5.dp else 0.dp,
                    color = theme.accent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            AsyncImage(
                model              = node.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            node.title,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            color      = theme.textPrimary,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNodeSheet(viewModel: GraphBuilderViewModel, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("ADD A TITLE", fontSize = 16.sp, fontWeight = FontWeight.Black, color = theme.textPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search movies & shows…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    focusedTextColor   = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary,
                )
            )
            Spacer(Modifier.height(12.dp))

            when (val state = searchState) {
                is AddNodeSearchState.Idle -> Unit
                is AddNodeSearchState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }
                is AddNodeSearchState.Error -> {
                    Text(state.message, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                is AddNodeSearchState.Results -> {
                    if (state.items.isEmpty()) {
                        Text("No results.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(state.items, key = { it.id }) { summary ->
                                SearchResultRow(
                                    summary = summary,
                                    onClick = {
                                        viewModel.addNode(summary)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(summary: MediaSummary, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.background)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = summary.posterUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(4.dp)).background(theme.surfaceHover)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary.releaseYear, fontSize = 11.sp, color = theme.textSecondary)
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.AddCircle, "Add", tint = theme.accent)
        }
    }
}

@Composable
private fun PublishTimelineDialog(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    shareState: ShareTimelineState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val isSharing = shareState is ShareTimelineState.Sharing

    AlertDialog(
        onDismissRequest = { if (!isSharing) onDismiss() },
        title = { Text("Publish to Community", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Timeline title") },
                    singleLine = true,
                    enabled = !isSharing,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Short description") },
                    enabled = !isSharing,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (shareState is ShareTimelineState.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(shareState.message, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSharing && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                if (isSharing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("PUBLISH", fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSharing) { Text("Cancel") }
        }
    )
}
