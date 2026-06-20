package com.example.watchorderengine.ui.universe

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.Universe
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.viewmodel.UniverseListUiState
import com.example.watchorderengine.viewmodel.UniverseListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseListScreen(
    onUniverseClick: (String) -> Unit,
    viewModel: UniverseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Universe") },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WatchOrderColors.VoidDark,
                    titleContentColor = WatchOrderColors.TextPrimary
                )
            )
        },
        containerColor = WatchOrderColors.VoidDark
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is UniverseListUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UniverseListUiState.Error -> Text(state.message, color = WatchOrderColors.BranchCoral, modifier = Modifier.align(Alignment.Center))
                is UniverseListUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.universes) { universe ->
                            UniverseItem(universe, onClick = { onUniverseClick(universe.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UniverseItem(universe: Universe, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WatchOrderColors.CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.height(160.dp)) {
            AsyncImage(
                model = universe.bannerUrl ?: universe.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, WatchOrderColors.DeepSpace),
                            startY = 100f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    universe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WatchOrderColors.TextPrimary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    universe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WatchOrderColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
