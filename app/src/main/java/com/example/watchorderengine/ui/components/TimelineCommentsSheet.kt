package com.example.watchorderengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.UniverseComment
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.repository.FriendActivityRepository
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CommentSheetViewModel @Inject constructor(
    val repository: FriendActivityRepository,
    val auth: FirebaseAuth,
    val userPrefs: UserPreferencesRepository
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineCommentsSheet(
    universeId: String,
    onDismiss: () -> Unit,
    viewModel: CommentSheetViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val comments by viewModel.repository.observeComments(universeId).collectAsStateWithLifecycle(initialValue = emptyList())

    var commentText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<UniverseComment?>(null) }
    var isPosting by remember { mutableStateOf(false) }

    val topLevelComments = remember(comments) { comments.filter { it.parentCommentId == null } }
    val repliesByParent = remember(comments) { comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = theme.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Discussion (${comments.size})",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = theme.textPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = theme.textSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Comments list
            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No discussion comments yet. Be the first to start the conversation!",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topLevelComments, key = { it.commentId }) { comment ->
                        CommentRow(
                            comment = comment,
                            replies = repliesByParent[comment.commentId] ?: emptyList(),
                            onReplyClick = {
                                replyingToComment = comment
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Reply banner if replying
            if (replyingToComment != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Replying to ${replyingToComment!!.authorName}",
                        fontSize = 11.sp,
                        color = theme.accent,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { replyingToComment = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cancel reply", tint = theme.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Comment Input Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Write a comment...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.border.copy(alpha = 0.3f),
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = commentText.trim()
                        if (text.isBlank() || isPosting) return@IconButton
                        val uid = viewModel.auth.currentUser?.uid ?: return@IconButton
                        isPosting = true
                        scope.launch {
                            val name = viewModel.userPrefs.username.first()
                            val avatar = viewModel.userPrefs.avatarUrl.first()
                            viewModel.repository.postComment(
                                universeId = universeId,
                                authorId = uid,
                                authorName = name,
                                authorAvatarUrl = avatar,
                                text = text,
                                parentCommentId = replyingToComment?.commentId
                            )
                            commentText = ""
                            replyingToComment = null
                            isPosting = false
                        }
                    },
                    enabled = commentText.isNotBlank() && !isPosting,
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (commentText.isNotBlank()) theme.accent else theme.surface, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Post Comment",
                        tint = if (commentText.isNotBlank()) Color.Black else theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: UniverseComment,
    replies: List<UniverseComment>,
    onReplyClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val formattedTime = remember(comment.createdAt) {
        comment.createdAt?.toDate()?.let {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(it)
        } ?: "Just now"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = comment.authorName,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(theme.surfaceHover),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.AccountCircle)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        comment.authorName.ifBlank { "Explorer" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = theme.textPrimary
                    )
                    Text(
                        formattedTime,
                        fontSize = 10.sp,
                        color = theme.textSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    comment.text,
                    fontSize = 13.sp,
                    color = theme.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.clickable { onReplyClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = "Reply",
                        tint = theme.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Reply",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent
                    )
                }
            }
        }

        // Nested replies
        if (replies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(start = 42.dp, top = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                replies.forEach { reply ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        AsyncImage(
                            model = reply.authorAvatarUrl,
                            contentDescription = reply.authorName,
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(theme.surfaceHover),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.AccountCircle)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                reply.authorName.ifBlank { "Explorer" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = theme.textPrimary
                            )
                            Text(
                                reply.text,
                                fontSize = 12.sp,
                                color = theme.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
