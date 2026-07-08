package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.watchorderengine.data.import_list.ImportedAnimeEntry
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.ImportSource
import com.example.watchorderengine.ui.viewmodel.ImportUiState
import com.example.watchorderengine.ui.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportListScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val theme    = LocalAppTheme.current
    val uiState  by viewModel.uiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<ImportSource>(ImportSource.AniList) }
    var overwriteExisting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "IMPORT ANIME LIST",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = theme.textPrimary
            )
        }

        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "import_state"
        ) { state ->
            when (state) {

                // ── Idle / form ───────────────────────────────────────────────
                is ImportUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Source selector tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ImportSourceChip(
                                label     = "AniList",
                                selected  = selectedSource is ImportSource.AniList,
                                accentColor = Color(0xFF02A9FF),
                                onClick   = { selectedSource = ImportSource.AniList }
                            )
                            ImportSourceChip(
                                label     = "MyAnimeList",
                                selected  = selectedSource is ImportSource.MAL,
                                accentColor = Color(0xFF2E51A2),
                                onClick   = { selectedSource = ImportSource.MAL }
                            )
                        }

                        // Platform info card
                        Surface(
                            color = theme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (selectedSource is ImportSource.AniList)
                                        "AniList import reads your public anime list. " +
                                        "Make sure your list visibility is set to Public in your AniList profile settings."
                                    else
                                        "MAL import uses the Jikan API to read your public MyAnimeList. " +
                                        "Make sure your list is not set to Private in your MAL profile.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Username input
                        OutlinedTextField(
                            value         = username,
                            onValueChange = { username = it },
                            label         = {
                                Text(
                                    if (selectedSource is ImportSource.AniList)
                                        "AniList Username"
                                    else
                                        "MyAnimeList Username"
                                )
                            },
                            modifier     = Modifier.fillMaxWidth(),
                            singleLine   = true,
                            leadingIcon  = {
                                Icon(Icons.Default.Person, null, tint = theme.textPrimary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = theme.accent,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor          = theme.accent,
                                focusedLabelColor    = theme.accent,
                                focusedTextColor     = theme.textPrimary,
                                unfocusedTextColor   = theme.textPrimary
                            )
                        )

                        // Overwrite toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Overwrite existing tracking",
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 14.sp,
                                    color      = theme.textPrimary
                                )
                                Text(
                                    "If off, shows already tracked in the app are kept as-is",
                                    fontSize   = 11.sp,
                                    color      = Color.Gray
                                )
                            }
                            Switch(
                                checked         = overwriteExisting,
                                onCheckedChange = { overwriteExisting = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.accent,
                                    checkedTrackColor = theme.accent.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Button(
                            onClick  = { viewModel.fetchPreview(username, selectedSource) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape    = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "FETCH LIST",
                                fontWeight = FontWeight.Black,
                                fontSize   = 15.sp
                            )
                        }
                    }
                }

                // ── Loading ───────────────────────────────────────────────────
                is ImportUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = theme.accent)
                            Spacer(Modifier.height(16.dp))
                            Text("Fetching your list…", color = Color.Gray)
                        }
                    }
                }

                // ── Syncing ────────────────────────────────────────────────────
                is ImportUiState.Syncing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { if (state.total > 0) state.current.toFloat() / state.total else 0f },
                                color = theme.accent,
                                strokeWidth = 6.dp,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "Importing ${state.current} / ${state.total}",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                "Syncing with cloud and marking episodes...",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // ── Preview ───────────────────────────────────────────────────
                is ImportUiState.Preview -> {
                    val entries  = state.entries
                    val grouped  = entries.groupBy { it.trackingState }
                    val sourceName = if (state.source is ImportSource.AniList) "AniList" else "MAL"

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Summary banner
                        Surface(color = theme.surface) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${entries.size} shows from $sourceName",
                                        fontWeight = FontWeight.Black,
                                        fontSize   = 16.sp,
                                        color      = theme.textPrimary
                                    )
                                    Text(
                                        grouped.entries.joinToString(" · ") {
                                            "${it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() }}: ${it.value.size}"
                                        },
                                        fontSize = 11.sp,
                                        color    = Color.Gray
                                    )
                                }
                                Button(
                                    onClick  = { viewModel.confirmImport(entries, overwriteExisting) },
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                                    shape    = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Text("IMPORT", fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            }
                        }

                        // Entry list
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            grouped.forEach { (state, list) ->
                                item {
                                    Text(
                                        text     = stateLabel(state),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color    = stateColor(state),
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(list, key = { "${it.malId}_${it.anilistId}_${it.title}" }) { entry ->
                                    ImportEntryRow(entry = entry)
                                }
                            }
                        }
                    }
                }

                // ── Success ───────────────────────────────────────────────────
                is ImportUiState.Success -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint     = Color(0xFF4ADE80),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "${state.written} shows imported",
                                fontWeight = FontWeight.Black,
                                fontSize   = 22.sp,
                                color      = theme.textPrimary
                            )
                            if (state.written < state.total) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${state.total - state.written} could not be resolved (make sure they are searchable in the app)",
                                    fontSize  = 12.sp,
                                    color     = Color.Gray,
                                    modifier  = Modifier.padding(horizontal = 32.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = onBack) {
                                Text("Done", color = theme.textPrimary)
                            }
                        }
                    }
                }

                // ── Error ─────────────────────────────────────────────────────
                is ImportUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                tint     = Color(0xFFFF6B6B),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.message,
                                color     = Color(0xFFFF6B6B),
                                fontSize  = 14.sp,
                                modifier  = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick  = { viewModel.reset() },
                                colors   = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) { Text("Try Again") }
                        }
                    }
                }
            }
        }

        // ── Bottom Syncing Indicator ──────────────────────────────────────────
        val syncingState = uiState as? ImportUiState.Syncing
        if (syncingState != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Syncing with Firestore...",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "${syncingState.current} / ${syncingState.total}",
                            color = theme.accent,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val progressVal = if (syncingState.total > 0) syncingState.current.toFloat() / syncingState.total else 0f
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = theme.accent,
                        trackColor = theme.accent.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSourceChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick      = onClick,
        shape        = RoundedCornerShape(24.dp),
        color        = if (selected) accentColor.copy(alpha = 0.18f) else theme.surface,
        border       = if (selected) BorderStroke(1.5.dp, accentColor) else BorderStroke(1.dp, Color.Gray.copy(0.3f))
    ) {
        Text(
            text       = label,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            color      = if (selected) accentColor else Color.Gray,
            fontSize   = 13.sp,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ImportEntryRow(entry: ImportedAnimeEntry) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model         = entry.coverImageUrl,
            contentDescription = null,
            contentScale  = ContentScale.Crop,
            modifier      = Modifier
                .size(40.dp, 56.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = entry.title,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = theme.textPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (entry.userRating != null) {
                Text(
                    "★ ${String.format("%.1f", entry.userRating)} / 10",
                    fontSize = 11.sp,
                    color    = Color(0xFFFFD700)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = stateColor(entry.trackingState).copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text     = stateLabel(entry.trackingState),
                color    = stateColor(entry.trackingState),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

private fun stateLabel(state: TrackingState) = when (state) {
    TrackingState.WATCHING   -> "WATCHING"
    TrackingState.COMPLETED  -> "COMPLETED"
    TrackingState.PAUSED     -> "ON HOLD"
    TrackingState.PLANNED    -> "PLAN TO WATCH"
    TrackingState.DROPPED    -> "DROPPED"
}

private fun stateColor(state: TrackingState) = when (state) {
    TrackingState.WATCHING   -> Color(0xFF60A5FA)
    TrackingState.COMPLETED  -> Color(0xFF4ADE80)
    TrackingState.PAUSED     -> Color(0xFFFACC15)
    TrackingState.PLANNED    -> Color(0xFFA78BFA)
    TrackingState.DROPPED    -> Color(0xFFFF6B6B)
}
