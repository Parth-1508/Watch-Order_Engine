package com.example.watchorderengine.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.watchorderengine.ui.theme.LocalAppTheme

@Composable
fun ThemeBorderModifier(modifier: Modifier = Modifier): Modifier {
    val theme = LocalAppTheme.current
    return when {
        theme.isComic -> modifier
            .border(2.dp, Color.Black)
            .drawBehind {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = size
                )
            }
        theme.isManga -> modifier
            .border(2.dp, Color.Black)
            .drawBehind {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(-4.dp.toPx(), 4.dp.toPx()),
                    size = size,
                    alpha = 1f
                )
            }
        else -> modifier.clip(RoundedCornerShape(theme.appRadius))
    }
}

@Composable
fun StatusBadge(type: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val bgColor = when (type.lowercase()) {
        "canon" -> theme.statusCanon
        "filler" -> theme.statusFiller
        "mixed" -> theme.statusMixed
        "recommended" -> theme.accent
        else -> Color.Gray
    }

    Surface(
        modifier = modifier
            .then(
                if (theme.isComic || theme.isManga) {
                    Modifier.border(2.dp, Color.Black)
                } else {
                    Modifier.clip(CircleShape)
                }
            ),
        color = bgColor,
        shadowElevation = if (theme.isComic) 2.dp else 0.dp
    ) {
        Text(
            text = type.uppercase(),
            color = if (theme.isComic || theme.isManga) Color.Black else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CategoryTab(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val bgColor = if (isSelected) {
        if (theme.isComic) Color.Black else theme.accent
    } else {
        theme.surface
    }
    val textColor = if (isSelected) {
        if (theme.isComic) Color(0xFF00FF00) else theme.primary
    } else {
        theme.textPrimary
    }

    Surface(
        modifier = Modifier
            .padding(end = 8.dp)
            .clickable { onClick() }
            .then(
                if (theme.isComic) {
                    Modifier
                        .border(2.dp, Color.Black)
                        .then(if (isSelected) Modifier.shadow(10.dp, spotColor = Color(0xFF00FF00)) else Modifier)
                } else {
                    Modifier.clip(CircleShape).border(2.dp, theme.border, CircleShape)
                }
            ),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name.uppercase(),
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = CircleShape,
                    color = if (isSelected) textColor else theme.textSecondary
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = count.toString(),
                            color = if (isSelected) bgColor else theme.surface,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(show: MediaShowItem, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .then(ThemeBorderModifier())
                .background(Color.Black)
        ) {
            AsyncImage(
                model = show.imageUrl,
                contentDescription = show.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.9f
            )
            
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                StatusBadge(type = show.badge)
            }

            if (show.watchlistStatus == "Completed") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color(0xFF00FF00), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("✓ DONE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = show.title,
            color = theme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        
        Text(
            text = show.genres.take(2).joinToString(" • "),
            color = theme.textSecondary,
            fontSize = 10.sp
        )

        if (show.progress != null && show.totalEpisodes != null) {
            val progressFactor = show.progress.toFloat() / show.totalEpisodes
            Spacer(modifier = Modifier.height(4.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${show.progress} / ${show.totalEpisodes} EP", fontSize = 10.sp, color = theme.textSecondary)
                    Text("${(progressFactor * 100).toInt()}%", fontSize = 10.sp, color = theme.textSecondary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(theme.border, if (theme.isComic) RoundedCornerShape(0.dp) else CircleShape)
                        .padding(if (theme.isComic) 1.dp else 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFactor)
                            .fillMaxHeight()
                            .background(theme.accent, if (theme.isComic) RoundedCornerShape(0.dp) else CircleShape)
                    )
                }
            }
        }
    }
}
