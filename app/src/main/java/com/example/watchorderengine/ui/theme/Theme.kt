package com.example.watchorderengine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary              = WOEColors.Watching,
    onPrimary            = Color.White,
    background           = WOEColors.OledBlack,
    onBackground         = WOEColors.TextPrimaryDark,
    surface              = WOEColors.SurfaceCard,
    onSurface            = WOEColors.TextPrimaryDark,
    surfaceVariant       = WOEColors.SurfaceElevated,
    onSurfaceVariant     = WOEColors.TextSecondaryDark,
    error                = WOEColors.Dropped,
    onError              = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary              = WOEColors.Watching,
    onPrimary            = Color.White,
    background           = WOEColors.LightBackground,
    onBackground         = WOEColors.TextPrimaryLight,
    surface              = WOEColors.LightSurfaceCard,
    onSurface            = WOEColors.TextPrimaryLight,
    surfaceVariant       = WOEColors.LightSurface,
    onSurfaceVariant     = WOEColors.TextSecondaryLight,
    outline              = WOEColors.LightBorder,
    error                = WOEColors.Dropped,
    onError              = Color.White,
)

val WOETypography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 45.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 24.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun WatchOrderEngineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = WOETypography,
        content     = content
    )
}
