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
    onProfileClick: () -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    
    val watchlist = viewModel.watchlistPaged.collectAsLazyPagingItems()
    val activeCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val trendingList by viewModel.trendingList.collectAsStateWithLifecycle()
    val recentlyReleased by viewModel.recentlyReleased.collectAsStateWithLifecycle()
    val nextUpItem by viewModel.nextUp.collectAsStateWithLifecycle()
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
        onProfileClick = onProfileClick,
        getAvatarModel = { viewModel.getAvatarModel(it) },
        nextUpItem = nextUpItem,
        onResumeClick = { internalId -> 
            val season = nextUpItem?.targetSeason
            if (season != null) {
                onMediaClick(internalId + "?initialSeason=$season")
            } else {
                onMediaClick(internalId)
            }
        },
        recommendations = recommendations,
        trendingList = trendingList,
        recentlyReleased = recentlyReleased
    )
}
