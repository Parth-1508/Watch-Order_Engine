package com.example.watchorderengine.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.theme.LocalAppTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onShowClick: (MediaShowItem) -> Unit,
    onSettingsClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .drawBehind {
                if (theme.isComic) {
                    val dotRadius = 1.dp.toPx()
                    val spacing = 16.dp.toPx()
                    for (x in 0 until (size.width / spacing).toInt() + 1) {
                        for (y in 0 until (size.height / spacing).toInt() + 1) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.1f),
                                radius = dotRadius,
                                center = Offset(x * spacing, y * spacing)
                            )
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // Header
            Header(
                isSearchOpen = state.isSearchOpen,
                query = state.searchQuery,
                onQueryChanged = onSearchQueryChanged,
                onToggleSearch = onSearchToggle,
                onSettingsClick = onSettingsClick
            )

            // Category Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.categories) { category ->
                    val count = state.shows.count { it.watchlistStatus == category }
                    CategoryTab(
                        name = category,
                        count = count,
                        isSelected = state.activeCategory == category,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }

            // Main Content Area
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Active Category Label
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            if (theme.isComic) rotationZ = 2f
                        }
                        .drawBehind {
                            drawRect(color = Color.Black)
                            drawRect(color = Color.Magenta, style = Stroke(width = 2.dp.toPx()))
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.activeCategory.uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                val filteredShows = state.shows.filter { it.watchlistStatus == state.activeCategory }
                
                if (filteredShows.isEmpty()) {
                    // Empty State Replica
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tv, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        }
                        Text("NO SHOWS HERE YET", color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                        Text(
                            text = "Open a show and set it to ${state.activeCategory}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    // Responsive Grid
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        filteredShows.chunked(2).forEach { rowShows ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                rowShows.forEach { show ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        MediaCard(show = show, onClick = { onShowClick(show) })
                                    }
                                }
                                if (rowShows.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // DISCOVER Section
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "DISCOVER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Discovery horizontal row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(state.shows.filter { it.watchlistStatus == null }.take(10)) { show ->
                        MediaCard(show = show, onClick = { onShowClick(show) })
                    }
                }
            }
        }
    }
}

@Composable
fun Header(
    isSearchOpen: Boolean,
    query: String,
    onQueryChanged: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(16.dp)
            .drawBehind {
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 4.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (Left)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, theme.textPrimary, CircleShape)
        ) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?crop=faces&fit=crop&w=100&h=100",
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Title or Search Bar (Center/Expanded)
        AnimatedContent(
            targetState = isSearchOpen,
            modifier = Modifier.weight(1f),
            label = "header_center"
        ) { searching ->
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    placeholder = { Text("Search title...", fontSize = 14.sp) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = theme.surface,
                        unfocusedContainerColor = theme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textSecondary) }
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "WATCH ORDER",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = (-1).sp
                        ),
                        color = theme.textPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Icons (Right)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onToggleSearch(!isSearchOpen) },
                modifier = Modifier.size(40.dp).border(2.dp, theme.textPrimary, CircleShape)
            ) {
                Icon(if (isSearchOpen) Icons.Default.Close else Icons.Default.Search, null, tint = theme.textPrimary)
            }
            if (!isSearchOpen) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp).border(2.dp, theme.textPrimary, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, null, tint = theme.textPrimary)
                }
            }
        }
    }
}

@Composable
fun SearchOverlay(
    query: String,
    results: List<MediaShowItem>,
    onResultClick: (MediaShowItem) -> Unit
) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        color = theme.background.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${results.size} RESULTS",
                color = theme.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { show ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(show) }
                            .then(ThemeBorderModifier())
                            .background(theme.surface)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = show.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp, 64.dp).then(ThemeBorderModifier()),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(show.title, color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text(show.genres.joinToString(" • "), color = theme.textSecondary, fontSize = 10.sp)
                        }
                        StatusBadge(type = show.badge)
                    }
                }
            }
        }
    }
}
