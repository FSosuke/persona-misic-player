package com.fsosuke.personamusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.SmartPlaylist
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.halftone
import com.fsosuke.personamusic.ui.components.jaggedCardShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.launch

/** Id used for the built-in favourites "playlist". */
const val FAVOURITES_ID = -1L

private val CardSize = 150.dp
private val RowHeight = 170.dp

/**
 * The red halftone board: playlist cards zig-zag left/right and are strung together by a thick
 * black line, like the confidant map in Persona 5.
 */
@Composable
fun PlaylistsScreen(
    onOpen: (Long) -> Unit,
    onOpenYt: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSmart: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.repository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val liked by container.repository.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by rememberSaveable { mutableStateOf(false) }

    val ytFeed by container.ytLibrary.feed.collectAsStateWithLifecycle()
    val downloaded by container.downloads.downloadedSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    val cards = buildList {
        add(BoardCard("fav", "Favourites", "${liked.size}", liked.firstOrNull { it.artworkUri != null }?.artworkUri, isFavourites = true) { onOpen(FAVOURITES_ID) })
        add(BoardCard("downloads", "Downloads", "${downloaded.size}", downloaded.firstOrNull { it.artworkUri != null }?.artworkUri, isDownloads = true) { onOpenDownloads() })
        SmartPlaylist.entries.forEach { smart ->
            add(BoardCard("smart-${smart.id}", smart.title, "AUTO", null, isSmart = true) { onOpenSmart(smart.id) })
        }
        playlists.forEach { p -> add(BoardCard("local-${p.id}", p.name, "${p.songCount}", p.coverUri) { onOpen(p.id) }) }
        ytFeed.playlists.forEach { p -> add(BoardCard("yt-${p.id}", p.title, null, p.thumbnail, isYouTube = true) { onOpenYt(p.id) }) }
    }

    Box(Modifier.fillMaxSize().halftone(colors.accent, Color.Black.copy(alpha = 0.18f))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                PersonaHeader(
                    title = "Playlist",
                    action = {
                        IconButton(onClick = { creating = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "New playlist", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    },
                )
            }
            itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                BoxWithConstraints(Modifier.fillMaxWidth().height(RowHeight)) {
                    val leftX = 24.dp + CardSize / 2
                    val rightX = maxWidth - 24.dp - CardSize / 2
                    val myX = if (index % 2 == 0) leftX else rightX
                    val nextX = if (index % 2 == 0) rightX else leftX
                    val hasNext = index < cards.lastIndex
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (hasNext) {
                                    drawLine(
                                        Color.Black,
                                        start = Offset(myX.toPx(), size.height / 2),
                                        end = Offset(nextX.toPx(), size.height * 1.5f),
                                        strokeWidth = 18.dp.toPx(),
                                    )
                                }
                            },
                    )
                    PlaylistCard(
                        card = card,
                        index = index,
                        modifier = Modifier
                            .align(if (index % 2 == 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 24.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = Color.Black,
            shape = RectangleShape,
            title = { RansomText("New playlist", fontSize = PersonaType.title.fontSize) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { container.repository.createPlaylist(name.trim()) }
                        creating = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

private class BoardCard(
    val key: String,
    val name: String,
    val badge: String?,
    val cover: String?,
    val isFavourites: Boolean = false,
    val isYouTube: Boolean = false,
    val isDownloads: Boolean = false,
    val isSmart: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun PlaylistCard(card: BoardCard, index: Int, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    val shape = jaggedCardShape(index + 7)
    Box(modifier.size(CardSize).rotate(if (index % 2 == 0) -3f else 2.5f).clickable(onClick = card.onClick)) {
        // Drop shadow in dark accent, offset like a cut-out pasted on the board.
        Box(Modifier.matchParentSize().offset(8.dp, 8.dp).background(Color(0xFF5A0008), shape))
        Box(Modifier.matchParentSize().background(Color.Black, shape).padding(10.dp)) {
            if (card.isSmart) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.Center).size(64.dp),
                )
            } else if (card.isDownloads && card.cover == null) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.Center).size(64.dp),
                )
            } else if (card.isFavourites && card.cover == null) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.Center).size(64.dp),
                )
            } else {
                Artwork(card.cover, Modifier.matchParentSize().padding(top = 18.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .background(Color.Black)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(card.name, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val badge = if (card.isYouTube) "YT" else card.badge
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .background(if (card.isYouTube) Color.White else colors.accent)
                    .padding(horizontal = 6.dp),
            ) {
                Text(badge, color = if (card.isYouTube) Color.Black else colors.onAccent, style = PersonaType.label)
            }
        }
    }
}
