package com.example.watchorderengine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchorderengine.data.model.CommunityPost
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
                        filterPosts(_searchQuery.value)
                    }.onFailure { e ->
                        _uiState.value = CommunityUiState.Error(e.message ?: "Failed to load the community feed.")
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        filterPosts(query)
    }

    private fun filterPosts(query: String) {
        if (query.isBlank()) {
            _uiState.value = CommunityUiState.Success(allPosts)
        } else {
            val filtered = allPosts.filter { 
                it.universeTitle.contains(query, ignoreCase = true) || 
                it.universeDescription.contains(query, ignoreCase = true) ||
                it.authorName.contains(query, ignoreCase = true)
            }
            _uiState.value = CommunityUiState.Success(filtered)
        }
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        observeFeed(showLoadingState = false)
    }

    fun likePost(postId: String) {
        val uid = currentUserId ?: return
        val currentState = _uiState.value as? CommunityUiState.Success ?: return
        val previousPosts = currentState.posts

        val optimisticPosts = previousPosts.map { post ->
            if (post.postId != postId) return@map post
            val alreadyLiked = uid in post.likedByUsers
            post.copy(
                likedByUsers = if (alreadyLiked) post.likedByUsers - uid else post.likedByUsers + uid,
                likesCount   = if (alreadyLiked) (post.likesCount - 1).coerceAtLeast(0) else post.likesCount + 1
            )
        }
        _uiState.value = CommunityUiState.Success(optimisticPosts)

        viewModelScope.launch {
            repository.toggleLikePost(postId, uid).onFailure {
                if (_uiState.value == CommunityUiState.Success(optimisticPosts)) {
                    _uiState.value = CommunityUiState.Success(previousPosts)
                }
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

    fun deletePost(postId: String, authorUserId: String) {
        viewModelScope.launch {
            repository.deletePost(postId, authorUserId)
        }
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
