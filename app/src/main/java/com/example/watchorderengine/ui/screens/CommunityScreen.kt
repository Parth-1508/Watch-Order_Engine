package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.model.CommunityPost
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.data.model.SharedTimelinePayload
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.data.cache.TmdbMetadataCache
import com.example.watchorderengine.data.model.CommunityComment
import com.example.watchorderengine.network.model.TmdbMediaDetail
import com.example.watchorderengine.network.model.TmdbMediaType
import com.example.watchorderengine.ui.components.TimelineCommentsSheet
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.timeline.components.BranchingTimelineView
import com.example.watchorderengine.ui.viewmodel.CommunityUiState
import com.example.watchorderengine.ui.viewmodel.CommunityViewModel
import com.example.watchorderengine.ui.viewmodel.ImportState
import com.example.watchorderengine.viewmodel.DisplayNode
import com.example.watchorderengine.viewmodel.TimelineRow
import com.example.watchorderengine.viewmodel.TimelineViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = hiltViewModel(),
    onMediaClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onFriendActivityClick: () -> Unit = {},
    onCreateGraphClick: () -> Unit = {}
) {
    val theme         = LocalAppTheme.current
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing  by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val importState   by viewModel.importState.collectAsStateWithLifecycle()
    val selectedPost  by viewModel.selectedPost.collectAsStateWithLifecycle()
    val searchQuery   by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTag   by viewModel.selectedTag.collectAsStateWithLifecycle()
    val hasUnreadNotifs by viewModel.hasUnreadNotifications.collectAsStateWithLifecycle()
    
    val currentUserId = viewModel.currentUserId
    val listState     = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                snackbarHostState.showSnackbar("Timeline imported successfully!")
                viewModel.resetImportState()
            }
            is ImportState.Failed -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
                viewModel.resetImportState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = theme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGraphClick,
                containerColor = theme.accent
            ) {
                Icon(Icons.Default.AccountTree, "Build a timeline")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top Header ──────────────────────────────────────────────────
            CommunityHeader(
                searchQuery = searchQuery,
                onSearchChange = { viewModel.onSearchQueryChanged(it) },
                onNotificationsClick = onNotificationsClick,
                onFriendActivityClick = onFriendActivityClick,
                hasUnreadNotifications = hasUnreadNotifs,
                isLoading = uiState is CommunityUiState.Loading
            )

            CommunityPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh    = { viewModel.refreshFeed() },
                listState    = listState
            ) {
                val posts = (uiState as? CommunityUiState.Success)?.posts ?: emptyList()
                val heroPost = remember(posts) { posts.maxByOrNull { it.likesCount } ?: posts.firstOrNull() }
                val otherPosts = remember(posts, heroPost) { posts.filter { it.postId != heroPost?.postId } }

                LazyColumn(
                    state             = listState,
                    modifier          = Modifier.fillMaxSize(),
                    contentPadding    = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. External Discussion Hub
                    item { DiscussionHubSection() }

                    // 2. Quick Tags / Filters
                    item { 
                        TrendingTagsSection(
                            selectedTag = selectedTag,
                            onTagClick = { tag ->
                                viewModel.onTagSelected(tag)
                            }
                        ) 
                    }

                    // 3. Feed Status/Content
                    item {
                        Text(
                            "GLOBAL ACTIVITY",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    when (val state = uiState) {
                        is CommunityUiState.Loading -> {
                            item {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = theme.accent)
                                }
                            }
                        }
                        is CommunityUiState.Error -> {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(state.message, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
                                        Button(onClick = { viewModel.refreshFeed() }, modifier = Modifier.padding(16.dp)) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                        is CommunityUiState.Success -> {
                            if (state.posts.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                        EmptyFeedView(theme)
                                    }
                                }
                            } else {
                                // Hero Post
                                if (heroPost != null) {
                                    item {
                                        HeroPostCard(
                                            post = heroPost,
                                            currentUserId = currentUserId,
                                            onLikeClick = { viewModel.likePost(heroPost.postId) },
                                            onImportClick = { viewModel.importTimeline(heroPost) },
                                            onClick = { viewModel.selectPost(heroPost) },
                                            tmdbCache = viewModel.getCache()
                                        )
                                    }
                                }

                                items(otherPosts, key = { it.postId }) { post ->
                                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        CommunityPostCard(
                                            post            = post,
                                            currentUserId   = currentUserId,
                                            onLikeClick     = { viewModel.likePost(post.postId) },
                                            onImportClick   = { viewModel.importTimeline(post) },
                                            onClick         = { viewModel.selectPost(post) },
                                            tmdbCache       = viewModel.getCache()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedPost != null) {
        CommunityPostDetailSheet(
            post = selectedPost!!,
            currentUserId = currentUserId,
            onDismiss = { viewModel.selectPost(null) },
            onImport = { viewModel.importTimeline(selectedPost!!) },
            onDelete = {
                viewModel.deletePost(selectedPost!!.postId, selectedPost!!.userId)
                viewModel.selectPost(null)
            },
            onCommentsClick = { viewModel.openComments(selectedPost!!) },
            importState = importState,
            onMediaClick = onMediaClick,
            onAuthorClick = onAuthorClick,
            getAvatarModel = { viewModel.getAvatarModel(it) },
            tmdbCache = viewModel.getCache()
        )
    }

    val commentsPost by viewModel.commentsPost.collectAsStateWithLifecycle()
    if (commentsPost != null) {
        CommentsSheet(
            post = commentsPost!!,
            currentUserId = currentUserId,
            comments = viewModel.comments.collectAsStateWithLifecycle().value,
            isLoading = viewModel.commentsLoading.collectAsStateWithLifecycle().value,
            getAvatarModel = { viewModel.getAvatarModel(it) },
            onDismiss = { viewModel.closeComments() },
            onSend = { text, parentId -> viewModel.postComment(text, parentId) },
            onDeleteComment = { viewModel.deleteComment(it) },
            onLikeComment = { viewModel.likeComment(it) },
            onAuthorClick = onAuthorClick
        )
    }
}

@Composable
fun HeroPostCard(
    post: CommunityPost,
    currentUserId: String?,
    onLikeClick: () -> Unit,
    onImportClick: () -> Unit,
    onClick: () -> Unit,
    tmdbCache: TmdbMetadataCache
) {
    val theme = LocalAppTheme.current
    
    // Resolve poster: Payload URL > Cache Fallback (Observable via TmdbMetadataCache)
    val posterUrl by remember(post.nodesJson) {
        derivedStateOf {
            val payload = SharedTimelineCodec.decode(post.nodesJson)
            val firstNode = payload?.nodes?.firstOrNull() ?: return@derivedStateOf null
            
            if (!firstNode.posterUrl.isNullOrBlank()) firstNode.posterUrl
            else (tmdbCache.get(firstNode.tmdb_id) as? TmdbFetchState.Success)?.detail?.posterUrl
        }
    }

    val isLiked = currentUserId != null && (currentUserId in post.likedByUsers)
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "FEATURED TIMELINE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = theme.accent,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .graphicsLayer {
                    if (theme.isComic) rotationZ = 1f
                },
            shape = RoundedCornerShape(theme.appRadius.coerceAtLeast(16.dp)),
            color = theme.surface,
            onClick = onClick,
            border = if (theme.isComic) BorderStroke(2.dp, Color.Black) else BorderStroke(1.dp, theme.border.copy(alpha = 0.1f)),
            tonalElevation = 8.dp
        ) {
            Box {
                // Background Image
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.4f
                    )
                }
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, theme.surface.copy(0.95f)),
                                startY = 100f
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = theme.accent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "FEATURED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.Favorite, null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${post.likesCount} Enthusiasts", color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        post.universeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = theme.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.isOfficial) {
                        Surface(
                            color = theme.accent, 
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 8.dp),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified, 
                                    contentDescription = null, 
                                    tint = Color.White, 
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "CREATED BY WOE", 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            "by ${post.authorName}",
                            color = theme.accent.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Big Like & Import Buttons for Hero
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Import Button
                    Surface(
                        onClick = onImportClick,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = theme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(2.dp, theme.accent.copy(alpha = 0.5f)),
                        tonalElevation = 12.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Import",
                                tint = theme.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Like Button
                    Surface(
                        onClick = onLikeClick,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = if (isLiked) Color(0xFFFF4B6E) else theme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(2.dp, if (isLiked) Color.White.copy(0.3f) else Color(0xFFFF4B6E).copy(alpha = 0.5f)),
                        tonalElevation = 12.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.White else Color(0xFFFF4B6E),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNotificationsClick: () -> Unit = {},
    onFriendActivityClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    isLoading: Boolean = false
) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                if (theme.isComic) rotationZ = -1f
            }
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(theme.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.example.watchorderengine.R.drawable.ic_launcher_foreground),
                contentDescription = "Watch Order Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onFriendActivityClick,
                modifier = Modifier.background(theme.surface, CircleShape).size(40.dp)
            ) {
                Icon(Icons.Default.People, "Friend Activity", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.background(theme.surface, CircleShape).size(40.dp)
                ) {
                    Icon(Icons.Outlined.Notifications, null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                }
                if (hasUnreadNotifications) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .background(theme.accent, CircleShape)
                            .border(2.dp, theme.background, CircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                if (theme.isComic) rotationZ = 0.5f
            },
            placeholder = { Text("Search timelines, universes, or authors...", fontSize = 14.sp, color = theme.textSecondary.copy(0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.accent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, null, tint = theme.textSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.surface,
                unfocusedContainerColor = theme.surface,
                disabledContainerColor = theme.surface,
                focusedIndicatorColor = theme.accent,
                unfocusedIndicatorColor = theme.border.copy(0.5f),
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                cursorColor = theme.accent
            ),
            singleLine = true
        )
    }
}

@Composable
fun DiscussionHubSection(showTitle: String? = null, imdbId: String? = null) {
    val theme = LocalAppTheme.current
    val uriHandler = LocalUriHandler.current
    
    val encodedTitle = remember(showTitle) {
        showTitle?.let { try { URLEncoder.encode(it, "UTF-8") } catch(e: Exception) { null } }
    }

    val forums = remember(showTitle, imdbId, encodedTitle) {
        if (showTitle != null) {
            val imdbUrl = if (!imdbId.isNullOrBlank()) "https://www.imdb.com/title/$imdbId/reviews"
                          else "https://www.imdb.com/find/?q=$encodedTitle"
            val rtUrl = "https://www.google.com/search?q=site:rottentomatoes.com+$encodedTitle"
            val redditUrl = "https://www.reddit.com/r/movies/search/?q=$encodedTitle"
            val letterboxdUrl = "https://letterboxd.com/search/$encodedTitle/"

            listOf(
                ForumLink("IMDb Reviews", "Direct Reviews", imdbUrl, Icons.Default.StarOutline),
                ForumLink("Rotten Tomatoes", "Critics & Ratings", rtUrl, Icons.Default.Poll),
                ForumLink("Reddit Thread", "Community Search", redditUrl, Icons.Default.ChatBubbleOutline),
                ForumLink("Letterboxd", "Film Journal", letterboxdUrl, Icons.Default.Visibility)
            )
        } else {
            listOf(
                ForumLink("Reddit Movies", "r/movies", "https://www.reddit.com/r/movies/", Icons.Default.ChatBubbleOutline),
                ForumLink("IMDb Boards", "Community", "https://www.imdb.com/community/", Icons.Default.StarOutline),
                ForumLink("Letterboxd", "Journal", "https://letterboxd.com/journal/", Icons.Default.Visibility),
                ForumLink("Rotten Tomatoes", "Critics", "https://www.rottentomatoes.com/", Icons.Default.Poll),
                ForumLink("Fandom Wiki", "Knowledge", "https://www.fandom.com/", Icons.Default.MenuBook)
            )
        }
    }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "EXTERNAL DISCUSSION HUBS",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = theme.accent,
            letterSpacing = 1.sp
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(forums) { forum ->
                Surface(
                    onClick = { uriHandler.openUri(forum.url) },
                    modifier = Modifier
                        .width(160.dp)
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.textSecondary.copy(0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.accent.copy(0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(forum.icon, null, tint = theme.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(forum.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary, maxLines = 1)
                            Text(forum.subtitle, fontSize = 11.sp, color = theme.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingTagsSection(
    selectedTag: String?,
    onTagClick: (String?) -> Unit
) {
    val theme = LocalAppTheme.current
    val tags = listOf("Marvel", "Star Wars", "DC Universe", "Anime", "Horror", "Sci-Fi")
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags) { tag ->
            val isSelected = selectedTag.equals(tag, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onTagClick(if (isSelected) null else tag) },
                label = { Text(tag, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = theme.textSecondary,
                    selectedContainerColor = theme.accent.copy(alpha = 0.2f),
                    selectedLabelColor = theme.accent
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = theme.textSecondary.copy(alpha = 0.2f),
                    selectedBorderColor = theme.accent,
                    borderWidth = 1.dp,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

data class ForumLink(val title: String, val subtitle: String, val url: String, val icon: ImageVector)

@Composable
fun EmptyFeedView(theme: com.example.watchorderengine.ui.theme.AppThemeConfig) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(theme.accent.copy(0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = theme.accent.copy(0.5f)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "NO TIMELINES YET",
            color = theme.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Try a different search or be the first to share your watch order!",
            color = theme.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPostCard(
    post: CommunityPost,
    currentUserId: String?,
    onLikeClick: () -> Unit,
    onImportClick: () -> Unit,
    onClick: () -> Unit,
    tmdbCache: TmdbMetadataCache
) {
    val theme = LocalAppTheme.current
    val isLiked = currentUserId != null && (currentUserId in post.likedByUsers)
    val isOwner = currentUserId != null && currentUserId == post.userId

    // Resolve poster: Explicit Banner > Cache Fallback (Observable via TmdbMetadataCache)
    // We avoid decoding the full nodesJson during scroll by relying on post.bannerPosterUrl.
    val posterUrl by remember(post.postId, post.bannerPosterUrl) {
        derivedStateOf {
            if (!post.bannerPosterUrl.isNullOrBlank()) {
                post.bannerPosterUrl
            } else {
                // Fallback: If banner is missing, try to get the first node's ID from the payload
                // and check the cache. We only do this if banner is null.
                val payload = SharedTimelineCodec.decode(post.nodesJson)
                val firstNode = payload?.nodes?.firstOrNull() ?: return@derivedStateOf null
                (tmdbCache.get(firstNode.tmdb_id) as? TmdbFetchState.Success)?.detail?.posterUrl
            }
        }
    }

    val accentColor = remember(post.accentColor) {
        post.accentColor?.let { try { Color(android.graphics.Color.parseColor("#$it")) } catch(e: Exception) { null } }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .graphicsLayer {
                if (theme.isComic) rotationZ = if (post.postId.hashCode() % 2 == 0) 0.5f else -0.5f
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* Could show a menu if needed */ }
            ),
        shape = RoundedCornerShape(theme.appRadius),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = when {
            theme.isComic -> BorderStroke(1.5.dp, Color.Black)
            accentColor != null -> BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            else -> null
        }
    ) {
        Box(modifier = Modifier.height(160.dp).background(theme.surfaceHover)) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = "Cover for ${post.universeTitle}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            } else {
                // Background fallback for blank posters
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            colors = listOf(theme.accent.copy(0.1f), theme.surface)
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieFilter, 
                        contentDescription = null, 
                        tint = theme.accent.copy(0.2f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
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
                Text(
                    post.universeTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (post.isOfficial) {
                        Surface(
                            color = accentColor ?: theme.accent, 
                            shape = RoundedCornerShape(4.dp),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified, 
                                    contentDescription = null, 
                                    tint = Color.White, 
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "CREATED BY WOE", 
                                    fontSize = 8.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            "by ${post.authorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (isLiked) Color(0xFFFF4B6E) else theme.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${post.likesCount}", color = theme.textSecondary, fontSize = 12.sp)
                }
            }

            // Big Like & Import Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Import Button
                Surface(
                    onClick = onImportClick,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = theme.surface.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f)),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import",
                            tint = theme.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Like Button
                Surface(
                    onClick = onLikeClick,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = if (isLiked) Color(0xFFFF4B6E) else theme.surface.copy(alpha = 0.8f),
                    border = if (!isLiked) BorderStroke(1.dp, Color(0xFFFF4B6E).copy(alpha = 0.5f)) else null,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.White else Color(0xFFFF4B6E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPostDetailSheet(
    post: CommunityPost,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onCommentsClick: () -> Unit = {},
    importState: ImportState,
    onMediaClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    getAvatarModel: (String?) -> Any?,
    tmdbCache: TmdbMetadataCache
) {
    val theme = LocalAppTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val payload = remember(post.nodesJson) { SharedTimelineCodec.decode(post.nodesJson) }
    val rows = remember(payload) { payload?.let { computePreviewRows(it, tmdbCache) } ?: emptyList() }
    val accentColor = remember(post.accentColor) {
        post.accentColor?.let { try { Color(android.graphics.Color.parseColor("#$it")) } catch(e: Exception) { null } }
    }

    val isOwner = currentUserId != null && currentUserId == post.userId
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = theme.surface,
            title = { Text("Delete Timeline?", color = theme.textPrimary, fontWeight = FontWeight.Black) },
            text = { Text("This will permanently remove this timeline from the global community feed. This action cannot be undone.", color = theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = { 
                    showDeleteConfirm = false
                    onDelete() 
                }) {
                    Text("DELETE", color = theme.statusFiller, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", color = theme.textSecondary)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textSecondary.copy(0.3f)) },
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Starry Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val random = java.util.Random(post.postId.hashCode().toLong())
                repeat(40) {
                    drawCircle(
                        color = theme.accent.copy(alpha = 0.1f + random.nextFloat() * 0.2f),
                        radius = (1f + random.nextFloat() * 2f).dp.toPx(),
                        center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable(enabled = post.userId.isNotBlank()) { onAuthorClick(post.userId) },
                            shape = CircleShape,
                            color = theme.surface,
                            border = BorderStroke(2.dp, theme.accent.copy(0.3f))
                        ) {
                            AsyncImage(
                                model = getAvatarModel(post.authorAvatarUrl) ?: "https://ui-avatars.com/api/?name=${post.authorName}",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                post.universeTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = theme.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (post.isOfficial) {
                                Surface(
                                    color = accentColor ?: theme.accent, 
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("CREATED BY WOE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            } else {
                                Text(
                                    "shared by ${post.authorName}",
                                    color = theme.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Discussion Comments Button
                            Surface(
                                onClick = onCommentsClick,
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = theme.surface.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, theme.border.copy(alpha = 0.3f)),
                                tonalElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = "Discussion",
                                        tint = theme.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Import Button
                            Surface(
                                onClick = onImport,
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = theme.surface.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f)),
                                enabled = importState !is ImportState.Importing,
                                tonalElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (importState is ImportState.Importing) {
                                        CircularProgressIndicator(
                                            color = theme.accent, 
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Import",
                                            tint = theme.accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            if (isOwner && !post.isOfficial) {
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete my post",
                                        tint = theme.statusFiller,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (post.universeDescription.isNotBlank()) {
                        var descriptionExpanded by remember(post.postId) { mutableStateOf(false) }
                        var descriptionCanExpand by remember(post.postId) { mutableStateOf(false) }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            post.universeDescription,
                            color = theme.textSecondary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!descriptionExpanded) descriptionCanExpand = result.hasVisualOverflow
                            }
                        )
                        if (descriptionCanExpand || descriptionExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { descriptionExpanded = !descriptionExpanded }
                            ) {
                                Text(
                                    text = if (descriptionExpanded) "Show less" else "Read more",
                                    color = theme.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Icon(
                                    imageVector = if (descriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                }

                HorizontalDivider(color = theme.textSecondary.copy(0.1f), thickness = 1.dp)

                // Graph Section
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                ) {
                    if (rows.isNotEmpty()) {
                        BranchingTimelineView(
                            rows = rows,
                            edges = payload?.edges ?: emptyList(),
                            onNodeToggle = { /* No toggle in preview */ },
                            onNodeClick = { onMediaClick(TimelineViewModel.resolveMediaId(it.node)) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BrokenImage, null, tint = theme.textSecondary.copy(0.3f), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No timeline data found", color = theme.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        if (showCommentsSheet) {
            TimelineCommentsSheet(
                universeId = post.postId,
                onDismiss = { showCommentsSheet = false }
            )
        }
    }
}

fun computePreviewRows(payload: SharedTimelinePayload, tmdbCache: TmdbMetadataCache): List<TimelineRow> {
    val layout = try {
        GraphEngine.computeLayout(payload.nodes, payload.edges)
    } catch (e: Exception) {
        return emptyList()
    }
    
    val nodeById = payload.nodes.associateBy { it.id }
    
    val displayNodes = layout.sortedIds.mapNotNull { id ->
        val node = nodeById[id] ?: return@mapNotNull null
        
        // Try to get fresh metadata from cache if the payload is missing it
        val cached = tmdbCache.get(node.tmdb_id) as? TmdbFetchState.Success
        val posterUrl = if (!node.posterUrl.isNullOrBlank()) node.posterUrl else cached?.detail?.posterUrl
        val mediaType = if (node.tmdb_media_type.isNotBlank()) {
             if (node.tmdb_media_type == "movie") TmdbMediaType.MOVIE else TmdbMediaType.TV
        } else {
             cached?.detail?.mediaType ?: if (node.content_type == "MOVIE") TmdbMediaType.MOVIE else TmdbMediaType.TV
        }

        DisplayNode(
            node = node,
            column = layout.columnMap[id] ?: 0,
            isCompleted = false,
            isSpoilerBlurred = false,
            metadata = TmdbFetchState.Success(
                TmdbMediaDetail(
                    tmdbId = node.tmdb_id,
                    title = node.title,
                    overview = "",
                    posterUrl = posterUrl,
                    backdropUrl = null,
                    releaseDate = "",
                    runtimeMinutes = 0,
                    episodeCount = node.episodeCount,
                    seasonCount = 0,
                    voteAverage = 0f,
                    voteCount = 0,
                    genres = emptyList(),
                    status = "",
                    tagline = "",
                    imdbId = null,
                    mediaType = mediaType
                )
            )
        )
    }
    
    val displayNodeMeta = displayNodes.associate { it.node.id to (it.column to it.isCompleted) }
    val connections = GraphEngine.computeConnections(
        displayNodeMap = displayNodeMeta,
        edges = payload.edges,
        levelMap = layout.levelMap
    )

    return displayNodes
        .groupBy { layout.levelMap[it.node.id] ?: 0 }
        .entries
        .sortedBy { it.key }
        .map { (level, nodesAtLevel) ->
            TimelineRow(
                level = level,
                nodes = nodesAtLevel.sortedBy { it.column },
                totalColumns = layout.maxColumns,
                outgoing = connections[level] ?: emptyList()
            )
        }
}

private fun relativeTimeLabel(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val diffMs = System.currentTimeMillis() - timestampMillis
    val minutes = diffMs / 60_000
    val hours   = minutes / 60
    val days    = hours / 24
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24   -> "${hours}h ago"
        days < 7     -> "${days}d ago"
        else         -> "${days / 7}w ago"
    }
}

@Composable
private fun CommunityPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxDragPx   = thresholdPx * 1.6f
    val theme = LocalAppTheme.current

    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isRefreshing) {
                if (isRefreshing) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val atTop = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                        if ((atTop && dragAmount > 0f) || offsetY.value > 0f) {
                            change.consume()
                            scope.launch {
                                val next = (offsetY.value + dragAmount * 0.5f).coerceIn(0f, maxDragPx)
                                offsetY.snapTo(next)
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value >= thresholdPx) onRefresh()
                            offsetY.animateTo(0f, animationSpec = spring())
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetY.animateTo(0f, animationSpec = spring()) }
                    }
                )
            }
    ) {
        Box(modifier = Modifier.offset { IntOffset(0, offsetY.value.roundToInt()) }) {
            content()
        }

        if (offsetY.value > 0f || isRefreshing) {
            val pullProgress = (offsetY.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(30.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { if (isRefreshing) 0.5f else pullProgress },
                    color = theme.accent,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

// ─── Comments ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    post: CommunityPost,
    currentUserId: String?,
    comments: List<CommunityComment>,
    isLoading: Boolean,
    getAvatarModel: (String?) -> Any?,
    onDismiss: () -> Unit,
    onSend: (text: String, parentCommentId: String?) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onLikeComment: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommunityComment?>(null) }

    val topLevel = remember(comments) { comments.filter { it.parentCommentId == null } }
    val repliesByParent = remember(comments) { comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textSecondary.copy(0.3f)) },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                if (post.commentsCount > 0) "${post.commentsCount} Comments" else "Comments",
                color = theme.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = theme.border.copy(alpha = 0.1f))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && comments.isEmpty() -> CircularProgressIndicator(
                        color = theme.accent,
                        modifier = Modifier.align(Alignment.Center).size(28.dp)
                    )
                    topLevel.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, null,
                            tint = theme.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No comments yet", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Be the first to say something",
                            color = theme.textSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(topLevel, key = { it.commentId }) { comment ->
                            Column {
                                CommentRow(
                                    comment = comment,
                                    currentUserId = currentUserId,
                                    getAvatarModel = getAvatarModel,
                                    onLike = { onLikeComment(comment.commentId) },
                                    onDelete = { onDeleteComment(comment) },
                                    onReply = { replyingTo = comment },
                                    onAuthorClick = onAuthorClick
                                )
                                val replies = repliesByParent[comment.commentId].orEmpty()
                                if (replies.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.padding(start = 40.dp, top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        replies.forEach { reply ->
                                            CommentRow(
                                                comment = reply,
                                                currentUserId = currentUserId,
                                                getAvatarModel = getAvatarModel,
                                                onLike = { onLikeComment(reply.commentId) },
                                                onDelete = { onDeleteComment(reply) },
                                                onReply = null,
                                                onAuthorClick = onAuthorClick,
                                                compact = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = theme.border.copy(alpha = 0.1f))

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                replyingTo?.let { parent ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Replying to ${parent.authorName}",
                            color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(16.dp).clickable { replyingTo = null }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 1000) inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (replyingTo != null) "Write a reply..." else "Add a comment...",
                                color = theme.textSecondary
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.border.copy(alpha = 0.3f),
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                        ),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSend(inputText, replyingTo?.commentId)
                                inputText = ""
                                replyingTo = null
                            }
                        },
                        shape = CircleShape,
                        color = if (inputText.isNotBlank()) theme.accent else theme.surface,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.Black else theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommunityComment,
    currentUserId: String?,
    getAvatarModel: (String?) -> Any?,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onReply: (() -> Unit)?,
    onAuthorClick: (String) -> Unit,
    compact: Boolean = false
) {
    val theme = LocalAppTheme.current
    val isOwner = currentUserId != null && currentUserId == comment.userId
    val isLikedByMe = currentUserId != null && currentUserId in comment.likedByUsers

    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .size(if (compact) 28.dp else 36.dp)
                .clickable(enabled = comment.userId.isNotBlank()) { onAuthorClick(comment.userId) },
            shape = CircleShape,
            color = theme.surface
        ) {
            AsyncImage(
                model = getAvatarModel(comment.authorAvatarUrl) ?: "https://ui-avatars.com/api/?name=${comment.authorName}",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                comment.authorName,
                color = theme.textPrimary, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 13.sp
            )
            Text(
                comment.text,
                color = theme.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                lineHeight = 18.sp
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    relativeTimeLabel(comment.timestamp),
                    color = theme.textSecondary.copy(alpha = 0.6f), fontSize = 10.sp
                )
                if (onReply != null) {
                    Text(
                        "Reply", color = theme.textSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        modifier = Modifier.clickable { onReply() }
                    )
                }
                if (isOwner) {
                    Text(
                        "Delete", color = theme.statusFiller.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        modifier = Modifier.clickable { onDelete() }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLike() }
                ) {
                    Icon(
                        if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like comment",
                        tint = if (isLikedByMe) Color(0xFFFF4B6E) else theme.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    if (comment.likesCount > 0) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${comment.likesCount}",
                            color = theme.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
