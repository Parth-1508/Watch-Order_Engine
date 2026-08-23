package com.example.watchorderengine.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DynamicPalette"

/**
 * A small, self-contained set of colors extracted from a show's poster/backdrop
 * via the Android Palette API. Used to give [com.example.watchorderengine.ui.screens.MediaDetailScreen]
 * a per-show accent instead of the one fixed color from the active [com.example.watchorderengine.ui.theme.AppThemeConfig],
 * similar in spirit to Material You's wallpaper-based theming.
 */
data class ShowPalette(
    /** The most eye-catching swatch found in the art — used as the new `accent`/`primary` M3 color. */
    val accent: Color,
    /** A readable text/icon color chosen by Palette to sit on top of [accent]. */
    val onAccent: Color,
    /** A calmer, secondary swatch — used for things like the progress ring track or gradients. */
    val muted: Color
)

/**
 * Downloads [imageUrl] through Coil's shared [coil.ImageLoader] (so it benefits from the
 * same memory/disk cache the rest of the app already uses for this image), runs it through
 * the Palette API off the main thread, and picks the most usable swatch as an accent color.
 *
 * Returns null on any failure — no network, a decode error, or no usable swatch in the
 * image — so callers can always fall back to the show's static theme colors.
 */
suspend fun extractShowPalette(context: Context, imageUrl: String?): ShowPalette? {
    if (imageUrl.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                // Palette needs to read raw pixels back off the bitmap, which hardware
                // bitmaps (Coil's default on API 26+) don't support — force a software copy.
                .allowHardware(false)
                // We only need a handful of buckets, not full resolution — downscaling
                // keeps extraction fast even on a slow connection or a big backdrop.
                .size(200, 200)
                .build()

            val drawable = context.imageLoader.execute(request).drawable ?: return@withContext null
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmapOrNull()
            ?: return@withContext null

            Palette.from(bitmap).generate().toShowPalette()
        } catch (e: Exception) {
            // Never let a theming nicety crash or block the detail screen.
            Log.w(TAG, "Palette extraction failed for $imageUrl", e)
            null
        }
    }
}

/**
 * Picks swatches in order of visual "punch": saturated vibrant colors read best as an
 * accent; dominant/muted swatches are a graceful fallback so a wash of grey poster art
 * still produces *some* color instead of giving up entirely.
 */
private fun Palette.toShowPalette(): ShowPalette? {
    val accentSwatch = vibrantSwatch
        ?: lightVibrantSwatch
        ?: darkVibrantSwatch
        ?: dominantSwatch
        ?: mutedSwatch
        ?: return null

    val secondarySwatch = darkMutedSwatch ?: mutedSwatch ?: lightMutedSwatch ?: accentSwatch

    return ShowPalette(
        accent = Color(accentSwatch.rgb),
        // Palette computes body/title text color based on the swatch's own luminance,
        // so this is already contrast-safe against `accent` without any math on our end.
        onAccent = Color(accentSwatch.bodyTextColor),
        muted = Color(secondarySwatch.rgb)
    )
}

/**
 * Composable helper that (re-)extracts a [ShowPalette] whenever [imageUrl] changes.
 * Emits null while loading, while disabled, or on failure — treat null as "use the
 * default theme," not as an error state.
 */
@Composable
fun rememberShowPalette(imageUrl: String?, enabled: Boolean = true): State<ShowPalette?> {
    val context = LocalContext.current
    return produceState<ShowPalette?>(initialValue = null, imageUrl, enabled) {
        value = if (enabled) extractShowPalette(context, imageUrl) else null
    }
}
