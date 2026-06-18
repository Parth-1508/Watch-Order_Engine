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
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.theme.LocalAppTheme
import kotlin.math.abs

@Composable
fun DiscoveryScreen(
    onMediaClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val theme = LocalAppTheme.current
    val genres = listOf("All", "Sci-Fi", "Action", "Cyberpunk", "Fantasy", "Military", "Psychological", "Thriller", "Ninja", "Mecha", "Drama", "Adventure")
    var activeGenre by remember { mutableStateOf("All") }
    
    val mockCards = listOf(
        DiscoveryCardData("5", "NEON GENESIS: REDUX", "Sci-Fi • Psychological • Mecha", "Teenage pilots defend what remains of humanity...", "https://images.unsplash.com/photo-1643560413634-edc1135c7e4b"),
        DiscoveryCardData("2", "CYBER CITY X", "Cyberpunk • Mystery • Thriller", "A rogue cop and a sentient AI tear through a neon-drenched undercity...", "https://images.unsplash.com/photo-1601042879364-f3947d3f9c16")
    )
    
    var currentCards by remember { mutableStateOf(mockCards) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (currentCards.isNotEmpty()) {
            AsyncImage(
                model = currentCards.last().image,
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
                if (currentCards.isEmpty()) {
                    EmptyDiscoveryView { currentCards = mockCards }
                } else {
                    currentCards.forEachIndexed { index, card ->
                        DiscoveryCard(
                            card = card,
                            isTop = index == currentCards.size - 1,
                            onSwipe = { status ->
                                currentCards = currentCards.filter { it.showId != card.showId }
                            }
                        )
                    }
                }
            }
        }
    }
}

data class DiscoveryCardData(val showId: String, val title: String, val tags: String, val desc: String, val image: String)

@Composable
fun DiscoveryCard(
    card: DiscoveryCardData,
    isTop: Boolean,
    onSwipe: (String) -> Unit
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
                                    onSwipe(if (offsetX > 0) "Watching" else "Dropped")
                                } else if (offsetY < -300) {
                                    onSwipe("Planned")
                                } else if (offsetY > 300) {
                                    onSwipe("Paused")
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
    ) {
        AsyncImage(
            model = card.image,
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
                    card.title,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 36.sp
                )
                Text(card.tags, color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Text(card.desc, color = Color.LightGray, fontSize = 12.sp, maxLines = 2)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("LORE STATS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Canvas(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.height / 2
                        repeat(6) { i ->
                            val angle = (i * 60f) * (Math.PI / 180f).toFloat()
                            drawLine(
                                Color.White.copy(alpha = 0.2f),
                                center,
                                Offset(center.x + radius * kotlin.math.cos(angle), center.y + radius * kotlin.math.sin(angle))
                            )
                        }
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
        Box(modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Refresh, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("You're all caught up!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Text("Come back later for more.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onReset,
            modifier = Modifier.padding(top = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("RESET DECK", fontWeight = FontWeight.Black)
        }
    }
}
