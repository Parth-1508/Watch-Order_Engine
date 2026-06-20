package com.example.watchorderengine.ui.screens.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreenWrapper(
    onMediaClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val watching by viewModel.watchingList.collectAsState()
    val planned by viewModel.plannedList.collectAsState()
    val trending by viewModel.trendingList.collectAsState()
    
    var state by remember {
        mutableStateOf(
            HomeUiState(
                shows = emptyList()
            )
        )
    }
    
    val realShows = remember(watching, planned, trending) {
        val mappedWatching = watching.map { it.toMediaShowItem("Watching") }
        val mappedPlanned = planned.map { it.toMediaShowItem("Planned") }
        val mappedTrending = trending.map { it.toMediaShowItem(null) }
        
        (mappedWatching + mappedPlanned + mappedTrending).distinctBy { it.internalId }
    }
    
    LaunchedEffect(realShows) {
        state = state.copy(shows = realShows)
    }

    HomeScreen(
        state = state,
        onCategorySelected = { state = state.copy(activeCategory = it) },
        onSearchQueryChanged = { state = state.copy(searchQuery = it) },
        onSearchToggle = { 
            if (it) onSearchClick() 
            else state = state.copy(isSearchOpen = false) 
        },
        onShowClick = { onMediaClick(it.internalId) },
        onSettingsClick = onSettingsClick
    )
}

private fun com.example.watchorderengine.data.model.MediaSummary.toMediaShowItem(status: String?) = MediaShowItem(
    id = tmdbId,
    internalId = id,
    title = title,
    imageUrl = posterUrl ?: "",
    genres = genres,
    badge = mediaCategory.name,
    watchlistStatus = status
)
