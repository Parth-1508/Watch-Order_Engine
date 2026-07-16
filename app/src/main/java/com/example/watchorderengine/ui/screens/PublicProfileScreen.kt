package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.UserStats
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.PublicProfileUiState
import com.example.watchorderengine.ui.viewmodel.PublicProfileViewModel

@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.textPrimary)
                }
            }

            when (val state = uiState) {
                is PublicProfileUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }
                is PublicProfileUiState.NotFound -> {
                    EmptyPublicProfileState(
                        icon = Icons.Default.PersonOff,
                        title = "NO PROFILE YET",
                        subtitle = "This user hasn't set up a public profile."
                    )
                }
                is PublicProfileUiState.Error -> {
                    EmptyPublicProfileState(
                        icon = Icons.Default.CloudOff,
                        title = "COULDN'T LOAD PROFILE",
                        subtitle = state.message
                    )
                }
                is PublicProfileUiState.Loaded -> {
                    PublicProfileContent(
                        displayName = state.profile.displayName,
                        avatarUrl = state.profile.avatarUrl,
                        isStatsPublic = state.profile.isStatsPublic,
                        isFavoritesPublic = state.profile.isFavoritesPublic,
                        watchStats = state.profile.watchStats,
                        favoriteShows = state.profile.favoriteShows,
                        onMediaClick = onMediaClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicProfileContent(
    displayName: String,
    avatarUrl: String?,
    isStatsPublic: Boolean,
    isFavoritesPublic: Boolean,
    watchStats: UserStats?,
    favoriteShows: List<MediaSummary>,
    onMediaClick: (String) -> Unit,
) {
    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(theme.accent.copy(alpha = 0.3f), theme.background)
                        )
                    )
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = avatarUrl ?: "https://ui-avatars.com/api/?name=${displayName.ifBlank { "User" }}&background=random&color=fff",
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(3.dp, theme.accent, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    displayName.ifBlank { "Explorer" }.uppercase(),
                    color = theme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                
                if (watchStats?.profileRank != null) {
                    Surface(
                        color = theme.accent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            watchStats.profileRank.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        if (!isStatsPublic && !isFavoritesPublic) {
            EmptyPublicProfileState(
                icon = Icons.Default.Lock,
                title = "PRIVATE PROFILE",
                subtitle = "$displayName hasn't made their stats or favorites public."
            )
            return@Column
        }

        // Stats
        if (isStatsPublic) {
            SectionHeader("WATCHLIST STATS")
            if (watchStats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PublicStatCard("Episodes", watchStats.totalEpisodesWatched.toString(), Icons.Default.Tv, Modifier.weight(1f))
                    PublicStatCard("Movies", watchStats.totalMoviesWatched.toString(), Icons.Default.Movie, Modifier.weight(1f))
                    PublicStatCard("Completed", watchStats.showsCompleted.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
                }
                if (watchStats.topGenres.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        watchStats.topGenres.forEach { genre ->
                            Surface(
                                color = theme.accent.copy(alpha = 0.1f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = theme.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "No stats recorded yet.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Favorites
        if (isFavoritesPublic) {
            SectionHeader("FAVORITE SHOWS")
            if (favoriteShows.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(favoriteShows, key = { it.id }) { media ->
                        Column(modifier = Modifier.width(100.dp).clickable { onMediaClick(media.id) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.surface)
                            ) {
                                AsyncImage(
                                    model = media.posterUrl,
                                    contentDescription = media.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                media.title,
                                color = theme.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No favorites picked yet.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PublicStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = modifier.then(ThemeBorderModifier()),
        color = theme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
            Text(value, color = theme.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyPublicProfileState(icon: ImageVector, title: String, subtitle: String) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(100.dp).background(theme.accent.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(48.dp), tint = theme.accent.copy(0.5f))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = theme.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val theme = LocalAppTheme.current
    Text(
        text = title,
        color = theme.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
