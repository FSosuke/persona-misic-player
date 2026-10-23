package com.fsosuke.personamusic.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.WithSongMenu
import com.fsosuke.personamusic.ui.components.LocalOpenArtist
import com.fsosuke.personamusic.ui.components.jaggedCardShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.youtube.HomeShelf
import com.fsosuke.personamusic.youtube.YtItem

/** One personalised YouTube Music shelf ("Quick picks", "Mixed for you", …) in Persona dress. */
@Composable
fun YtShelf(
    shelf: HomeShelf,
    index: Int,
    currentSongId: String?,
    onPlay: (songs: List<Song>, startIndex: Int) -> Unit,
    onOpenCollection: (id: String) -> Unit,
) {
    val colors = LocalPersonaColors.current
    val songs = shelf.items.filterIsInstance<YtItem.SongItem>().map { it.song }
    val pointLeft = index % 2 == 1
    val openArtist = LocalOpenArtist.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (pointLeft) Alignment.CenterStart else Alignment.CenterEnd) {
            CalloutBanner(shelf.title, Modifier.padding(horizontal = 12.dp), pointLeft = pointLeft)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(shelf.items) { i, item ->
                when (item) {
                    is YtItem.SongItem -> {
                        val isCurrent = item.song.id == currentSongId
                        WithSongMenu(item.song, onClick = { onPlay(songs, songs.indexOf(item.song).coerceAtLeast(0)) }) { clickModifier ->
                            YtCard(
                                title = item.song.title,
                                subtitle = item.song.artist,
                                artwork = item.song.artworkUri,
                                seed = i,
                                highlighted = isCurrent,
                                isCollection = false,
                                clickModifier = clickModifier,
                                onClick = {},
                            )
                        }
                    }
                    is YtItem.ArtistItem -> YtCard(
                        title = item.name,
                        subtitle = "Artist",
                        artwork = item.thumbnail,
                        seed = i,
                        highlighted = false,
                        isCollection = false,
                        round = true,
                        onClick = { openArtist(item.name, item.browseId) },
                    )
                    is YtItem.Collection -> YtCard(
                        title = item.playlist.title,
                        subtitle = item.playlist.subtitle,
                        artwork = item.playlist.thumbnail,
                        seed = i,
                        highlighted = false,
                        isCollection = true,
                        onClick = { onOpenCollection(item.playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun YtCard(
    title: String,
    subtitle: String?,
    artwork: String?,
    seed: Int,
    highlighted: Boolean,
    isCollection: Boolean,
    round: Boolean = false,
    clickModifier: Modifier? = null,
    onClick: () -> Unit,
) {
    val colors = LocalPersonaColors.current
    val shape = if (round) CircleShape else jaggedCardShape(seed + 3)
    Column(Modifier.width(150.dp).then(clickModifier ?: Modifier.clickable(onClick = onClick))) {
        Box {
            Artwork(
                artwork,
                Modifier
                    .rotate(if (seed % 2 == 0) -2.5f else 2f)
                    .size(150.dp)
                    .border(3.dp, if (highlighted) colors.accent else Color.White, shape)
                    .padding(3.dp),
                shape = shape,
            )
            if (isCollection) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .rotate(-8f)
                        .background(colors.accent)
                        .padding(4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            color = if (highlighted) colors.accent else Color.White,
            style = PersonaType.title.copy(fontSize = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
