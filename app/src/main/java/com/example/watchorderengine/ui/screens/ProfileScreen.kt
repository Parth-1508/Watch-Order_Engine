package com.example.watchorderengine.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.data.model.UserStats
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a picker-selected image into app-private storage and returns a
 * stable file:// path. A raw content:// URI from the Photo Picker is only
 * guaranteed valid for the lifetime of the grant — persisting it directly
 * as the avatar URL risks it failing to load after the picker's underlying
 * permission is revoked (e.g. after some Android versions/OEMs reclaim
 * transient grants). Copying the bytes once, up front, avoids that entirely.
 */
private suspend fun copyImageToAppStorage(context: Context, sourceUri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val destFile = File(context.filesDir, "profile_avatar.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

@Composable
fun ProfileScreen(
    onMediaClick: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stats by viewModel.stats.collectAsState()
    val username by viewModel.username.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(username) }

    val scrollState = rememberScrollState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val localPath = copyImageToAppStorage(context, uri)
                if (localPath != null) {
                    // No manual cache-busting needed: Coil already includes
                    // the file's last-modified timestamp in its cache key
                    // for file:// URIs (fixed in Coil well before the 2.6.0
                    // version this project uses), so overwriting
                    // profile_avatar.jpg with new bytes is already enough
                    // for Coil to detect the change and skip its stale cache.
                    viewModel.updateAvatarUrl("file://$localPath")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp)
        ) {
            // Header: Avatar + Identity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                // Background Gradient
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
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=200&h=200",
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(3.dp, theme.accent, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            color = theme.accent,
                            modifier = Modifier.size(32.dp).offset(x = 4.dp, y = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.Black, modifier = Modifier.padding(6.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isEditingName) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.padding(horizontal = 32.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Black),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.border
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.updateUsername(editedName)
                                    isEditingName = false
                                }) {
                                    Icon(Icons.Default.Check, null, tint = theme.accent)
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                username.uppercase(),
                                color = theme.textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            IconButton(onClick = { 
                                editedName = username
                                isEditingName = true 
                            }) {
                                Icon(Icons.Default.Edit, null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    
                    Text(
                        text = "Level ${((stats?.totalEpisodesWatched ?: 0) / 50) + 1} Cinephile",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Streak Banner
            if ((stats?.streakDays ?: 0) > 0) {
                StreakBanner(stats!!.streakDays)
            }

            // Quick Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Episodes",
                    value = stats?.totalEpisodesWatched?.toString() ?: "0",
                    icon = Icons.Default.Tv,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Movies",
                    value = stats?.totalMoviesWatched?.toString() ?: "0",
                    icon = Icons.Default.Movie,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Score",
                    value = String.format("%.1f", stats?.averageRating ?: 0f),
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )
            }

            // Recently Watched
            if (stats?.recentlyWatched?.isNotEmpty() == true) {
                SectionHeader("RECENTLY WATCHED")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    items(stats!!.recentlyWatched) { media ->
                        RecentlyWatchedItem(media, onClick = { onMediaClick(media.id) })
                    }
                }
            }

            // Detailed Stats List
            SectionHeader("WATCHLIST METRICS")
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .then(ThemeBorderModifier()),
                color = theme.surface.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetricRow("Completed Shows", stats?.showsCompleted ?: 0, theme.statusCanon)
                    MetricRow("Currently Watching", stats?.showsWatching ?: 0, theme.accent)
                    MetricRow("Planned to Watch", stats?.showsPlanned ?: 0, theme.textSecondary)
                    MetricRow("Paused / On Hold", stats?.showsPaused ?: 0, theme.statusMixed)
                    MetricRow("Dropped", stats?.showsDropped ?: 0, theme.statusFiller)
                }
            }

            // Genre Affinity
            SectionHeader("TASTE PROFILE")
            if (stats?.topGenres?.isNotEmpty() == true) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surface)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "Top Genre: ${stats?.favoriteGenre}",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats!!.topGenres.forEach { genre ->
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
                }
            }
        }
        
        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        }
    }
}

@Composable
private fun StreakBanner(days: Int) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF59E0B), // Flame Orange
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Whatshot, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$days DAY STREAK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("Keep it up! You're on a roll.", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
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
private fun RecentlyWatchedItem(media: MediaSummary, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.width(100.dp).clickable { onClick() }) {
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

@Composable
private fun MetricRow(label: String, value: Int, color: Color) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(label, color = theme.textSecondary, fontSize = 13.sp)
        }
        Text(value.toString(), color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
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
