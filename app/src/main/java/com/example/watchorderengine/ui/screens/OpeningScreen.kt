package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.ui.theme.LocalAppTheme

@Composable
fun OpeningScreen(
    onEnter: () -> Unit,
    onSkip: () -> Unit
) {
    val theme = LocalAppTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "opening")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Replica DAG Background (Simplified for Compose)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw some mock DAG lines and dots with pulse
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Logo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(rotation)
                        .border(2.dp, theme.accent.copy(alpha = 0.4f), CircleShape)
                )
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = theme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shadowElevation = 20.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "WO",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            color = Color.White
                        )
                    }
                }
            }

            // Title
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("WATCH", fontSize = 36.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Color.White)
                    Text("ORDER", fontSize = 36.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Color.White)
                }
                Text("ENGINE", fontSize = 36.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = theme.accent)
                
                Text(
                    "The ultimate DAG-powered ecosystem for mapping and tracking your viewing timelines.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(260.dp).padding(top = 16.dp)
                )
            }

            // Features Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(listOf(
                    "🗺️" to "Skill Tree",
                    "🃏" to "Discovery Deck",
                    "🏆" to "Player Profile"
                )) { (emoji, title) ->
                    Surface(
                        modifier = Modifier.width(160.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(emoji, fontSize = 24.sp)
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Map any series as a visual timeline", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // CTA
            Button(
                onClick = onEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("ENTER THE ENGINE", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
        
        // Skip Button
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("SKIP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
