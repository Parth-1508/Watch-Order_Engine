package com.example.watchorderengine.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.watchorderengine.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * A self-contained, Compose-native alternative to uCrop for the avatar picker flow.
 */
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }

    var scale by remember { mutableStateOf(1f) }
    var offsetFraction by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUri) {
        isLoading = true
        sourceBitmap = loadDownsampledBitmap(context, imageUri)
        scale = 1f
        offsetFraction = Offset.Zero
        isLoading = false
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = theme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = theme.textPrimary)
                    }
                    Text(
                        "ADJUST PHOTO",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        enabled = sourceBitmap != null && !isCropping,
                        onClick = {
                            isCropping = true
                        }
                    ) {
                        if (isCropping) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = theme.accent)
                        } else {
                            Icon(Icons.Default.Check, "Confirm crop", tint = theme.accent)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    if (isLoading || bmp == null) {
                        CircularProgressIndicator(color = theme.accent)
                    } else {
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .pointerInput(bmp) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                                        scale = newScale
                                        val panFraction = Offset(pan.x / size.width, pan.y / size.height)
                                        offsetFraction = clampOffsetFraction(offsetFraction + panFraction, scale)
                                    }
                                }
                        ) {
                            val frameSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                                drawCroppedPreview(imageBitmap, scale, offsetFraction, frameSizePx)
                            }
                        }

                        // Circular crop-frame overlay
                        ComposeCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = size.minDimension / 2f - strokeWidth,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }
                }

                Text(
                    "Drag to reposition · pinch to zoom",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    LaunchedEffect(isCropping) {
        if (!isCropping) return@LaunchedEffect
        val bmp = sourceBitmap
        if (bmp == null) {
            isCropping = false
            return@LaunchedEffect
        }
        val cropped = withContext(Dispatchers.Default) {
            cropBitmap(bmp, scale, offsetFraction)
        }
        isCropping = false
        onCropped(cropped)
    }
}

private fun clampOffsetFraction(candidate: Offset, scale: Float): Offset {
    val maxFraction = (scale - 1f) / 2f
    return Offset(
        x = candidate.x.coerceIn(-maxFraction, maxFraction),
        y = candidate.y.coerceIn(-maxFraction, maxFraction)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCroppedPreview(
    image: ImageBitmap,
    scale: Float,
    offsetFraction: Offset,
    frameSizePx: Float,
) {
    val coverScale = max(
        frameSizePx / image.width.toFloat(),
        frameSizePx / image.height.toFloat()
    )
    val totalScale = coverScale * scale
    val drawWidth = image.width * totalScale
    val drawHeight = image.height * totalScale

    val left = (frameSizePx - drawWidth) / 2f + offsetFraction.x * frameSizePx
    val top = (frameSizePx - drawHeight) / 2f + offsetFraction.y * frameSizePx

    clipRect {
        drawImage(
            image = image,
            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
        )
    }
}

private suspend fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val maxEdge = 1600
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            var sampleSize = 1
            val largestEdge = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (largestEdge / sampleSize > maxEdge) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

private fun cropBitmap(source: Bitmap, scale: Float, offsetFraction: Offset, outputSizePx: Int = 512): Bitmap {
    val output = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val frameSizePx = outputSizePx.toFloat()
    val coverScale = max(
        frameSizePx / source.width.toFloat(),
        frameSizePx / source.height.toFloat()
    )
    val totalScale = coverScale * scale
    val drawWidth = source.width * totalScale
    val drawHeight = source.height * totalScale
    val left = (frameSizePx - drawWidth) / 2f + offsetFraction.x * frameSizePx
    val top = (frameSizePx - drawHeight) / 2f + offsetFraction.y * frameSizePx

    val matrix = Matrix().apply {
        postScale(totalScale, totalScale)
        postTranslate(left, top)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    return output
}
