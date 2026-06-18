package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.EpisodeItem
import com.example.watchorderengine.data.model.MediaDetail
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.MediaDetailViewModel

@Composable
fun MediaDetailScreen(
    mediaId: String,
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val media by viewModel.mediaDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(mediaId) {
        viewModel.loadMediaDetail(mediaId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        if (isLoading && media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else {
            media?.let { detail ->
                DetailContent(
                    detail = detail,
                    onBack = onBack,
                    onUpdateTracking = { viewModel.updateTrackingState(detail.id, it) },
                    onToggleEpisode = { viewModel.toggleEpisodeWatched(it.id, detail.id) }
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    onBack: () -> Unit,
    onUpdateTracking: (TrackingState) -> Unit,
    onToggleEpisode: (EpisodeItem) -> Unit
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    val chunks = remember(detail.seasons) {
        detail.seasons.map { it.name }.ifEmpty { listOf("Episodes") }
    }
    var activeChunk by remember { mutableStateOf(chunks[0]) }
    var activeTab by remember { mutableStateOf("episodes") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
            AsyncImage(
                model = detail.backdropUrl ?: detail.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, theme.background),
                            startY = 400f
                        )
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                IconButton(
                    onClick = { /* Share */ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White)
                }
            }

            val progress = detail.userProgress?.let {
                if (detail.numberOfEpisodes ?: 0 > 0) it.totalEpisodesWatched.toFloat() / detail.numberOfEpisodes!! else 0f
            } ?: 0f
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 16.dp)
                    .size(56.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 4.dp.toPx()))
                    drawArc(
                        color = theme.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineMedium,
                color = theme.textPrimary,
                fontWeight = FontWeight.Black
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(detail.releaseYear, color = Color.LightGray, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                    Text(detail.voteAverage.toString(), color = Color(0xFFFFD700), fontSize = 14.sp)
                }
                Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(detail.ageRating, color = Color.White, fontSize = 10.sp)
                }
            }

            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .then(ThemeBorderModifier()),
                    color = theme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            detail.userProgress?.trackingState?.displayName ?: "Add to Watchlist",
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            null,
                            tint = theme.textSecondary
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(theme.surface)
                ) {
                    TrackingState.entries.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(state.displayName, color = theme.textPrimary) },
                            onClick = {
                                onUpdateTracking(state)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 16.dp)
        ) {
            listOf("episodes", "characters", "chronology").forEach { tab ->
                val isSelected = activeTab == tab
                Column(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .clickable { activeTab = tab }
                ) {
                    Text(
                        text = tab.uppercase(),
                        color = if (isSelected) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .fillMaxWidth()
                                .background(theme.accent)
                        )
                    }
                }
            }
        }

        Crossfade(targetState = activeTab, label = "tab_content") { tab ->
            when (tab) {
                "episodes" -> EpisodesTab(chunks, activeChunk, { activeChunk = it })
                "characters" -> CharactersTab(detail)
                "chronology" -> ChronologyTab(detail)
            }
        }
    }
}

@Composable
private fun EpisodesTab(
    chunks: List<String>,
    activeChunk: String,
    onChunkClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chunks) { chunk ->
                    val isSelected = chunk == activeChunk
                    Surface(
                        modifier = Modifier.clickable { onChunkClick(chunk) },
                        shape = CircleShape,
                        color = if (isSelected) Color.White else theme.surface,
                        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            chunk,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = if (isSelected) Color.Black else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder for real episodes
        repeat(3) { i ->
            EpisodeRow(i + 1)
        }
    }
}

@Composable
private fun EpisodeRow(num: Int) {
    val theme = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }
    var watched by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { expanded = !expanded },
        color = theme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp, 50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Episode $num", color = Color.Gray, fontSize = 10.sp)
                    Text("Title Placeholder", color = if (watched) Color.Gray else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                IconButton(onClick = { watched = !watched }) {
                    Icon(
                        if (watched) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                        null,
                        tint = if (watched) Color(0xFF4ADE80) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CharactersTab(detail: MediaDetail) {
    Column(modifier = Modifier.padding(16.dp)) {
        detail.cast.take(5).forEach { cast ->
            CharacterRow(cast.character, cast.name, cast.profileUrl)
        }
    }
}

@Composable
private fun CharacterRow(name: String, actor: String, profileUrl: String?) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profileUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.DarkGray).border(2.dp, theme.accent, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(actor, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("FAN CHAT", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("142 Online", color = Color.Gray, fontSize = 8.sp)
                    }
                    Text("Bot: $name is one of the greats for sure.", color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(24.dp).background(theme.surface, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Join discussion...", color = Color.Gray, fontSize = 9.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronologyTab(detail: MediaDetail) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(Icons.Default.Build, null, tint = theme.accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Watch Order Guide", color = Color.White, fontWeight = FontWeight.Bold)
        }
        detail.arcs.forEachIndexed { i, arc ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.accent).border(2.dp, theme.background, CircleShape))
                    if (i < detail.arcs.size - 1) {
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(arc.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("Ep ${arc.startEpisode}-${arc.endEpisode}", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(arc.synopsis, color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
