package com.example.watchorderengine.ui.timeline

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.timeline.components.BranchingTimelineView
import com.example.watchorderengine.ui.viewmodel.CommunityViewModel
import com.example.watchorderengine.ui.viewmodel.ShareTimelineState
import com.example.watchorderengine.viewmodel.*

private data class Star(val x: Float, val y: Float, val alpha: Float, val radius: Float)

@Composable
fun TimelineScreen(
    universeId: String,
    onBack: () -> Unit,
    onNodeDetail: (nodeId: String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
    communityViewModel: CommunityViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val shareState by communityViewModel.shareState.collectAsStateWithLifecycle()

    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(universeId) { viewModel.initialize(universeId) }

    LaunchedEffect(shareState) {
        if (shareState is ShareTimelineState.Shared) {
            // Show success and reset
            communityViewModel.resetShareState()
            showShareDialog = false
        }
    }

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
            .background(theme.background)
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
                            colors = listOf(theme.accent.copy(alpha = 0.05f), Color.Transparent),
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
                onSpoilerToggle = { viewModel.toggleSpoilerShield() },
                onShareCommunityClick = { showShareDialog = true }
            )

            CompletionBanner(visible = isUniverseComplete)

            when (val state = uiState) {
                is TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = theme.accent) 
                }
                is TimelineUiState.Success -> TimelineContent(state, viewModel)
                is TimelineUiState.Error -> Text("Error: ${state.message}", color = theme.statusFiller, modifier = Modifier.padding(16.dp))
            }
        }

        if (isSyncing) {
            SyncingOverlay()
        }

        if (showShareDialog) {
            val success = uiState as? TimelineUiState.Success
            if (success != null) {
                ShareToCommunityDialog(
                    universeName = success.universe.name,
                    onDismiss = { 
                        showShareDialog = false
                        communityViewModel.resetShareState()
                    },
                    onShare = { description ->
                        val nodesJson = SharedTimelineCodec.encode(success.nodes, success.edges)
                        communityViewModel.shareTimeline(
                            title = success.universe.name,
                            description = description,
                            nodesJson = nodesJson
                        )
                    },
                    shareState = shareState
                )
            }
        }
    }
}

@Composable
private fun ShareToCommunityDialog(
    universeName: String,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    shareState: ShareTimelineState
) {
    val theme = LocalAppTheme.current
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        title = {
            Text("Share to Community", color = theme.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Share your custom watch order for '$universeName' with the global community.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Add a short description...", color = theme.textSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.3f)
                    ),
                    maxLines = 3
                )
                if (shareState is ShareTimelineState.Failed) {
                    Text(
                        shareState.message,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(description) },
                enabled = shareState !is ShareTimelineState.Sharing,
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                if (shareState is ShareTimelineState.Sharing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Share", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun SyncingOverlay() {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = theme.accent,
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
    val theme = LocalAppTheme.current
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            dismissed = false
            kotlinx.coroutines.delay(AUTO_DISMISS_MS)
            dismissed = true
        }
    }

    val showBanner = visible && !dismissed

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
                            theme.accent.copy(alpha = 0.15f),
                            theme.accent.copy(alpha = 0.3f),
                            theme.accent.copy(alpha = 0.15f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            theme.accent.copy(alpha = 0.4f),
                            theme.accent,
                            theme.accent.copy(alpha = 0.4f)
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
                        color      = theme.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text     = "You've completed every entry in this timeline.",
                        fontSize = 11.sp,
                        color    = theme.textPrimary.copy(alpha = 0.65f),
                        lineHeight = 16.sp
                    )
                }

                IconButton(
                    onClick  = { dismissed = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(theme.textPrimary.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint     = theme.textPrimary.copy(alpha = 0.5f),
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
    onSpoilerToggle: () -> Unit,
    onShareCommunityClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val universeName = (uiState as? TimelineUiState.Success)?.universe?.name ?: "Skill Tree"
    val spoilerEnabled = (uiState as? TimelineUiState.Success)?.spoilerShieldEnabled ?: true
    val completedCount = (uiState as? TimelineUiState.Success)?.completedCount ?: 0
    val totalCount = (uiState as? TimelineUiState.Success)?.totalNodeCount ?: 0

    var showShareMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textPrimary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    universeName,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("SKILL TREE", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Box {
                Surface(
                    onClick = { showShareMenu = true },
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border.copy(alpha = 0.1f)),
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Share, "Share", tint = theme.accent, modifier = Modifier.size(20.dp))
                    }
                }

                DropdownMenu(
                    expanded = showShareMenu,
                    onDismissRequest = { showShareMenu = false },
                    modifier = Modifier.background(theme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard Share", color = theme.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = theme.accent) },
                        onClick = {
                            showShareMenu = false
                            val shareText = "Check out my watch order for $universeName on Watch Order Engine! I've completed $completedCount/$totalCount entries."
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Post to Community", color = theme.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Groups, null, tint = theme.accent) },
                        onClick = {
                            showShareMenu = false
                            onShareCommunityClick()
                        }
                    )
                }
            }

            Surface(
                onClick = onSpoilerToggle,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (spoilerEnabled) theme.statusMixed.copy(alpha = 0.15f) else theme.surface,
                border = BorderStroke(
                    width = 1.dp, 
                    color = if (spoilerEnabled) theme.statusMixed.copy(alpha = 0.5f) else theme.border.copy(alpha = 0.1f)
                ),
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (spoilerEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                        contentDescription = "Toggle Spoilers", 
                        tint = if (spoilerEnabled) theme.statusMixed else theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineContent(
    state: TimelineUiState.Success,
    viewModel: TimelineViewModel
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        // Floating Hint
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = theme.accent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Tip: Long-press any hexagonal node to toggle its watch status.",
                    color = theme.textPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
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
