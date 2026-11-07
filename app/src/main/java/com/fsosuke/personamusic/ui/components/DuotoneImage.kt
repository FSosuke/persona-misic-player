package com.fsosuke.personamusic.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** High-contrast greyscale – the first step of turning any picture into P5 art. */
private val inkMatrix = ColorMatrix(
    floatArrayOf(
        0.299f * 1.7f, 0.587f * 1.7f, 0.114f * 1.7f, 0f, -90f,
        0.299f * 1.7f, 0.587f * 1.7f, 0.114f * 1.7f, 0f, -90f,
        0.299f * 1.7f, 0.587f * 1.7f, 0.114f * 1.7f, 0f, -90f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/**
 * Shows a user-picked image restyled for Persona 5: inked black & white, optionally multiplied
 * with the accent so whites turn red and shadows stay black.
 */
@Composable
fun DuotoneImage(path: String, tint: Color?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(inkMatrix),
        alpha = alpha,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (tint != null) drawRect(tint, blendMode = BlendMode.Multiply)
            },
    )
}

/** Copies a picked image into app storage so it survives the picker's temporary permission. */
suspend fun importBackground(context: Context, uri: Uri, name: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
        // New file name each time so image caches never show the previous picture.
        dir.listFiles { f -> f.name.startsWith(name) }?.forEach { it.delete() }
        val target = File(dir, "$name-${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: return@runCatching null
        target.absolutePath
    }.getOrNull()
}
