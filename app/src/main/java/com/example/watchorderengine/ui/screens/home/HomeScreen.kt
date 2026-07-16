package com.example.watchorderengine.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.paging.compose.LazyPagingItems
import com.example.watchorderengine.data.recommendation.Recommendation
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState

// ─── "Next Up" data model ─────────────────────────────────────────────────────

data class NextUpItem(
    val internalId: String,
    val showTitle: String,
    val episodeLabel: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val progressPercent: Int = 0,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    watchlist: LazyPagingItems<MediaSummary>,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onShowClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    getAvatarModel: (String?) -> Any? = { it },
    nextUpItem: NextUpItem? = null,
    onResumeClick: (internalId: String) -> Unit = {},
    recommendations: List<Recommendation> = emptyList()
) {
    val theme = LocalAppTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        if (state.isLoading && watchlist.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header
                item {
                    Header(
                        isSearchOpen = state.isSearchOpen,
                        query = state.searchQuery,
                        onQueryChanged = onSearchQueryChanged,
                        onToggleSearch = onSearchToggle,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onProfileClick,
                        getAvatarModel = getAvatarModel,
                        profilePictureUrl = state.profilePictureUrl
                    )
                }

                // "Next Up" Quick Resume Card
                item {
                    AnimatedVisibility(
                        visible = nextUpItem != null,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        nextUpItem?.let { item ->
                            NextUpCard(
                                item     = item,
                                onResume = { onResumeClick(item.internalId) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Category Tabs
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.categories) { category ->
                            val count = when(category) {
                                "Watching" -> state.watchingCount
                                "Planned" -> state.plannedCount
                                "Completed" -> state.completedCount
                                "Dropped" -> state.droppedCount
                                "Paused" -> state.pausedCount
                                else -> 0
                            }
                            CategoryTab(
                                name = category,
                                count = count,
                                isSelected = state.activeCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }

                // Active Category Label
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                if (theme.isComic) rotationZ = 2f
                            }
                            .drawBehind {
                                drawRect(color = Color.Black)
                                drawRect(color = theme.accent, style = Stroke(width = 2.dp.toPx()))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.activeCategory.uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (watchlist.itemCount == 0 && watchlist.loadState.refresh is androidx.paging.LoadState.NotLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tv, 
                                    contentDescription = null, 
                                    tint = Color.Gray, 
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text("NO SHOWS HERE YET", color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                            Text(
                                text = "Open a show and set it to ${state.activeCategory}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    // Grid mapping for Paging items
                    val rowCount = (watchlist.itemCount + 1) / 2
                    items(rowCount) { rowIndex ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val firstIndex = rowIndex * 2
                            val secondIndex = firstIndex + 1
                            
                            watchlist[firstIndex]?.let { show ->
                                Box(modifier = Modifier.weight(1f)) {
                                    MediaCardPaged(show = show, onClick = { onShowClick(show.id) })
                                }
                            } ?: Box(modifier = Modifier.weight(1f))
                            
                            if (secondIndex < watchlist.itemCount) {
                                watchlist[secondIndex]?.let { show ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        MediaCardPaged(show = show, onClick = { onShowClick(show.id) })
                                    }
                                } ?: Box(modifier = Modifier.weight(1f))
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // RECOMMENDED FOR YOU Section
                if (recommendations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            "RECOMMENDED FOR YOU",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(recommendations, key = { it.media.id }) { recommendation ->
                                // Mapping MediaEntity to MediaSummary for simpler UI component
                                val summary = MediaSummary(
                                    id = recommendation.media.id,
                                    tmdbId = recommendation.media.tmdbId,
                                    title = recommendation.media.title,
                                    posterUrl = recommendation.media.posterUrl,
                                    backdropUrl = recommendation.media.backdropUrl,
                                    mediaCategory = com.example.watchorderengine.data.model.MediaCategory.valueOf(recommendation.media.mediaCategory),
                                    voteAverage = recommendation.media.voteAverage,
                                    releaseYear = recommendation.media.releaseYear,
                                    trackingState = null,
                                    ageRating = recommendation.media.ageRating
                                )
                                MediaCardPaged(
                                    modifier = Modifier.width(130.dp),
                                    show = summary,
                                    onClick = { onShowClick(summary.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCardPaged(
    show: MediaSummary, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = modifier
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .then(ThemeBorderModifier())
                .background(Color.Black)
        ) {
            AsyncImage(
                model = show.posterUrl,
                contentDescription = show.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.9f
            )
            
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                StatusBadge(type = show.mediaCategory.name)
            }

            if (show.trackingState == TrackingState.COMPLETED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color(0xFF00FF00), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("✓ DONE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = show.title,
            color = theme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        
        Text(
            text = show.genres.take(2).joinToString(" • "),
            color = theme.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NextUpCard(
    item: NextUpItem,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "play_pulse")
    val playScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playScale"
    )

    val accentGold = Color(0xFFFFBF3C)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF141B2D))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model              = item.backdropUrl ?: item.posterUrl,
                    contentDescription = item.showTitle,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to Color.Black.copy(alpha = 0.2f),
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape    = RoundedCornerShape(6.dp),
                    color    = accentGold
                ) {
                    Row(
                        modifier           = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint     = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "NEXT UP",
                            color         = Color.Black,
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text       = item.showTitle,
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle  = FontStyle.Italic,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = item.episodeLabel,
                        color    = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progressPercent / 100f)
                            .fillMaxHeight()
                            .background(accentGold)
                    )
                }
            }

            Button(
                onClick  = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentGold),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint               = Color.Black,
                    modifier           = Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text          = "RESUME STORY",
                    color         = Color.Black,
                    fontWeight    = FontWeight.Black,
                    fontSize      = 16.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun Header(
    isSearchOpen: Boolean,
    query: String,
    onQueryChanged: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    getAvatarModel: (String?) -> Any? = { it },
    profilePictureUrl: String? = null
) {
    val theme = LocalAppTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)
            .drawBehind {
                val yPosition = size.height + 12.dp.toPx()
                drawLine(
                    color = Color.Black.copy(alpha = 0.1f),
                    start = Offset(-16.dp.toPx(), yPosition),
                    end = Offset(size.width + 16.dp.toPx(), yPosition),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (Left) — tapping navigates to Profile
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, theme.textPrimary, CircleShape)
                .clickable(onClickLabel = "Open profile") { onProfileClick() }
        ) {
            if (profilePictureUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(theme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = getAvatarModel(profilePictureUrl),
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
                )
            }
        }

        // Title or Search Bar (Center/Expanded)
        AnimatedContent(
            targetState = isSearchOpen,
            modifier = Modifier.weight(1f),
            label = "header_center"
        ) { searching ->
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    placeholder = { Text("Search title...", fontSize = 14.sp) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = theme.surface,
                        unfocusedContainerColor = theme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textSecondary) }
                )
            } else {
                Text(
                    text = "WATCH ORDER",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-1).sp
                    ),
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        // Icons (Right)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onToggleSearch(!isSearchOpen) },
                modifier = Modifier.size(40.dp).border(2.dp, theme.textPrimary, CircleShape)
            ) {
                Icon(if (isSearchOpen) Icons.Default.Close else Icons.Default.Search, null, tint = theme.textPrimary)
            }
            if (!isSearchOpen) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp).border(2.dp, theme.textPrimary, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, null, tint = theme.textPrimary)
                }
            }
        }
    }
}
