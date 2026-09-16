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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
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

private val WidgetBackground     = ColorProvider(R.color.widget_background)
private val WidgetSurface        = ColorProvider(R.color.widget_surface)
private val WidgetGlassBorder    = ColorProvider(R.color.widget_glass_border)
private val WidgetGlassFrost     = ColorProvider(R.color.widget_glass_frost)
private val WidgetAccentGold     = ColorProvider(R.color.widget_accent_gold)
private val WidgetAccentBlue     = ColorProvider(R.color.widget_accent_blue)
private val WidgetPillGoldSoft   = ColorProvider(R.color.widget_pill_gold_soft)
private val WidgetPillBlueSoft   = ColorProvider(R.color.widget_pill_blue_soft)
private val WidgetOnAccent       = ColorProvider(R.color.black)
private val WidgetTextPrimary    = ColorProvider(R.color.widget_text_primary)
private val WidgetTextSecondary  = ColorProvider(R.color.widget_text_secondary)
private val WidgetTrack          = ColorProvider(R.color.widget_track)

private val SmallSize  = DpSize(120.dp, 120.dp)
private val MediumSize = DpSize(250.dp, 120.dp)
private val LargeSize  = DpSize(250.dp, 280.dp)

private val CompactHeightThreshold = 150.dp

private const val MAX_EPISODES = 3

class UpcomingCalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SmallSize, MediumSize, LargeSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetEntryPoint().mediaRepository()

        val episodes = try {
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
            repository.getUpcomingEpisodes()
                .filter { it.airDate >= todayIso }
                .take(MAX_EPISODES)
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

class RefreshUpcomingCalendarAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
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
    episodes.mapNotNull { it.posterUrl }.distinct().map { url ->
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

private enum class Urgency { TODAY, TOMORROW, LATER }

private data class AirBadge(val label: String, val urgency: Urgency)

private fun airBadge(airDate: String): AirBadge {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(airDate) ?: return AirBadge(airDate, Urgency.LATER)

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)

        val target = Calendar.getInstance()
        target.time = date
        target.set(Calendar.HOUR_OF_DAY, 0); target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0); target.set(Calendar.MILLISECOND, 0)

        val diffDays = ((target.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()

        when (diffDays) {
            0 -> AirBadge("AIRING TODAY", Urgency.TODAY)
            1 -> AirBadge("AIRING TOMORROW", Urgency.TOMORROW)
            else -> AirBadge(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date).uppercase(), Urgency.LATER)
        }
    } catch (e: Exception) {
        AirBadge(airDate, Urgency.LATER)
    }
}

@Composable
private fun UpcomingCalendarContent(episodes: List<UpcomingEpisode>, posters: Map<String, Bitmap>) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .appWidgetBackground()
    ) {
        if (episodes.isEmpty()) {
            EmptyState()
        } else {
            val size = LocalSize.current
            if (size.height < CompactHeightThreshold) {
                CompactHero(episode = episodes.first(), poster = posters[episodes.first().posterUrl])
            } else {
                FullCalendar(episodes = episodes, posters = posters)
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
            Text("🗓", style = TextStyle(fontSize = 22.sp))
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                "CALENDAR EMPTY",
                style = TextStyle(color = WidgetAccentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                "No upcoming episodes for your Watching list.",
                style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun CompactHero(episode: UpcomingEpisode, poster: Bitmap?) {
    val badge = remember(episode.airDate) { airBadge(episode.airDate) }
    GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
                .clickable(actionRunCallback<OpenMediaDetailAction>(
                    parameters = actionParametersOf(WidgetParams.MEDIA_ID to episode.mediaId)
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterThumb(poster, width = 50.dp, height = 74.dp)
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                AirBadgePill(badge)
                Spacer(modifier = GlanceModifier.height(4.dp))
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
        }
    }
}

@Composable
private fun FullCalendar(episodes: List<UpcomingEpisode>, posters: Map<String, Bitmap>) {
    GlassCard(modifier = GlanceModifier.fillMaxSize().padding(2.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🗓  UPCOMING RELEASES",
                    style = TextStyle(color = WidgetTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .size(22.dp)
                        .clickable(actionRunCallback<RefreshUpcomingCalendarAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⟳", style = TextStyle(color = WidgetAccentGold, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                }
            }
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(episodes, itemId = { (it.mediaId + it.airDate + it.episodeNumber).hashCode().toLong() }) { episode ->
                    Column {
                        CalendarRow(episode = episode, poster = posters[episode.posterUrl])
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(episode: UpcomingEpisode, poster: Bitmap?) {
    val badge = remember(episode.airDate) { airBadge(episode.airDate) }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetSurface)
            .cornerRadius(14.dp)
            .clickable(actionRunCallback<OpenMediaDetailAction>(
                parameters = actionParametersOf(WidgetParams.MEDIA_ID to episode.mediaId)
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(66.dp)
                .background(if (badge.urgency == Urgency.LATER) WidgetAccentBlue else WidgetAccentGold)
                .cornerRadius(2.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(10.dp))
        PosterThumb(poster, width = 42.dp, height = 62.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight().padding(vertical = 8.dp)) {
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
            Spacer(modifier = GlanceModifier.height(5.dp))
            AirBadgePill(badge)
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
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
private fun AirBadgePill(badge: AirBadge) {
    when (badge.urgency) {
        Urgency.TODAY -> Box(
            modifier = GlanceModifier
                .background(WidgetAccentGold)
                .cornerRadius(6.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(badge.label, style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 9.sp))
        }
        Urgency.TOMORROW -> Box(
            modifier = GlanceModifier
                .background(WidgetPillGoldSoft)
                .cornerRadius(6.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(badge.label, style = TextStyle(color = WidgetAccentGold, fontWeight = FontWeight.Bold, fontSize = 9.sp))
        }
        Urgency.LATER -> Box(
            modifier = GlanceModifier
                .background(WidgetPillBlueSoft)
                .cornerRadius(6.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(badge.label, style = TextStyle(color = WidgetAccentBlue, fontWeight = FontWeight.Bold, fontSize = 9.sp))
        }
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
            Text("📺", style = TextStyle(fontSize = 16.sp))
        }
    }
}
