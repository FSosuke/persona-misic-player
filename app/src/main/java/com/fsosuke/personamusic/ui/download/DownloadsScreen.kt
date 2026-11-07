package com.fsosuke.personamusic.ui.download

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.MenuEntry
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.launch

/** Everything saved for offline listening, plus what is still downloading. */
@Composable
fun DownloadsScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val downloads = container.downloads
    val colors = LocalPersonaColors.current
    val songs by downloads.downloadedSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by downloads.tasks.collectAsStateWithLifecycle()
    val folder by container.settings.downloadFolder.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()

    fun play(index: Int, shuffle: Boolean = false) = container.player.play(songs, index, shuffle)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Downloads")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        item {
            Text(
                if (folder == null) "No folder chosen yet – it is asked the first time you download."
                else "Saved in: ${downloads.folderLabel()}",
                color = colors.muted,
                style = PersonaType.caption,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        val active = tasks.values.toList()
        if (active.isNotEmpty()) {
            item { DashedSectionTitle("In progress") }
            items(active, key = { "task-${it.song.id}" }) { task -> TaskRow(task) }
        }

        if (songs.isEmpty() && active.isEmpty()) {
            item {
                EmptyState(
                    "Nothing saved",
                    "Use \"Download\" in a song's ⋮ menu or \"Save\" on a YouTube Music playlist to listen offline.",
                )
            }
        }
        if (songs.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonaButton("Play", onClick = { play(0) })
                    PersonaButton("Shuffle", onClick = { play(0, shuffle = true) }, inverted = true)
                    Spacer(Modifier.weight(1f))
                    Text("${songs.size} songs", color = colors.muted, style = PersonaType.caption)
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song,
                    onClick = { play(index) },
                    showArtwork = true,
                    isCurrent = song.id == playerState.current?.id,
                    extraMenu = { dismiss ->
                        MenuEntry("Delete file", Icons.Filled.DeleteOutline) {
                            dismiss()
                            container.appScope.launch { downloads.remove(song.id) }
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TaskRow(task: com.fsosuke.personamusic.download.DownloadManager.Task) {
    val downloads = LocalAppContainer.current.downloads
    val colors = LocalPersonaColors.current
    val song: Song = task.song
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkUri, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.error != null) {
                Text("Failed: ${task.error}", color = colors.accent, style = PersonaType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                val progress = task.progress
                if (progress == null) {
                    LinearProgressIndicator(color = colors.accent, trackColor = Color(0xFF333333), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = colors.accent,
                        trackColor = Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
        if (task.error != null) {
            IconButton(onClick = { downloads.enqueue(listOf(song)) }) { Icon(Icons.Filled.Refresh, "Retry", tint = Color.White) }
            IconButton(onClick = { downloads.dismissFailed(song.id) }) { Icon(Icons.Filled.Close, "Dismiss", tint = Color(0xFF888888)) }
        }
    }
}
