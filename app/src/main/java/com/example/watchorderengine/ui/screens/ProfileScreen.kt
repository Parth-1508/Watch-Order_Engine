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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onSettingsClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    val stats by viewModel.stats.collectAsState()

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
                "ENGINEER",
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
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(4.dp, Brush.sweepGradient(listOf(Color.Magenta, theme.accent, Color.Cyan)), CircleShape)
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
                            Text("USER_01", fontSize = 24.sp, fontWeight = FontWeight.Black, color = theme.textPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Verified, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        }
                        Text("LEVEL ${((stats?.totalMinutesWatched ?: 0) / 1000) + 1} TRACKER", fontSize = 12.sp, color = theme.accent, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // XP Bar
                        val xpProgress = ((stats?.totalMinutesWatched ?: 0) % 1000) / 1000f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.textPrimary.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(xpProgress.coerceIn(0.1f, 1f))
                                    .fillMaxHeight()
                                    .background(theme.accent)
                            )
                        }
                        Text(
                            "${(stats?.totalMinutesWatched ?: 0) % 1000} / 1,000 XP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                // Level Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(theme.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Lv. ${((stats?.totalMinutesWatched ?: 0) / 1000) + 1}", color = theme.primary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
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
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Schedule, value = "${(stats?.totalMinutesWatched ?: 0) / 60}h", label = "WATCH TIME", tint = theme.accent)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Whatshot, value = "${stats?.totalEpisodesWatched ?: 0}", label = "EPISODES", tint = Color.Magenta)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Star, value = "${stats?.averageRating ?: 0f}", label = "AVG RATING", tint = Color.Yellow)
        }

        // Library Section
        SectionTitle("COLLECTION")
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Visibility, value = "${stats?.showsWatching ?: 0}", label = "WATCHING", tint = Color.Cyan)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Bookmark, value = "${stats?.showsPlanned ?: 0}", label = "PLANNED", tint = Color.Green)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.CheckCircle, value = "${stats?.showsCompleted ?: 0}", label = "DONE", tint = theme.accent)
        }

        // Trophy Room Section
        SectionTitle("TROPHY ROOM")
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) theme.accent.copy(alpha = 0.1f) else theme.textPrimary.copy(alpha = 0.05f))
                        .border(
                            2.dp,
                            if (i == 0) theme.accent else theme.textPrimary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (i == 0) Icons.Default.EmojiEvents else Icons.Default.Lock,
                        null,
                        tint = if (i == 0) theme.accent else theme.textSecondary,
                        modifier = Modifier.size(24.dp)
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
        color = theme.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(16.dp)
            .drawBehind {
                drawRect(theme.accent.copy(alpha = 0.2f))
            }
            .border(1.dp, theme.accent.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
