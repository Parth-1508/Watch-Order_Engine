package com.example.watchorderengine.ui.timeline.components

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.ui.theme.WatchOrderColors
import com.example.watchorderengine.viewmodel.DisplayNode

val HexagonShape = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val radius = size.minDimension / 2
            val angle = (Math.PI * 2) / 6
            for (i in 0 until 6) {
                val x = size.width / 2 + radius * Math.cos(angle * i - Math.PI / 2).toFloat()
                val y = size.height / 2 + radius * Math.sin(angle * i - Math.PI / 2).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The individual node card rendered in the timeline.
 */
@OptIn(ExperimentalFoundationApi::class)
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
        displayNode.isSpoilerBlurred -> WatchOrderColors.SpoilerPurple.copy(alpha = 0.5f)
        else                         -> WatchOrderColors.CardBorder.copy(alpha = 0.3f)
    }

    val spoilerModifier: Modifier = if (displayNode.isSpoilerBlurred) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(
                radius = blurRadius,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            )
        } else {
            Modifier.alpha(0.1f)
        }
    } else Modifier

    val scale by animateFloatAsState(
        targetValue = if (displayNode.isCompleted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "completed_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .padding(4.dp)
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCheckToggle,
                enabled = !displayNode.isSpoilerBlurred
            ),
        contentAlignment = Alignment.Center
    ) {
        // Hexagonal background for skill-tree feel
        Canvas(modifier = Modifier.size(80.dp)) {
            val path = Path().apply {
                val radius = size.minDimension / 2
                val angle = (Math.PI * 2) / 6
                for (i in 0 until 6) {
                    val x = size.width / 2 + radius * Math.cos(angle * i - Math.PI / 2).toFloat()
                    val y = size.height / 2 + radius * Math.sin(angle * i - Math.PI / 2).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, color = WatchOrderColors.CardSurface)
            drawPath(path, color = borderColor, style = Stroke(width = 2.dp.toPx()))
            
            if (displayNode.isCompleted) {
                drawPath(path, color = WatchOrderColors.CompletedGreen.copy(alpha = 0.1f))
            }
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(HexagonShape)
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WatchOrderColors.AccentGold)
                }
                else -> {
                    Icon(
                        imageVector = node.type.icon(),
                        contentDescription = null,
                        tint = WatchOrderColors.AccentBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (displayNode.isSpoilerBlurred) {
            Icon(
                Icons.Default.Lock,
                null,
                tint = WatchOrderColors.SpoilerPurple,
                modifier = Modifier.size(20.dp)
            )
        } else if (displayNode.isCompleted) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = WatchOrderColors.CompletedGreen,
                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).offset(x = 8.dp, y = 8.dp)
            )
        }

        // Floating Title below
        Text(
            text = if (displayNode.isSpoilerBlurred) "???" else node.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (displayNode.isCompleted) WatchOrderColors.TextPrimary else WatchOrderColors.TextSecondary,
            modifier = Modifier.offset(y = 50.dp).width(90.dp),
            textAlign = TextAlign.Center,
            fontSize = 9.sp,
            fontWeight = if (displayNode.isCompleted) FontWeight.Bold else FontWeight.Normal
        )
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

/** Maps MediaCategory to a Material Icon that conveys the media format clearly. */
private fun MediaCategory.icon(): ImageVector = when (this) {
    MediaCategory.MOVIE   -> Icons.Filled.Movie
    MediaCategory.TV_SHOW  -> Icons.Filled.Tv
    MediaCategory.ANIME   -> Icons.Filled.Animation
    MediaCategory.EPISODE -> Icons.Filled.PlayCircleFilled
    MediaCategory.SHORT   -> Icons.Filled.PlayArrow
    MediaCategory.SPECIAL -> Icons.Filled.StarRate
    MediaCategory.COMIC   -> Icons.AutoMirrored.Filled.MenuBook
    MediaCategory.NOVEL   -> Icons.Filled.AutoStories
    MediaCategory.GAME    -> Icons.Filled.SportsEsports
}

/** Builds the metadata subtitle: "SERIES • 2021 • 26 eps" or "MOVIE • 2019 • 181 min". */
private fun buildMetaString(node: MediaNode): String {
    val parts = buildList {
        add(node.type.name.lowercase().replaceFirstChar { it.uppercase() })
        if (node.releaseYear > 0) add(node.releaseYear.toString())
        when {
            (node.type == MediaCategory.TV_SHOW || node.type == MediaCategory.ANIME) && (node.episodeCount > 0) ->
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