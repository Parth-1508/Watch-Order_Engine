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
    val deck by viewModel.discoveryDeck.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val platformFilter by viewModel.platformFilter.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (deck.isNotEmpty()) {
            AsyncImage(
                model = deck.last().backdropUrl ?: deck.last().posterUrl,
                contentDescription = "Background artwork for ${deck.last().title}",
                modifier = Modifier.fillMaxSize().alpha(0.3f),
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
                        label = "All",
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
                        "PLATFORMS:",
                        color = Color.Gray,
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
                if (isLoading) {
                    CircularProgressIndicator(color = theme.accent)
                } else if (deck.isEmpty()) {
                    EmptyDiscoveryView { viewModel.resetDeck() }
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
        if (!isLoading && deck.isEmpty() && activeCategory != null) {
             Box(Modifier.align(Alignment.Center)) {
                 Button(onClick = { viewModel.resetDeck() }) {
                     Text("Retry Connection")
                 }
             }
        }
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            label.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (isSelected) Color.Black else Color.White,
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f)
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
                color = Color.White,
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
                                    // Swipe left = SKIP (temporary — resurfaces
                                    // after a manual reset), NOT a permanent
                                    // Dropped decision. Permanent dismissal has
                                    // its own explicit "Not Interested" button.
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
            .clip(RoundedCornerShape(24.dp))
            .background(Color.DarkGray)
            .clickable(enabled = isTop, onClick = onClick)
    ) {
        AsyncImage(
            model = media.posterUrl,
            contentDescription = "Poster for ${media.title}",
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
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Not interested", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 600f
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    media.title,
                    color = Color.White,
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
                offsetX > 100 -> "WATCHING"
                offsetX < -100 -> "SKIP"
                offsetY < -100 -> "PLANNING"
                offsetY > 100 -> "PAUSE"
                else -> ""
            }
            val color = when {
                offsetX > 100 -> Color.Green
                offsetX < -100 -> Color.Red
                offsetY < -100 -> theme.accent
                offsetY > 100 -> Color.Yellow
                else -> Color.White
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

/**
 * Replaces the old LoreStatsRadar hexagon — a fake radar chart drawn from
 * seeded-random numbers labeled POP/SCORE/CAST/LORE/HYP/VIBE, none of which
 * corresponded to real data. This shows the genre tags and rating TMDB
 * actually provides, so the card communicates something real instead of
 * decorative noise.
 */
@Composable
fun MediaInfoPanel(media: MediaSummary, modifier: Modifier = Modifier) {
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
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        media.ageRating,
                        color = Color.White,
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
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            genre,
                            color = Color.White,
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
fun EmptyDiscoveryView(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "You're all caught up!",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic
        )
        Text(
            "Check back later, or reset to see skipped titles again.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onReset) {
            Text("Reset Deck", fontWeight = FontWeight.Bold)
        }
    }
}
