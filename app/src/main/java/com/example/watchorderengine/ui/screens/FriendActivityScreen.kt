package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.ActivityType
import com.example.watchorderengine.data.model.UserActivity
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.FriendActivityUiState
import com.example.watchorderengine.ui.viewmodel.FriendActivityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FriendActivityScreen(
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    viewModel: FriendActivityViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

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
                Text(
                    "FRIEND ACTIVITY",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = theme.accent,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.textPrimary)
                    }
                }
            }

            when (val state = uiState) {
                is FriendActivityUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }
                is FriendActivityUiState.Error -> {
                    FriendActivityEmptyState(
                        icon = Icons.Default.CloudOff,
                        title = "COULDN'T LOAD ACTIVITY",
                        subtitle = state.message
                    )
                }
                is FriendActivityUiState.Loaded -> {
                    if (state.activity.isEmpty()) {
                        FriendActivityEmptyState(
                            icon = Icons.Default.People,
                            title = "NO ACTIVITY YET",
                            subtitle = "Follow people to see their completions and ratings here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.activity, key = { it.activityId }) { activity ->
                                FriendActivityRow(
                                    activity = activity,
                                    getAvatarModel = { viewModel.getAvatarModel(it) },
                                    onMediaClick = { onMediaClick(activity.mediaId) },
                                    onAuthorClick = { onAuthorClick(activity.userId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendActivityRow(
    activity: UserActivity,
    getAvatarModel: (String?) -> Any?,
    onMediaClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onMediaClick,
        shape = RoundedCornerShape(14.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp).clickable { onAuthorClick() },
                shape = CircleShape,
                color = theme.background
            ) {
                AsyncImage(
                    model = getAvatarModel(activity.userAvatarUrl)
                        ?: "https://ui-avatars.com/api/?name=${activity.userName}",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val actionText = when (activity.type) {
                    ActivityType.COMPLETED -> "finished watching"
                    ActivityType.RATED -> "rated"
                }
                Text(
                    "${activity.userName} $actionText ${activity.mediaTitle}",
                    color = theme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        activity.type == ActivityType.RATED && activity.rating != null -> {
                            Icon(Icons.Default.Star, null, tint = theme.accent, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${activity.rating}/10",
                                color = theme.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        activity.type == ActivityType.COMPLETED -> {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = theme.statusCanon,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Text(
                        relativeActivityTimeLabel(activity.timestamp),
                        color = theme.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            activity.mediaPosterUrl?.let { posterUrl ->
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(40.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun FriendActivityEmptyState(icon: ImageVector, title: String, subtitle: String) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = theme.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = theme.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun relativeActivityTimeLabel(timestampMillis: Long): String {
    val diff = System.currentTimeMillis() - timestampMillis
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMillis))
    }
}
