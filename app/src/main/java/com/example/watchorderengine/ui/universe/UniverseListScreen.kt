package com.example.watchorderengine.ui.universe

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.Universe
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.viewmodel.UniverseListUiState
import com.example.watchorderengine.viewmodel.UniverseListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseListScreen(
    onUniverseClick: (String) -> Unit,
    onCommunityRedirect: () -> Unit = {},
    viewModel: UniverseListViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "SELECT UNIVERSE", 
                        fontWeight = FontWeight.Black, 
                        modifier = Modifier.graphicsLayer {
                            if (theme.isComic) rotationZ = -1f
                        }
                    ) 
                },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.textPrimary
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is UniverseListUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = theme.accent
                )
                is UniverseListUiState.Error -> Text(
                    state.message, 
                    color = theme.statusFiller, 
                    modifier = Modifier.align(Alignment.Center)
                )
                is UniverseListUiState.Success -> {
                    if (state.universes.isEmpty()) {
                        EmptyUniversesView(onCommunityRedirect)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.universes) { universe ->
                                val isRegenerating = (state as? UniverseListUiState.Success)
                                    ?.regeneratingUniverseIds?.contains(universe.id) ?: false

                                UniverseItem(
                                    universe = universe,
                                    isRegenerating = isRegenerating,
                                    onClick = { onUniverseClick(universe.id) },
                                    onRegenerate = { viewModel.regenerateUniverse(universe.id) },
                                    onDelete = { viewModel.deleteUniverse(universe.id) },
                                    onToggleCompletion = { viewModel.toggleUniverseCompletion(universe.id, it) }
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
fun EmptyUniversesView(onCommunityRedirect: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(theme.accent.copy(0.1f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = theme.accent.copy(0.5f)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            Text(
                "NO GRAPHS YET",
                color = theme.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                "Generate a watch order from any show detail screen, or import community-shared timelines to get started.",
                color = theme.textSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCommunityRedirect,
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Explore, null, tint = Color.Black)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text("EXPLORE COMMUNITY", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UniverseItem(
    universe: Universe,
    isRegenerating: Boolean = false,
    onClick: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onToggleCompletion: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .graphicsLayer {
                if (theme.isComic) rotationZ = if (universe.id.hashCode() % 2 == 0) 0.5f else -0.5f
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (!isRegenerating) showMenu = true }
            ),
        shape = RoundedCornerShape(theme.appRadius),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (theme.isComic) BorderStroke(1.5.dp, Color.Black) else null
    ) {
        Box(modifier = Modifier.height(160.dp)) {
            AsyncImage(
                model = universe.bannerUrl ?: universe.posterUrl,
                contentDescription = "Cover for ${universe.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, theme.background),
                            startY = 100f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                if (isRegenerating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = theme.accent,
                        trackColor = theme.accent.copy(alpha = 0.2f)
                    )
                }
                Text(
                    universe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    universe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Context Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(theme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Regenerate Timeline", color = theme.textPrimary) },
                    onClick = {
                        showMenu = false
                        onRegenerate()
                    },
                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = theme.accent) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Graph", color = theme.textPrimary) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = theme.statusFiller) }
                )
                DropdownMenuItem(
                    text = { Text("Mark All Watched", color = theme.textPrimary) },
                    onClick = {
                        showMenu = false
                        onToggleCompletion(true)
                    },
                    leadingIcon = { Icon(Icons.Default.Check, null, tint = theme.statusCanon) }
                )
                DropdownMenuItem(
                    text = { Text("Mark All Unwatched", color = theme.textPrimary) },
                    onClick = {
                        showMenu = false
                        onToggleCompletion(false)
                    },
                    leadingIcon = { Icon(Icons.Default.Close, null, tint = theme.textSecondary) }
                )
            }
        }
    }
}
