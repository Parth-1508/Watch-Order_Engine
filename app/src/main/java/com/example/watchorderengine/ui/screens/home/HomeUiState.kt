package com.example.watchorderengine.ui.screens.home

data class MediaShowItem(
    val id: Int,
    val internalId: String,
    val title: String,
    val imageUrl: String,
    val genres: List<String>,
    val badge: String,
    val progress: Int? = null,
    val totalEpisodes: Int? = null,
    val watchlistStatus: String? = null
)

data class HomeUiState(
    val categories: List<String> = listOf("Watching", "Planned", "Completed", "Paused", "Dropped"),
    val activeCategory: String = "Watching",
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val shows: List<MediaShowItem> = emptyList(),
    val isLoading: Boolean = true
)
