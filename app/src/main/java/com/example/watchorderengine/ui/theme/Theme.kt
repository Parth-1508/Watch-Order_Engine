package com.example.watchorderengine.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    DEFAULT, LIGHT, DARK, COMIC, MANGA, FUNK
}

data class AppThemeConfig(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val statusCanon: Color,
    val statusFiller: Color,
    val statusMixed: Color,
    val appRadius: Dp,
    val isComic: Boolean = false,
    val isManga: Boolean = false,
    val isFunk: Boolean = false
)

val LocalAppTheme = staticCompositionLocalOf<AppThemeConfig> {
    error("No AppThemeConfig provided")
}

private val LightThemeConfig = AppThemeConfig(
    primary = WOEColors.MangaPrimary,
    secondary = WOEColors.MangaSecondary,
    accent = WOEColors.MangaAccent,
    background = WOEColors.MangaBackground,
    surface = WOEColors.MangaSurface,
    surfaceHover = WOEColors.MangaSurfaceHover,
    textPrimary = WOEColors.MangaTextPrimary,
    textSecondary = WOEColors.MangaTextSecondary,
    border = WOEColors.MangaBorder,
    statusCanon = WOEColors.MangaStatusCanon,
    statusFiller = WOEColors.MangaStatusFiller,
    statusMixed = WOEColors.MangaStatusMixed,
    appRadius = 0.dp,
    isManga = true
)

private val DarkThemeConfig = AppThemeConfig(
    primary = WOEColors.DarkPrimary,
    secondary = WOEColors.DarkSecondary,
    accent = WOEColors.DarkAccent,
    background = WOEColors.DarkBackground,
    surface = WOEColors.DarkSurface,
    surfaceHover = WOEColors.DarkSurfaceHover,
    textPrimary = WOEColors.DarkTextPrimary,
    textSecondary = WOEColors.DarkTextSecondary,
    border = WOEColors.DarkBorder,
    statusCanon = WOEColors.DarkStatusCanon,
    statusFiller = WOEColors.DarkStatusFiller,
    statusMixed = WOEColors.DarkStatusMixed,
    appRadius = 12.dp
)

private val ComicThemeConfig = AppThemeConfig(
    primary = WOEColors.ComicPrimary,
    secondary = WOEColors.ComicSecondary,
    accent = WOEColors.ComicAccent,
    background = WOEColors.ComicBackground,
    surface = WOEColors.ComicSurface,
    surfaceHover = WOEColors.ComicSurfaceHover,
    textPrimary = WOEColors.ComicTextPrimary,
    textSecondary = WOEColors.ComicTextSecondary,
    border = WOEColors.ComicBorder,
    statusCanon = WOEColors.ComicStatusCanon,
    statusFiller = WOEColors.ComicStatusFiller,
    statusMixed = WOEColors.ComicStatusMixed,
    appRadius = 0.dp,
    isComic = true
)

private val MangaThemeConfig = AppThemeConfig(
    primary = WOEColors.BollywoodPrimary,
    secondary = WOEColors.BollywoodSecondary,
    accent = WOEColors.BollywoodAccent,
    background = WOEColors.BollywoodBackground,
    surface = WOEColors.BollywoodSurface,
    surfaceHover = WOEColors.BollywoodSurfaceHover,
    textPrimary = WOEColors.BollywoodTextPrimary,
    textSecondary = WOEColors.BollywoodTextSecondary,
    border = WOEColors.BollywoodBorder,
    statusCanon = WOEColors.BollywoodStatusCanon,
    statusFiller = WOEColors.BollywoodStatusFiller,
    statusMixed = WOEColors.BollywoodStatusMixed,
    appRadius = 24.dp
)

private val FunkThemeConfig = AppThemeConfig(
    primary = WOEColors.RetroPrimary,
    secondary = WOEColors.RetroSecondary,
    accent = WOEColors.RetroAccent,
    background = WOEColors.RetroBackground,
    surface = WOEColors.RetroSurface,
    surfaceHover = WOEColors.RetroSurfaceHover,
    textPrimary = WOEColors.RetroTextPrimary,
    textSecondary = WOEColors.RetroTextSecondary,
    border = WOEColors.RetroBorder,
    statusCanon = WOEColors.RetroStatusCanon,
    statusFiller = WOEColors.RetroStatusFiller,
    statusMixed = WOEColors.RetroStatusMixed,
    appRadius = 8.dp,
    isFunk = true
)

private val DefaultThemeConfig = AppThemeConfig(
    primary = WOEColors.NarutoPrimary,
    secondary = WOEColors.NarutoSecondary,
    accent = WOEColors.NarutoAccent,
    background = WOEColors.NarutoBackground,
    surface = WOEColors.NarutoSurface,
    surfaceHover = WOEColors.NarutoSurfaceHover,
    textPrimary = WOEColors.NarutoTextPrimary,
    textSecondary = WOEColors.NarutoTextSecondary,
    border = WOEColors.NarutoBorder,
    statusCanon = WOEColors.NarutoStatusCanon,
    statusFiller = WOEColors.NarutoStatusFiller,
    statusMixed = WOEColors.NarutoStatusMixed,
    appRadius = 12.dp
)

@Composable
fun WatchOrderEngineTheme(
    mode: AppThemeMode = AppThemeMode.DEFAULT,
    content: @Composable () -> Unit
) {
    val config = when (mode) {
        AppThemeMode.DEFAULT -> DefaultThemeConfig
        AppThemeMode.LIGHT -> LightThemeConfig
        AppThemeMode.DARK -> DarkThemeConfig
        AppThemeMode.COMIC -> ComicThemeConfig
        AppThemeMode.MANGA -> MangaThemeConfig
        AppThemeMode.FUNK -> FunkThemeConfig
    }

    val isLightTheme = mode == AppThemeMode.LIGHT || mode == AppThemeMode.MANGA

    val colorScheme = if (isLightTheme) {
        lightColorScheme(
            primary = config.accent,
            onPrimary = config.primary,
            background = config.background,
            onBackground = config.textPrimary,
            surface = config.surface,
            onSurface = config.textPrimary,
            secondary = config.secondary,
            onSecondary = config.textPrimary,
            surfaceVariant = config.surfaceHover,
            onSurfaceVariant = config.textSecondary,
            outline = config.border
        )
    } else {
        darkColorScheme(
            primary = config.accent,
            onPrimary = config.primary,
            background = config.background,
            onBackground = config.textPrimary,
            surface = config.surface,
            onSurface = config.textPrimary,
            secondary = config.secondary,
            onSecondary = config.textPrimary,
            surfaceVariant = config.surfaceHover,
            onSurfaceVariant = config.textSecondary,
            outline = config.border
        )
    }

    CompositionLocalProvider(LocalAppTheme provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WOETypography,
            content = content
        )
    }
}
