package com.example.watchorderengine.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil.imageLoader
import coil.request.ImageRequest
import com.example.watchorderengine.data.model.ContinueWatchingItem
import com.example.watchorderengine.di.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

private const val TAG = "ContinueWatchingWidget"

/** 
 * Design Note: These colors now exactly match WatchOrderColors (DeepSpace, CardSurface, AccentGold).
 * Using resource IDs ensures we don't hit "same library group" lint errors on some build paths.
 */
private val WidgetBackground     = ColorProvider(com.example.watchorderengine.R.color.widget_background)
private val WidgetSurface        = ColorProvider(com.example.watchorderengine.R.color.widget_surface)
private val WidgetAccent         = ColorProvider(com.example.watchorderengine.R.color.widget_accent_gold)
private val WidgetOnAccent       = ColorProvider(com.example.watchorderengine.R.color.black)
private val WidgetTextPrimary    = ColorProvider(com.example.watchorderengine.R.color.white)
private val WidgetTextSecondary  = ColorProvider(com.example.watchorderengine.R.color.widget_text_secondary)
private val WidgetTrack          = ColorProvider(com.example.watchorderengine.R.color.widget_track)

object WidgetParams {
    val MEDIA_ID = ActionParameters.Key<String>("mediaId")
    val EPISODE_ID = ActionParameters.Key<String>("episodeId")
}

private val SmallSize  = DpSize(120.dp, 120.dp)
private val MediumSize = DpSize(250.dp, 120.dp)
private val LargeSize  = DpSize(250.dp, 280.dp)

class ContinueWatchingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SmallSize, MediumSize, LargeSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetEntryPoint().mediaRepository()

        val items = try {
            repository.getContinueWatchingItems(limit = 5)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Continue Watching items: ${e.message}")
            emptyList()
        }

        val posters = loadPosterBitmaps(context, items)

        provideContent {
            ContinueWatchingContent(items = items, posters = posters)
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            ContinueWatchingWidget().updateAll(context)
        }
    }
}

private suspend fun loadPosterBitmaps(
    context: Context,
    items: List<ContinueWatchingItem>
): Map<String, Bitmap> = withContext(Dispatchers.IO) {
    items.mapNotNull { it.posterUrl }.distinct().map { url ->
        async {
            val bitmap = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(180, 270)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                (drawable as? BitmapDrawable)?.bitmap ?: drawable?.toBitmapOrNull()
            }.getOrNull()
            url to bitmap
        }
    }.awaitAll().mapNotNull { (url, bmp) -> bmp?.let { url to it } }.toMap()
}

@Composable
private fun ContinueWatchingContent(
    items: List<ContinueWatchingItem>,
    posters: Map<String, Bitmap>
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        if (items.isEmpty()) {
            EmptyState()
        } else {
            val size = LocalSize.current
            if (size.height < 150.dp) {
                HeroRow(item = items.first(), poster = posters[items.first().posterUrl])
            } else {
                FullList(items = items, posters = posters)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "NOTHING IN PROGRESS",
            style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            "Your recently watched shows will appear here.",
            style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun HeroRow(item: ContinueWatchingItem, poster: Bitmap?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to item.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(poster, width = 52.dp, height = 78.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Box(
                modifier = GlanceModifier
                    .background(WidgetAccent)
                    .cornerRadius(4.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    "NEXT UP",
                    style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                )
            }
            Text(
                item.showTitle,
                maxLines = 1,
                style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Text(
                item.episodeLabel,
                maxLines = 1,
                style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp)
            )
        }
        if (item.nextEpisodeId != null) {
            MarkWatchedButton(item, size = 32.dp)
        }
    }
}

@Composable
private fun FullList(items: List<ContinueWatchingItem>, posters: Map<String, Bitmap>) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WATCH ORDER ENGINE",
                style = TextStyle(color = WidgetTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .clickable(actionRunCallback<RefreshContinueWatchingAction>()),
                contentAlignment = Alignment.Center
            ) {
                // Circular arrows refresh icon
                Text("\u21BB", style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            }
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(items, itemId = { it.mediaId.hashCode().toLong() }) { item ->
                Column {
                    ItemRow(item = item, poster = posters[item.posterUrl])
                    Spacer(modifier = GlanceModifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ContinueWatchingItem, poster: Bitmap?) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetSurface)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to item.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(poster, width = 44.dp, height = 66.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                item.showTitle,
                maxLines = 1,
                style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
            Text(
                item.episodeLabel,
                maxLines = 1,
                style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            ProgressTrack(percent = item.progressPercent, totalWidth = 110.dp)
        }
        if (item.nextEpisodeId != null) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            MarkWatchedButton(item, size = 28.dp)
        }
    }
}

@Composable
private fun ProgressTrack(percent: Int, totalWidth: Dp) {
    val filled = totalWidth * (percent.coerceIn(0, 100) / 100f)
    Box(
        modifier = GlanceModifier
            .width(totalWidth)
            .height(4.dp)
            .background(WidgetTrack)
            .cornerRadius(2.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .width(filled)
                .height(4.dp)
                .background(WidgetAccent)
                .cornerRadius(2.dp)
        ) {}
    }
}

@Composable
private fun PosterThumb(bitmap: Bitmap?, width: Dp, height: Dp) {
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .background(WidgetTrack)
            .cornerRadius(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MarkWatchedButton(item: ContinueWatchingItem, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(WidgetAccent)
            .cornerRadius(size / 2)
            .clickable(actionRunCallback<MarkEpisodeWatchedAction>(
                parameters = actionParametersOf(
                    WidgetParams.MEDIA_ID to item.mediaId,
                    WidgetParams.EPISODE_ID to (item.nextEpisodeId ?: "")
                )
            )),
        contentAlignment = Alignment.Center
    ) {
        // Checkmark symbol
        Text("\u2713", style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp))
    }
}
