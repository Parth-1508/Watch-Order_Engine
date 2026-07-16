package com.example.watchorderengine.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.ui.components.AvatarCropDialog
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.EditProfileViewModel
import com.example.watchorderengine.ui.viewmodel.SaveProfileState

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val isStatsPublic by viewModel.isStatsPublic.collectAsStateWithLifecycle()
    val isFavoritesPublic by viewModel.isFavoritesPublic.collectAsStateWithLifecycle()
    val favoriteShows by viewModel.favoriteShows.collectAsStateWithLifecycle()
    val candidateShows by viewModel.candidateShows.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isUploadingAvatar by viewModel.isUploadingAvatar.collectAsStateWithLifecycle()
    val pendingCropUri by viewModel.pendingCropUri.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveProfileState.Saved -> {
                snackbarHostState.showSnackbar("Profile saved.")
                viewModel.acknowledgeSaveResult()
                onBack()
            }
            is SaveProfileState.Failed -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.acknowledgeSaveResult()
            }
            else -> {}
        }
    }

    pendingCropUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onCancel = { viewModel.dismissCrop() },
            onCropped = { bitmap -> viewModel.onAvatarCropped(bitmap, context.cacheDir) }
        )
    }

    Scaffold(
        containerColor = theme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
                    "EDIT PROFILE",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = viewModel.getAvatarModel(avatarUrl) ?: "https://ui-avatars.com/api/?name=${displayName.ifBlank { "User" }}&background=random&color=fff",
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .border(3.dp, theme.accent, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(28.dp))
                        }
                    }
                    Surface(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = CircleShape,
                        color = theme.accent,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, "Change profile picture", tint = Color.Black, modifier = Modifier.padding(7.dp))
                    }
                }
            }

            // Display name
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "DISPLAY NAME",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { viewModel.updateDisplayName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.border,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // Privacy
            SectionHeader("PRIVACY")
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .then(ThemeBorderModifier()),
                color = theme.surface
            ) {
                Column {
                    PreferenceToggleRow(
                        icon = Icons.Default.BarChart,
                        title = "PUBLIC STATS",
                        subtitle = if (isStatsPublic) "VISIBLE ON YOUR PUBLIC PROFILE" else "HIDDEN FROM OTHER USERS",
                        checked = isStatsPublic,
                        onCheckedChange = { viewModel.setStatsPublic(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = theme.textPrimary.copy(alpha = 0.05f)
                    )
                    PreferenceToggleRow(
                        icon = Icons.Default.Favorite,
                        title = "PUBLIC FAVORITES",
                        subtitle = if (isFavoritesPublic) "VISIBLE ON YOUR PUBLIC PROFILE" else "HIDDEN FROM OTHER USERS",
                        checked = isFavoritesPublic,
                        onCheckedChange = { viewModel.setFavoritesPublic(it) }
                    )
                }
            }

            // Favorite shows
            SectionHeader("FAVORITE SHOWS (UP TO 5)")
            if (candidateShows.isEmpty()) {
                Text(
                    "Mark shows as Watching or Completed to pick favorites here.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .heightIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(candidateShows, key = { it.id }) { media ->
                        FavoriteShowTile(
                            media = media,
                            isSelected = favoriteShows.any { it.id == media.id },
                            onClick = { viewModel.toggleFavoriteShow(media) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveProfile() },
                enabled = saveState !is SaveProfileState.Saving,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (saveState is SaveProfileState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("SAVE PROFILE", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
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
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun FavoriteShowTile(media: MediaSummary, isSelected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surface)
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) theme.accent else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(theme.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            media.title,
            color = theme.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
