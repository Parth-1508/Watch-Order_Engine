package com.example.watchorderengine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.ui.theme.LocalAppTheme

// ─── Data ─────────────────────────────────────────────────────────────────────

private data class GenreChip(val emoji: String, val label: String)

private val GENRE_OPTIONS = listOf(
    GenreChip("⚔️", "Action"),
    GenreChip("😂", "Comedy"),
    GenreChip("💀", "Horror"),
    GenreChip("🚀", "Sci-Fi"),
    GenreChip("🔍", "Mystery"),
    GenreChip("💕", "Romance"),
    GenreChip("🧙", "Fantasy"),
    GenreChip("🎭", "Drama"),
    GenreChip("🔫", "Thriller"),
    GenreChip("🥷", "Anime"),
    GenreChip("🦸", "Superhero"),
    GenreChip("📚", "Documentary"),
)

// Minimum genres the user must pick before "Continue" unlocks
private const val MIN_SELECTIONS = 3

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * Onboarding taste-profile picker.
 */
@Composable
fun TasteProfileSetupScreen(
    onComplete: (Set<String>) -> Unit,
    onSkip: () -> Unit
) {
    val theme = LocalAppTheme.current
    val engineAccent  = Color(0xFFFFBF3C)   // AccentGold — locked to Engine palette
    val engineSurface = Color(0xFF141B2D)

    val selectedGenres = remember { mutableStateListOf<String>() }
    val canContinue = selectedGenres.size >= MIN_SELECTIONS

    // Pulsing glow for the CTA when it becomes active
    val infiniteTransition = rememberInfiniteTransition(label = "cta_pulse")
    val ctaScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ctaScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black, engineSurface)
                )
            )
    ) {
        // ── Skip button (top-right) ─────────────────────────────────────────
        TextButton(
            onClick  = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Text(
                "SKIP",
                color      = Color.Gray,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(72.dp))

            // ── Header copy ─────────────────────────────────────────────────
            Text(
                text       = "BUILD YOUR",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = engineAccent,
                letterSpacing = 3.sp
            )
            Text(
                text       = "TASTE PROFILE",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle  = FontStyle.Italic,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Pick at least $MIN_SELECTIONS genres you love.\nWe'll tailor your recommendations.",
                fontSize  = 13.sp,
                color     = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(32.dp))

            // ── Progress indicator ──────────────────────────────────────────
            AnimatedContent(
                targetState = selectedGenres.size,
                label       = "badge_count"
            ) { count ->
                Text(
                    text       = if (count == 0) "Select genres below" else "$count selected",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (count >= MIN_SELECTIONS) engineAccent else Color.Gray,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Genre grid ─────────────────────────────────────────────────
            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                modifier              = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                items(GENRE_OPTIONS, key = { it.label }) { chip ->
                    val isSelected = selectedGenres.contains(chip.label)
                    GenreChipItem(
                        chip       = chip,
                        isSelected = isSelected,
                        accent     = engineAccent,
                        onClick    = {
                            if (isSelected) selectedGenres.remove(chip.label)
                            else selectedGenres.add(chip.label)
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── CTA ─────────────────────────────────────────────────────────
            Button(
                onClick  = { if (canContinue) onComplete(selectedGenres.toSet()) },
                enabled  = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(if (canContinue) ctaScale else 1f),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = engineAccent,
                    disabledContainerColor = Color.White.copy(alpha = 0.12f)
                )
            ) {
                Text(
                    text          = if (canContinue) "LET'S GO →" else "PICK ${MIN_SELECTIONS - selectedGenres.size} MORE",
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color         = if (canContinue) Color.Black else Color.Gray
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Genre chip item ──────────────────────────────────────────────────────────

@Composable
private fun GenreChipItem(
    chip: GenreChip,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue  = if (isSelected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label        = "chip_scale_${chip.label}"
    )

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                width  = if (isSelected) 2.dp else 1.dp,
                color  = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
                shape  = RoundedCornerShape(16.dp)
            ),
        color = if (isSelected) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(chip.emoji, fontSize = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    chip.label,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                    textAlign  = TextAlign.Center
                )
            }
            // Checkmark badge (top-right corner)
            AnimatedVisibility(
                visible = isSelected,
                enter   = scaleIn() + fadeIn(),
                exit    = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint               = Color.Black,
                        modifier           = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
