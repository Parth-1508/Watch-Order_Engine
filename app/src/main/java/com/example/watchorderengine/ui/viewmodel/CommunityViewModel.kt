package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.CommunityPost
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.repository.CommunityRepository
import com.example.watchorderengine.data.repository.TmdbRepository
import com.example.watchorderengine.data.cache.TmdbMetadataCache
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for [CommunityScreen], driven entirely by [CommunityViewModel.uiState]. */
sealed interface CommunityUiState {
    object Loading : CommunityUiState
    data class Success(val posts: List<CommunityPost>) : CommunityUiState
    data class Error(val message: String) : CommunityUiState
}

/** Separate state for the "Share Timeline" action. */
sealed interface ShareTimelineState {
    object Idle : ShareTimelineState
    object Sharing : ShareTimelineState
    object Shared : ShareTimelineState
    data class Failed(val message: String) : ShareTimelineState
}

sealed interface ImportState {
    object Idle : ImportState
    object Importing : ImportState
    data class Success(val universeId: String) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val auth: FirebaseAuth,
    private val tmdbRepo: TmdbRepository,
    private val tmdbCache: TmdbMetadataCache
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _shareState = MutableStateFlow<ShareTimelineState>(ShareTimelineState.Idle)
    val shareState: StateFlow<ShareTimelineState> = _shareState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** Currently selected post for the detail bottom sheet. */
    private val _selectedPost = MutableStateFlow<CommunityPost?>(null)
    val selectedPost: StateFlow<CommunityPost?> = _selectedPost.asStateFlow()

    private var allPosts = listOf<CommunityPost>()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private var feedJob: Job? = null

    init {
        observeFeed()
    }

    private fun observeFeed(showLoadingState: Boolean = true) {
        feedJob?.cancel()
        if (showLoadingState) _uiState.value = CommunityUiState.Loading

        feedJob = viewModelScope.launch {
            repository.fetchGlobalFeed()
                .onEach { _isRefreshing.value = false }
                .collect { result ->
                    result.onSuccess { posts ->
                        allPosts = posts
                        filterPosts(_searchQuery.value, _selectedTag.value)
                        
                        // Automatically fetch metadata for the first node of each post 
                        // so thumbnails aren't blank if they were left null.
                        viewModelScope.launch {
                            val firstNodes = posts.mapNotNull { post ->
                                SharedTimelineCodec.decode(post.nodesJson)?.nodes?.firstOrNull()
                            }
                            tmdbRepo.fetchAndCache(firstNodes)
                        }
                    }.onFailure { e ->
                        _uiState.value = CommunityUiState.Error(e.message ?: "Failed to load the community feed.")
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        filterPosts(query, _selectedTag.value)
    }

    /**
     * Tapping a Trending Tag chip filters the feed to that tag; tapping the
     * same tag again clears it back to the plain feed.
     */
    fun onTagSelected(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
        filterPosts(_searchQuery.value, _selectedTag.value)
    }

    private fun filterPosts(query: String, tag: String?) {
        var filtered = allPosts
        
        if (tag != null) {
            val tagLower = tag.lowercase()
            filtered = filtered.filter { post ->
                // 1. Check explicit tags
                val hasTag = post.tags.any { it.equals(tag, ignoreCase = true) }
                if (hasTag) return@filter true
                
                // 2. Keyword fallback for older posts or implicit matching
                val content = (post.universeTitle + " " + post.universeDescription).lowercase()
                val matches = when (tagLower) {
                    "marvel" -> content.contains("spiderman") || content.contains("spider-man") || content.contains("avengers") || content.contains("marvel") || content.contains("iron man") || content.contains("mcu")
                    "star wars" -> content.contains("star wars") || content.contains("jedi") || content.contains("mandalorian") || content.contains("skywalker")
                    "dc universe" -> content.contains("batman") || content.contains("superman") || content.contains("wonder woman") || content.contains("justice league") || content.contains("dceu") || content.contains("dc universe")
                    "anime" -> content.contains("anime") || content.contains("naruto") || content.contains("fate/") || content.contains("one piece") || content.contains("dragon ball") || content.contains("bleach")
                    "horror" -> content.contains("horror") || content.contains("conjuring") || content.contains("annabelle") || content.contains("nun") || content.contains("insidious") || content.contains("scary")
                    "sci-fi" -> content.contains("sci-fi") || content.contains("science fiction") || content.contains("interstellar") || content.contains("star trek") || content.contains("dune")
                    else -> false
                }
                matches
            }
        }
        
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.universeTitle.contains(query, ignoreCase = true) || 
                it.universeDescription.contains(query, ignoreCase = true) ||
                it.authorName.contains(query, ignoreCase = true) ||
                it.tags.any { t -> t.contains(query, ignoreCase = true) }
            }
        }
        
        _uiState.value = CommunityUiState.Success(filtered)
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        observeFeed(showLoadingState = false)
    }

    fun likePost(postId: String) {
        val uid = currentUserId ?: return
        
        // Optimistic Update: Update both the live UI state AND the backing list
        // to prevent search/filter snapbacks before the network responds.
        val updateFunc = { posts: List<CommunityPost> ->
            posts.map { post ->
                if (post.postId != postId) post
                else {
                    val alreadyLiked = uid in post.likedByUsers
                    post.copy(
                        likedByUsers = if (alreadyLiked) post.likedByUsers - uid else post.likedByUsers + uid,
                        likesCount   = if (alreadyLiked) (post.likesCount - 1).coerceAtLeast(0L) else post.likesCount + 1L
                    )
                }
            }
        }

        val previousAllPosts = allPosts
        allPosts = updateFunc(allPosts)
        filterPosts(_searchQuery.value, _selectedTag.value)

        viewModelScope.launch {
            repository.toggleLikePost(postId, uid).onFailure {
                // Revert on failure
                allPosts = previousAllPosts
                filterPosts(_searchQuery.value, _selectedTag.value)
            }
        }
    }

    fun shareTimeline(title: String, description: String, nodesJson: String) {
        if (title.isBlank()) {
            _shareState.value = ShareTimelineState.Failed("Give your timeline a title before sharing.")
            return
        }
        viewModelScope.launch {
            _shareState.value = ShareTimelineState.Sharing
            repository.shareTimeline(title, description, nodesJson).fold(
                onSuccess = { _shareState.value = ShareTimelineState.Shared },
                onFailure = { e -> _shareState.value = ShareTimelineState.Failed(e.message ?: "Share failed.") }
            )
        }
    }

    fun resetShareState() {
        _shareState.value = ShareTimelineState.Idle
    }

    fun selectPost(post: CommunityPost?) {
        _selectedPost.value = post
        if (post != null) {
            fetchPostMetadata(post)
        }
    }

    private fun fetchPostMetadata(post: CommunityPost) {
        val payload = com.example.watchorderengine.data.model.SharedTimelineCodec.decode(post.nodesJson) ?: return
        viewModelScope.launch {
            tmdbRepo.fetchAndCache(payload.nodes)
        }
    }

    fun importTimeline(post: CommunityPost) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing
            repository.importTimeline(post).fold(
                onSuccess = { id -> _importState.value = ImportState.Success(id) },
                onFailure = { e -> _importState.value = ImportState.Failed(e.message ?: "Import failed") }
            )
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun getCache() = tmdbCache
}
