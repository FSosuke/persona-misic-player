package com.fsosuke.personamusic.ui.youtube

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.youtube.YtPlaylistPage
import kotlinx.coroutines.launch

private sealed interface PageState {
    data object Loading : PageState
    data class Loaded(val page: YtPlaylistPage) : PageState
    data class Failed(val message: String) : PageState
}

/** A YouTube Music playlist, album or mix, read live from the account. */
@Composable
fun YtPlaylistScreen(playlistId: String, onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<PageState>(PageState.Loading, playlistId, attempt) {
        value = PageState.Loading
        value = runCatching { container.ytLibrary.playlist(playlistId) }
            .fold<PageState, YtPlaylistPage>({ PageState.Loaded(it) }, { PageState.Failed(it.localizedMessage ?: it.javaClass.simpleName) })
    }
    val playerState by container.player.state.collectAsStateWithLifecycle()

    fun play(songs: List<Song>, index: Int, shuffle: Boolean = false) {
        container.appScope.launch { container.repository.remember(songs) }
        container.player.play(songs, index, shuffle)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader((state as? PageState.Loaded)?.page?.title ?: "YT Music")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        when (val s = state) {
            PageState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            is PageState.Failed -> item {
                EmptyState("Mission failed", s.message) {
                    PersonaButton("Retry", onClick = { attempt++ })
                }
            }
            is PageState.Loaded -> {
                val page = s.page
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(page.thumbnail, Modifier.rotate(-3f).size(110.dp).border(4.dp, Color.White))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            page.subtitle?.let {
                                Text(it, color = colors.muted, style = PersonaType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${page.songs.size} songs", color = Color.White, style = PersonaType.body)
                        }
                    }
                }
                if (page.songs.isEmpty()) {
                    item { EmptyState("Empty", "This playlist has no playable songs.") }
                } else {
                    item {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PersonaButton("Play", onClick = { play(page.songs, 0) })
                            PersonaButton("Shuffle", onClick = { play(page.songs, 0, shuffle = true) }, inverted = true)
                        }
                    }
                    itemsIndexed(page.songs, key = { i, song -> "$i-${song.id}" }) { index, song ->
                        SongRow(song, onClick = { play(page.songs, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
