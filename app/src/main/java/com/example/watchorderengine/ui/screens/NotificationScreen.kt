package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.watchorderengine.data.model.Notification
import com.example.watchorderengine.data.model.NotificationType
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.NotificationUiState
import com.example.watchorderengine.ui.viewmodel.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onNotificationClick: (Notification) -> Unit,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "NOTIFICATIONS", 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.textPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.markAllAsRead() }) {
                        Text("Mark all read", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.textPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is NotificationUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }
                is NotificationUiState.Error -> {
                    ErrorState(state.message)
                }
                is NotificationUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        EmptyNotificationsView()
                    } else {
                        NotificationList(
                            notifications = state.notifications,
                            onNotifClick = { 
                                viewModel.markAsRead(it.id)
                                onNotificationClick(it)
                            },
                            onDelete = { viewModel.deleteNotification(it.id) },
                            getAvatarModel = { viewModel.getAvatarModel(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationList(
    notifications: List<Notification>,
    onNotifClick: (Notification) -> Unit,
    onDelete: (Notification) -> Unit,
    getAvatarModel: (String?) -> Any?
) {
    val theme = LocalAppTheme.current
    
    val grouped = remember(notifications) {
        notifications.groupBy { notif ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = notif.timestamp
            val now = Calendar.getInstance()
            
            when {
                isSameDay(cal, now) -> "Today"
                isYesterday(cal, now) -> "Yesterday"
                else -> "Older"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        grouped.forEach { (header, items) ->
            item {
                Text(
                    text = header.uppercase(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
            items(items, key = { it.id }) { notif ->
                NotificationItem(
                    notification = notif,
                    onClick = { onNotifClick(notif) },
                    onDelete = { onDelete(notif) },
                    getAvatarModel = getAvatarModel
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    getAvatarModel: (String?) -> Any?
) {
    val theme = LocalAppTheme.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            ),
        color = if (notification.isRead) theme.surface.copy(alpha = 0.3f) else theme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
        border = if (!notification.isRead) BorderStroke(1.dp, theme.accent.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(typeColor(notification.type, theme).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (notification.senderAvatarUrl != null) {
                    AsyncImage(
                        model = getAvatarModel(notification.senderAvatarUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = typeIcon(notification.type),
                        contentDescription = null,
                        tint = typeColor(notification.type, theme),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    color = if (notification.isRead) theme.textSecondary else theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (notification.isRead) FontWeight.Bold else FontWeight.Black
                )
                Text(
                    text = notification.message,
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Text(
                    text = formatTime(notification.timestamp),
                    color = theme.textSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                )
            }
            
            if (notification.imageUrl != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = notification.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 32.dp, height = 48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

private fun typeIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.LIKE -> Icons.Default.Favorite
    NotificationType.IMPORT -> Icons.Default.CloudDownload
    NotificationType.RECOMMENDATION -> Icons.Default.Explore
    NotificationType.STREAK -> Icons.Default.Whatshot
    NotificationType.SYSTEM -> Icons.Default.Notifications
}

private fun typeColor(type: NotificationType, theme: com.example.watchorderengine.ui.theme.AppThemeConfig): Color = when (type) {
    NotificationType.LIKE -> Color(0xFFFF4B6E)
    NotificationType.IMPORT -> theme.accent
    NotificationType.RECOMMENDATION -> Color(0xFF60A5FA)
    NotificationType.STREAK -> Color(0xFFF59E0B)
    NotificationType.SYSTEM -> theme.textSecondary
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(cal1: Calendar, now: Calendar): Boolean {
    val yesterday = now.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return cal1.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun EmptyNotificationsView() {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(100.dp).background(theme.accent.copy(0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.NotificationsNone, null, modifier = Modifier.size(48.dp), tint = theme.textSecondary.copy(0.3f))
            }
            Spacer(Modifier.height(24.dp))
            Text("ALL CAUGHT UP", color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text("Check back later for updates!", color = theme.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = theme.statusFiller, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}
