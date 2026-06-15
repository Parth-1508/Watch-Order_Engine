package com.example.watchorderengine.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.EpisodeType

object WOEColors {
    // Dark Palette (OLED)
    val OledBlack       = Color(0xFF000000)
    val SurfaceBase     = Color(0xFF0D0D0D)
    val SurfaceCard     = Color(0xFF1A1A1A)
    val SurfaceElevated = Color(0xFF252525)
    
    // Light Palette
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface    = Color(0xFFF5F5F7)
    val LightSurfaceCard = Color(0xFFFFFFFF)
    val LightBorder     = Color(0xFFE5E5EA)

    // Tracking State Accents (Vibrant)
    val Watching    = Color(0xFF0A84FF)  // Neon blue
    val Completed   = Color(0xFF30DB5B)  // Emerald green
    val Planned     = Color(0xFFBF5AF2)  // Vivid purple
    val Paused      = Color(0xFFFFD60A)  // Gold
    val Dropped     = Color(0xFFFF453A)  // Coral red

    // Episode Type Badges
    val Canon       = Color(0xFFFFBF3C)  // Warm gold
    val Filler      = Color(0xFF636366)  // Muted gray
    val Mixed       = Color(0xFFFF9F0A)  // Amber orange

    // Text hierarchy
    val TextPrimaryDark    = Color(0xFFFFFFFF)
    val TextSecondaryDark  = Color(0xFF8E8E93)
    val TextPrimaryLight   = Color(0xFF000000)
    val TextSecondaryLight = Color(0xFF3C3C43)

    // Accents
    val AccentGold     = Color(0xFFFFD700)
    val GlassWhite     = Color(0x1AFFFFFF)
    val GlassBorder    = Color(0x33FFFFFF)
}

fun TrackingState.color(): Color = when (this) {
    TrackingState.WATCHING   -> WOEColors.Watching
    TrackingState.COMPLETED  -> WOEColors.Completed
    TrackingState.PLANNED    -> WOEColors.Planned
    TrackingState.PAUSED     -> WOEColors.Paused
    TrackingState.DROPPED    -> WOEColors.Dropped
}

fun EpisodeType.badgeColor(): Color = when (this) {
    EpisodeType.CANON  -> WOEColors.Canon
    EpisodeType.FILLER -> WOEColors.Filler
    EpisodeType.MIXED  -> WOEColors.Mixed
}
