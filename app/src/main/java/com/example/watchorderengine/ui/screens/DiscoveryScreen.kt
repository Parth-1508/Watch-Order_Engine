package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.watchorderengine.R
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.DiscoveryViewModel
import com.example.watchorderengine.ui.viewmodel.SwipeAction
import kotlin.math.abs

@Composable
fun DiscoveryScreen(
    onMediaClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val pagingItems = viewModel.pagingData.collectAsLazyPagingItems()
    val isLoading = pagingItems.loadState.refresh is androidx.paging.LoadState.Loading
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val platformFilter by viewModel.platformFilter.collectAsStateWithLifecycle()
    val swipedIds by viewModel.swipedIds.collectAsStateWithLifecycle()

    // Create a local deck from paging items
    // We only show a few items at a time to keep the stack performance high
    val deck = remember(pagingItems.itemCount, pagingItems.loadState, swipedIds) {
        val list = mutableListOf<MediaSummary>()
        for (i in 0 until pagingItems.itemCount) {
            val item = pagingItems[i]
            if (item != null && item.id !in swipedIds) {
                list.add(item)
            }
            if (list.size >= 5) break // Only show top 5 in stack
        }
        list.reversed() // Reverse so the first item is drawn last (on top)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        if (deck.isNotEmpty()) {
            AsyncImage(
                model = deck.last().backdropUrl ?: deck.last().posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.15f),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Category chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        label = stringResource(R.string.discovery_all),
                        isSelected = activeCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
                items(viewModel.categories) { category ->
                    CategoryChip(
                        label = category.label,
                        isSelected = activeCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            // Platform filters
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        stringResource(R.string.discovery_platforms),
                        color = theme.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                items(platformFilter.availablePlatforms) { platform ->
                    val isSelected = platformFilter.selectedProviderIds.containsAll(platform.providerIds)
                    PlatformChip(
                        platform = platform,
                        isSelected = isSelected,
                        onClick = { viewModel.togglePlatform(platform) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && deck.isEmpty()) {
                    CircularProgressIndicator(color = theme.accent)
                } else if (deck.isEmpty() && !isLoading) {
                    EmptyDiscoveryView(theme) { viewModel.resetDeck() }
                } else {
                    deck.forEachIndexed { index, media ->
                        DiscoveryCard(
                            media = media,
                            isTop = index == deck.size - 1,
                            onSwipe = { action -> viewModel.handleSwipe(media, action) },
                            onDismiss = { viewModel.dismissPermanently(media) },
                            onClick = { onMediaClick(media.id) }
                        )
                    }
                }
            }
        }

        // Retry UI if loading fails and deck is empty
        if (pagingItems.loadState.refresh is androidx.paging.LoadState.Error && deck.isEmpty()) {
             Box(Modifier.align(Alignment.Center)) {
                 Button(
                     onClick = { pagingItems.retry() },
                     colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                 ) {
                     Text(stringResource(R.string.discovery_retry), color = Color.White)
                 }
             }
        }
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (isSelected) theme.accent else theme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isSelected) theme.accent else theme.textSecondary.copy(alpha = 0.2f))
    ) {
        Text(
            label.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (isSelected) Color.White else theme.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PlatformChip(
    platform: com.example.watchorderengine.ui.viewmodel.StreamingPlatform,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) theme.accent.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) theme.accent else theme.textSecondary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsyncImage(
                model = platform.logoUrl,
                contentDescription = platform.displayName,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = platform.displayName.uppercase(),
                color = theme.textPrimary,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
            )
        }
    }
}

@Composable
fun DiscoveryCard(
    media: MediaSummary,
    isTop: Boolean,
    onSwipe: (SwipeAction) -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val animatedScale by animateFloatAsState(if (isTop) 1f else 0.95f, label = "scale")
    val rotation = (offsetX / 20).coerceIn(-15f, 15f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .scale(animatedScale)
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .rotate(rotation)
            .then(
                if (isTop) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            },
                            onDragEnd = {
                                if (abs(offsetX) > 300) {
                                    onSwipe(if (offsetX > 0) SwipeAction.WATCH else SwipeAction.SKIP)
                                } else if (offsetY < -300) {
                                    onSwipe(SwipeAction.PLAN)
                                } else if (offsetY > 300) {
                                    onSwipe(SwipeAction.PAUSE)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .clip(RoundedCornerShape(theme.appRadius.coerceAtLeast(16.dp)))
            .background(theme.surface)
            .clickable(enabled = isTop, onClick = onClick)
            .then(if (theme.isComic) Modifier.border(2.dp, Color.Black, RoundedCornerShape(theme.appRadius.coerceAtLeast(16.dp))) else Modifier)
    ) {
        AsyncImage(
            model = media.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isTop) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.background.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.discovery_not_interested), tint = theme.textPrimary)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, theme.background.copy(alpha = 0.95f)),
                        startY = 600f
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    media.title,
                    color = theme.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 36.sp
                )
                Text(
                    media.mediaCategory.name.replace("_", " ") + " • " + media.releaseYear,
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                MediaInfoPanel(media = media, modifier = Modifier.fillMaxWidth())
            }
        }

        // Action Indicators
        if (isTop && (abs(offsetX) > 100 || abs(offsetY) > 100)) {
            val label = when {
                offsetX > 100 -> stringResource(R.string.discovery_watching)
                offsetX < -100 -> stringResource(R.string.discovery_skip)
                offsetY < -100 -> stringResource(R.string.discovery_planning)
                offsetY > 100 -> stringResource(R.string.discovery_pause)
                else -> ""
            }
            val color = when {
                offsetX > 100 -> Color.Green
                offsetX < -100 -> Color.Red
                offsetY < -100 -> theme.accent
                offsetY > 100 -> Color.Yellow
                else -> theme.textPrimary
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = color,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.rotate(-15f)
                )
            }
        }
    }
}

@Composable
fun MediaInfoPanel(media: MediaSummary, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                String.format("%.1f", media.voteAverage),
                color = Color(0xFFFFD700),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (media.ageRating != "NR") {
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = theme.accent.copy(alpha = 0.15f)
                ) {
                    Text(
                        media.ageRating,
                        color = theme.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (media.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(media.genres.take(4)) { genre ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.surface.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            genre,
                            color = theme.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDiscoveryView(theme: com.example.watchorderengine.ui.theme.AppThemeConfig, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = theme.accent.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.discovery_caught_up),
            color = theme.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic
        )
        Text(
            stringResource(R.string.discovery_reset_deck_desc),
            color = theme.textSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
        ) {
            Text(stringResource(R.string.discovery_reset_deck_button), fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
