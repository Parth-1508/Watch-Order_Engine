package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import android.os.Build
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.watchorderengine.data.model.WatchProviderItem
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
    val media by viewModel.mediaDetail.collectAsStateWithLifecycle()
    val universes by viewModel.universes.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isEpisodesLoading by viewModel.isEpisodesLoading.collectAsStateWithLifecycle()
    val isBulkSyncing by viewModel.isBulkSyncing.collectAsStateWithLifecycle()
    val generationError by viewModel.generationError.collectAsStateWithLifecycle()
    val generationSuccess by viewModel.generationSuccess.collectAsStateWithLifecycle()
    val aggregatedReviews by viewModel.aggregatedReviews.collectAsStateWithLifecycle()

    LaunchedEffect(mediaId) {
        android.util.Log.d("MediaDetail", "Loading mediaId: $mediaId")
        viewModel.loadMediaDetail(mediaId)
    }
    
    val episodesBySeason by viewModel.episodes.collectAsStateWithLifecycle()
    val bulkMarkPrompt by viewModel.bulkMarkPrompt.collectAsStateWithLifecycle()
    val showWelcomeTip by viewModel.showWelcomeTip.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        media?.let { detail ->
            key(detail.id) {
                DetailContent(
                    detail = detail,
                    episodes = episodesBySeason,
                    isAnalyzing = isAnalyzing,
                    isEpisodesLoading = isEpisodesLoading,
                    universe = universes,
                    generationError = generationError,
                    generationSuccess = generationSuccess,
                    bulkMarkPrompt = bulkMarkPrompt,
                    showWelcomeTip = showWelcomeTip,
                    aggregatedReviews = aggregatedReviews,
                    onDismissGenerationError = { viewModel.dismissGenerationError() },
                    onDismissGenerationSuccess = { viewModel.dismissGenerationSuccess() },
                    onBack = onBack,
                    onUpdateTracking = { viewModel.updateTrackingState(detail.id, it) },
                    onToggleEpisode = { viewModel.toggleEpisodeWatched(it, detail.id) },
                    onSeasonChange = { viewModel.loadEpisodes(detail.id, it) },
                    onGenerateOrder = { viewModel.generateWatchOrder(detail.id) },
                    onUniverseClick = onUniverseClick,
                    onCharacterClick = onCharacterClick,
                    viewModel = viewModel
                )
            }
        }

        if (isBulkSyncing) {
            SyncingOverlay()
        }
        
        if (isLoading && media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        }
        
        if (!isLoading && media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load details", color = theme.textPrimary)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadMediaDetail(mediaId) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SyncingOverlay() {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.8f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = theme.accent,
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Syncing progress to cloud...",
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "This might take a moment",
                color = theme.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(
    detail: MediaDetail,
    episodes: List<EpisodeItem>,
    isAnalyzing: Boolean,
    isEpisodesLoading: Boolean,
    universe: List<com.example.watchorderengine.data.model.Universe>,
    generationError: String?,
    generationSuccess: Boolean,
    bulkMarkPrompt: EpisodeItem?,
    showWelcomeTip: Boolean,
    aggregatedReviews: List<com.example.watchorderengine.data.model.ReviewItem>,
    onDismissGenerationError: () -> Unit,
    onDismissGenerationSuccess: () -> Unit,
    onBack: () -> Unit,
    onUpdateTracking: (TrackingState?) -> Unit,
    onToggleEpisode: (EpisodeItem) -> Unit,
    onSeasonChange: (Int) -> Unit,
    onGenerateOrder: () -> Unit,
    onUniverseClick: (String) -> Unit,
    onCharacterClick: (Int, String, String, Boolean, Int?) -> Unit,
    viewModel: MediaDetailViewModel
) {
    val theme = LocalAppTheme.current
    val initialTab = if (detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE) "chronology" else "episodes"
    var activeTab by remember { mutableStateOf(initialTab) }
    var selectedSeason by remember { mutableIntStateOf(detail.seasons.firstOrNull()?.seasonNumber ?: 1) }

    val watchedCount = detail.userProgress?.totalEpisodesWatched ?: 0
    val totalEps = detail.numberOfEpisodes ?: 0
    val progress = if (totalEps > 0) (watchedCount.toFloat() / totalEps).coerceAtMost(1f) else 0f

    val tabs = remember(detail.mediaCategory) {
        if (detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.MOVIE) {
            listOf("chronology", "characters", "reviews")
        } else {
            listOf("episodes", "characters", "chronology", "reviews")
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Bulk Mark Prompt Dialog
            item {
                if (bulkMarkPrompt != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissBulkMark() },
                        containerColor = theme.surface,
                        titleContentColor = theme.textPrimary,
                        textContentColor = theme.textSecondary,
                        title = { Text("Mark previous episodes?") },
                        text = { Text("You haven't marked episodes before ${bulkMarkPrompt.episodeNumber}. Do you want to mark all previous episodes as watched?") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.confirmBulkMark(detail.id) }) {
                                Text("Mark Episodes", color = theme.accent)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissBulkMark() }) {
                                Text("Cancel", color = theme.textSecondary)
                            }
                        }
                    )
                }
            }

            // Backdrop & Hero Section
            item {
                Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                    AsyncImage(
                        model = detail.backdropUrl ?: detail.posterUrl,
                        contentDescription = "Show Backdrop",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Movie)
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
                                .background(theme.background.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = theme.textPrimary)
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
                                .background(theme.background.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Share, "Share show", tint = theme.textPrimary)
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
                                drawCircle(color = theme.textSecondary.copy(alpha = 0.15f), style = Stroke(width = 4.dp.toPx()))
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
                                color = theme.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Title & Stats
            item {
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
                        Text(detail.releaseYear, color = theme.textSecondary, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                            Text(String.format("%.1f", detail.voteAverage), color = Color(0xFFFFD700), fontSize = 14.sp)
                        }
                        Box(modifier = Modifier.border(1.dp, theme.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(detail.ageRating, color = theme.textPrimary, fontSize = 10.sp)
                        }
                        Text("${watchedCount}/${totalEps} eps", color = theme.textSecondary, fontSize = 10.sp)
                    }
                    
                    // Overview Section
                    if (detail.overview.isNotBlank()) {
                        var isExpanded by remember { mutableStateOf(false) }
                        Text(
                            text = detail.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textSecondary,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null 
                                ) { isExpanded = !isExpanded }
                                .animateContentSize(),
                            lineHeight = 20.sp,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Watch Providers
                    if (detail.watchProviders.isNotEmpty()) {
                        WatchProvidersCard(detail.watchProviders)
                    }

                    // Trailer
                    if (!detail.trailerKey.isNullOrBlank()) {
                        TrailerCard(detail.trailerKey)
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
                            DropdownMenuItem(
                                text = { Text("None (Remove)", color = theme.textPrimary) },
                                onClick = {
                                    onUpdateTracking(null)
                                    expanded = false
                                }
                            )
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
            }

            // Tabs (Sticky)
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.background)
                        .drawBehind {
                            drawLine(
                                color = theme.textSecondary.copy(alpha = 0.1f),
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
                                .clickable { 
                                    activeTab = tab
                                    scope.launch {
                                        if (listState.firstVisibleItemIndex >= 3) {
                                            listState.animateScrollToItem(3)
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = tab.uppercase(),
                                color = if (isSelected) theme.textPrimary else theme.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(40.dp)
                                        .background(theme.accent)
                                )
                            }
                        }
                    }
                }
            }

            // Content based on active tab
            when (activeTab) {
                "episodes" -> {
                    // Tip Card
                    if (showWelcomeTip && episodes.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = theme.accent.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lightbulb, null, tint = theme.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Tip: Mark the episode you just watched, and you'll be able to mark all previous episodes at once!",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        lineHeight = 16.sp
                                    )
                                    IconButton(onClick = { viewModel.dismissWelcomeTip() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, tint = theme.textSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // Season Selector
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(detail.seasons) { season ->
                                    val isSelected = season.seasonNumber == selectedSeason
                                    Surface(
                                        modifier = Modifier.clickable { 
                                            selectedSeason = season.seasonNumber
                                            onSeasonChange(season.seasonNumber) 
                                        },
                                        shape = CircleShape,
                                        color = if (isSelected) theme.textPrimary else theme.surface,
                                        border = if (isSelected) null else BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.1f))
                                    ) {
                                        Text(
                                            "S${season.seasonNumber}",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                            color = if (isSelected) theme.background else theme.textSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { viewModel.markSeasonAsWatched(detail.id, selectedSeason) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Checklist, "Mark Season", tint = theme.accent, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { viewModel.unmarkSeasonAsWatched(detail.id, selectedSeason) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.RemoveDone, "Unmark Season", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    // Episodes
                    if (isAnalyzing || isEpisodesLoading) {
                        items(3) { EpisodeRowPlaceholder() }
                    } else if (episodes.isEmpty()) {
                        item {
                            Text(
                                "No episodes found for this season.",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(episodes, key = { it.id }) { episode ->
                            EpisodeRow(episode, onToggleEpisode)
                        }
                    }
                }
                "characters" -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            detail.cast.take(10).forEach { cast ->
                                CharacterRow(
                                    cast = cast,
                                    viewModel = viewModel,
                                    characterArtUrl = if (detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.ANIME) viewModel.characterArtFor(cast.character) else null,
                                    onClick = { onCharacterClick(cast.tmdbId, cast.character, detail.title, detail.mediaCategory == com.example.watchorderengine.data.model.MediaCategory.ANIME, detail.anilistId) }
                                )
                            }
                        }
                    }
                }
                "chronology" -> {
                    item {
                        ChronologyTab(
                            detail = detail,
                            isAnalyzing = isAnalyzing,
                            universe = universe,
                            generationError = generationError,
                            generationSuccess = generationSuccess,
                            onGenerate = onGenerateOrder,
                            onUniverseClick = onUniverseClick,
                            onDismissError = onDismissGenerationError,
                            onDismissSuccess = onDismissGenerationSuccess
                        )
                    }
                }
                "reviews" -> {
                    item {
                        ReviewsTab(
                            mediaId = detail.id,
                            mediaTitle = detail.title,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewsTab(
    mediaId: String,
    mediaTitle: String,
    viewModel: MediaDetailViewModel
) {
    val theme = LocalAppTheme.current
    val reviews by viewModel.aggregatedReviews.collectAsStateWithLifecycle()
    val isReviewsLoading by viewModel.isReviewsLoading.collectAsStateWithLifecycle()
    var showReviewSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { showReviewSheet = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
        ) {
            Icon(Icons.Default.RateReview, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("WRITE A REVIEW", color = Color.White, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(24.dp))

        if (isReviewsLoading && reviews.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else if (reviews.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No reviews yet. Be the first to share your thoughts!",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://www.google.com/search?q=${mediaTitle}+movie+reviews") },
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SEARCH ON GOOGLE")
                }
            }
        } else {
            reviews.forEach { review ->
                ReviewItem(
                    review = review,
                    onDelete = { viewModel.deleteReview(review.id) }
                )
            }
        }
    }

    if (showReviewSheet) {
        ReviewSubmissionDialog(
            onDismiss = { showReviewSheet = false },
            onSubmit = { rating, text, spoilers, emoji ->
                viewModel.submitReview(mediaId, rating, text, spoilers, emoji)
                showReviewSheet = false
            }
        )
    }
}

@Composable
private fun ReviewItem(
    review: com.example.watchorderengine.data.model.ReviewItem,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current
    var isExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fallbackAvatar = "https://ui-avatars.com/api/?name=${review.authorName.ifBlank { "User" }}&background=random&color=fff"
                    AsyncImage(
                        model = review.authorAvatarUrl.takeIf { !it.isNullOrBlank() } ?: fallbackAvatar,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.3f)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = review.authorName,
                                color = theme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(review.emojiReaction, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceBadge(review.source)
                            if (review.rating != null) {
                                Spacer(Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = String.format("%.1f", review.rating),
                                        color = Color(0xFFFFD700),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                Row {
                    if (review.externalUrl != null) {
                        IconButton(onClick = { uriHandler.openUri(review.externalUrl) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.OpenInNew, null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (review.source == com.example.watchorderengine.data.model.ReviewSource.LOCAL) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, tint = theme.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (review.reviewText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                if (review.hasSpoilers && !isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.background.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { isExpanded = true }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("REVIEW CONTAINS SPOILERS. TAP TO SHOW.", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val cleanText = if (review.source == com.example.watchorderengine.data.model.ReviewSource.LOCAL) {
                        review.reviewText 
                    } else {
                        review.reviewText.replace(Regex("<[^>]*>"), "")
                    }
                    
                    Text(
                        text = cleanText,
                        color = theme.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    )
                }
            }
            
            if (review.createdAt > 0) {
                Spacer(Modifier.height(8.dp))
                val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                Text(
                    text = "Posted on ${sdf.format(java.util.Date(review.createdAt))}",
                    color = theme.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: com.example.watchorderengine.data.model.ReviewSource) {
    val (color, label) = when (source) {
        com.example.watchorderengine.data.model.ReviewSource.LOCAL -> Color(0xFF4FC3F7) to "WOE"
        com.example.watchorderengine.data.model.ReviewSource.TMDB -> Color(0xFF01B4E4) to "TMDB"
        com.example.watchorderengine.data.model.ReviewSource.ANILIST -> Color(0xFF02A9FF) to "AniList"
        com.example.watchorderengine.data.model.ReviewSource.MAL -> Color(0xFF2E51A2) to "MAL"
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSubmissionDialog(
    onDismiss: () -> Unit,
    onSubmit: (Float, String, Boolean, String) -> Unit
) {
    val theme = LocalAppTheme.current
    var rating by remember { mutableFloatStateOf(8f) }
    var text by remember { mutableStateOf("") }
    var hasSpoilers by remember { mutableStateOf(false) }
    
    val ratingEmojis = listOf("🤬", "😡", "☹️", "😐", "🤨", "🙂", "😊", "🤩", "😍", "🤯")
    val emojiLabels = listOf("Horrible", "Bad", "Poor", "Meh", "Hmm", "Okay", "Good", "Great", "Amazing", "Masterpiece")

    val currentEmojiIndex = (rating.toInt() - 1).coerceIn(0, 9)
    val currentEmoji = ratingEmojis[currentEmojiIndex]
    val currentLabel = emojiLabels[currentEmojiIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        title = { Text("Write a Review", fontWeight = FontWeight.Black, color = theme.textPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Drag to rate your experience", fontSize = 12.sp, color = theme.textSecondary)
                
                Spacer(Modifier.height(24.dp))
                
                Text(currentEmoji, fontSize = 48.sp)
                Text(
                    text = currentLabel,
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(16.dp))
                
                Slider(
                    value = rating,
                    onValueChange = { rating = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = theme.accent,
                        inactiveTrackColor = theme.textSecondary.copy(alpha = 0.2f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(theme.background, CircleShape)
                                .border(2.dp, theme.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(currentEmoji, fontSize = 20.sp)
                        }
                    }
                )
                
                Text(
                    text = "${String.format("%.1f", rating)} / 10", 
                    fontWeight = FontWeight.Black, 
                    color = theme.textPrimary,
                    fontSize = 16.sp
                )
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 2000) text = it },
                    placeholder = { Text("Share your thoughts...", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.2f),
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
                Text("${text.length}/2000", color = theme.textSecondary, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasSpoilers,
                        onCheckedChange = { hasSpoilers = it },
                        colors = CheckboxDefaults.colors(checkedColor = theme.accent)
                    )
                    Text("Contains spoilers", fontSize = 13.sp, color = theme.textPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, text, hasSpoilers, currentEmoji) }) {
                Text("Submit", fontWeight = FontWeight.Bold, color = theme.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
private fun EpisodeRow(episode: EpisodeItem, onToggleEpisode: (EpisodeItem) -> Unit) {
    val theme = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }

    val blurRadius by animateDpAsState(
        targetValue = if (episode.isSpoilerBlurred) 16.dp else 0.dp,
        label = "episode_blur"
    )

    val spoilerModifier = if (episode.isSpoilerBlurred) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier.alpha(0.1f)
        }
    } else Modifier

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = !episode.isSpoilerBlurred) { expanded = !expanded },
        color = if (episode.isSpoilerBlurred) theme.surface.copy(alpha = 0.2f) else theme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = theme.textSecondary.copy(alpha = 0.05f)
        )
    ) {
        Box {
            Column(modifier = spoilerModifier) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp, 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.background)
                    ) {
                        AsyncImage(
                            model = episode.stillUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (episode.isWatched) 0.4f else 0.8f)
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ep ${episode.episodeNumber}", color = theme.textSecondary, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            val isFiller =
                                episode.episodeType == com.example.watchorderengine.data.model.EpisodeType.FILLER
                            Surface(
                                color = if (isFiller) theme.statusFiller.copy(alpha = 0.2f) else theme.statusCanon.copy(
                                    alpha = 0.2f
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isFiller) "FILLER" else "CANON",
                                    color = if (isFiller) theme.statusFiller else theme.statusCanon,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = episode.title,
                            color = if (episode.isWatched) theme.textSecondary else theme.textPrimary,
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
                            tint = if (episode.isWatched) theme.statusCanon else theme.textSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (expanded) {
                    Text(
                        text = episode.overview.ifBlank { "No synopsis available." },
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            if (episode.isSpoilerBlurred) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = theme.textPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "SPOILER PROTECTED",
                            color = theme.textPrimary.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surface)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.width(40.dp).height(8.dp).background(theme.surface))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).background(theme.surface))
            }
        }
    }
}

@Composable
private fun WatchProvidersCard(providers: List<WatchProviderItem>) {
    val theme = LocalAppTheme.current
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(ThemeBorderModifier()),
        color = theme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "WHERE TO WATCH",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                providers.firstOrNull()?.justWatchUrl?.let { url ->
                    Text(
                        "See all ↗",
                        color = theme.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { uriHandler.openUri(url) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val grouped = providers.groupBy { it.offerType }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("stream", "free", "rent", "buy").forEach { type ->
                    val list = grouped[type] ?: emptyList()
                    if (list.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = when(type) {
                                    "stream", "free" -> Color(0xFF4ADE80).copy(alpha = 0.1f)
                                    "rent" -> Color(0xFFF59E0B).copy(alpha = 0.1f)
                                    else -> Color(0xFF3B82F6).copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.width(52.dp)
                            ) {
                                Text(
                                    type.uppercase(),
                                    color = when(type) {
                                        "stream", "free" -> Color(0xFF4ADE80)
                                        "rent" -> Color(0xFFF59E0B)
                                        else -> Color(0xFF3B82F6)
                                    },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(list) { provider ->
                                    AsyncImage(
                                        model = provider.logoUrl,
                                        contentDescription = provider.providerName,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { provider.justWatchUrl?.let { uriHandler.openUri(it) } },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerCard(trailerKey: String) {
    val theme = LocalAppTheme.current
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(vertical = 12.dp)
            .clickable { uriHandler.openUri("vnd.youtube:$trailerKey") }
            .then(ThemeBorderModifier()),
        color = theme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://img.youtube.com/vi/$trailerKey/maxresdefault.jpg",
                contentDescription = "Trailer Thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.7f
            )
            
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "WATCH ON YOUTUBE",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(20.dp)
            )
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
    val characterArt by viewModel.characterArt.collectAsStateWithLifecycle()
    val theme = LocalAppTheme.current

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
                color = theme.textSecondary,
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

    val primaryImage = characterArtUrl ?: cast.profileUrl
    val showVoiceBadge = characterArtUrl != null && characterArtUrl != cast.profileUrl

    Surface(
        onClick = onClick,
        modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(
                        model = primaryImage,
                        contentDescription = cast.character,
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(theme.surface).border(2.dp, theme.accent, CircleShape),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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
                                .background(theme.background)
                                .border(1.5.dp, theme.background, CircleShape),
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(cast.character, color = theme.textPrimary, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showVoiceBadge) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accent, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(cast.name, color = theme.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = biography ?: "Fetching character data...",
                color = theme.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().background(theme.background.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FAN VIBE:", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Most loved character in this arc", color = theme.textSecondary, fontSize = 9.sp)
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
    generationSuccess: Boolean,
    onGenerate: () -> Unit,
    onUniverseClick: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit
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

        if (generationSuccess) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                color = Color(0xFF4ADE80).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch order generated successfully!", color = Color(0xFF4ADE80), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissSuccess, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFF4ADE80))
                    }
                }
            }
        }

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
                Text("Watch Order Guide", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            }
            
            if (detail.arcs.isEmpty() && !isAnalyzing) {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("GENERATE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }

        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = theme.accent)
                    Text("AI is analyzing episodes...", color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else if (detail.arcs.isEmpty()) {
            Text(
                "No watch order guide available for this show yet. Click generate to use Gemini AI.",
                color = theme.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        } else {
            detail.arcs.forEachIndexed { i, arc ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.accent).border(2.dp, theme.background, CircleShape))
                        if (i < detail.arcs.size - 1) {
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(theme.textSecondary.copy(alpha = 0.1f)))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(arc.name, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text("S${arc.startSeason} E${arc.startEpisode} - S${arc.endSeason} E${arc.endEpisode}", color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(arc.synopsis, color = theme.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
