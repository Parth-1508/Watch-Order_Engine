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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.DiscoveryViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DiscoveryScreen(
    onMediaClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val deck by viewModel.discoveryDeck.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val genres = listOf("All", "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance", "Sci-Fi", "TV Movie", "Thriller", "War", "Western")
    var activeGenre by remember { mutableStateOf("All") }
    
    val filteredDeck = remember(deck, activeGenre) {
        if (activeGenre == "All") deck
        else deck.filter { media -> 
            media.genres.any { it.equals(activeGenre, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (filteredDeck.isNotEmpty()) {
            AsyncImage(
                model = filteredDeck.last().backdropUrl ?: filteredDeck.last().posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    val isSelected = activeGenre == genre
                    Surface(
                        modifier = Modifier.clickable { activeGenre = genre },
                        shape = CircleShape,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            genre.uppercase(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
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
                } else if (filteredDeck.isEmpty()) {
                    EmptyDiscoveryView { viewModel.resetDeck() }
                } else {
                    filteredDeck.forEachIndexed { index, media ->
                        DiscoveryCard(
                            media = media,
                            isTop = index == filteredDeck.size - 1,
                            onSwipe = { state ->
                                viewModel.handleSwipe(media, state)
                            },
                            onClick = { onMediaClick(media.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryCard(
    media: MediaSummary,
    isTop: Boolean,
    onSwipe: (TrackingState) -> Unit,
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
                                    onSwipe(if (offsetX > 0) TrackingState.WATCHING else TrackingState.DROPPED)
                                } else if (offsetY < -300) {
                                    onSwipe(TrackingState.PLANNED)
                                } else if (offsetY > 300) {
                                    onSwipe(TrackingState.PAUSED)
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
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
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
                    media.mediaCategory.name + " • " + media.releaseYear, 
                    color = theme.accent, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LoreStatsRadar(media = media, modifier = Modifier.fillMaxWidth().height(150.dp))
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

@Composable
fun LoreStatsRadar(media: MediaSummary, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.height * 0.4f
        val sides = 6
        val labels = listOf("POP", "SCORE", "CAST", "LORE", "HYP", "VIBE")
        
        // Draw Grid
        repeat(3) { layer ->
            val scale = (layer + 1) / 3f
            val path = Path()
            for (i in 0 until sides) {
                val angle = (i * 360f / sides - 90f) * (Math.PI / 180f).toFloat()
                val x = center.x + radius * scale * cos(angle)
                val y = center.y + radius * scale * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = 0.1f), style = Stroke(width = 1.dp.toPx()))
        }
        
        // Draw Axes and Labels
        for (i in 0 until sides) {
            val angle = (i * 360f / sides - 90f) * (Math.PI / 180f).toFloat()
            drawLine(
                Color.White.copy(alpha = 0.1f),
                center,
                Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            )
        }
        
        // Draw Dynamic Stats Path based on TMDB ID
        val statsPath = Path()
        val random = java.util.Random(media.tmdbId.toLong())
        val score = (media.voteAverage / 10f).coerceIn(0.4f, 1f)
        
        for (i in 0 until sides) {
            // Mix actual score with some random variation for a "radar" look
            val valScale = (0.3f + random.nextFloat() * 0.4f + score * 0.3f).coerceIn(0.2f, 1f)
            val angle = (i * 360f / sides - 90f) * (Math.PI / 180f).toFloat()
            val x = center.x + radius * valScale * cos(angle)
            val y = center.y + radius * valScale * sin(angle)
            if (i == 0) statsPath.moveTo(x, y) else statsPath.lineTo(x, y)
        }
        statsPath.close()
        drawPath(statsPath, Color.Cyan.copy(alpha = 0.3f))
        drawPath(statsPath, Color.Cyan, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun EmptyDiscoveryView(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Refresh, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("You're all caught up!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Text("Come back later for more trending shows.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onReset,
            modifier = Modifier.padding(top = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("RESET DECK", fontWeight = FontWeight.Black)
        }
    }
}
