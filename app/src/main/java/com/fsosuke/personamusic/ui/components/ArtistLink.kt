package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.fsosuke.personamusic.data.isRealArtist
import com.fsosuke.personamusic.data.primaryArtist

/** Opens the artist page: (name, optional YouTube Music channel id). Provided by the nav host. */
val LocalOpenArtist = staticCompositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }

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
