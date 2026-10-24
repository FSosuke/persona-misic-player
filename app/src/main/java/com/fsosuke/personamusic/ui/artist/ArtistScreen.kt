package com.fsosuke.personamusic.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Confidants
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.Sparkle
import com.fsosuke.personamusic.ui.components.phantomSlashes
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.youtube.YtShelf
import com.fsosuke.personamusic.youtube.YtArtistPage
import kotlinx.coroutines.launch

private sealed interface ArtistState {
    data object Loading : ArtistState
    data object NotFound : ArtistState
    data class Loaded(val page: YtArtistPage) : ArtistState
    data class Failed(val message: String) : ArtistState
}

/**
 * Artist page: YouTube Music top songs, albums, singles and similar artists, plus the artist's
 * songs from the local library. The "confidant rank" grows with how often you listen.
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
    val displayName = page?.name ?: name
    val confidant = Confidants.of(displayName, plays)
    val rank = confidant.rank

    fun play(songs: List<Song>, index: Int, shuffle: Boolean = false) {
        container.appScope.launch { container.repository.remember(songs) }
        container.player.play(songs, index, shuffle)
    }

    LazyColumn(Modifier.fillMaxSize().background(colors.background), contentPadding = contentPadding) {
        item {
            Box(Modifier.fillMaxWidth().height(340.dp).phantomSlashes(colors)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = 10.dp)
                        .size(210.dp)
                        .rotate(-4f),
                ) {
                    Box(Modifier.matchParentSize().offset(9.dp, 9.dp).background(Color.Black))
                    Artwork(
                        page?.thumbnail ?: local.firstOrNull()?.artworkUri,
                        Modifier.matchParentSize().background(Color.White).border(8.dp, Color.White).padding(8.dp),
                    )
                }
                Sparkle(Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 30.dp).size(80.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                        .rotate(-3f)
                        .background(Color.Black)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    RansomText(displayName, fontSize = 30.sp)
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                ShoutLabel(
                    if (confidant.isMax) "${confidant.arcana.title} · MAX" else "${confidant.arcana.title} · Rank $rank",
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 60.dp, top = 16.dp),
                    tilt = -6f,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val all = (page?.songs.orEmpty() + local).distinctBy { it.id }
                if (all.isNotEmpty()) {
                    PersonaButton("Play", onClick = { play(all, 0) })
                    PersonaButton("Shuffle", onClick = { play(all, 0, shuffle = true) }, inverted = true)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("$plays plays", color = Color.White, style = PersonaType.body)
                    Text("Confidant", color = colors.muted, style = PersonaType.caption)
                }
            }
        }

        item {
            val following = followed.any { it.equals(displayName, ignoreCase = true) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonaButton(
                    if (following) "✓ Following" else "+ Follow",
                    onClick = { container.releaseRadar.toggleFollow(displayName) },
                    inverted = !following,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (following) "New releases show up in Release Radar." else "Get notified about new releases.",
                    color = colors.muted,
                    style = PersonaType.caption,
                    modifier = Modifier.weight(1f),
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
            is ArtistState.Loaded -> {
                val songs = s.page.songs
                if (songs.isNotEmpty()) {
                    item { DashedSectionTitle("Top songs") }
                    itemsIndexed(songs, key = { i, song -> "yt-$i-${song.id}" }) { index, song ->
                        SongRow(song, onClick = { play(songs, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
                    }
                }
            }
        }

        if (local.isNotEmpty()) {
            item { DashedSectionTitle("Your library") }
            itemsIndexed(local, key = { _, song -> "local-${song.id}" }) { index, song ->
                SongRow(song, onClick = { play(local, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
            }
        }

        page?.shelves?.let { shelves ->
            itemsIndexed(shelves, key = { i, shelf -> "shelf-$i-${shelf.title}" }) { i, shelf ->
                YtShelf(
                    shelf = shelf,
                    index = i,
                    currentSongId = playerState.current?.id,
                    onPlay = { songs, index -> play(songs, index) },
                    onOpenCollection = onOpenYtPlaylist,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
