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
import com.example.watchorderengine.network.model.TmdbMediaDetail
import com.example.watchorderengine.network.model.TmdbMediaType
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
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = hiltViewModel(),
    onMediaClick: (String) -> Unit
) {
    val theme         = LocalAppTheme.current
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing  by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val importState   by viewModel.importState.collectAsStateWithLifecycle()
    val selectedPost  by viewModel.selectedPost.collectAsStateWithLifecycle()
    val searchQuery   by viewModel.searchQuery.collectAsStateWithLifecycle()
    
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
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top Header ──────────────────────────────────────────────────
            CommunityHeader(
                searchQuery = searchQuery,
                onSearchChange = { viewModel.onSearchQueryChanged(it) }
            )

            CommunityPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh    = { viewModel.refreshFeed() },
                listState    = listState
            ) {
                LazyColumn(
                    state             = listState,
                    modifier          = Modifier.fillMaxSize(),
                    contentPadding    = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. External Discussion Hub
                    item { DiscussionHubSection() }

                    // 2. Quick Tags / Filters
                    item { TrendingTagsSection() }

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
                        // Hero Post (Most Liked or First)
                        item {
                            val heroPost = state.posts.maxByOrNull { it.likesCount } ?: state.posts.first()
                            HeroPostCard(
                                post = heroPost,
                                currentUserId = currentUserId,
                                onLikeClick = { viewModel.likePost(heroPost.postId) },
                                onClick = { viewModel.selectPost(heroPost) }
                            )
                        }

                                items(state.posts, key = { it.postId }) { post ->
                                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        CommunityPostCard(
                                            post            = post,
                                            currentUserId   = currentUserId,
                                            onLikeClick     = { viewModel.likePost(post.postId) },
                                            onDeleteClick   = { viewModel.deletePost(post.postId, post.userId) },
                                            onClick         = { viewModel.selectPost(post) }
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
            onDismiss = { viewModel.selectPost(null) },
            onImport = { viewModel.importTimeline(selectedPost!!) },
            importState = importState,
            onMediaClick = onMediaClick,
            tmdbCache = viewModel.getCache() // I need to add this to ViewModel
        )
    }
}

@Composable
fun HeroPostCard(
    post: CommunityPost,
    currentUserId: String?,
    onLikeClick: () -> Unit,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val posterUrls = remember(post.nodesJson) { extractPosterUrls(post.nodesJson) }
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
                .height(220.dp),
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            onClick = onClick
        ) {
            Box {
                // Background Image (First poster blurred or dimmed)
                if (posterUrls.isNotEmpty()) {
                    AsyncImage(
                        model = posterUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                }
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, theme.surface.copy(0.9f)),
                                startY = 0f
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = theme.accent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "POPULAR",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${post.likesCount} enthusiasts liked this", color = theme.textSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        post.universeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Created by ${post.authorName}",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                }

                // Big Like Button for Hero
                Surface(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    color = if (isLiked) Color(0xFFFF4B6E) else theme.surface.copy(alpha = 0.6f),
                    border = if (!isLiked) BorderStroke(1.dp, Color(0xFFFF4B6E).copy(alpha = 0.5f)) else null,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.White else Color(0xFFFF4B6E),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, null, tint = theme.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "COMMUNITY",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textPrimary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* Could open user profile or notifications */ }) {
                Icon(Icons.Outlined.Notifications, null, tint = theme.textSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search timelines, universes, or authors...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.accent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.surface,
                unfocusedContainerColor = theme.surface,
                disabledContainerColor = theme.surface,
                focusedIndicatorColor = theme.accent,
                unfocusedIndicatorColor = theme.textSecondary.copy(0.2f),
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            ),
            singleLine = true
        )
    }
}

@Composable
fun DiscussionHubSection() {
    val theme = LocalAppTheme.current
    val uriHandler = LocalUriHandler.current
    
    val forums = listOf(
        ForumLink("Reddit Movies", "r/movies", "https://www.reddit.com/r/movies/", Icons.Default.ChatBubbleOutline),
        ForumLink("IMDb Boards", "Community", "https://www.imdb.com/community/", Icons.Default.StarOutline),
        ForumLink("Letterboxd", "Journal", "https://letterboxd.com/journal/", Icons.Default.Visibility),
        ForumLink("Rotten Tomatoes", "Critics", "https://www.rottentomatoes.com/", Icons.Default.Poll),
        ForumLink("Fandom Wiki", "Knowledge", "https://www.fandom.com/", Icons.Default.MenuBook)
    )

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
fun TrendingTagsSection() {
    val theme = LocalAppTheme.current
    val tags = listOf("Marvel", "Star Wars", "DC Universe", "Anime", "Horror", "Sci-Fi", "Game of Thrones")
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags) { tag ->
            SuggestionChip(
                onClick = { /* In a real app, this would filter */ },
                label = { Text(tag, fontSize = 12.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = theme.surface,
                    labelColor = theme.textPrimary
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    borderColor = theme.textSecondary.copy(0.2f),
                    enabled = true
                )
            )
        }
    }
}

data class ForumLink(val title: String, val subtitle: String, val url: String, val icon: ImageVector)

@Composable
fun EmptyFeedView(theme: com.example.watchorderengine.ui.theme.AppThemeConfig) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CloudQueue, null, modifier = Modifier.size(64.dp), tint = theme.textSecondary.copy(0.4f))
        Spacer(Modifier.height(16.dp))
        Text("No shared timelines found.", color = theme.textSecondary, fontWeight = FontWeight.Bold)
        Text("Try a different search or share your own!", color = theme.textSecondary.copy(0.6f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPostCard(
    post: CommunityPost,
    currentUserId: String?,
    onLikeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val isLiked = currentUserId != null && (currentUserId in post.likedByUsers)
    val isOwner = currentUserId != null && currentUserId == post.userId
    val posterUrls = remember(post.nodesJson) { extractPosterUrls(post.nodesJson) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* Could show a menu if needed */ }
            ),
        shape = RoundedCornerShape(theme.appRadius),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.height(160.dp)) {
            AsyncImage(
                model = posterUrls.firstOrNull(),
                contentDescription = "Cover for ${post.universeTitle}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
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
                    Text(
                        "by ${post.authorName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            // Big Like Button
            Surface(
                onClick = onLikeClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(44.dp),
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

            if (isOwner) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, "Delete", tint = theme.statusFiller)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPostDetailSheet(
    post: CommunityPost,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    importState: ImportState,
    onMediaClick: (String) -> Unit,
    tmdbCache: TmdbMetadataCache
) {
    val theme = LocalAppTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val payload = remember(post.nodesJson) { SharedTimelineCodec.decode(post.nodesJson) }
    
    val rows = remember(payload) { payload?.let { computePreviewRows(it, tmdbCache) } ?: emptyList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textSecondary.copy(0.3f)) },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = post.authorAvatarUrl ?: "https://ui-avatars.com/api/?name=${post.authorName}",
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(theme.surface),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(post.universeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = theme.textPrimary)
                        Text("Shared by ${post.authorName}", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text(post.universeDescription, color = theme.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape = RoundedCornerShape(12.dp),
                    enabled = importState !is ImportState.Importing
                ) {
                    if (importState is ImportState.Importing) {
                        CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(12.dp))
                        Text("IMPORT TO MY GRAPHS", fontWeight = FontWeight.Black)
                    }
                }
            }

            HorizontalDivider(color = theme.textSecondary.copy(0.1f))

            // Graph Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                if (rows.isNotEmpty()) {
                    BranchingTimelineView(
                        rows = rows,
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

private fun extractPosterUrls(nodesJson: String): List<String> = try {
    val nodes = JSONObject(nodesJson).optJSONArray("nodes") ?: return emptyList()
    val urls = mutableListOf<String>()
    for (i in 0 until minOf(nodes.length(), 10)) {
        val node = nodes.getJSONObject(i)
        val posterUrl = node.optString("posterUrl")
        if (posterUrl.isNotBlank()) {
            urls.add(posterUrl)
        } else {
            // Fallback for old posts: try to guess based on tmdb_id if possible
            // Note: This is still likely broken for many old nodes because TMDB needs a hash.
        }
    }
    urls
} catch (e: Exception) {
    emptyList()
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
