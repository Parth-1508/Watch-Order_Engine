package com.example.watchorderengine.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.CharacterDetail
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.CharacterDetailState
import com.example.watchorderengine.ui.viewmodel.CharacterDetailViewModel

@Composable
fun CharacterDetailScreen(
    tmdbPersonId: Int,
    characterName: String,
    showTitle: String,
    isAnime: Boolean,
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    viewModel: CharacterDetailViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val state by viewModel.state.collectAsState()
    val photoIndex by viewModel.photoIndex.collectAsState()

    LaunchedEffect(tmdbPersonId, characterName) {
        viewModel.load(tmdbPersonId, characterName, showTitle, isAnime)
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        when (val s = state) {
            is CharacterDetailState.Loading -> CharacterDetailLoading(onBack)
            is CharacterDetailState.Error -> CharacterDetailError(
                message = s.message,
                onRetry = { viewModel.retry(tmdbPersonId, characterName, showTitle, isAnime) },
                onBack = onBack
            )
            is CharacterDetailState.Success -> CharacterDetailBody(
                detail = s.detail,
                photoIndex = photoIndex,
                onPhotoSelect = viewModel::setPhotoIndex,
                onBack = onBack,
                onMediaClick = onMediaClick
            )
        }
    }
}

// ─── Loading / Error ────────────────────────────────────────────────────────────

@Composable
private fun CharacterDetailLoading(onBack: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = theme.accent)
            Spacer(Modifier.height(12.dp))
            Text("Loading character…", color = theme.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CharacterDetailError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = theme.statusFiller, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = theme.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(chipRadius(theme.appRadius)),
                color = theme.accent
            ) {
                Text(
                    "Retry",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
}

/** Clamps a theme's card radius down to something sane for small chips/pills/thumbnails. */
private fun chipRadius(appRadius: androidx.compose.ui.unit.Dp) =
    if (appRadius > 12.dp) 12.dp else appRadius

// ─── Body ───────────────────────────────────────────────────────────────────────

@Composable
private fun CharacterDetailBody(
    detail: CharacterDetail,
    photoIndex: Int,
    onPhotoSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    var activeTab by remember { mutableStateOf(0) }

    val heroImages = remember(detail) {
        buildList {
            detail.characterImageUrl?.let { add(it) }
            detail.actorPhotos.filter { it != detail.characterImageUrl }.take(7).forEach { add(it) }
        }
    }
    val heroUrl = heroImages.getOrNull(photoIndex) ?: detail.actorProfileUrl

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {

        // ── Hero — same backdrop/gradient/back-button pattern as MediaDetailScreen ──
        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            AsyncImage(
                model = heroUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, theme.background),
                        startY = 260f
                    )
                )
            )
            BackButton(onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))

            // Floating avatar, overlapping the bottom edge of the hero
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 52.dp)
            ) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = detail.characterName,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(theme.surface)
                        .border(3.dp, theme.accent, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).offset((-2).dp, (-2).dp),
                    shape = RoundedCornerShape(6.dp),
                    color = roleColor(detail.characterRole, theme)
                ) {
                    Text(
                        detail.characterRole,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(60.dp))

        // ── Name block ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                detail.characterName,
                color = theme.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!detail.characterNativeName.isNullOrBlank()) {
                Text(
                    detail.characterNativeName,
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val byVoice = detail.voiceActorName != null
                Text(
                    if (byVoice) "Voiced by " else "Played by ",
                    color = theme.textSecondary, fontSize = 13.sp
                )
                Text(
                    if (byVoice) detail.voiceActorName!! else detail.actorName,
                    color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                detail.characterGender?.let { QuickStat("Gender", it, Modifier.weight(1f)) }
                detail.characterAge?.let { QuickStat("Age", it, Modifier.weight(1f)) }
                detail.actorBirthday?.let { QuickStat("Born", it.take(4), Modifier.weight(1f)) }
                detail.actorPlaceOfBirth?.let {
                    QuickStat("From", it.substringAfterLast(",").trim().ifBlank { it.take(12) }, Modifier.weight(1f))
                }
            }
        }

        // ── Photo strip ───────────────────────────────────────────────────────────
        if (heroImages.size > 1) {
            Spacer(Modifier.height(20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(heroImages) { idx, url ->
                    val isSelected = idx == photoIndex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 68.dp else 56.dp)
                            .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) theme.accent else theme.border,
                                shape = RoundedCornerShape(chipRadius(theme.appRadius))
                            )
                            .clickable { onPhotoSelect(idx) }
                    ) {
                        AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // ── Tab bar ───────────────────────────────────────────────────────────────
        val tabs = buildList {
            add("Character")
            add("Actor")
            if (detail.knownForCredits.isNotEmpty()) add("Filmography")
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                .background(theme.surface.copy(alpha = 0.5f))
                .border(1.dp, theme.border, RoundedCornerShape(chipRadius(theme.appRadius)))
        ) {
            tabs.forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                        .background(if (activeTab == idx) theme.accent else Color.Transparent)
                        .clickable { activeTab = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == idx) Color.Black else theme.textSecondary
                    )
                }
            }
        }

        Crossfade(targetState = activeTab, label = "character_tab") { tab ->
            when (tab) {
                0 -> CharacterTab(detail)
                1 -> ActorTab(detail)
                2 -> FilmographyTab(detail, onMediaClick)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** Ties role badge color to theme tokens instead of hardcoded gold/blue/gray. */
private fun roleColor(role: String, theme: com.example.watchorderengine.ui.theme.AppThemeConfig) = when (role) {
    "MAIN" -> theme.accent
    "SUPPORTING" -> theme.statusMixed
    else -> theme.textSecondary
}

@Composable
private fun QuickStat(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
            .background(theme.surface.copy(alpha = 0.4f))
            .border(1.dp, theme.border, RoundedCornerShape(chipRadius(theme.appRadius)))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = theme.textPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Tab 0: Character ───────────────────────────────────────────────────────────

@Composable
private fun CharacterTab(detail: CharacterDetail) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (detail.characterDescription.isNotBlank()) {
            SectionHeader("About this character")
            InfoCard {
                Text(detail.characterDescription, color = theme.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        } else {
            InfoCard {
                Text(
                    "No character-specific bio available — showing actor details instead.",
                    color = theme.textSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        if (detail.voiceActorName != null) {
            SectionHeader("Japanese Voice Actor")
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = detail.voiceActorImageUrl,
                        contentDescription = detail.voiceActorName,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(theme.surfaceHover),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(detail.voiceActorName, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accent, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Japanese dub", color = theme.accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        SectionHeader("Quick Facts")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.characterRole.let { FactRow("Role", it) }
                detail.characterGender?.let { FactRow("Gender", it) }
                detail.characterAge?.let { FactRow("Age", it) }
                if (detail.characterDescription.isBlank() && detail.characterGender == null && detail.characterAge == null) {
                    Text("This title isn't tagged as anime, so AniList enrichment was skipped.",
                        color = theme.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── Tab 1: Actor ───────────────────────────────────────────────────────────────

@Composable
private fun ActorTab(detail: CharacterDetail) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (detail.actorBiography.isNotBlank()) {
            SectionHeader("Biography")
            InfoCard {
                Text(detail.actorBiography, color = theme.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        SectionHeader("Personal Info")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FactRow("Name", detail.actorName)
                detail.actorGender?.let { FactRow("Gender", it) }
                detail.actorBirthday?.let { FactRow("Born", it) }
                detail.actorDeathday?.let { FactRow("Died", it) }
                detail.actorPlaceOfBirth?.let { FactRow("Birthplace", it) }
                detail.actorKnownFor?.let { FactRow("Known for", it) }
                if (detail.actorAlsoKnownAs.isNotEmpty()) {
                    FactRow("Also known as", detail.actorAlsoKnownAs.take(3).joinToString(", "))
                }
            }
        }

        if (detail.actorPhotos.size > 1) {
            SectionHeader("Photos")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(detail.actorPhotos) { url ->
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier
                            .size(width = 90.dp, height = 120.dp)
                            .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                            .background(theme.surfaceHover),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

// ─── Tab 2: Filmography ─────────────────────────────────────────────────────────

@Composable
private fun FilmographyTab(detail: CharacterDetail, onMediaClick: (String) -> Unit) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (detail.knownForCredits.isNotEmpty()) {
            SectionHeader("Known For", modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(detail.knownForCredits, key = { it.creditId }) { credit ->
                    KnownForCard(credit, onClick = { onMediaClick(credit.mediaId) }, modifier = Modifier.width(120.dp))
                }
            }
        }

        if (detail.allCastCredits.size > detail.knownForCredits.size) {
            SectionHeader("All Credits", modifier = Modifier.padding(horizontal = 20.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.allCastCredits.forEach { credit ->
                    CreditListRow(credit, onClick = { onMediaClick(credit.mediaId) })
                }
            }
        }
    }
}

/** Mirrors MediaGridItem's poster-card convention exactly (same shape/elevation/gradient/rating pill). */
@Composable
private fun KnownForCard(credit: CreditItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(0.7f).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = credit.posterUrl,
                contentDescription = credit.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 200f)
                )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    credit.title, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(credit.year, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    if (credit.voteAverage > 0f) {
                        Box(
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                String.format("%.1f", credit.voteAverage),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall, fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditListRow(credit: CreditItem, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = theme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(chipRadius(theme.appRadius)),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = credit.posterUrl,
                contentDescription = credit.title,
                modifier = Modifier.size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                    .background(theme.surfaceHover),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(credit.title, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (credit.character.isNotBlank()) {
                    Text("as ${credit.character}", color = theme.textSecondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (credit.year.isNotBlank()) Text(credit.year, color = theme.textSecondary, fontSize = 10.sp)
                    credit.episodeCount?.let {
                        Text("· $it eps", color = theme.textSecondary, fontSize = 10.sp)
                    }
                }
            }
            if (credit.voteAverage > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = theme.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(String.format("%.1f", credit.voteAverage), color = theme.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Shared small pieces ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Text(
        title.uppercase(),
        color = theme.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth().then(ThemeBorderModifier()),
        color = theme.surface.copy(alpha = 0.3f),
        border = if (theme.isComic || theme.isManga) null else BorderStroke(1.dp, theme.border)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    val theme = LocalAppTheme.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.4f), textAlign = TextAlign.End)
    }
}
