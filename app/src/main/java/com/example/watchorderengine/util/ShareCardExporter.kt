package com.example.watchorderengine.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val AUTHORITY_SUFFIX = ".fileprovider"
private const val SHARE_DIR_NAME = "shared_cards"

/**
 * Captures a Composable subtree into a PNG and launches the native Android
 * Share Sheet, for posting a timeline/graph as an image on social media.
 */
object ShareCardExporter {

    suspend fun shareGraphicsLayer(
        context: Context,
        graphicsLayer: GraphicsLayer,
        fileNamePrefix: String = "watch_order_timeline",
        shareTitle: String = "Share your timeline",
    ) {
        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
        val uri = saveBitmapToCache(context, bitmap, fileNamePrefix)
        launchShareSheet(context, uri, shareTitle)
    }

    private suspend fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String,
    ): android.net.Uri = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_DIR_NAME).apply { mkdirs() }

        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        shareDir.listFiles()?.forEach { f -> if (f.lastModified() < oneDayAgo) f.delete() }

        val file = File(shareDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
    }

    private fun launchShareSheet(context: Context, uri: android.net.Uri, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
