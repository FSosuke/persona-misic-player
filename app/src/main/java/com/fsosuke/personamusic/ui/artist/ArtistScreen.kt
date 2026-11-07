package com.fsosuke.personamusic.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Confidants
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.JaggedPanel
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.LocalOpenArtist
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PolygonShape
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.StickerTitle
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.redStarField
import com.fsosuke.personamusic.ui.components.startRadio
import com.fsosuke.personamusic.ui.components.wedgeBands
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.theme.displayName
import com.fsosuke.personamusic.ui.theme.rankLabel
import com.fsosuke.personamusic.youtube.HomeShelf
import com.fsosuke.personamusic.youtube.YtArtistPage
import com.fsosuke.personamusic.youtube.YtItem
import kotlinx.coroutines.launch

private sealed interface ArtistState {
    data object Loading : ArtistState
    data object NotFound : ArtistState
    data class Loaded(val page: YtArtistPage) : ArtistState
    data class Failed(val message: String) : ArtistState
}

/** The black card under "INFO": cut corners and a spike on the right, like the P5 status card. */
private val infoCardShape = PolygonShape(
    listOf(
        Offset(0f, 0f), Offset(0.66f, 0.05f), Offset(0.74f, -0.02f), Offset(0.76f, 0.06f), Offset(1f, 0.02f),
        Offset(1f, 0.62f), Offset(0.9f, 1f), Offset(0.06f, 0.94f), Offset(0f, 0.7f),
    ),
)

/**
 * Artist page in the style of the P5 status screens: the artist's picture as a big header with
 * an "INFO" sticker, a black card with name and actions, the POPULAR songs in a jagged frame,
 * then SINGLES / ALBUM shelves on red and grey backdrops.
 */
@Composable
fun ArtistScreen(
    name: String,
    browseId: String?,
    onBack: () -> Unit,
    onOpenYtPlaylist: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val online by produceState<ArtistState>(ArtistState.Loading, name, browseId, attempt) {
        value = ArtistState.Loading
        value = runCatching { container.ytLibrary.artist(name, browseId) }.fold<ArtistState, YtArtistPage?>(
            { page -> if (page == null) ArtistState.NotFound else ArtistState.Loaded(page) },
            { ArtistState.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
        )
    }
    val local by remember(name) { container.repository.localSongsByArtist(name) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val plays by remember(name) { container.repository.artistPlays(name) }.collectAsStateWithLifecycle(initialValue = 0)
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val followed by container.releaseRadar.followed.collectAsStateWithLifecycle()
    val page = (online as? ArtistState.Loaded)?.page
    val artistName = page?.name ?: name
    val confidant = Confidants.of(artistName, plays)
    val following = followed.any { it.equals(artistName, ignoreCase = true) }
    val allSongs = (page?.songs.orEmpty() + local).distinctBy { it.id }
    var showAllPopular by remember { mutableStateOf(false) }

    fun play(songs: List<Song>, index: Int, shuffle: Boolean = false) {
        container.appScope.launch { container.repository.remember(songs) }
        container.player.play(songs, index, shuffle)
    }

    LazyColumn(Modifier.fillMaxSize().background(Color.Black), contentPadding = contentPadding) {
        // Header picture with the "INFO" sticker.
        item {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                Artwork(
                    page?.thumbnail ?: local.firstOrNull()?.artworkUri,
                    Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                )
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 12.dp, y = 18.dp)
                        .rotate(-4f),
                ) {
                    RansomText("INFO", fontSize = 64.sp)
                }
                Text(
                    "${confidant.arcana.displayName} · ${rankLabel(confidant.rank, confidant.isMax)}",
                    color = Color.White,
                    style = PersonaType.label.copy(fontSize = 13.sp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 26.dp)
                        .rotate(-3f)
                        .background(Color.Black)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        // Name + radio / shuffle / follow.
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .background(Color.White, infoCardShape)
                    .padding(4.dp)
                    .background(Color.Black, infoCardShape)
                    .padding(top = 22.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    artistName,
                    color = Color.White,
                    style = PersonaType.title.copy(fontSize = 28.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    IconButton(onClick = { allSongs.firstOrNull()?.let { startRadio(container, context, it) } }) {
                        Icon(Icons.Filled.Sensors, "Artist radio", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { if (allSongs.isNotEmpty()) play(allSongs, 0, shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = Color.White, modifier = Modifier.size(38.dp).rotate(-12f))
                    }
                    IconButton(onClick = { container.releaseRadar.toggleFollow(artistName) }) {
                        Icon(
                            if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (following) "Unfollow" else "Follow",
                            tint = if (following) colors.accent else Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    "$plays plays" + if (following) " · following" else "",
                    color = Color(0xFF9E9E9E),
                    style = PersonaType.caption,
                )
            }
        }

        when (val s = online) {
            ArtistState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            is ArtistState.Failed -> item {
                EmptyState("Mission failed", s.message) { PersonaButton("Retry", onClick = { attempt++ }) }
            }
            ArtistState.NotFound -> if (local.isEmpty()) item {
                EmptyState("No trace", "Couldn't find \"$name\" on YouTube Music.")
            }
            is ArtistState.Loaded -> Unit
        }

        // POPULAR: the stepped black list on the red star field.
        val popular = page?.songs.orEmpty().ifEmpty { local }
        if (popular.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().redStarField(colors).padding(top = 18.dp, bottom = 26.dp)) {
                    StickerTitle("Popular", Modifier.align(Alignment.CenterHorizontally).offset(x = 30.dp))
                    Spacer(Modifier.height(10.dp))
                    JaggedPanel(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                        Column {
                            val shown = if (showAllPopular) popular else popular.take(5)
                            shown.forEachIndexed { index, song ->
                                PopularRow(
                                    song,
                                    // Each row sits a little further left, like a staircase.
                                    indent = ((shown.size - 1 - index).coerceAtMost(4) * 10).dp,
                                    isCurrent = song.id == playerState.current?.id,
                                ) { play(popular, popular.indexOf(song)) }
                            }
                            if (popular.size > 5) {
                                Text(
                                    if (showAllPopular) "LESS" else "MORE (${popular.size})",
                                    color = colors.accent,
                                    style = PersonaType.label.copy(fontSize = 14.sp),
                                    modifier = Modifier.align(Alignment.End).clickable { showAllPopular = !showAllPopular }.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // SINGLES / ALBUM … one sticker title per YouTube Music shelf, alternating backdrops.
        val shelves = page?.shelves.orEmpty().filter { it.items.isNotEmpty() }
        shelves.forEachIndexed { i, shelf ->
            item(key = "shelf-$i-${shelf.title}") {
                ShelfSection(shelf, index = i, onOpenCollection = onOpenYtPlaylist, onPlay = { songs, at -> play(songs, at) })
            }
        }

        if (local.isNotEmpty() && page != null) {
            item {
                Column(Modifier.fillMaxWidth().wedgeBands(colors).padding(top = 22.dp, bottom = 12.dp)) {
                    StickerTitle("Your library", Modifier.align(Alignment.End).padding(end = 16.dp), fontSize = 36.sp)
                }
            }
            items(local, key = { "local-${it.id}" }) { song ->
                PopularRow(song, indent = 0.dp, isCurrent = song.id == playerState.current?.id) { play(local, local.indexOf(song)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PopularRow(song: Song, indent: androidx.compose.ui.unit.Dp, isCurrent: Boolean, onClick: () -> Unit) {
    val colors = LocalPersonaColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = indent, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkUri, Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) colors.accent else Color.White,
                style = PersonaType.title.copy(fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, color = Color(0xFFBDBDBD), style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (song.durationMs > 0) {
            Text(formatDuration(song.durationMs), color = Color(0xFFBDBDBD), style = PersonaType.caption, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ShelfSection(
    shelf: HomeShelf,
    index: Int,
    onOpenCollection: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    val colors = LocalPersonaColors.current
    val openArtist = LocalOpenArtist.current
    val backdrop = if (index % 2 == 0) Modifier.wedgeBands(colors) else Modifier.redStarField(colors)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(120.dp).then(backdrop)) {
            StickerTitle(
                shelf.title,
                Modifier.align(if (index % 2 == 0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 18.dp),
                tilt = if (index % 2 == 0) -2f else 1.5f,
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White))
        val songs = shelf.items.filterIsInstance<YtItem.SongItem>().map { it.song }
        LazyRow(
            Modifier.fillMaxWidth().background(Color.Black),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shelf.items.size) { i ->
                val item = shelf.items[i]
                val (title, subtitle, cover) = when (item) {
                    is YtItem.Collection -> Triple(item.playlist.title, item.playlist.subtitle, item.playlist.thumbnail)
                    is YtItem.SongItem -> Triple(item.song.title, item.song.artist, item.song.artworkUri)
                    is YtItem.ArtistItem -> Triple(item.name, null, item.thumbnail)
                }
                Column(
                    Modifier
                        .width(150.dp)
                        .clickable {
                            when (item) {
                                is YtItem.Collection -> onOpenCollection(item.playlist.id)
                                is YtItem.SongItem -> onPlay(songs, songs.indexOf(item.song))
                                is YtItem.ArtistItem -> openArtist(item.name, item.browseId)
                            }
                        },
                ) {
                    val round = item is YtItem.ArtistItem
                    Artwork(
                        cover,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .then(if (round) Modifier.clip(CircleShape) else Modifier)
                            .border(2.dp, Color.White, if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(title, color = Color.White, style = PersonaType.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, color = Color(0xFFBDBDBD), style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
