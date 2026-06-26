package com.example.watchorderengine.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.data.model.EpisodeType

object WOEColors {
    // --- LIGHT MODE (Apple Aesthetic) ---
    val LightPrimary = Color(0xFF000000)
    val LightSecondary = Color(0xFFF3F4F6)
    val LightAccent = Color(0xFF007AFF)
    val LightBackground = Color(0xFFFBFBFD)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHover = Color(0xFFF1F1F1)
    val LightTextPrimary = Color(0xFF1D1D1F)
    val LightTextSecondary = Color(0xFF86868B)
    val LightBorder = Color(0xFFE5E5EA)
    val LightStatusCanon = Color(0xFF34C759)
    val LightStatusFiller = Color(0xFFFF3B30)
    val LightStatusMixed = Color(0xFFFF9500)

    // --- DARK MODE (Netflix Aesthetic) ---
    val DarkPrimary = Color(0xFFFFFFFF)
    val DarkSecondary = Color(0xFF1A1A1A)
    val DarkAccent = Color(0xFFE50914)
    val DarkBackground = Color(0xFF000000)
    val DarkSurface = Color(0xB3141414) // rgba(20, 20, 20, 0.7)
    val DarkSurfaceHover = Color(0xE6282828) // rgba(40, 40, 40, 0.9)
    val DarkTextPrimary = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFFA3A3A3)
    val DarkBorder = Color(0x1AFFFFFF) // rgba(255, 255, 255, 0.1)
    val DarkStatusCanon = Color(0xFF4ADE80)
    val DarkStatusFiller = Color(0xFFF87171)
    val DarkStatusMixed = Color(0xFFFBBF24)

    // --- COMIC-CON MODE ---
    val ComicPrimary = Color(0xFFFFFFFF) // For text on accent
    val ComicSecondary = Color(0xFF1A1A1A)
    val ComicAccent = Color(0xFFFF00FF)
    val ComicBackground = Color(0xFFDBEAFE)
    val ComicSurface = Color(0xFFFFFFFF)
    val ComicSurfaceHover = Color(0xFFEFF6FF)
    val ComicTextPrimary = Color(0xFF000000)
    val ComicTextSecondary = Color(0xFF1E3A8B)
    val ComicBorder = Color(0xFF000000)
    val ComicStatusCanon = Color(0xFF00FF00)
    val ComicStatusFiller = Color(0xFFFF4500)
    val ComicStatusMixed = Color(0xFF00FFFF)

    // --- MANGA MODE ---
    val MangaPrimary = Color(0xFFFFFFFF) // For text on accent
    val MangaSecondary = Color(0xFF000000)
    val MangaAccent = Color(0xFF000000)
    val MangaBackground = Color(0xFFFFFFFF)
    val MangaSurface = Color(0xFFF3F4F6)
    val MangaSurfaceHover = Color(0xFFE5E7EB)
    val MangaTextPrimary = Color(0xFF000000)
    val MangaTextSecondary = Color(0xFF4B5563)
    val MangaBorder = Color(0xFF000000)
    val MangaStatusCanon = Color(0xFF111827)
    val MangaStatusFiller = Color(0xFF9CA3AF)
    val MangaStatusMixed = Color(0xFF4B5563)

    // --- RETRO VAPORWAVE MODE ---
    val RetroPrimary = Color(0xFFFFFFFF)
    val RetroSecondary = Color(0xFF2D1B4E)
    val RetroAccent = Color(0xFFFF00C8)
    val RetroBackground = Color(0xFF120458)
    val RetroSurface = Color(0xFF200F54)
    val RetroSurfaceHover = Color(0xFF2A1B63)
    val RetroTextPrimary = Color(0xFF00FFCC)
    val RetroTextSecondary = Color(0xFFFF00C8)
    val RetroBorder = Color(0xFFFF00C8)
    val RetroStatusCanon = Color(0xFF00FFCC)
    val RetroStatusFiller = Color(0xFFFF00C8)
    val RetroStatusMixed = Color(0xFFFACC15)

    // --- BOLLYWOOD MODE ---
    val BollywoodPrimary = Color(0xFFFFFFFF)
    val BollywoodSecondary = Color(0xFFFFFBEB)
    val BollywoodAccent = Color(0xFFE11D48)
    val BollywoodBackground = Color(0xFFFFF7ED)
    val BollywoodSurface = Color(0xFFFFEDD5)
    val BollywoodSurfaceHover = Color(0xFFFED7AA)
    val BollywoodTextPrimary = Color(0xFF831843)
    val BollywoodTextSecondary = Color(0xFFBE123C)
    val BollywoodBorder = Color(0xFFF59E0B)
    val BollywoodStatusCanon = Color(0xFF16A34A)
    val BollywoodStatusFiller = Color(0xFFDC2626)
    val BollywoodStatusMixed = Color(0xFFD97706)

    // --- NARUTO MODE ---
    val NarutoPrimary = Color(0xFFFFFFFF)
    val NarutoSecondary = Color(0xFF1F2937)
    val NarutoAccent = Color(0xFFF97316)
    val NarutoBackground = Color(0xFF111827)
    val NarutoSurface = Color(0xFF1F2937)
    val NarutoSurfaceHover = Color(0xFF374151)
    val NarutoTextPrimary = Color(0xFFF9FAFB)
    val NarutoTextSecondary = Color(0xFF9CA3AF)
    val NarutoBorder = Color(0xFFF97316)
    val NarutoStatusCanon = Color(0xFF3B82F6)
    val NarutoStatusFiller = Color(0xFFEF4444)
    val NarutoStatusMixed = Color(0xFFEAB308)
}

object WatchOrderColors {
    val DeepSpace       = Color(0xFF080B14)
    val VoidDark        = Color(0xFF0D1017)
    val CardSurface     = Color(0xFF141B2D)
    val CardBorder      = Color(0xFF1E2D45)
    val ElevatedSurface = Color(0xFF1A2438)

    val AccentGold      = Color(0xFFFFBF3C)
    val AccentBlue      = Color(0xFF4390F7)
    val CompletedGreen  = Color(0xFF3BDB8A)
    val BranchCoral     = Color(0xFFFF6B6B)
    val SpoilerPurple   = Color(0xFF8B5CF6)

    val TextPrimary     = Color(0xFFECEEF2)
    val TextSecondary   = Color(0xFF7A8499)
    val TextMuted       = Color(0xFF3D4554)

    val ConnectorActive = Color(0xFF3BDB8A)
    val ConnectorIdle   = Color(0xFF1E2D45)
}
