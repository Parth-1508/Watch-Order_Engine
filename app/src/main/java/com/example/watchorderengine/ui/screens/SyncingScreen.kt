package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.data.model.MOVIE_FACTS
import com.example.watchorderengine.data.model.SyncProgress
import com.example.watchorderengine.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay

@Composable
fun SyncingScreen(
    syncProgress: SyncProgress?
) {
    val theme = LocalAppTheme.current
    
    var currentFactIndex by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        while(true) {
            delay(5000) // Change fact every 5 seconds
            currentFactIndex = (currentFactIndex + 1) % MOVIE_FACTS.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        contentAlignment = Alignment.Center
    ) {
        // Background Glow
        Box(
            modifier = Modifier
                .size(500.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(theme.accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Icon
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                shape = CircleShape,
                color = theme.accent.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(2.dp, theme.accent)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Syncing up the data,\nsetting up WOE for you",
                color = theme.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(56.dp))

            // Progress Bar Container
            Column(modifier = Modifier.fillMaxWidth(0.85f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        syncProgress?.stage ?: "Connecting to Engine...",
                        color = theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${((syncProgress?.progress ?: 0f) * 100).toInt()}%",
                        color = theme.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                
                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { syncProgress?.progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = theme.accent,
                    trackColor = theme.surface.copy(alpha = 0.5f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            Spacer(Modifier.height(72.dp))

            // Fun Facts Section with Crossfade
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.surface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.border.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MovieFilter, null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ENTERTAINMENT FACT",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    
                    Crossfade(targetState = MOVIE_FACTS[currentFactIndex], label = "fact_fade") { fact ->
                        Text(
                            text = fact,
                            color = theme.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            minLines = 2
                        )
                    }
                }
            }
        }
    }
}
