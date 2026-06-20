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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.ui.timeline.components.BranchingTimelineView
import com.example.watchorderengine.viewmodel.*

@Composable
fun TimelineScreen(
    universeId: String,
    onBack: () -> Unit,
    onNodeDetail: (nodeId: String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(universeId) { viewModel.initialize(universeId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineEvent.NavigateToDetail -> onNodeDetail(event.nodeId)
                is TimelineEvent.ShowSnackbar -> { /* Handle snackbar */ }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchOrderColors.DeepSpace)
            .drawBehind {
                // Ambient Starfield
                val random = java.util.Random(42)
                repeat(100) {
                    val x = random.nextFloat() * size.width
                    val y = random.nextFloat() * size.height
                    val alpha = 0.1f + random.nextFloat() * 0.4f
                    val radius = 0.5.dp.toPx() + random.nextFloat() * 1.5.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }
                
                // Nebula glow
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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimelineHeader(
                uiState = uiState,
                onBack = onBack,
                onSearchToggle = { /* Search */ },
                onSpoilerToggle = { viewModel.toggleSpoilerShield() }
            )

            when (val state = uiState) {
                is TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WatchOrderColors.AccentGold) }
                is TimelineUiState.Success -> TimelineContent(state, viewModel)
                is TimelineUiState.Error -> Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    uiState: TimelineUiState,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, null, tint = WatchOrderColors.AccentGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(universeName, color = WatchOrderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            IconButton(onClick = onSearchToggle, modifier = Modifier.size(36.dp).background(WatchOrderColors.ElevatedSurface, CircleShape)) {
                Icon(Icons.Default.Search, null, tint = WatchOrderColors.TextSecondary, modifier = Modifier.size(18.dp))
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
            onNodeClick = { node -> viewModel.onNodeClick(node.node.id) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

