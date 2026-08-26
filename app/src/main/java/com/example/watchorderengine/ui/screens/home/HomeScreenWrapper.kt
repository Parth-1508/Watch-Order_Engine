package com.example.watchorderengine.ui.screens.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.watchorderengine.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreenWrapper(
    onMediaClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onDiscoverClick: () -> Unit = {}
) {
    val viewModel: HomeViewModel = hiltViewModel()
    
    val watchlist = viewModel.watchlistPaged.collectAsLazyPagingItems()
    val activeCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val trendingList by viewModel.trendingList.collectAsStateWithLifecycle()
    val recentlyReleased by viewModel.recentlyReleased.collectAsStateWithLifecycle()
    val nextUpList by viewModel.nextUpList.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val watchingCount by viewModel.watchingCount.collectAsStateWithLifecycle()
    val plannedCount by viewModel.plannedCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val droppedCount by viewModel.droppedCount.collectAsStateWithLifecycle()
    val pausedCount by viewModel.pausedCount.collectAsStateWithLifecycle()

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val state = HomeUiState(
        activeCategory = activeCategory,
        searchQuery = searchQuery,
        isSearchOpen = isSearchOpen,
        isLoading = isLoading,
        profilePictureUrl = avatarUrl,
        watchingCount = watchingCount,
        plannedCount = plannedCount,
        completedCount = completedCount,
        droppedCount = droppedCount,
        pausedCount = pausedCount
    )

    HomeScreen(
        state = state,
        watchlist = watchlist,
        onCategorySelected = { viewModel.setCategory(it) },
        onSearchQueryChanged = { searchQuery = it },
        onSearchToggle = { 
            if (it) onSearchClick() 
            else isSearchOpen = false 
        },
        onShowClick = { onMediaClick(it) },
        onSettingsClick = onSettingsClick,
        onCalendarClick = onCalendarClick,
        onProfileClick = onProfileClick,
        onDiscoverClick = onDiscoverClick,
        getAvatarModel = { viewModel.getAvatarModel(it) },
        nextUpItems = nextUpList,
        onResumeClick = { item -> 
            val season = item.targetSeason
            val mediaId = item.mediaId
            if (season != null) {
                onMediaClick(mediaId + "?initialSeason=$season")
            } else {
                onMediaClick(mediaId)
            }
        },
        recommendations = recommendations,
        trendingList = trendingList,
        recentlyReleased = recentlyReleased
    )
}
