package com.example.watchorderengine.ui.timeline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.ui.timeline.components.BranchingTimelineView
import com.example.watchorderengine.viewmodel.*

private data class Star(val x: Float, val y: Float, val alpha: Float, val radius: Float)

@Composable
fun TimelineScreen(
    universeId: String,
    onBack: () -> Unit,
    onNodeDetail: (nodeId: String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    LaunchedEffect(universeId) { viewModel.initialize(universeId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineEvent.NavigateToDetail -> onNodeDetail(event.mediaId)
                is TimelineEvent.ShowSnackbar     -> { /* Handle snackbar */ }
            }
        }
    }

    val isUniverseComplete = remember(uiState) {
        (uiState as? TimelineUiState.Success)?.let { s ->
            s.totalNodeCount > 0 && s.completedCount >= s.totalNodeCount
        } ?: false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchOrderColors.DeepSpace)
            .drawWithCache {
                val random = java.util.Random(42)
                val stars = List(100) {
                    val x = random.nextFloat()
                    val y = random.nextFloat()
                    val alpha = 0.1f + random.nextFloat() * 0.4f
                    val radius = (0.5f + random.nextFloat() * 1.5f).dp.toPx()
                    Star(x, y, alpha, radius)
                }
                onDrawBehind {
                    stars.forEach { star ->
                        drawCircle(
                            color = Color.White.copy(alpha = star.alpha),
                            radius = star.radius,
                            center = Offset(star.x * size.width, star.y * size.height)
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(WatchOrderColors.AccentGold.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(size.width * 0.8f, size.height * 0.2f),
                            radius = size.width * 0.6f
                        ),
                        center = Offset(size.width * 0.8f, size.height * 0.2f),
                        radius = size.width * 0.6f
                    )
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimelineHeader(
                uiState = uiState,
                onBack = onBack,
                onSpoilerToggle = { viewModel.toggleSpoilerShield() }
            )

            CompletionBanner(visible = isUniverseComplete)

            when (val state = uiState) {
                is TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WatchOrderColors.AccentGold) }
                is TimelineUiState.Success -> TimelineContent(state, viewModel)
                is TimelineUiState.Error -> Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        }

        if (isSyncing) {
            SyncingOverlay()
        }
    }
}

@Composable
fun SyncingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = WatchOrderColors.AccentGold,
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Syncing timeline progress...",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "Updating your watchlist and cloud sync",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ─── Completion Banner ────────────────────────────────────────────────────────

private const val AUTO_DISMISS_MS = 6_000L

@Composable
private fun CompletionBanner(visible: Boolean) {
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            dismissed = false
            kotlinx.coroutines.delay(AUTO_DISMISS_MS)
            dismissed = true
        }
    }

    val showBanner = visible && !dismissed

    val shimmerOffset by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue  = -1f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label         = "shimmerOffset"
    )

    AnimatedVisibility(
        visible = showBanner,
        enter   = slideInVertically(
            initialOffsetY = { -it },
            animationSpec  = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(300)),
        exit    = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(400)
        ) + fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A1200),
                            Color(0xFF3D2B00),
                            Color(0xFF1A1200)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            WatchOrderColors.AccentGold.copy(alpha = 0.4f),
                            WatchOrderColors.AccentGold,
                            WatchOrderColors.AccentGold.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val bounce by rememberInfiniteTransition(label = "trophy_bounce").animateFloat(
                    initialValue  = 0f,
                    targetValue   = -6f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bounce"
                )
                Text(
                    text     = "🏆",
                    fontSize = 28.sp,
                    modifier = Modifier.offset(y = bounce.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "UNIVERSE CONQUERED!",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Black,
                        color      = WatchOrderColors.AccentGold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text     = "You've completed every entry in this timeline.",
                        fontSize = 11.sp,
                        color    = Color.White.copy(alpha = 0.65f),
                        lineHeight = 16.sp
                    )
                }

                IconButton(
                    onClick  = { dismissed = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint     = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    uiState: TimelineUiState,
    onBack: () -> Unit,
    onSpoilerToggle: () -> Unit
) {
    val universeName = (uiState as? TimelineUiState.Success)?.universe?.name ?: "Skill Tree"
    val spoilerEnabled = (uiState as? TimelineUiState.Success)?.spoilerShieldEnabled ?: true
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = WatchOrderColors.TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, null, tint = WatchOrderColors.AccentGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        universeName,
                        color = WatchOrderColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text("Progress Tracker", color = WatchOrderColors.TextSecondary, fontSize = 10.sp)
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onSpoilerToggle,
                modifier = Modifier.size(36.dp).background(
                    if (spoilerEnabled) WatchOrderColors.SpoilerPurple.copy(alpha = 0.2f) else WatchOrderColors.ElevatedSurface, 
                    CircleShape
                )
            ) {
                Icon(
                    if (spoilerEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                    null, 
                    tint = if (spoilerEnabled) WatchOrderColors.SpoilerPurple else WatchOrderColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TimelineContent(
    state: TimelineUiState.Success,
    viewModel: TimelineViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.availableTags) { tag ->
                val isSelected = tag.tagId == state.activeRouteTag
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) WatchOrderColors.AccentGold else WatchOrderColors.ElevatedSurface,
                    border = BorderStroke(1.dp, if (isSelected) WatchOrderColors.AccentGold else WatchOrderColors.CardBorder),
                    onClick = { viewModel.setActiveRoute(tag.tagId) }
                ) {
                    Text(
                        tag.label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = if (isSelected) WatchOrderColors.DeepSpace else WatchOrderColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        BranchingTimelineView(
            rows = state.rows,
            onNodeToggle = { node -> viewModel.toggleNodeCompletion(node.node.id, node.isCompleted) },
            onNodeClick = { node -> viewModel.onNodeClick(node.node) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
