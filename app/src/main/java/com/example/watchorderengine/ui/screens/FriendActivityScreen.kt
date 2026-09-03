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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.repository.FriendActivityItem
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.FriendActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendActivityScreen(
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    viewModel: FriendActivityViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { Text("Friend Activity", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = theme.accent, modifier = Modifier.align(Alignment.Center))
                }
                isEmpty -> {
                    EmptyFriendActivityState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(feed, key = { it.timestampMillis.toString() + it.hashCode() }) { item ->
                            FriendActivityCard(item = item, onMediaClick = onMediaClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendActivityState(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Groups, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "No activity yet",
            color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Follow people to see their recent completions and ratings here.",
            color = theme.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FriendActivityCard(
    item: FriendActivityItem,
    onMediaClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    val authorName = item.authorName
    val authorAvatarUrl = item.authorAvatarUrl

    val mediaId = when (item) {
        is FriendActivityItem.Completion -> item.event.mediaId
        is FriendActivityItem.Rating -> item.review.mediaId
    }
    val mediaTitle = when (item) {
        is FriendActivityItem.Completion -> item.event.mediaTitle
        is FriendActivityItem.Rating -> item.review.mediaTitle
    }
    val mediaPosterUrl = when (item) {
        is FriendActivityItem.Completion -> item.event.mediaPosterUrl
        is FriendActivityItem.Rating -> item.review.mediaPosterUrl
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMediaClick(mediaId) },
        shape = RoundedCornerShape(theme.appRadius.coerceAtLeast(14.dp)),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = authorAvatarUrl,
                contentDescription = authorName,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(theme.surfaceHover),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.AccountCircle)
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item is FriendActivityItem.Rating) Icons.Default.Star else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (item) {
                            is FriendActivityItem.Completion -> "$authorName finished watching"
                            is FriendActivityItem.Rating -> "$authorName rated ${item.review.rating.toString().removeSuffix(".0")}★"
                        },
                        color = theme.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    mediaTitle,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (item is FriendActivityItem.Completion && item.event.episodeCount > 0) {
                    Text(
                        "${item.event.episodeCount} episodes",
                        color = theme.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (item is FriendActivityItem.Rating && item.review.reviewText.isNotBlank()) {
                    Text(
                        item.review.reviewText,
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = mediaPosterUrl,
                contentDescription = mediaTitle,
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)).background(theme.surfaceHover),
                contentScale = ContentScale.Crop
            )
        }
    }
}
