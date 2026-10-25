package com.fsosuke.personamusic.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.SmartPlaylist
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.SavePlaylistDialog
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.rememberDownloadAction
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

/** A self-updating playlist built from the listening history. */
@Composable
fun SmartPlaylistScreen(playlist: SmartPlaylist, onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val songs by remember(playlist) { container.repository.smartPlaylist(playlist) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val download = rememberDownloadAction()
    var saving by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader(playlist.title)
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        item {
            Text(
                "${playlist.japanese} · ${playlist.description}",
                color = colors.muted,
                style = PersonaType.caption,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (songs.isEmpty()) {
            item { EmptyState("Nothing yet", "This playlist fills itself as you listen. Come back later.") }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { PersonaButton("Play", onClick = { container.player.play(songs, 0) }) }
                    item { PersonaButton("Shuffle", onClick = { container.player.play(songs, 0, shuffle = true) }, inverted = true) }
                    item { PersonaButton("Save", onClick = { saving = true }, inverted = true) }
                    item { PersonaButton("Download", onClick = { download(songs) }, inverted = true) }
                }
            }
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                SongRow(song, onClick = { container.player.play(songs, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (saving) SavePlaylistDialog(songs, playlist.title, onDismiss = { saving = false })
}
