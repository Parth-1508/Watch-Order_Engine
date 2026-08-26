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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.example.watchorderengine.data.model.UpcomingEpisode
import com.example.watchorderengine.di.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "UpcomingCalendarWidget"

private val WidgetBackground     = ColorProvider(com.example.watchorderengine.R.color.widget_background)
private val WidgetSurface        = ColorProvider(com.example.watchorderengine.R.color.widget_surface)
private val WidgetAccent         = ColorProvider(com.example.watchorderengine.R.color.widget_accent_blue)
private val WidgetAccentGold     = ColorProvider(com.example.watchorderengine.R.color.widget_accent_gold)
private val WidgetTextPrimary    = ColorProvider(com.example.watchorderengine.R.color.widget_text_primary)
private val WidgetTextSecondary  = ColorProvider(com.example.watchorderengine.R.color.widget_text_secondary)
private val WidgetTrack          = ColorProvider(com.example.watchorderengine.R.color.widget_track)

private val SmallSize  = DpSize(120.dp, 120.dp)
private val MediumSize = DpSize(250.dp, 120.dp)
private val LargeSize  = DpSize(250.dp, 280.dp)

class UpcomingCalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SmallSize, MediumSize, LargeSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetEntryPoint().mediaRepository()

        val episodes = try {
            repository.getUpcomingEpisodes()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load upcoming episodes: ${e.message}")
            emptyList()
        }

        val posters = loadPosterBitmaps(context, episodes)

        provideContent {
            UpcomingCalendarContent(episodes = episodes, posters = posters)
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            UpcomingCalendarWidget().updateAll(context)
        }
    }
}

class UpcomingCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingCalendarWidget()
}

class RefreshUpcomingCalendarAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        try {
            context.widgetEntryPoint().mediaRepository().refreshCurrentSeasonForWatchingShows()
        } catch (e: Exception) {
            Log.w(TAG, "Calendar widget refresh failed: ${e.message}")
        }
        UpcomingCalendarWidget.refreshAll(context)
    }
}

private suspend fun loadPosterBitmaps(
    context: Context,
    episodes: List<UpcomingEpisode>
): Map<String, Bitmap> = withContext(Dispatchers.IO) {
    episodes.mapNotNull { it.posterUrl }.distinct().take(10).map { url ->
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

private fun relativeDayLabel(airDate: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(airDate) ?: return airDate
        
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)
        
        val target = Calendar.getInstance()
        target.time = date
        target.set(Calendar.HOUR_OF_DAY, 0); target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0); target.set(Calendar.MILLISECOND, 0)
        
        val diffDays = ((target.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
        
        when (diffDays) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        airDate
    }
}

@Composable
private fun UpcomingCalendarContent(episodes: List<UpcomingEpisode>, posters: Map<String, Bitmap>) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        if (episodes.isEmpty()) {
            EmptyState()
        } else {
            val size = LocalSize.current
            if (size.height < 150.dp) {
                HeroRow(episode = episodes.first(), poster = posters[episodes.first().posterUrl])
            } else {
                FullList(episodes = episodes, posters = posters)
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
            "CALENDAR EMPTY",
            style = TextStyle(color = WidgetAccentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            "No upcoming episodes for your Watching list.",
            style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun HeroRow(episode: UpcomingEpisode, poster: Bitmap?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to episode.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(poster, width = 52.dp, height = 78.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                relativeDayLabel(episode.airDate).uppercase(),
                style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            Text(
                episode.showTitle,
                maxLines = 1,
                style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Text(
                "${episode.seasonEpisodeLabel} — ${episode.episodeName}",
                maxLines = 1,
                style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp)
            )
        }
    }
}

@Composable
private fun FullList(episodes: List<UpcomingEpisode>, posters: Map<String, Bitmap>) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RELEASE CALENDAR",
                style = TextStyle(color = WidgetTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .clickable(actionRunCallback<RefreshUpcomingCalendarAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text("\u21BB", style = TextStyle(color = WidgetAccentGold, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            }
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(episodes, itemId = { (it.mediaId + it.airDate + it.episodeNumber).hashCode().toLong() }) { episode ->
                Column {
                    EpisodeRow(episode = episode, poster = posters[episode.posterUrl])
                    Spacer(modifier = GlanceModifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: UpcomingEpisode, poster: Bitmap?) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetSurface)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to episode.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumb(poster, width = 44.dp, height = 66.dp)
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                episode.showTitle,
                maxLines = 1,
                style = TextStyle(color = WidgetTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
            Text(
                "${episode.seasonEpisodeLabel} — ${episode.episodeName}",
                maxLines = 1,
                style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp)
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            relativeDayLabel(episode.airDate),
            style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        )
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
