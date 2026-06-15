package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.ui.components.MediaGridItem
import com.example.watchorderengine.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMediaClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onUniversesClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val watchingList by viewModel.watchingList.collectAsState()
    val plannedList by viewModel.plannedList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Order Engine", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onUniversesClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Universes")
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (watchingList.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("Continue Watching", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(watchingList) { media ->
                    MediaGridItem(media = media, onClick = { onMediaClick(media.id) })
                }
            }

            if (plannedList.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("Planned to Watch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                }
                items(plannedList) { media ->
                    MediaGridItem(media = media, onClick = { onMediaClick(media.id) })
                }
            }

            if (watchingList.isEmpty() && plannedList.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Your list is empty. Start by searching for media!")
                    }
                }
            }
        }
    }
}
