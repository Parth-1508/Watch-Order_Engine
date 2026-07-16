package com.example.watchorderengine.ui.timeline.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.viewmodel.DisplayNode

/** Fixed poster height for every node card — keeps rows aligned without needing zoom. */
val NodePosterHeight: Dp = 128.dp

/**
 * A single node in the simplified watch-order timeline.
 * A clean poster card (no hexagons) with a status border and a small
 * checkmark / lock badge — legible at a fixed scale, no pinch-zoom required.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineNodeCard(
    displayNode: DisplayNode,
    onCheckToggle: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = com.example.watchorderengine.ui.theme.LocalAppTheme.current
    val node = displayNode.node
    val metadata = displayNode.metadata
    val shape = RoundedCornerShape(theme.appRadius.coerceIn(8.dp, 16.dp))

    val blurRadius: Dp by animateDpAsState(
        targetValue = if (displayNode.isSpoilerBlurred) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "spoiler_blur"
    )

    val borderColor = when {
        displayNode.isCompleted      -> theme.statusCanon
        displayNode.isSpoilerBlurred -> theme.statusMixed.copy(alpha = 0.6f)
        else                         -> theme.border.copy(alpha = 0.25f)
    }
    val borderWidth = if (displayNode.isCompleted) 2.dp else 1.dp

    val spoilerModifier: Modifier = if (displayNode.isSpoilerBlurred) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier.alpha(0.12f)
        }
    } else Modifier

    val scale by animateFloatAsState(
        targetValue = if (displayNode.isCompleted) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "completed_scale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .padding(4.dp)
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCheckToggle,
                enabled = !displayNode.isSpoilerBlurred
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NodePosterHeight)
                    .clip(shape)
                    .background(theme.surfaceHover)
                    .border(borderWidth, borderColor, shape)
            ) {
                when (metadata) {
                    is TmdbFetchState.Success -> {
                        AsyncImage(
                            model = metadata.detail.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(spoilerModifier),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    is TmdbFetchState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = theme.accent
                            )
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = node.type.icon(),
                                contentDescription = null,
                                tint = theme.accent.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                if (displayNode.isCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.statusCanon.copy(alpha = 0.10f))
                    )
                }

                if (displayNode.isSpoilerBlurred) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        tint = theme.statusMixed,
                        modifier = Modifier.align(Alignment.Center).size(22.dp)
                    )
                }
            }

            if (displayNode.isCompleted) {
                Surface(
                    shape = CircleShape,
                    color = theme.statusCanon,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (displayNode.isSpoilerBlurred) "???" else node.title.ifBlank { " " },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (displayNode.isCompleted) theme.textPrimary else theme.textSecondary,
            modifier = Modifier.padding(horizontal = 2.dp),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            fontWeight = if (displayNode.isCompleted) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 13.sp
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
