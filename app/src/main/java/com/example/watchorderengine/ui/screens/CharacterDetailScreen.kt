package com.example.watchorderengine.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.CharacterDetail
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.ui.screens.home.ThemeBorderModifier
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.ui.viewmodel.CharacterDetailState
import com.example.watchorderengine.ui.viewmodel.CharacterDetailViewModel

/**
 * Main screen for viewing character details.
 *
 * Displays a hero image (with a toggleable gallery if multiple images are available),
 * core stats (gender, age, birth year), and a tabbed interface for character biography,
 * actor biography, chronological appearances in the franchise, and general filmography.
 *
 * @param tmdbPersonId The unique identifier for the actor in TMDB.
 * @param characterName The display name of the character.
 * @param showTitle The title of the parent show or movie franchise.
 * @param isAnime Boolean flag indicating if the character/show is anime (used for specific art rendering).
 * @param onBack Callback to navigate back.
 * @param onMediaClick Callback when a related media item is clicked, passing its media ID.
 * @param anilistId Optional AniList identifier for anime-specific data fetching.
 * @param viewModel The state holder for this screen.
 */
@Composable
fun CharacterDetailScreen(
    tmdbPersonId: Int,
    characterName: String,
    showTitle: String,
    isAnime: Boolean,
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    anilistId: Int? = null,
    viewModel: CharacterDetailViewModel = hiltViewModel()
) {
    val theme = LocalAppTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoIndex by viewModel.photoIndex.collectAsStateWithLifecycle()

    LaunchedEffect(tmdbPersonId, characterName) {
        viewModel.load(tmdbPersonId, characterName, showTitle, isAnime, anilistId)
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        when (val s = state) {
            is CharacterDetailState.Loading -> CharacterDetailLoading(onBack)
            is CharacterDetailState.Error -> CharacterDetailError(
                message = s.message,
                onRetry = { viewModel.retry(tmdbPersonId, characterName, showTitle, isAnime, anilistId) },
                onBack = onBack
            )
            is CharacterDetailState.Success -> CharacterDetailBody(
                detail = s.detail,
                photoIndex = photoIndex,
                onPhotoSelect = viewModel::setPhotoIndex,
                onBack = onBack,
                onMediaClick = onMediaClick,
                isAnime = isAnime
            )
        }
    }
}

// ─── Loading / Error ────────────────────────────────────────────────────────────

/**
 * Loading state view for the character detail screen.
 */
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

/**
 * Error state view for the character detail screen, allowing for retries.
 */
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

private fun chipRadius(appRadius: androidx.compose.ui.unit.Dp) =
    if (appRadius > 12.dp) 12.dp else appRadius

// ─── Body ───────────────────────────────────────────────────────────────────────

/**
 * The main content of the character detail screen, shown after data is successfully loaded.
 *
 * @param detail The [CharacterDetail] data model.
 * @param photoIndex The currently selected index in the hero image gallery.
 * @param onPhotoSelect Callback to update the selected gallery index.
 * @param onBack Callback for the back button.
 * @param onMediaClick Callback for clicking media items.
 * @param isAnime Whether to show anime-specific UI elements.
 */
@Composable
private fun CharacterDetailBody(
    detail: CharacterDetail,
    photoIndex: Int,
    onPhotoSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    isAnime: Boolean
) {
    val theme = LocalAppTheme.current
    val scrollState = rememberScrollState()
    var activeTab by remember { mutableStateOf(0) }

    val heroImages = remember(detail) {
        buildList {
            addAll(detail.characterPhotos)
            detail.actorProfileUrl?.let { add(it) }
            addAll(detail.actorPhotos)
            detail.characterImageUrl?.let { add(it) }
        }.distinct().filter { url ->
            val lower = url.lowercase()
            val placeholders = listOf(
                "placeholder", "no_image", "silhouette", "default", "missing", 
                "none", "empty", "blank", "not-found", "null", "no-photo", 
                "generic", "uncredited", "no_headshot"
            )
            placeholders.none { lower.contains(it) } && !lower.endsWith(".svg")
        }
    }
    
    val safeHeroImages = heroImages
    val heroUrl = safeHeroImages.getOrNull(photoIndex) ?: safeHeroImages.firstOrNull()

    val hasHeroImage = !heroUrl.isNullOrBlank()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {

        if (hasHeroImage) {
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                BackButton(onBack, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

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

        if (safeHeroImages.size > 1) {
            Spacer(Modifier.height(20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(safeHeroImages) { idx, url ->
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
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
                        )
                    }
                }
            }
        }

        val tabs = buildList {
            add("Character")
            add("Actor")
            if (detail.characterAppearances.isNotEmpty()) add("Appearances")
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
            when (tabs.getOrNull(tab)) {
                "Character" -> CharacterTab(detail, isAnime)
                "Actor" -> ActorTab(detail)
                "Appearances" -> AppearancesTab(detail)
                "Filmography" -> FilmographyTab(detail, onMediaClick)
                else -> Unit
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

private fun roleColor(role: String, theme: com.example.watchorderengine.ui.theme.AppThemeConfig) = when (role) {
    "MAIN" -> theme.accent
    "SUPPORTING" -> theme.statusMixed
    else -> theme.textSecondary
}

/**
 * A small card-like component used to display a single character attribute (e.g., Age, Gender).
 */
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

/**
 * Tab content showing character-specific lore, biography, and voice actor information.
 */
@Composable
private fun CharacterTab(detail: CharacterDetail, isAnime: Boolean) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (detail.characterDescription.isNotBlank()) {
            SectionHeader("About this character")
            if (detail.characterDescription.length > 200) {
                WikiLoreCard(lore = detail.characterDescription)
            } else {
                InfoCard {
                    Text(detail.characterDescription, color = theme.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        } else {
            InfoCard {
                Text(
                    "No character-specific bio found — showing actor details instead.",
                    color = theme.textSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        val showWikiSupplementBlock = !detail.wikiLore.isNullOrBlank() &&
            detail.characterDescription != detail.wikiLore &&
            detail.characterDescription.length > 150

        if (showWikiSupplementBlock) {
            SectionHeader("Lore — Wikipedia")
            WikiLoreCard(lore = detail.wikiLore!!)
        }

        if (detail.characterDescription.isNotBlank()) {
            when (detail.loreSource) {
                "wikipedia" -> LoreAttributionFooter("Source: Wikipedia (CC BY-SA 4.0)")
                "gemini" -> LoreAttributionFooter("Source: AI Generated (Gemini)")
                "anilist" -> LoreAttributionFooter("Source: AniList")
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
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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
            }
        }

        val displayArt = remember(detail, isAnime) {
            if (!isAnime) {
                emptyList()
            } else {
                val fictional = detail.characterPhotos.filter { it.isNotBlank() }
                if (fictional.isEmpty() && !detail.characterImageUrl.isNullOrBlank()) {
                    listOf(detail.characterImageUrl).filter { it.isNotBlank() }
                } else {
                    fictional
                }
            }
        }

        if (isAnime && displayArt.isNotEmpty()) {
            SectionHeader("Fictional Art")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displayArt) { url ->
                    AsyncImage(
                        model              = url,
                        contentDescription = "Character Art",
                        modifier           = Modifier
                            .size(width = 100.dp, height = 140.dp)
                            .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                            .background(theme.surfaceHover),
                        contentScale       = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
                    )
                }
            }
        }
    }
}

// ─── Tab 1: Actor ───────────────────────────────────────────────────────────────

/**
 * Tab content showing the actor's biography and personal details.
 */
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
                items(detail.actorPhotos.filter { it.isNotBlank() }) { url ->
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier
                            .size(width = 90.dp, height = 120.dp)
                            .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                            .background(theme.surfaceHover),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
                    )
                }
            }
        }
    }
}

// ─── Tab: Appearances ───

/**
 * Tab content listing all appearances of this character within the current franchise.
 */
@Composable
private fun AppearancesTab(detail: CharacterDetail) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Every movie/special in this franchise where ${detail.characterName} appears as a character — " +
                "not the voice actor's other roles.",
            color = theme.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        val rows = detail.characterAppearances.chunked(2)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { appearance ->
                        AppearanceCard(appearance, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(appearance: com.example.watchorderengine.data.model.CharacterAppearance, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(chipRadius(theme.appRadius)))
                .background(theme.surfaceHover)
        ) {
            AsyncImage(
                model = appearance.imageUrl,
                contentDescription = appearance.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)), startY = 180f)
                )
            )
            appearance.role?.let { role ->
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = roleColor(role, theme)
                ) {
                    Text(
                        role, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            appearance.title, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (!appearance.year.isNullOrBlank()) {
            Text(appearance.year, color = theme.textSecondary, fontSize = 10.sp)
        }
    }
}

// ─── Tab 2: Filmography ─────────────────────────────────────────────────────────

/**
 * Tab content listing the actor's general filmography (known for and all credits).
 */
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
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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

/**
 * A specialized card for displaying lore that might contain spoilers.
 *
 * Includes a "Tap to Reveal" overlay and a blur effect to protect the user from spoilers.
 */
@Composable
private fun WikiLoreCard(lore: String) {
    val theme    = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }

    var revealed by remember { mutableStateOf(false) }
    val blurRadius by animateDpAsState(
        targetValue   = if (revealed) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 400),
        label         = "wikiLoreBlur"
    )

    InfoCard {
        Box {
            Column(modifier = Modifier.blur(blurRadius)) {
                Text(
                    text       = lore,
                    color      = theme.textSecondary,
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                    fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines   = if (expanded) Int.MAX_VALUE else 4,
                    overflow   = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!overflows) overflows = result.hasVisualOverflow
                    }
                )

                if (overflows || expanded) {
                    TextButton(
                        onClick  = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        enabled  = revealed
                    ) {
                        Text(
                            if (expanded) "Show less" else "Show more",
                            color    = theme.accent,
                            fontSize = 12.sp
                        )
                    }
                }

                LoreAttributionFooter("Source: Wikipedia (CC BY-SA 4.0)")
            }

            if (!revealed) {
                val scrimAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.94f
                Surface(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClickLabel = "Reveal spoilers") { revealed = true },
                    color = theme.background.copy(alpha = scrimAlpha)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape  = RoundedCornerShape(chipRadius(theme.appRadius)),
                            color  = theme.surface,
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff, contentDescription = null,
                                    tint = theme.accent, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Tap to Reveal Spoilers",
                                    color = theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoreAttributionFooter(text: String) {
    val theme = LocalAppTheme.current
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = theme.border, thickness = 0.5.dp)
    Spacer(Modifier.height(6.dp))
    Text(
        text,
        color      = theme.textSecondary.copy(alpha = 0.6f),
        fontSize   = 10.sp,
        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic
    )
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
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.AccountCircle)
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
