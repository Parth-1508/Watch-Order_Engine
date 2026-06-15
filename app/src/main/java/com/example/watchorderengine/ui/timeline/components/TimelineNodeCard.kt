package com.example.watchorderengine.ui.timeline.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.watchorderengine.data.MediaNode
import com.example.watchorderengine.data.MediaType
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.viewmodel.DisplayNode

/**
 * The individual node card rendered in the timeline.
 */
@Composable
fun TimelineNodeCard(
    displayNode: DisplayNode,
    onCheckToggle: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = displayNode.node
    val metadata = displayNode.metadata

    // ── Spoiler blur animation ────────────────────────────────────────────────
    val blurRadius: Dp by animateDpAsState(
        targetValue = if (displayNode.isSpoilerBlurred) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "spoiler_blur"
    )

    val borderColor = when {
        displayNode.isCompleted      -> WatchOrderColors.CompletedGreen
        displayNode.isSpoilerBlurred -> WatchOrderColors.SpoilerPurple
        else                         -> WatchOrderColors.CardBorder
    }

    val spoilerModifier: Modifier = if (displayNode.isSpoilerBlurred) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(
                radius = blurRadius,
                edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
            )
        } else {
            Modifier.alpha(0.08f)
        }
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick, enabled = !displayNode.isSpoilerBlurred),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WatchOrderColors.CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .drawColoredLeftBorder(borderColor, width = 3.dp)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Poster / Icon ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WatchOrderColors.ElevatedSurface)
                    .then(spoilerModifier),
                contentAlignment = Alignment.Center
            ) {
                when (metadata) {
                    is TmdbFetchState.Success -> {
                        AsyncImage(
                            model = metadata.detail.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    is TmdbFetchState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    else -> {
                        Icon(
                            imageVector = node.type.icon(),
                            contentDescription = null,
                            tint = WatchOrderColors.AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── Title and metadata ────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f).then(spoilerModifier)) {
                val title = if (metadata is TmdbFetchState.Success) metadata.detail.title else node.title
                Text(
                    text = if (displayNode.isSpoilerBlurred && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) "???" else title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = WatchOrderColors.TextPrimary
                )

                Spacer(Modifier.height(4.dp))

                val metaText = if (metadata is TmdbFetchState.Success) {
                    "${metadata.detail.releaseYear} · ${metadata.detail.runtimeDisplay}"
                } else {
                    buildMetaString(node)
                }

                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WatchOrderColors.TextSecondary
                )

                if (node.phase.isNotBlank() && !displayNode.isSpoilerBlurred) {
                    Spacer(Modifier.height(4.dp))
                    PhaseBadge(phase = node.phase)
                }
            }

            Checkbox(
                checked = displayNode.isCompleted,
                onCheckedChange = { onCheckToggle() },
                enabled = !displayNode.isSpoilerBlurred,
                colors = CheckboxDefaults.colors(
                    checkedColor = WatchOrderColors.CompletedGreen,
                    checkmarkColor = WatchOrderColors.DeepSpace
                )
            )
        }
    }
}

/** A small pill-shaped badge showing which phase/arc this node belongs to. */
@Composable
private fun PhaseBadge(phase: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = WatchOrderColors.ElevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = phase,
            style = MaterialTheme.typography.labelSmall,
            color = WatchOrderColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Maps MediaType to a Material Icon that conveys the media format clearly. */
private fun MediaType.icon(): ImageVector = when (this) {
    MediaType.MOVIE   -> Icons.Filled.Movie
    MediaType.SERIES  -> Icons.Filled.Tv
    MediaType.EPISODE -> Icons.Filled.PlayCircleFilled
    MediaType.SHORT   -> Icons.Filled.PlayArrow
    MediaType.SPECIAL -> Icons.Filled.StarRate
    MediaType.COMIC   -> Icons.AutoMirrored.Filled.MenuBook
    MediaType.NOVEL   -> Icons.Filled.AutoStories
    MediaType.GAME    -> Icons.Filled.SportsEsports
}

/** Builds the metadata subtitle: "SERIES • 2021 • 26 eps" or "MOVIE • 2019 • 181 min". */
private fun buildMetaString(node: MediaNode): String {
    val parts = buildList {
        add(node.type.name.lowercase().replaceFirstChar { it.uppercase() })
        if (node.releaseYear > 0) add(node.releaseYear.toString())
        when {
            (node.type == MediaType.SERIES) && (node.episodeCount > 0) ->
                add("${node.episodeCount} eps")
            node.durationMin > 0 ->
                add("${node.durationMin} min")
        }
    }
    return parts.joinToString(" · ")
}

/**
 * Extension function to draw a colored left-border stripe using Modifier.drawBehind().
 * Avoids an extra Box composable just for a border accent.
 */
private fun Modifier.drawColoredLeftBorder(color: Color, width: Dp): Modifier =
    this.drawBehind {
        drawRect(
            color = color,
            size = androidx.compose.ui.geometry.Size(width.toPx(), size.height)
        )
    }