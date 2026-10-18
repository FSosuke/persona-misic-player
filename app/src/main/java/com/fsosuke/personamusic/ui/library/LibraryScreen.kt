package com.fsosuke.personamusic.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) { Songs("Songs"), Albums("Albums"), Favourites("Favourites") }

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalPersonaColors.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var scanning by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    val songs by container.repository.localSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val liked by container.repository.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by container.player.state.collectAsStateWithLifecycle()

    fun rescan() {
        scope.launch {
            scanning = true
            runCatching { container.repository.rescanLocal() }
            scanning = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) rescan()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) rescan()
    }

    fun play(list: List<Song>, index: Int, shuffle: Boolean = false) {
        scope.launch { container.repository.remember(list) }
        container.player.play(list, index, shuffle)
    }

    val visible = when (tab) {
        LibraryTab.Songs -> songs
        LibraryTab.Favourites -> liked
        LibraryTab.Albums -> emptyList()
    }
    val albums = remember(songs) { songs.groupBy { it.album ?: "Unknown album" }.toList().sortedBy { it.first.lowercase() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            PersonaHeader(
                title = "Library",
                action = {
                    if (hasPermission) {
                        IconButton(onClick = ::rescan, enabled = !scanning) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LibraryTab.entries.forEach { t ->
                        val selected = t == tab
                        Box(
                            Modifier
                                .rotate(if (selected) -5f else 0f)
                                .background(if (selected) colors.accent else Color.Black, slantShape(0.1f))
                                .clickable { tab = t }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                t.label.uppercase(),
                                color = if (selected) colors.onAccent else Color.White,
                                style = PersonaType.label.copy(fontSize = if (selected) 18.sp else 15.sp),
                            )
                        }
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                EmptyState(
                    title = "Access denied",
                    message = "Allow access to your music files so the Phantom Thieves can find your local songs.",
                ) {
                    PersonaButton("Grant access", onClick = { launcher.launch(audioPermission) })
                }
            }
            return@LazyColumn
        }

        if (tab != LibraryTab.Albums && visible.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonaButton("Play", onClick = { play(visible, 0) })
                    PersonaButton("Shuffle", onClick = { play(visible, 0, shuffle = true) }, inverted = true)
                    Spacer(Modifier.weight(1f))
                    Text("${visible.size} songs", color = colors.muted, style = PersonaType.caption)
                }
            }
        }

        when (tab) {
            LibraryTab.Songs, LibraryTab.Favourites -> {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (scanning) "Infiltrating…" else "Nothing here",
                            message = if (tab == LibraryTab.Songs) "No music files found on this device yet." else "Tap ♥ on a song to add it to your favourites.",
                        )
                    }
                }
                itemsIndexed(visible, key = { _, s -> s.id }) { index, song ->
                    SongRow(song, onClick = { play(visible, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
                }
            }
            LibraryTab.Albums -> {
                val rows = albums.chunked(2)
                itemsIndexed(rows, key = { _, row -> row.first().first }) { rowIndex, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEachIndexed { col, (album, tracks) ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { play(tracks, 0) },
                            ) {
                                Artwork(
                                    tracks.first().artworkUri,
                                    Modifier
                                        .rotate(if ((rowIndex + col) % 2 == 0) -2f else 2f)
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .border(3.dp, Color.White),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(album, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${tracks.first().artist} · ${tracks.size}",
                                    color = colors.muted,
                                    style = PersonaType.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

