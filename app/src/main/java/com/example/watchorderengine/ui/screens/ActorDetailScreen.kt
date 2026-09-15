package com.example.watchorderengine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.ActorDetail
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.ActorDetailUiState
import com.example.watchorderengine.ui.viewmodel.ActorDetailViewModel

@Composable
fun ActorDetailScreen(
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    viewModel: ActorDetailViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            }

            when (val state = uiState) {
                is ActorDetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }
                is ActorDetailUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(state.message, color = theme.textSecondary, fontSize = 14.sp)
                    }
                }
                is ActorDetailUiState.Loaded -> {
                    ActorDetailContent(
                        actor = state.actor,
                        isFavorite = state.isFavorite,
                        onFavoriteToggle = { viewModel.toggleFavorite() },
                        onMediaClick = onMediaClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ActorDetailContent(
    actor: ActorDetail,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onMediaClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    var bioExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, MOVIES, TV

    val filteredFilmography = remember(actor.filmography, selectedFilter) {
        when (selectedFilter) {
            "MOVIES" -> actor.filmography.filter { it.mediaType == "movie" }
            "TV" -> actor.filmography.filter { it.mediaType == "tv" }
            else -> actor.filmography
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(theme.accent.copy(alpha = 0.3f), theme.background)
                            )
                        )
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite Actor",
                        tint = if (isFavorite) Color(0xFFFF4B6E) else theme.textPrimary
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = actor.profilePath,
                        contentDescription = actor.name,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(3.dp, theme.accent, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        actor.name.uppercase(),
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (!actor.knownForDepartment.isNullOrBlank()) {
                        Text(
                            actor.knownForDepartment.uppercase(),
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Biography
        if (actor.biography.isNotBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "BIOGRAPHY",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = actor.biography,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (bioExpanded) "Show Less" else "Read More",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { bioExpanded = !bioExpanded }
                    )
                }
            }
        }

        // Photos gallery carousel
        if (actor.images.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "PHOTOS",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(actor.images) { photoUrl ->
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 80.dp, height = 110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surface),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Top Works Section
        if (actor.topWorks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "TOP WORKS",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(actor.topWorks, key = { it.creditId + it.mediaId }) { credit ->
                            CreditCard(credit = credit, onMediaClick = onMediaClick)
                        }
                    }
                }
            }
        }

        // Filmography Header + Filters
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                Text(
                    "FILMOGRAPHY",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${actor.filmography.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == "MOVIES",
                        onClick = { selectedFilter = "MOVIES" },
                        label = { Text("Movies", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == "TV",
                        onClick = { selectedFilter = "TV" },
                        label = { Text("TV Shows", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Filmography List
        items(filteredFilmography, key = { it.creditId + it.mediaId }) { credit ->
            FilmographyRow(credit = credit, onMediaClick = onMediaClick)
        }
    }
}

@Composable
private fun CreditCard(credit: CreditItem, onMediaClick: (String) -> Unit) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onMediaClick(credit.mediaId) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surface)
        ) {
            AsyncImage(
                model = credit.posterUrl,
                contentDescription = credit.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            credit.title,
            color = theme.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (credit.character.isNotBlank()) {
            Text(
                asCharacterLabel(credit.character),
                color = theme.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FilmographyRow(credit: CreditItem, onMediaClick: (String) -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onMediaClick(credit.mediaId) },
        shape = RoundedCornerShape(12.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = credit.posterUrl,
                contentDescription = credit.title,
                modifier = Modifier
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.background),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    credit.title,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (credit.character.isNotBlank()) {
                    Text(
                        asCharacterLabel(credit.character),
                        color = theme.accent,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (credit.year.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    credit.year,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun asCharacterLabel(character: String): String =
    if (character.lowercase().startsWith("as ")) character else "as $character"
