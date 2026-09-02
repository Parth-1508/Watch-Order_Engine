package com.example.watchorderengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.watchorderengine.data.graph.GraphEngine
import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.example.watchorderengine.util.ShareCardExporter
import kotlinx.coroutines.launch

private const val MAX_VISIBLE_TITLES = 8

@Composable
fun TimelineShareCardContent(
    title: String,
    nodes: List<MediaNode>,
    edges: List<Edge>,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current

    val orderedNodes = remember(nodes, edges) {
        val sortedIds = GraphEngine.computeLayout(nodes, edges).sortedIds
        val byId = nodes.associateBy { it.id }
        sortedIds.mapNotNull { byId[it] }.ifEmpty { nodes }
    }
    val visibleNodes = orderedNodes.take(MAX_VISIBLE_TITLES)
    val remainingCount = (orderedNodes.size - MAX_VISIBLE_TITLES).coerceAtLeast(0)

    Column(
        modifier = modifier
            .width(340.dp)
            .background(theme.background)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountTree, null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "WATCH ORDER ENGINE",
                fontSize     = 10.sp,
                fontWeight   = FontWeight.Black,
                color        = theme.accent,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            title.ifBlank { "My Custom Timeline" },
            fontSize   = 22.sp,
            fontWeight = FontWeight.Black,
            color      = theme.textPrimary,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${orderedNodes.size} title${if (orderedNodes.size == 1) "" else "s"}",
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = theme.textSecondary
        )

        Spacer(Modifier.height(20.dp))

        visibleNodes.forEachIndexed { index, node ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model              = node.posterUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(46.dp, 66.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.surfaceHover)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        node.title,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = theme.textPrimary,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (node.releaseYear > 0) {
                        Text(node.releaseYear.toString(), fontSize = 11.sp, color = theme.textSecondary)
                    }
                }
            }
            if (index != visibleNodes.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 22.dp)
                        .height(14.dp)
                        .width(2.dp)
                        .background(theme.accent.copy(alpha = 0.4f))
                )
            }
        }

        if (remainingCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "+ $remainingCount more",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = theme.accent,
                modifier   = Modifier.padding(start = 58.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.15f))
        Spacer(Modifier.height(10.dp))
        Text(
            "Created with Watch Order Engine",
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            color      = theme.textSecondary
        )
    }
}

@Composable
fun ShareTimelineDialog(
    title: String,
    nodes: List<MediaNode>,
    edges: List<Edge>,
    onDismiss: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var isSharing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "PREVIEW",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Black,
                color      = theme.textSecondary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                TimelineShareCardContent(title = title, nodes = nodes, edges = edges)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, enabled = !isSharing) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        isSharing = true
                        scope.launch {
                            ShareCardExporter.shareGraphicsLayer(
                                context        = context,
                                graphicsLayer  = graphicsLayer,
                                fileNamePrefix = "timeline",
                                shareTitle     = title.ifBlank { "My Custom Timeline" },
                            )
                            isSharing = false
                            onDismiss()
                        }
                    },
                    enabled = !isSharing,
                    colors  = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            color       = Color.White,
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SHARE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
