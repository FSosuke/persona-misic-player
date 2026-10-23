package com.fsosuke.personamusic.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fsosuke.personamusic.AppContainer
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.isRealArtist
import com.fsosuke.personamusic.data.primaryArtist
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Switch
import com.fsosuke.personamusic.youtube.YtPlaylist

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
fun Artwork(uri: String?, modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    val colors = LocalPersonaColors.current
    Box(modifier.clip(shape).background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.pattern, modifier = Modifier.fillMaxSize(0.5f))
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Plain list row as on the search screen: title, artist, duration, overflow menu. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = false,
    isCurrent: Boolean = false,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val colors = LocalPersonaColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(song.artworkUri, Modifier.size(48.dp).border(2.dp, if (isCurrent) colors.accent else Color.White))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) colors.accent else Color(0xFFF2E9E9),
                style = PersonaType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistText(song.artist, color = colors.muted, style = PersonaType.caption)
        }
        DownloadBadge(song, Modifier.padding(end = 8.dp))
        Text(formatDuration(song.durationMs), color = colors.muted, style = PersonaType.caption)
        SongMenuButton(song, extraMenu = extraMenu)
    }
}

/** Song actions: queue, radio, favourite, playlist, download, artist, share. */
@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addToPlaylist by remember { mutableStateOf(false) }
    val liked by remember(song.id) { container.repository.isLiked(song.id) }.collectAsState(initial = false)
    val openArtist = LocalOpenArtist.current
    val download = rememberDownloadAction()
    val savedDownloads by container.downloads.downloaded.collectAsState()

    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            containerColor = Color.Black,
            modifier = Modifier.border(2.dp, Color.White),
        ) {
            MenuEntry("Play next", Icons.Filled.SkipNext) {
                onDismiss()
                scope.launch { container.repository.remember(listOf(song)) }
                container.player.playNext(song)
            }
            MenuEntry("Start radio", Icons.Filled.Radio) {
                onDismiss()
                startRadio(container, context, song)
            }
            MenuEntry("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) {
                onDismiss()
                scope.launch { container.repository.remember(listOf(song)) }
                container.player.addToQueue(song)
            }
            MenuEntry(if (liked) "Remove from favourites" else "Add to favourites", if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                onDismiss()
                scope.launch { container.setLiked(song, !liked) }
            }
            MenuEntry("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                onDismiss()
                addToPlaylist = true
            }
            if (song.source == SongSource.YOUTUBE) {
                if (song.id in savedDownloads) {
                    MenuEntry("Remove download", Icons.Filled.DeleteOutline) {
                        onDismiss()
                        container.appScope.launch { container.downloads.remove(song.id) }
                    }
                } else {
                    MenuEntry("Download", Icons.Filled.Download) {
                        onDismiss()
                        download(listOf(song))
                    }
                }
            }
            if (isRealArtist(song.artist)) {
                MenuEntry("Go to artist", Icons.Filled.Person) {
                    onDismiss()
                    openArtist(primaryArtist(song.artist), null)
                }
            }
            if (song.source == SongSource.YOUTUBE) {
                MenuEntry("Share", Icons.Filled.Share) {
                    onDismiss()
                    shareSong(context, song)
                }
            }
            extraMenu?.invoke(onDismiss)
        }
    }
    if (addToPlaylist) {
        AddToPlaylistDialog(song, onDismiss = { addToPlaylist = false })
    }
}

/**
 * Makes a song card tappable (play) and long-pressable (song menu with download, radio, …).
 * [content] must apply the given modifier to its clickable root.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithSongMenu(song: Song, onClick: () -> Unit, content: @Composable (Modifier) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        content(Modifier.combinedClickable(onClick = onClick, onLongClick = { expanded = true }))
        SongMenu(song, expanded, onDismiss = { expanded = false })
    }
}

/** Overflow (⋮) button opening the [SongMenu]. */
@Composable
fun SongMenuButton(
    song: Song,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF9A9A9A),
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = tint)
        }
        SongMenu(song, expanded, onDismiss = { expanded = false }, extraMenu = extraMenu)
    }
}

@Composable
fun MenuEntry(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Color.White, style = PersonaType.body) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = LocalPersonaColors.current.accent) },
        onClick = onClick,
    )
}

/** Replaces the queue with [song] followed by a YouTube Music radio of similar songs. */
fun startRadio(container: AppContainer, context: Context, song: Song) {
    Toast.makeText(context, "Tuning in to \"${song.title}\" radio…", Toast.LENGTH_SHORT).show()
    container.appScope.launch {
        val songs = runCatching { container.ytLibrary.radio(song) }.getOrDefault(emptyList())
        container.repository.remember(songs + song)
        withContext(Dispatchers.Main) {
            if (songs.isEmpty()) {
                Toast.makeText(context, "No radio found for this song", Toast.LENGTH_SHORT).show()
            } else {
                container.player.play(listOf(song) + songs)
            }
        }
    }
}

fun shareSong(context: Context, song: Song) {
    val text = if (song.source == SongSource.YOUTUBE) "${song.title} – ${song.artist}\n${song.uri}" else "${song.title} – ${song.artist}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.repository.playlists.collectAsState(initial = emptyList())
    val ytAccount by container.ytAccounts.account.collectAsState()
    val ytFeed by container.ytLibrary.feed.collectAsState()
    val ytPlaylists = remember(ytFeed) { container.ytLibrary.editablePlaylists() }
    var creating by rememberSaveable { mutableStateOf(false) }
    var onYouTube by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }

    fun addToYt(playlist: YtPlaylist) {
        onDismiss()
        container.appScope.launch {
            val result = runCatching { container.ytLibrary.addToPlaylist(playlist, song) }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    result.fold({ "Added to \"${playlist.title}\" on YouTube Music" }, { it.localizedMessage ?: "Failed" }),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        modifier = Modifier.border(3.dp, Color.White),
        shape = RectangleShape,
        title = { RansomText("Add to playlist", fontSize = 22.sp) },
        text = {
            if (creating) {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    if (ytAccount != null) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Create on YouTube Music", color = Color.White, style = PersonaType.body, modifier = Modifier.weight(1f))
                            Switch(checked = onYouTube, onCheckedChange = { onYouTube = it })
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(playlists, key = { "local-${it.id}" }) { playlist ->
                        DialogRow(playlist.name, badge = null) {
                            scope.launch { container.repository.addToPlaylist(playlist.id, song) }
                            onDismiss()
                        }
                    }
                    if (ytPlaylists.isNotEmpty()) {
                        item(key = "yt-header") {
                            Text(
                                "YOUTUBE MUSIC",
                                color = colors.accent,
                                style = PersonaType.label,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(ytPlaylists, key = { "yt-${it.id}" }) { playlist ->
                            DialogRow(playlist.title, badge = "YT") { addToYt(playlist) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        if (onYouTube) {
                            container.appScope.launch {
                                val result = runCatching { container.ytLibrary.createPlaylist(trimmed, song) }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        result.fold({ "Created \"$trimmed\" on YouTube Music" }, { it.localizedMessage ?: "Failed" }),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        } else {
                            scope.launch {
                                val id = container.repository.createPlaylist(trimmed)
                                container.repository.addToPlaylist(id, song)
                            }
                        }
                        onDismiss()
                    }
                }) { Text("Create") }
            } else {
                TextButton(onClick = { creating = true }) { Text("New playlist") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DialogRow(title: String, badge: String?, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Color.White, style = PersonaType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (badge != null) {
                Box(Modifier.background(Color.White).padding(horizontal = 5.dp)) {
                    Text(badge, color = Color.Black, style = PersonaType.label.copy(fontSize = 12.sp))
                }
            }
        }
        HorizontalDivider(color = Color(0xFF333333))
    }
}

/** The highlighted red-framed card from the search screen. */
@Composable
fun TopResultCard(song: Song, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val liked by remember(song.id) { container.repository.isLiked(song.id) }.collectAsState(initial = false)

    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier
                .rotate(-1f)
                .fillMaxWidth()
                .background(colors.accent, slantShape(0.02f))
                .padding(3.dp)
                .background(Color(0xFF141414), slantShape(0.02f))
                .clickable(onClick = onPlay)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artworkUri, Modifier.size(84.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = colors.accent, style = PersonaType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(6.dp))
                ArtistText(song.artist, color = colors.muted, style = PersonaType.body)
            }
            SongMenuButton(song, tint = Color(0xFFBBBBBB))
            IconButton(onClick = { scope.launch { container.setLiked(song, !liked) } }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (liked) colors.accent else Color(0xFFBBBBBB),
                )
            }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(colors.accent).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = colors.onAccent, modifier = Modifier.size(36.dp))
            }
        }
    }
}
