package com.example.watchorderengine.ui.screens.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreenWrapper(
    onMediaClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val watching by viewModel.watchingList.collectAsStateWithLifecycle()
    val planned by viewModel.plannedList.collectAsStateWithLifecycle()
    val completed by viewModel.completedList.collectAsStateWithLifecycle()
    val dropped by viewModel.droppedList.collectAsStateWithLifecycle()
    val paused by viewModel.pausedList.collectAsStateWithLifecycle()
    val trending by viewModel.trendingList.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val nextUpItem by viewModel.nextUp.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()

    var state by remember {
        mutableStateOf(
            HomeUiState(
                shows = emptyList()
            )
        )
    }

    LaunchedEffect(avatarUrl) {
        state = state.copy(profilePictureUrl = avatarUrl)
    }

    val realShows = remember(watching, planned, completed, dropped, paused, trending, recommendations) {
        val mappedWatching = watching.map { it.toMediaShowItem("Watching") }
        val mappedPlanned = planned.map { it.toMediaShowItem("Planned") }
        val mappedCompleted = completed.map { it.toMediaShowItem("Completed") }
        val mappedDropped = dropped.map { it.toMediaShowItem("Dropped") }
        val mappedPaused = paused.map { it.toMediaShowItem("Paused") }
        val mappedTrending = trending.map { it.toMediaShowItem(null) }

        val mappedRecs = recommendations.map { rec ->
            MediaShowItem(
                id = rec.media.tmdbId,
                internalId = rec.media.id,
                title = rec.media.title,
                imageUrl = rec.media.posterUrl ?: "",
                genres = rec.media.genres,
                badge = "RECOMMENDED",
                watchlistStatus = "Recommended"
            )
        }

        (mappedWatching + mappedPlanned + mappedCompleted + mappedDropped + mappedPaused + mappedTrending + mappedRecs)
            .distinctBy { it.internalId }
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
        onSettingsClick = onSettingsClick,
        nextUpItem = nextUpItem,
        onResumeClick = { onMediaClick(it) },
        recommendations = recommendations
    )
}

private fun MediaSummary.toMediaShowItem(status: String?) = MediaShowItem(
    id = tmdbId,
    internalId = id,
    title = title,
    imageUrl = posterUrl ?: "",
    genres = genres,
    badge = mediaCategory.name,
    watchlistStatus = status
)
