package com.fsosuke.personamusic.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.MenuEntry
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(playlistId: Long, onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val isFavourites = playlistId == FAVOURITES_ID

    val name by remember(playlistId) {
        if (isFavourites) flowOf("Favourites") else repo.playlist(playlistId).map { it?.name ?: "" }
    }.collectAsStateWithLifecycle(initialValue = "")
    val songs by remember(playlistId) {
        if (isFavourites) repo.likedSongs else repo.playlistSongs(playlistId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by container.player.state.collectAsStateWithLifecycle()

    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    fun play(index: Int, shuffle: Boolean = false) = container.player.play(songs, index, shuffle)

    fun remove(song: Song) {
        scope.launch {
            if (isFavourites) container.setLiked(song, false) else repo.removeFromPlaylist(playlistId, song.id)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader(
                    title = name.ifEmpty { " " },
                    action = {
                        if (!isFavourites) {
                            Row {
                                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Rename", tint = Color.White) }
                                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                            }
                        }
                    },
                )
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState("Empty", "Add songs from any song's ⋮ menu.") }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonaButton("Play", onClick = { play(0) })
                    PersonaButton("Shuffle", onClick = { play(0, shuffle = true) }, inverted = true)
                    Spacer(Modifier.weight(1f))
                    Text("${songs.size} songs", color = LocalPersonaColors.current.muted, style = PersonaType.caption)
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song,
                    onClick = { play(index) },
                    showArtwork = true,
                    isCurrent = song.id == playerState.current?.id,
                    extraMenu = { dismiss ->
                        MenuEntry("Remove from playlist", Icons.Filled.RemoveCircleOutline) {
                            dismiss()
                            remove(song)
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (renaming) {
        var newName by rememberSaveable { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Color.Black,
            shape = RectangleShape,
            title = { Text("Rename playlist", style = PersonaType.title, color = Color.White) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) scope.launch { repo.renamePlaylist(playlistId, newName.trim()) }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Color.Black,
            shape = RectangleShape,
            title = { Text("Delete \"$name\"?", style = PersonaType.title, color = Color.White) },
            text = { Text("The songs stay in your library.", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    // App scope: this screen leaves composition right away.
                    container.appScope.launch { repo.deletePlaylist(playlistId) }
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
