package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** Opens the artist page: (name, optional YouTube Music channel id). Provided by the nav host. */
val LocalOpenArtist = staticCompositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }

private val separators = Regex("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b|/)\\s*", RegexOption.IGNORE_CASE)

/** "Lyn, ATLUS Sound Team" → "Lyn". */
fun primaryArtist(artist: String): String = artist.split(separators).firstOrNull { it.isNotBlank() }?.trim() ?: artist

fun isRealArtist(artist: String) = artist.isNotBlank() && !artist.startsWith("Unknown", ignoreCase = true)

/** Artist name that opens the artist page when tapped. */
@Composable
fun ArtistText(artist: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val open = LocalOpenArtist.current
    Text(
        artist,
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (isRealArtist(artist)) modifier.clickable { open(primaryArtist(artist), null) } else modifier,
    )
}
