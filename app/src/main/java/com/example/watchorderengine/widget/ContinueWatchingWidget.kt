@file:Suppress("RestrictedApi")

package com.example.watchorderengine.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.watchorderengine.R
import com.example.watchorderengine.data.model.ContinueWatchingItem
import com.example.watchorderengine.di.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

private const val TAG = "ContinueWatchingWidget"

private val WidgetBackground     = ColorProvider(R.color.widget_background)
private val WidgetSurface       = ColorProvider(R.color.widget_surface)
private val WidgetGlassBorder   = ColorProvider(R.color.widget_glass_border)
private val WidgetGlassFrost    = ColorProvider(R.color.widget_glass_frost)
private val WidgetGhostBorder   = ColorProvider(R.color.widget_glass_ghost_border)
private val WidgetAccent        = ColorProvider(R.color.widget_accent_gold)
private val WidgetAccentDeep    = ColorProvider(R.color.widget_accent_gold_deep)
private val WidgetOnAccent      = ColorProvider(R.color.black)
private val WidgetTextPrimary   = ColorProvider(R.color.white)
private val WidgetTextSecondary = ColorProvider(R.color.widget_text_secondary)
private val WidgetTrack         = ColorProvider(R.color.widget_track)

object WidgetParams {
    val MEDIA_ID = ActionParameters.Key<String>("mediaId")
    val EPISODE_ID = ActionParameters.Key<String>("episodeId")
}

private val SmallSize  = DpSize(120.dp, 120.dp)
private val MediumSize = DpSize(250.dp, 120.dp)
private val LargeSize  = DpSize(250.dp, 280.dp)

private val CompactHeightThreshold = 150.dp

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

private data class SeasonEpisodeInfo(val badge: String, val episodeTitle: String?)

private val SEASON_EPISODE_REGEX = Regex("""^S(\d+)\s*E(\d+)(?:\s*—\s*(.+))?$""")

private fun parseEpisodeLabel(episodeLabel: String): SeasonEpisodeInfo {
    if (episodeLabel == "Movie") return SeasonEpisodeInfo("MOVIE", null)
    val match = SEASON_EPISODE_REGEX.find(episodeLabel) ?: return SeasonEpisodeInfo(episodeLabel, null)
    val (season, episode, title) = match.destructured
    return SeasonEpisodeInfo("Season $season Ep $episode", title.ifBlank { null })
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
    ) {
        if (items.isEmpty()) {
            EmptyState()
        } else {
            val size = LocalSize.current
            if (size.height < CompactHeightThreshold) {
                CompactHero(item = items.first(), poster = posters[items.first().posterUrl])
            } else {
                FeaturedList(items = items, posters = posters)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🍿", style = TextStyle(fontSize = 22.sp))
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                "NOTHING IN PROGRESS",
                style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                "Shows you're watching will show up here.",
                style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun CompactHero(item: ContinueWatchingItem, poster: Bitmap?) {
    val info = remember(item.episodeLabel) { parseEpisodeLabel(item.episodeLabel) }
    GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
                .clickable(actionRunCallback<OpenMediaDetailAction>(
                    parameters = actionParametersOf(WidgetParams.MEDIA_ID to item.mediaId)
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterThumb(poster, width = 50.dp, height = 74.dp)
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    item.showTitle,
                    maxLines = 1,
                    style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                )
                Text(
                    info.badge,
                    maxLines = 1,
                    style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                ProgressBar(percent = item.progressPercent, totalWidth = 64.dp, height = 4.dp)
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            ResumeIconButton(mediaId = item.mediaId, size = 34.dp)
        }
    }
}

private data class IndexedItem(val index: Int, val item: ContinueWatchingItem)

@Composable
private fun FeaturedList(items: List<ContinueWatchingItem>, posters: Map<String, Bitmap>) {
    val indexed = remember(items) { items.mapIndexed { i, it -> IndexedItem(i, it) } }
    GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "▶  CONTINUE WATCHING",
                    style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .size(22.dp)
                        .clickable(actionRunCallback<RefreshContinueWatchingAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⟳", style = TextStyle(color = WidgetTextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                }
            }
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(indexed, itemId = { it.item.mediaId.hashCode().toLong() }) { entry ->
                    Column {
                        if (entry.index == 0) {
                            FeaturedRow(item = entry.item, poster = posters[entry.item.posterUrl])
                        } else {
                            CompactRow(item = entry.item, poster = posters[entry.item.posterUrl])
                        }
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedRow(item: ContinueWatchingItem, poster: Bitmap?) {
    val info = remember(item.episodeLabel) { parseEpisodeLabel(item.episodeLabel) }
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetSurface)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionRunCallback<OpenMediaDetailAction>(
                    parameters = actionParametersOf(WidgetParams.MEDIA_ID to item.mediaId)
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterThumb(poster, width = 62.dp, height = 92.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    item.showTitle,
                    maxLines = 1,
                    style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    info.badge,
                    maxLines = 1,
                    style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                )
                info.episodeTitle?.let {
                    Text(it, maxLines = 1, style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp))
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                ProgressBar(percent = item.progressPercent, totalWidth = 120.dp, height = 6.dp)
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    "${item.progressPercent}% complete",
                    style = TextStyle(color = WidgetTextSecondary, fontSize = 9.sp)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        ResumeButton(mediaId = item.mediaId, modifier = GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun CompactRow(item: ContinueWatchingItem, poster: Bitmap?) {
    val info = remember(item.episodeLabel) { parseEpisodeLabel(item.episodeLabel) }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetSurface)
            .cornerRadius(14.dp)
            .padding(9.dp)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to item.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(poster, width = 40.dp, height = 60.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                item.showTitle,
                maxLines = 1,
                style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            )
            Text(
                info.badge,
                maxLines = 1,
                style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp)
            )
            Spacer(modifier = GlanceModifier.height(5.dp))
            ProgressBar(percent = item.progressPercent, totalWidth = 65.dp, height = 3.dp)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        ResumeIconButton(mediaId = item.mediaId, size = 28.dp)
        if (item.nextEpisodeId != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            MarkWatchedGhostButton(item, size = 28.dp)
        }
    }
}

@Composable
private fun GlassCard(
    modifier: GlanceModifier = GlanceModifier,
    radius: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(WidgetGlassBorder)
            .cornerRadius(radius)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetSurface)
                .cornerRadius(radius)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetGlassFrost)
                    .cornerRadius(radius)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Int, totalWidth: Dp, height: Dp) {
    val clamped = percent.coerceIn(0, 100)
    val filled = totalWidth * (clamped / 100f)
    Box(
        modifier = GlanceModifier
            .width(totalWidth)
            .height(height)
            .background(WidgetTrack)
            .cornerRadius(height / 2)
    ) {
        Box(
            modifier = GlanceModifier
                .width(filled)
                .height(height)
                .background(WidgetAccent)
                .cornerRadius(height / 2)
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
            .cornerRadius(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Text("🎬", style = TextStyle(fontSize = 16.sp))
        }
    }
}

@Composable
private fun ResumeButton(mediaId: String, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .background(WidgetAccentDeep)
            .cornerRadius(19.dp)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to mediaId)
            )),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(35.dp)
                .background(WidgetAccent)
                .cornerRadius(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶   RESUME",
                style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
        }
    }
}

@Composable
private fun ResumeIconButton(mediaId: String, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(WidgetAccentDeep)
            .cornerRadius(size / 2)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to mediaId)
            )),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .size(size - 2.dp)
                .background(WidgetAccent)
                .cornerRadius((size - 2.dp) / 2),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp))
        }
    }
}

@Composable
private fun MarkWatchedGhostButton(item: ContinueWatchingItem, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(WidgetGhostBorder)
            .cornerRadius(size / 2)
            .padding(1.dp)
            .clickable(actionRunCallback<MarkEpisodeWatchedAction>(
                parameters = actionParametersOf(
                    WidgetParams.MEDIA_ID to item.mediaId,
                    WidgetParams.EPISODE_ID to (item.nextEpisodeId ?: "")
                )
            )),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetSurface)
                .cornerRadius((size - 2.dp) / 2),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp))
        }
    }
}
