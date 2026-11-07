package com.fsosuke.personamusic.ui.library

import androidx.compose.foundation.layout.width
import com.fsosuke.personamusic.ui.theme.Lingo
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.TheaterComedy
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

private enum class LibraryTab(val label: String) {
    Recent("Recently played"), Favourites("Favorites"), MostPlayed("Most played"), Songs("Local songs"), Albums("Albums"),
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun LibraryScreen(contentPadding: PaddingValues, onOpenArtists: () -> Unit = {}, onOpenSaved: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalPersonaColors.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var scanning by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Recent) }
    val recent by remember { container.repository.recent(60) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mostPlayed by remember { container.repository.mostPlayed(60) }.collectAsStateWithLifecycle(initialValue = emptyList())
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
        LibraryTab.Recent -> recent
        LibraryTab.MostPlayed -> mostPlayed.map { it.song }
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
)
        }
        // The P5 menu board: tilted cards on red, white and black shards.
        item {
            LibraryBoard(
                selected = tab,
                onSelect = { tab = it },
                onOpenArtists = onOpenArtists,
                onOpenSaved = onOpenSaved,
            )
        }

        if (!hasPermission && (tab == LibraryTab.Songs || tab == LibraryTab.Albums)) {
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
            LibraryTab.Songs, LibraryTab.Favourites, LibraryTab.Recent, LibraryTab.MostPlayed -> {
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (scanning) "Infiltrating…" else "Nothing here",
                            message = when (tab) {
                                LibraryTab.Songs -> "No music files found on this device yet."
                                LibraryTab.Favourites -> "Tap ♥ on a song to add it to your favourites."
                                else -> "Play some music and it shows up here."
                            },
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


/**
 * Library menu board like the P5 main menu: ARTISTS, FAVORITES, SAVED, MOST PLAYED and
 * RECENTLY PLAYED as tilted cards with their own icons; local songs and albums as small tabs.
 */
@Composable
private fun LibraryBoard(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    val colors = LocalPersonaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(Color.Black)
                drawPath(Path().apply { moveTo(w * 0.55f, 0f); lineTo(w, 0f); lineTo(w, h * 0.55f); lineTo(w * 0.3f, h * 0.2f); close() }, colors.accent)
                drawPath(Path().apply { moveTo(0f, h * 0.08f); lineTo(w * 0.35f, h * 0.02f); lineTo(w * 0.2f, h * 0.5f); lineTo(0f, h * 0.6f); close() }, Color(colors.accent.red * 0.6f, colors.accent.green * 0.5f, colors.accent.blue * 0.5f))
                drawPath(Path().apply { moveTo(w * 0.3f, h * 0.45f); lineTo(w, h * 0.3f); lineTo(w, h * 0.8f); lineTo(w * 0.15f, h * 0.95f); close() }, Color(0xFFCFCFCF))
                drawPath(Path().apply { moveTo(0f, h * 0.7f); lineTo(w * 0.45f, h * 0.55f); lineTo(w * 0.6f, h); lineTo(0f, h); close() }, Color(0xFF1C1C1C))
                drawRect(Color.White, topLeft = Offset(0f, h - 6f), size = androidx.compose.ui.geometry.Size(w, 6f))
            },
    ) {
        BoardCard("Artists", Icons.Filled.TheaterComedy, false, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 96.dp), tilt = -3f, iconFirst = true, onClick = onOpenArtists)
        BoardCard("Favorites", Icons.Filled.FavoriteBorder, selected == LibraryTab.Favourites, Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 22.dp), tilt = 3f) { onSelect(LibraryTab.Favourites) }
        BoardCard("Saved", Icons.Filled.Download, false, Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 210.dp), tilt = 4f, onClick = onOpenSaved)
        BoardCard("Most played", Icons.Filled.Campaign, selected == LibraryTab.MostPlayed, Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 160.dp), tilt = -5f, iconFirst = true) { onSelect(LibraryTab.MostPlayed) }
        // RECENTLY / PLAYED: white strip over a black strip, like the game's menu.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
                .rotate(4f)
                .clickable { onSelect(LibraryTab.Recent) },
            horizontalAlignment = Alignment.Start,
        ) {
            val on = selected == LibraryTab.Recent
            Box(Modifier.background(if (on) colors.accent else Color.White).padding(horizontal = 26.dp, vertical = 4.dp)) {
                Text("RECENTLY", color = if (on) colors.onAccent else Color.Black, style = PersonaType.label.copy(fontSize = 26.sp))
            }
            Box(Modifier.offset(x = 120.dp).background(Color.Black).padding(horizontal = 26.dp, vertical = 4.dp)) {
                Text("PLAYED ★", color = Color.White, style = PersonaType.label.copy(fontSize = 22.sp))
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(LibraryTab.Songs, LibraryTab.Albums).forEach { t ->
                val on = t == selected
                Box(
                    Modifier
                        .background(if (on) colors.accent else Color.Black, slantShape(0.1f))
                        .border(2.dp, Color.White, slantShape(0.1f))
                        .clickable { onSelect(t) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(t.label.uppercase(), color = if (on) colors.onAccent else Color.White, style = PersonaType.label.copy(fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    tilt: Float,
    iconFirst: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalPersonaColors.current
    Row(
        modifier
            .rotate(tilt)
            .background(Color.White, slantShape(0.04f))
            .padding(3.dp)
            .background(if (selected) colors.accent else Color.Black, slantShape(0.04f))
            .border(1.dp, Color.Black, slantShape(0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = @Composable {
            Text(Lingo.of(label).uppercase(), color = if (selected) colors.onAccent else Color.White, style = PersonaType.label.copy(fontSize = 19.sp))
        }
        val image = @Composable {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp).rotate(-8f))
        }
        if (iconFirst) { image(); Spacer(Modifier.width(14.dp)); text() } else { text(); Spacer(Modifier.width(14.dp)); image() }
    }
}
