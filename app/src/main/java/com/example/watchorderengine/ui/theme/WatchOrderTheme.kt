package com.example.watchorderengine.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Premium Dark Palette ─────────────────────────────────────────────────────

object WatchOrderColors {
    val DeepSpace       = Color(0xFF080B14)  // Primary background (almost black)
    val VoidDark        = Color(0xFF0D1017)  // Scaffold background
    val CardSurface     = Color(0xFF141B2D)  // Card / node card surface
    val CardBorder      = Color(0xFF1E2D45)  // Subtle card border
    val ElevatedSurface = Color(0xFF1A2438)  // Slightly elevated elements

    val AccentGold      = Color(0xFFFFBF3C)  // Primary accent — selected, highlights
    val AccentBlue      = Color(0xFF4390F7)  // Secondary accent — info, links
    val CompletedGreen  = Color(0xFF3BDB8A)  // Node completion checkmark / line
    val BranchCoral     = Color(0xFFFF6B6B)  // Branch-point indicator
    val SpoilerPurple   = Color(0xFF8B5CF6)  // Spoiler shield badge

    val TextPrimary     = Color(0xFFECEEF2)  // Main text
    val TextSecondary   = Color(0xFF7A8499)  // Subtitles, metadata
    val TextMuted       = Color(0xFF3D4554)  // Placeholder / disabled

    val ConnectorActive = Color(0xFF3BDB8A)  // Completed connector line
    val ConnectorIdle   = Color(0xFF1E2D45)  // Pending connector line (dashed)
}

private val DarkColorScheme = darkColorScheme(
    background           = WatchOrderColors.VoidDark,
    surface              = WatchOrderColors.CardSurface,
    surfaceVariant       = WatchOrderColors.ElevatedSurface,
    primary              = WatchOrderColors.AccentGold,
    secondary            = WatchOrderColors.AccentBlue,
    onBackground         = WatchOrderColors.TextPrimary,
    onSurface            = WatchOrderColors.TextPrimary,
    onPrimary            = Color(0xFF1A1000),
    outline              = WatchOrderColors.CardBorder,
    outlineVariant       = WatchOrderColors.TextMuted
)

val WatchOrderTypography = Typography(
    titleLarge    = TextStyle(color = WatchOrderColors.TextPrimary,
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium   = TextStyle(color = WatchOrderColors.TextPrimary,
        fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge     = TextStyle(color = WatchOrderColors.TextPrimary,
        fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium    = TextStyle(color = WatchOrderColors.TextSecondary,
        fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall    = TextStyle(color = WatchOrderColors.TextMuted,
        fontWeight = FontWeight.Normal, fontSize = 11.sp)
)

@Composable
fun WatchOrderEngineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = WatchOrderTypography,
        content     = content
    )
}