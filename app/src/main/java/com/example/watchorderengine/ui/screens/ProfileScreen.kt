package com.example.watchorderengine.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
import com.example.watchorderengine.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Copies a picker-selected image into app-private storage and returns a
 * stable file:// path.
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
    onRateMediaClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val statsState by viewModel.stats.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val userReviews by viewModel.userReviews.collectAsStateWithLifecycle()

    val stats = statsState // Capture in local variable for smart casting

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(username) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    }
                    viewModel.updateAvatarUrlFromBitmap(bitmap)
                } catch (e: Exception) {
                    android.util.Log.e("ProfileScreen", "Failed to process picker image", e)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header: Avatar + Identity
            item {
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

                    IconButton(
                        onClick = onEditProfileClick,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                    ) {
                        Icon(Icons.Default.ManageAccounts, "Edit public profile & privacy", tint = theme.textPrimary)
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = viewModel.getAvatarModel(avatarUrl) ?: "https://ui-avatars.com/api/?name=${username.ifBlank { "User" }}&background=random&color=fff",
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
                                Icon(Icons.Default.CameraAlt, "Change profile picture", tint = Color.Black, modifier = Modifier.padding(6.dp))
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
                                        Icon(Icons.Default.Check, "Save name", tint = theme.accent)
                                    }
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        Icon(Icons.Default.Edit, "Edit name", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                
                                if (stats?.profileRank != null) {
                                    Surface(
                                        color = theme.accent,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            stats.profileRank.uppercase(),
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
                        
                        Text(
                            text = if (viewModel.userEmail != null) "Level ${((stats?.totalEpisodesWatched ?: 0) / 50) + 1} Cinephile" else "Guest Account",
                            color = theme.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        viewModel.userEmail?.let { email ->
                            Text(
                                text = email,
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Streak Banner
            if ((stats?.streakDays ?: 0) > 0) {
                item { StreakBanner(stats!!.streakDays) }
            }

            // Quick Stats Row
            item {
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
                        label = "Rating",
                        value = String.format("%.1f", stats?.averageRating ?: 0f),
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stats?.profileRank?.uppercase() ?: "NOVICE",
                        value = formatScore(stats?.profileScore ?: 0),
                        icon = Icons.Default.EmojiEvents,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recently Watched
            if (stats?.recentlyWatched?.isNotEmpty() == true) {
                item { SectionHeader("RECENTLY WATCHED") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        items(stats.recentlyWatched) { media ->
                            RecentlyWatchedItem(media, onClick = { onMediaClick(media.id) })
                        }
                    }
                }
            }

            // User Reviews
            if (userReviews.isNotEmpty()) {
                item { SectionHeader("YOUR REVIEWS") }
                items(userReviews) { review ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ReviewItemSmall(
                            review = review,
                            onMediaClick = { onMediaClick(review.mediaId) },
                            onDelete = { viewModel.deleteReview(review.id) }
                        )
                    }
                }
            }

            // Import Button
            item {
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                        .then(ThemeBorderModifier())
                        .clickable { onImportClick() },
                    color = theme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ImportExport, null, tint = theme.textPrimary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("IMPORT ANIME LIST", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.textPrimary)
                            Text("Sync from AniList or MyAnimeList", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Detailed Stats List
            item {
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
            }

            // Genre Affinity
            item {
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
                                "Top Genre: ${stats.favoriteGenre}",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                stats.topGenres.forEach { genre ->
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
                } else {
                    EmptyTasteProfile(
                        accentColor   = theme.accent,
                        onRateMedia   = onRateMediaClick
                    )
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
private fun EmptyTasteProfile(
    accentColor: Color,
    onRateMedia: () -> Unit
) {
    val axisCount  = 6
    val rings      = 4
    val graphAlpha = 0.18f

    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.05f))
            .border(1.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawWithCache {
                        onDrawBehind {
                            val cx       = size.width  / 2f
                            val cy       = size.height / 2f
                            val maxR     = size.minDimension / 2f * 0.88f
                            val angleStep = (2 * PI / axisCount).toFloat()

                            for (ring in 1..rings) {
                                val r    = maxR * (ring.toFloat() / rings)
                                val path = Path()
                                for (i in 0 until axisCount) {
                                    val angle = -PI.toFloat() / 2f + i * angleStep
                                    val x     = cx + r * cos(angle)
                                    val y     = cy + r * sin(angle)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                path.close()
                                drawPath(
                                    path  = path,
                                    color = accentColor.copy(alpha = graphAlpha),
                                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            for (i in 0 until axisCount) {
                                val angle = -PI.toFloat() / 2f + i * angleStep
                                drawLine(
                                    color       = accentColor.copy(alpha = graphAlpha),
                                    start       = Offset(cx, cy),
                                    end         = Offset(cx + maxR * cos(angle).toFloat(), cy + maxR * sin(angle).toFloat()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val ghostValues = floatArrayOf(0.35f, 0.20f, 0.55f, 0.30f, 0.45f, 0.25f)
                            val ghostPath   = Path()
                            for (i in 0 until axisCount) {
                                val angle = -PI.toFloat() / 2f + i * angleStep
                                val r     = maxR * ghostValues[i]
                                val x     = cx + r * cos(angle).toFloat()
                                val y     = cy + r * sin(angle).toFloat()
                                if (i == 0) ghostPath.moveTo(x, y) else ghostPath.lineTo(x, y)
                            }
                            ghostPath.close()

                            drawPath(
                                path  = ghostPath,
                                color = accentColor.copy(alpha = 0.07f)
                            )
                            drawPath(
                                path  = ghostPath,
                                color = accentColor.copy(alpha = 0.25f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            drawCircle(
                                color  = accentColor.copy(alpha = 0.3f),
                                radius = 3.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
            )

            Text(
                text       = "YOUR TASTE PROFILE\nIS EMPTY",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Black,
                fontStyle  = FontStyle.Italic,
                color      = accentColor,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
                letterSpacing = 1.sp
            )
            Text(
                text      = "Rate shows and movies to reveal your genre radar\nand unlock personalised recommendations.",
                fontSize  = 12.sp,
                color     = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Button(
                onClick  = onRateMedia,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(48.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint     = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "START RATING MEDIA",
                    color         = Color.Black,
                    fontWeight    = FontWeight.Black,
                    fontSize      = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun ReviewItemSmall(
    review: com.example.watchorderengine.data.db.entity.ReviewEntity,
    onMediaClick: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onMediaClick,
        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(10) { index ->
                        Icon(
                            imageVector = if (index < review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (index < review.rating) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = review.mediaTitle,
                        color = theme.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(review.emojiReaction, fontSize = 10.sp)
                }
                if (review.reviewText.isNotBlank()) {
                    Text(
                        text = review.reviewText,
                        color = theme.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (review.mediaPosterUrl != null) {
                AsyncImage(
                    model = review.mediaPosterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp, 48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
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

private fun formatScore(score: Int): String {
    return when {
        score >= 1_000_000 -> String.format("%.1fM", score / 1_000_000f)
        score >= 10_000    -> String.format("%.1fK", score / 1000f)
        else -> score.toString()
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
