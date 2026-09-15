package com.example.watchorderengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.FollowRelation
import com.example.watchorderengine.data.repository.FriendRepository
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FollowsSheetViewModel @Inject constructor(
    val friendRepository: FriendRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _following = MutableStateFlow<List<FollowRelation>>(emptyList())
    val following: StateFlow<List<FollowRelation>> = _following.asStateFlow()

    private val _followers = MutableStateFlow<List<FollowRelation>>(emptyList())
    val followers: StateFlow<List<FollowRelation>> = _followers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val followingSnap = firestore.collection("follows")
                    .whereEqualTo("followerId", userId)
                    .get().await()
                _following.value = followingSnap.documents.mapNotNull { doc ->
                    doc.toObject(FollowRelation::class.java)?.apply { followId = doc.id }
                }

                val followersSnap = firestore.collection("follows")
                    .whereEqualTo("followingId", userId)
                    .get().await()
                _followers.value = followersSnap.documents.mapNotNull { doc ->
                    doc.toObject(FollowRelation::class.java)?.apply { followId = doc.id }
                }
            } catch (e: Exception) {
                _following.value = emptyList()
                _followers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unfollow(targetUserId: String, currentUserId: String) {
        viewModelScope.launch {
            friendRepository.unfollowUser(targetUserId)
            load(currentUserId)
        }
    }

    fun follow(targetUserId: String, currentUserId: String) {
        viewModelScope.launch {
            friendRepository.followUser(targetUserId)
            load(currentUserId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowsListSheet(
    userId: String,
    initialTab: Int = 0, // 0 = Following, 1 = Followers
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: FollowsSheetViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    var selectedTab by remember { mutableStateOf(initialTab) }

    val following by viewModel.following.collectAsStateWithLifecycle()
    val followers by viewModel.followers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = theme.accent,
                    modifier = Modifier.weight(1f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Following (${following.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Followers (${followers.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = theme.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            } else {
                val currentList = if (selectedTab == 0) following else followers
                if (currentList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedTab == 0) "Not following anyone yet." else "No followers yet.",
                            color = theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(currentList, key = { it.followId.ifBlank { it.followerId + "_" + it.followingId } }) { item ->
                            val targetId = if (selectedTab == 0) item.followingId else item.followerId
                            val targetName = item.followerName.ifBlank { "Explorer" }
                            val targetAvatar = item.followerAvatarUrl

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onUserClick(targetId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = targetAvatar,
                                    contentDescription = targetName,
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(theme.background),
                                    contentScale = ContentScale.Crop,
                                    error = rememberVectorPainter(Icons.Default.AccountCircle)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    targetName,
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                if (selectedTab == 0) {
                                    OutlinedButton(
                                        onClick = { viewModel.unfollow(targetId, userId) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = theme.accent)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Following", fontSize = 10.sp, color = theme.textPrimary)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.follow(targetId, userId) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color.Black),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Follow Back", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
