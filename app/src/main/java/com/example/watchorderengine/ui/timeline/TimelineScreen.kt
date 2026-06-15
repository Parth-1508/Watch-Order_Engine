package com.example.watchorderengine.ui.timeline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.watchorderengine.data.ContextTag
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.ui.timeline.components.BranchingTimelineView
import com.example.watchorderengine.ui.timeline.components.TimelineNodeCard
import com.example.watchorderengine.viewmodel.*
import kotlinx.coroutines.flow.collectLatest

// ─── Main Screen Entry Point ──────────────────────────────────────────────────

/**
 * Root composable for the Timeline screen. Wires the ViewModel, handles
 * one-shot events, and delegates rendering to Success/Loading/Error views.
 *
 * @param universeId   Firestore document ID for the universe to display.
 * @param onBack       Navigation callback for the back button.
 * @param onNodeDetail Navigation callback when a node card is long-pressed/tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    universeId: String,
    onBack: () -> Unit,
    onNodeDetail: (nodeId: String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    // Initialize the ViewModel's observation pipeline.
    // LaunchedEffect(key) re-launches if universeId changes (e.g., user navigates
    // to a different universe without leaving the screen — unlikely but defensive).
    LaunchedEffect(universeId) { viewModel.initialize(universeId) }

    // Collect one-shot events (snackbars, navigation)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TimelineEvent.ShowSnackbar    -> snackbarHostState.showSnackbar(event.message)
                is TimelineEvent.NavigateToDetail -> onNodeDetail(event.nodeId)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = WatchOrderColors.VoidDark,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TimelineTopBar(
                uiState           = uiState,
                onBack            = onBack,
                onSpoilerToggle   = { viewModel.toggleSpoilerShield() },
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "timeline_content_transition",
            modifier = Modifier.padding(innerPadding)
        ) { state ->
            when (state) {
                is TimelineUiState.Loading ->
                    TimelineLoadingView(Modifier.fillMaxSize())

                is TimelineUiState.Error ->
                    TimelineErrorView(
                        message     = state.message,
                    onRetry     = state.retryAction,
                    modifier    = Modifier.fillMaxSize()
                )

                is TimelineUiState.Success ->
                    TimelineSuccessView(
                        state        = state,
                        onNodeToggle = { nodeId, completed ->
                            viewModel.toggleNodeCompletion(nodeId, completed)
                        },
                        onNodeClick  = { viewModel.onNodeClick(it) },
                        onRouteChange = { viewModel.setActiveRoute(it) },
                        modifier     = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

// ─── Top App Bar ──────────────────────────────────────────────────────────────

/**
 * Custom top bar with:
 *   - Universe name + back button
 *   - Spoiler shield toggle (eye icon)
 *   - Animated progress bar showing completion fraction
 *   - Completion count label
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    uiState: TimelineUiState,
    onBack: () -> Unit,
    onSpoilerToggle: () -> Unit
) {
    val universeName = (uiState as? TimelineUiState.Success)?.universe?.name ?: ""
    val progressFraction = (uiState as? TimelineUiState.Success)?.progressFraction ?: 0f
    val completedCount = (uiState as? TimelineUiState.Success)?.completedCount ?: 0
    val totalCount = (uiState as? TimelineUiState.Success)?.totalNodeCount ?: 0
    val shieldEnabled = (uiState as? TimelineUiState.Success)?.spoilerShieldEnabled ?: true

    // Animate the progress bar fill smoothly as nodes are checked off
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress_bar"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(WatchOrderColors.VoidDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = universeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = WatchOrderColors.TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WatchOrderColors.TextPrimary
                    )
                }
            },
            actions = {
                // Spoiler shield toggle — eye icon changes to eye-off when disabled
                IconButton(onClick = onSpoilerToggle) {
                    Icon(
                        imageVector = if (shieldEnabled) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (shieldEnabled) "Spoiler shield ON — tap to disable"
                        else "Spoiler shield OFF — tap to enable",
                        tint = if (shieldEnabled) WatchOrderColors.SpoilerPurple
                        else WatchOrderColors.TextMuted
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WatchOrderColors.VoidDark
            )
        )

        // ── Progress bar + counter ────────────────────────────────────────────
        if (totalCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Completion label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount of $totalCount completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchOrderColors.TextSecondary
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = WatchOrderColors.CompletedGreen
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Segmented progress bar with a subtle glow on the filled portion
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(WatchOrderColors.CardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        WatchOrderColors.CompletedGreen,
                                        WatchOrderColors.AccentGold
                                    )
                                )
                            )
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // Bottom divider
        HorizontalDivider(
            color = WatchOrderColors.CardBorder,
            thickness = 0.5.dp
        )
    }
}

// ─── Success View (main content) ──────────────────────────────────────────────

/**
 * The primary success state layout:
 *   - Route filter chips (horizontal scrollable row)
 *   - The branching timeline (main content area)
 */
@Composable
private fun TimelineSuccessView(
    state: TimelineUiState.Success,
    onNodeToggle: (nodeId: String, currentlyCompleted: Boolean) -> Unit,
    onNodeClick: (nodeId: String) -> Unit,
    onRouteChange: (routeTag: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        // ── Route Filter Chips ─────────────────────────────────────────────────
        // Horizontally scrollable row of chips. Tapping one re-runs the route
        // filter algorithm and rebuilds the entire timeline. Fast enough to be instant.
        if (state.availableTags.isNotEmpty()) {
            RouteFilterChips(
                tags          = state.availableTags,
                activeRouteTag = state.activeRouteTag,
                onRouteSelected = onRouteChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            )
        }

        // ── Branching Timeline ─────────────────────────────────────────────────
        BranchingTimelineView(
            rows         = state.rows,
            onNodeToggle = { node -> onNodeToggle(node.node.id, node.isCompleted) },
            onNodeClick  = { node -> onNodeClick(node.node.id) },
            modifier     = Modifier.fillMaxSize()
        )
    }
}

/**
 * Horizontal scrolling row of route filter [FilterChip]s.
 * The active chip is highlighted with the gold accent color.
 */
@Composable
private fun RouteFilterChips(
    tags: List<ContextTag>,
    activeRouteTag: String,
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = tags,
            key   = { tag -> tag.id }
        ) { tag ->
            val isSelected = tag.id == activeRouteTag

            FilterChip(
                selected = isSelected,
                onClick  = { onRouteSelected(tag.id) },
                label    = {
                    Text(
                        text  = tag.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) WatchOrderColors.DeepSpace
                        else WatchOrderColors.TextSecondary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor     = WatchOrderColors.AccentGold,
                    selectedLabelColor         = WatchOrderColors.DeepSpace,
                    containerColor             = WatchOrderColors.CardSurface,
                    labelColor                 = WatchOrderColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled               = true,
                    selected              = isSelected,
                    borderColor           = WatchOrderColors.CardBorder,
                    selectedBorderColor   = WatchOrderColors.AccentGold,
                    borderWidth           = 0.5.dp,
                    selectedBorderWidth   = 1.dp
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// ─── Loading View ─────────────────────────────────────────────────────────────

@Composable
private fun TimelineLoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color        = WatchOrderColors.AccentGold,
            strokeWidth  = 2.dp,
            modifier     = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Loading universe…",
            style = MaterialTheme.typography.bodyMedium,
            color = WatchOrderColors.TextSecondary
        )
    }
}

// ─── Error View ───────────────────────────────────────────────────────────────

@Composable
private fun TimelineErrorView(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = WatchOrderColors.BranchCoral,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyLarge,
            color = WatchOrderColors.TextSecondary
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onRetry,
                border  = BorderStroke(1.dp, WatchOrderColors.AccentGold),
                colors  = ButtonDefaults.outlinedButtonColors(
                    contentColor = WatchOrderColors.AccentGold
                )
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}