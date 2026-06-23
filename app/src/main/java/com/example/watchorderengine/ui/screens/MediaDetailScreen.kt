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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    onUniverseClick: (String) -> Unit = {},
    onCharacterClick: (Int, String, String, Boolean, Int?) -> Unit = { _, _, _, _, _ -> },
    viewModel: MediaDetailViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val media by viewModel.mediaDetail.collectAsState()
    val universes by viewModel.universes.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isEpisodesLoading by viewModel.isEpisodesLoading.collectAsState()
    val generationError by viewModel.generationError.collectAsState()

    LaunchedEffect(mediaId) {
        android.util.Log.d("MediaDetail", "Loading mediaId: $mediaId")
        viewModel.loadMediaDetail(mediaId)
    }
    
    // Explicitly watch episodes list for transition from loading to success
    val episodesBySeason by viewModel.episodes.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        media?.let { detail ->
            android.util.Log.d("MediaDetail", "Rendering DetailContent for: ${detail.title}")
            key(detail.id) {
                DetailContent(
                    detail = detail,
                    episodes = episodesBySeason, // Use the collected state here
                    isAnalyzing = isAnalyzing,
                    isEpisodesLoading = isEpisodesLoading,
                    universe = universes,
                    generationError = generationError,
                    onDismissGenerationError = { viewModel.dismissGenerationError() },
                    onBack = onBack,
                    onUpdateTracking = { viewModel.updateTrackingState(detail.id, it) },
                    onToggleEpisode = { viewModel.toggleEpisodeWatched(it.id, detail.id) },
                    onSeasonChange = { viewModel.loadEpisodes(detail.id, it) },
                    onGenerateOrder = { viewModel.generateWatchOrder(detail.id) },
                    onUniverseClick = onUniverseClick,
                    onCharacterClick = onCharacterClick,
                    viewModel = viewModel
                )
            }
        }
        
        if (isLoading && media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        }
        
        if (!isLoading && media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load details for $mediaId", color = theme.textPrimary)
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    episodes: List<EpisodeItem>,
    isAnalyzing: Boolean,
    isEpisodesLoading: Boolean,
    universe: List<com.example.watchorderengine.data.model.Universe>,
    generationError: String?,
    onDismissGenerationError: () -> Unit,
    onBack: () -> Unit,
    onUpdateTracking: (TrackingState) -> Unit,
    onToggleEpisode: (EpisodeItem) -> Unit,
    onSeasonChange: (Int) -> Unit,
    onGenerateOrder: () -> Unit,
    onUniverseClick: (String) -> Unit,
    onCharacterClick: (Int, String, String, Boolean, Int?) -> Unit,
    viewModel: MediaDetailViewModel
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    val initialTab = if (detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE) "chronology" else "episodes"
    var activeTab by remember { mutableStateOf(initialTab) }
    var selectedSeason by remember { mutableIntStateOf(detail.seasons.firstOrNull()?.seasonNumber ?: 1) }

    val watchedCount = episodes.count { it.isWatched }
    val totalEps = detail.numberOfEpisodes ?: episodes.size
    val progress = if (totalEps > 0) watchedCount.toFloat() / totalEps else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Backdrop & Hero Section
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
                val context = androidx.compose.ui.platform.LocalContext.current
                IconButton(
                    onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${detail.title} on Watch Order Engine!")
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White)
                }
            }

            // Progress Ring
            if (progress > 0) {
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
        }

        // Title & Stats
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
                    Text(String.format("%.1f", detail.voteAverage), color = Color(0xFFFFD700), fontSize = 14.sp)
                }
                Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(detail.ageRating, color = Color.White, fontSize = 10.sp)
                }
                Text("${watchedCount}/${totalEps} eps", color = Color.Gray, fontSize = 10.sp)
            }
            
            // Overview Section
            if (detail.overview.isNotBlank()) {
                Text(
                    text = detail.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Watchlist Selector
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

        // Tabs
        val tabs = if (detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE) {
            listOf("chronology", "characters")
        } else {
            listOf("episodes", "characters", "chronology")
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
            tabs.forEach { tab ->
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
                                .width(40.dp) // Fixed width for tab indicator
                                .background(theme.accent)
                        )
                    }
                }
            }
        }

        Crossfade(targetState = activeTab, label = "tab_content") { tab ->
            when (tab) {
                "episodes" -> EpisodesTab(
                    seasons = detail.seasons,
                    selectedSeason = selectedSeason,
                    episodes = episodes,
                    isAnalyzing = isAnalyzing,
                    isEpisodesLoading = isEpisodesLoading,
                    onSeasonChange = { 
                        selectedSeason = it
                        onSeasonChange(it)
                    },
                    onToggleEpisode = onToggleEpisode
                )
                "characters" -> CharactersTab(detail, onCharacterClick, viewModel)
                "chronology" -> ChronologyTab(detail, isAnalyzing, universe, generationError, onGenerateOrder, onUniverseClick, onDismissGenerationError)
            }
        }
    }
}

@Composable
private fun EpisodesTab(
    seasons: List<com.example.watchorderengine.data.model.SeasonSummary>,
    selectedSeason: Int,
    episodes: List<EpisodeItem>,
    isAnalyzing: Boolean,
    isEpisodesLoading: Boolean,
    onSeasonChange: (Int) -> Unit,
    onToggleEpisode: (EpisodeItem) -> Unit
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(16.dp)) {
        // Season Selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(seasons) { season ->
                val isSelected = season.seasonNumber == selectedSeason
                Surface(
                    modifier = Modifier.clickable { onSeasonChange(season.seasonNumber) },
                    shape = CircleShape,
                    color = if (isSelected) Color.White else theme.surface,
                    border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        "S${season.seasonNumber}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (isAnalyzing || isEpisodesLoading) {
            repeat(3) { EpisodeRowPlaceholder() }
        } else if (episodes.isEmpty()) {
            Text(
                "No episodes found for this season.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            episodes.forEach { episode ->
                EpisodeRow(episode, onToggleEpisode)
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeItem, onToggleEpisode: (EpisodeItem) -> Unit) {
    val theme = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }

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
                        .size(100.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = episode.stillUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (episode.isWatched) 0.4f else 0.8f)
                    )
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ep ${episode.episodeNumber}", color = Color.Gray, fontSize = 10.sp)
                        if (episode.episodeType != com.example.watchorderengine.data.model.EpisodeType.CANON) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.background(
                                if (episode.episodeType == com.example.watchorderengine.data.model.EpisodeType.FILLER) Color.Red.copy(alpha = 0.2f) else Color.Yellow.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            ).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(episode.episodeType.name, color = if (episode.episodeType == com.example.watchorderengine.data.model.EpisodeType.FILLER) Color.Red else Color.Yellow, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = episode.title, 
                        color = if (episode.isWatched) Color.Gray else Color.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (episode.isWatched) TextDecoration.LineThrough else null
                    )
                }
                IconButton(onClick = { onToggleEpisode(episode) }) {
                    Icon(
                        if (episode.isWatched) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                        null,
                        tint = if (episode.isWatched) Color(0xFF4ADE80) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (expanded) {
                Text(
                    text = episode.overview.ifBlank { "No synopsis available." },
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun EpisodeRowPlaceholder() {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.width(40.dp).height(8.dp).background(Color.DarkGray))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).background(Color.DarkGray))
            }
        }
    }
}

@Composable
private fun CharactersTab(
    detail: MediaDetail,
    onCharacterClick: (Int, String, String, Boolean, Int?) -> Unit,
    viewModel: MediaDetailViewModel
) {
    val isAnime = detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.ANIME
    // Batched AniList lookup (one request for the whole show) — already triggered
    // by the ViewModel when the media loaded; this just observes the result.
    val characterArt by viewModel.characterArt.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        detail.cast.take(10).forEach { cast ->
            CharacterRow(
                cast = cast,
                viewModel = viewModel,
                characterArtUrl = if (isAnime) viewModel.characterArtFor(cast.character) else null,
                onClick = { onCharacterClick(cast.tmdbId, cast.character, detail.title, isAnime, detail.anilistId) }
            )
        }
        if (isAnime && characterArt.isEmpty()) {
            Text(
                "Looking up character art on AniList…",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CharacterRow(
    cast: com.example.watchorderengine.data.model.CastMember,
    viewModel: MediaDetailViewModel,
    characterArtUrl: String?,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    var biography by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(cast.tmdbId) {
        biography = viewModel.getPersonBiography(cast.tmdbId)
    }

    // Primary avatar is the AniList character art when we found one (Luffy's own
    // illustration, not the voice actor's headshot) — falls back to the TMDB
    // profile photo for non-anime titles or characters AniList didn't match.
    val primaryImage = characterArtUrl ?: cast.profileUrl
    val showVoiceBadge = characterArtUrl != null && characterArtUrl != cast.profileUrl

    Surface(
        onClick = onClick,
        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(
                        model = primaryImage,
                        contentDescription = cast.character,
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.DarkGray).border(2.dp, theme.accent, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (showVoiceBadge) {
                        AsyncImage(
                            model = cast.profileUrl,
                            contentDescription = cast.name,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray)
                                .border(1.5.dp, theme.background, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(cast.character, color = Color.White, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showVoiceBadge) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accent, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(cast.name, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = biography ?: "Fetching character data...",
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            // Simulated Fan Chat (simplified)
            Box(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FAN VIBE:", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Most loved character in this arc", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun ChronologyTab(
    detail: MediaDetail,
    isAnalyzing: Boolean,
    universe: List<com.example.watchorderengine.data.model.Universe>,
    generationError: String?,
    onGenerate: () -> Unit,
    onUniverseClick: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(16.dp)) {
        if (generationError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                color = Color(0xFFF87171).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(generationError, color = Color(0xFFF87171), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFF87171))
                    }
                }
            }
        }

        // One card per universe this media belongs to — previously only the
        // first match was ever shown, so a crossover title that's part of
        // two generated universes silently dropped the second.
        universe.forEach { u ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onUniverseClick(u.id) },
                color = theme.accent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountTree, null, tint = theme.accent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Part of ${u.name}", color = theme.textPrimary, fontWeight = FontWeight.Bold)
                        Text("View full interactive skill tree", color = theme.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, null, tint = theme.accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Watch Order Guide", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            if (detail.arcs.isEmpty() && !isAnalyzing) {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("GENERATE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = theme.primary)
                }
            }
        }

        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = theme.accent)
                    Text("AI is analyzing episodes...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else if (detail.arcs.isEmpty()) {
            Text(
                "No watch order guide available for this show yet. Click generate to use Gemini AI.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        } else {
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
                        Text("S${arc.startSeason} E${arc.startEpisode} - S${arc.endSeason} E${arc.endEpisode}", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(arc.synopsis, color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
