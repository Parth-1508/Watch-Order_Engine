package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme

@Composable
fun ProfileScreen(
    onSettingsClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PLAYER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = theme.textPrimary
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, null, tint = theme.textPrimary)
            }
        }

        // Profile Card
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(ThemeBorderModifier()),
            color = theme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Dot pattern
                    }
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(4.dp, Brush.sweepGradient(listOf(Color.Magenta, Color.Cyan, Color.Green)), CircleShape)
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=200&h=100",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(20.dp))
                    
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ALEX 99", fontSize = 24.sp, fontWeight = FontWeight.Black, color = theme.textPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        Text("MASTER TRACKER", fontSize = 12.sp, color = theme.accent, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // XP Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .border(1.dp, Color.Black, RoundedCornerShape(10.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .fillMaxHeight()
                                    .background(theme.accent)
                            )
                            Text(
                                "0 / 1,000 XP",
                                modifier = Modifier.align(Alignment.Center),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Level Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Magenta, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Lv. 1", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Tabs (Profile / Stats)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("PROFILE", fontWeight = FontWeight.Black, color = Color.Black)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("STATS", fontWeight = FontWeight.Black, color = Color.White)
            }
        }

        // Battle Stats Section
        SectionTitle("BATTLE STATS")
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Schedule, value = "0h", label = "WATCH TIME")
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Whatshot, value = "0", label = "EPISODES", tint = Color.Magenta)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Cancel, value = "0", label = "DROPPED", tint = Color.Red)
        }

        // Trophy Room Section
        SectionTitle("TROPHY ROOM")
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) Color.Transparent else Color.White.copy(alpha = 0.05f))
                        .border(
                            2.dp,
                            if (i == 0) theme.accent else Color.White.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (i == 0) Icons.Default.EmojiEvents else Icons.Default.Lock,
                        null,
                        tint = if (i == 0) theme.accent else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    val theme = LocalAppTheme.current
    Text(
        text = title,
        color = Color.Black,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(16.dp)
            .drawBehind {
                drawRect(Color.Cyan)
            }
            .border(2.dp, Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, value: String, label: String, tint: Color = Color.Cyan) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = modifier
            .height(100.dp)
            .then(ThemeBorderModifier()),
        color = theme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = theme.textPrimary)
            Text(label, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}
