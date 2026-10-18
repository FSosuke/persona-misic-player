package com.fsosuke.personamusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.ui.components.AddToPlaylistDialog
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.shareSong
import com.fsosuke.personamusic.ui.components.starPattern
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(onClose: () -> Unit, onOpenStats: () -> Unit, onOpenCustomize: () -> Unit) {
    val container = LocalAppContainer.current
    val player = container.player
    val colors = LocalPersonaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsStateWithLifecycle()
    val position by player.positionMs.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }

    val song = state.current
    val liked by remember(song?.id) {
        container.repository.isLiked(song?.id ?: "")
    }.collectAsStateWithLifecycle(initialValue = false)

    Box(
        Modifier
            .fillMaxSize()
            .starPattern(colors.background, colors.pattern.copy(alpha = 0.5f), cell = 160f),
    ) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        RansomText("Now playing", fontSize = 22.sp)
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Star, "Menu", tint = colors.accent, modifier = Modifier.size(34.dp))
                    }
                }
            }

            if (song == null) {
                item { EmptyState("Silence…", "Nothing is playing. Pick a song to start the heist.") }
                return@LazyColumn
            }

            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .rotate(-4f)
                            .offset(10.dp, 10.dp)
                            .background(colors.accent),
                    )
                    Artwork(
                        song.artworkUri,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .rotate(-4f)
                            .border(6.dp, Color.White)
                            .padding(6.dp),
                    )
                    if (song.source == SongSource.YOUTUBE) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .offset((-12).dp, (-8).dp)
                                .rotate(-12f)
                                .background(Color.Black)
                                .border(2.dp, Color.White)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("YT MUSIC · NO ADS", color = Color.White, style = PersonaType.label.copy(fontSize = 12.sp))
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, style = PersonaType.title.copy(fontSize = 24.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = colors.muted, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { scope.launch { container.repository.setLiked(song, !liked) } }) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Favourite",
                            tint = if (liked) colors.accent else Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            item {
                PersonaSeekBar(
                    positionMs = position,
                    durationMs = state.durationMs,
                    onSeek = player::seekTo,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = player::toggleShuffle) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) colors.accent else Color(0xFF888888))
                    }
                    IconButton(onClick = player::previous) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp).rotate(-8f))
                    }
                    Box(
                        Modifier
                            .size(78.dp)
                            .rotate(-8f)
                            .background(Color.White)
                            .padding(4.dp)
                            .background(colors.accent)
                            .clickable(onClick = player::togglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/Pause",
                            tint = colors.onAccent,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    IconButton(onClick = player::next) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp).rotate(-8f))
                    }
                    IconButton(onClick = player::cycleRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            "Repeat",
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF888888) else colors.accent,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Text(
                        "⚠ $error",
                        color = colors.accent,
                        style = PersonaType.caption,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.rotate(-3f).background(Color.White).padding(horizontal = 16.dp, vertical = 2.dp)) {
                        Text("QUEUE", color = Color.Black, style = PersonaType.label.copy(fontSize = 20.sp))
                    }
                }
            }
            itemsIndexed(state.queue) { index, queued ->
                val isCurrent = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { player.skipToQueueItem(index) }
                        .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d".format(index + 1),
                        color = if (isCurrent) colors.accent else Color(0xFF777777),
                        style = PersonaType.label,
                        modifier = Modifier.width(36.dp),
                    )
                    Artwork(queued.artworkUri, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(queued.title, color = if (isCurrent) colors.accent else Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(queued.artist, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { player.removeQueueItem(index) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = Color(0xFF777777))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }

        StarMenu(
            visible = menuOpen,
            onDismiss = { menuOpen = false },
            entries = listOf(
                "Playlist" to { menuOpen = false; addToPlaylist = song != null },
                "Stats" to { menuOpen = false; onOpenStats() },
                "Customize" to { menuOpen = false; onOpenCustomize() },
                "Share" to { menuOpen = false; song?.let { shareSong(context, it) }; Unit },
            ),
        )
    }

    if (addToPlaylist && song != null) {
        AddToPlaylistDialog(song, onDismiss = { addToPlaylist = false })
    }
}

/** Slanted progress track whose thumb is a little music note badge. */
@Composable
fun PersonaSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                widthPx = size.width
                val h = size.height
                val w = size.width
                val track = Path().apply {
                    moveTo(0f, h * 0.45f); lineTo(w, h * 0.3f); lineTo(w - 12f, h * 0.7f); lineTo(0f, h * 0.7f); close()
                }
                drawPath(track, Color.Black)
                drawPath(track, Color.White, style = Stroke(width = 3f))
                val fx = w * fraction
                val fill = Path().apply {
                    moveTo(0f, h * 0.45f); lineTo(fx, h * 0.45f - (h * 0.15f) * fraction); lineTo(fx, h * 0.7f); lineTo(0f, h * 0.7f); close()
                }
                drawPath(fill, colors.accent)
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationX = fraction * widthPx - 18.dp.toPx() }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(3.dp, colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val shown = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
            Text(formatDuration(shown), color = colors.muted, style = PersonaType.caption)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs), color = colors.muted, style = PersonaType.caption)
        }
    }
}
