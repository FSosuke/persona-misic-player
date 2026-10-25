package com.fsosuke.personamusic.ui.player

import com.fsosuke.personamusic.playback.isRadio
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.ui.components.AddToPlaylistDialog
import com.fsosuke.personamusic.ui.components.ArtistText
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.Chunky
import com.fsosuke.personamusic.ui.components.ChunkyIcon
import com.fsosuke.personamusic.ui.components.DuotoneImage
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.Graffiti
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.Sparkle
import com.fsosuke.personamusic.ui.components.drawGrinningNote
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.phantomSlashes
import com.fsosuke.personamusic.ui.components.shareSong
import com.fsosuke.personamusic.ui.components.startRadio
import com.fsosuke.personamusic.ui.components.rememberDownloadAction
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.components.tornBand
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.launch

private enum class PlayerOverlay { Menu, Lyrics, Stats, Sleep }

@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCustomize: () -> Unit,
) {
    val container = LocalAppContainer.current
    val player = container.player
    val colors = LocalPersonaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsStateWithLifecycle()
    val position by player.positionMs.collectAsStateWithLifecycle()
    val background by container.settings.playerBackground.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf<PlayerOverlay?>(null) }
    var addToPlaylist by remember { mutableStateOf(false) }
    val closeThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }

    val song = state.current
    val download = rememberDownloadAction()
    val savedDownloads by container.downloads.downloaded.collectAsStateWithLifecycle()
    val isDownloaded = song != null && song.id in savedDownloads
    val radioFinds by container.radioFinds.saved.collectAsStateWithLifecycle()
    val liked by remember(song?.id) { container.repository.isLiked(song?.id ?: "") }
        .collectAsStateWithLifecycle(initialValue = false)

    Box(
        Modifier
            .fillMaxSize()
            .phantomSlashes(colors)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (overlay == null && dragged > closeThreshold) onClose(); dragged = 0f },
                    onDragCancel = { dragged = 0f },
                ) { _, dy -> dragged += dy }
            },
    ) {
        background?.let { DuotoneImage(it, tint = colors.accent, modifier = Modifier.fillMaxSize(), alpha = 0.55f) }
        Sparkle(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = 90.dp).size(120.dp))
        Sparkle(Modifier.align(Alignment.TopStart).offset(x = 30.dp, y = 40.dp).size(70.dp))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Grab handle + menu.
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 56.dp, height = 6.dp)
                        .background(Color(0xFF9A9A9A), RoundedCornerShape(3.dp))
                        .clickable(onClick = onClose),
                )
                SleepTimerBadge(
                    onClick = { overlay = PlayerOverlay.Sleep },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
                IconButton(onClick = { overlay = PlayerOverlay.Menu }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Star, "Menu", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            if (song == null) {
                EmptyState("Silence…", "Nothing is playing. Pick a song to start the heist.")
                return@Column
            }

            // Artwork with graffiti.
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight(0.92f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                    Box(Modifier.fillMaxSize().rotate(-3f).offset(8.dp, 10.dp).background(Color.Black))
                    Artwork(
                        song.artworkUri,
                        Modifier
                            .fillMaxSize()
                            .rotate(-3f)
                            .background(Color.White)
                            .border(10.dp, Color.White)
                            .padding(10.dp),
                    )
                    if (song.source == SongSource.YOUTUBE) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .offset((-6).dp, 6.dp)
                                .rotate(-8f)
                                .background(Color.Black)
                                .border(2.dp, Color.White)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("NO ADS", color = Color.White, style = PersonaType.label.copy(fontSize = 12.sp))
                        }
                    }
                }
                Graffiti("CHANGE", Modifier.align(Alignment.TopStart).offset(x = (-16).dp, y = (-6).dp), fontSize = 34.sp)
            }

            // Title card.
            Row(
                Modifier
                    .padding(start = 28.dp, end = 20.dp, top = 6.dp)
                    .fillMaxWidth()
                    .rotate(-1f)
                    .background(Color.White, slantShape(0.015f))
                    .padding(4.dp)
                    .background(Color.Black, slantShape(0.015f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, style = PersonaType.title.copy(fontSize = 21.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ArtistText(song.artist, color = Color(0xFFBDBDBD), style = PersonaType.body)
                }
                if (song.isRadio) {
                    // On the radio the heart saves the song on air to "Radio finds".
                    val stationName = song.album.orEmpty()
                    val onAir = song.title != stationName
                    val artist = song.artist.removeSuffix(" · $stationName")
                    val saved = onAir && container.radioFinds.key(song.title, artist) in radioFinds
                    if (onAir) {
                        ChunkyIcon(
                            if (saved) Chunky.HeartFilled else Chunky.Heart,
                            size = 30.dp,
                            fill = if (saved) colors.accent else Color.White,
                            onClick = {
                                if (!saved) scope.launch {
                                    val found = runCatching { container.radioFinds.save(song.title, artist) }.getOrNull()
                                    Toast.makeText(
                                        context,
                                        if (found != null) "Saved \"${found.title}\" to Radio finds" else "Couldn't find it on YouTube Music",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                } else {
                    ChunkyIcon(
                        if (liked) Chunky.HeartFilled else Chunky.Heart,
                        size = 30.dp,
                        fill = if (liked) colors.accent else Color.White,
                        onClick = { scope.launch { container.setLiked(song, !liked) } },
                    )
                }
                Spacer(Modifier.width(10.dp))
                ChunkyIcon(Chunky.Lyrics, size = 34.dp, onClick = { overlay = PlayerOverlay.Lyrics })
            }

            // Torn grey band with seek bar and controls.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .tornBand()
                    .padding(top = 26.dp, bottom = 30.dp),
            ) {
                PersonaSeekBar(
                    positionMs = position,
                    durationMs = state.durationMs,
                    onSeek = player::seekTo,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(72.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(colors.accent, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.45f), strokeWidth = 10f)
                    }
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModeButton(
                            label = if (state.shuffle) "ON" else "OFF",
                            active = state.shuffle,
                        ) {
                            ChunkyIcon(
                                Chunky.Shuffle,
                                size = 40.dp,
                                fill = if (state.shuffle) colors.accent else Color(0xFFCFCFCF),
                                onClick = player::toggleShuffle,
                            )
                        }
                        ChunkyIcon(Chunky.Previous, size = 46.dp, onClick = player::previous)
                        ChunkyIcon(if (state.isPlaying) Chunky.Pause else Chunky.Play, size = 64.dp, onClick = player::togglePlay)
                        ChunkyIcon(Chunky.Next, size = 46.dp, onClick = player::next)
                        ModeButton(
                            label = when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> "ONE"
                                Player.REPEAT_MODE_ALL -> "ALL"
                                else -> "OFF"
                            },
                            active = state.repeatMode != Player.REPEAT_MODE_OFF,
                        ) {
                            ChunkyIcon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Chunky.RepeatOne else Chunky.Repeat,
                                size = 40.dp,
                                fill = when (state.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Color(0xFFCFCFCF)
                                    Player.REPEAT_MODE_ALL -> colors.accent
                                    else -> colors.highlight
                                },
                                onClick = player::cycleRepeat,
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Text("⚠ $it", color = colors.accent, style = PersonaType.caption, modifier = Modifier.padding(horizontal = 24.dp))
            }

            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
                    .clickable(onClick = onOpenQueue)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChunkyIcon(Chunky.Queue, size = 24.dp)
                Spacer(Modifier.width(10.dp))
                Text("QUEUE", color = Color.White, style = PersonaType.label.copy(fontSize = 18.sp))
            }
        }

        StarMenu(
            visible = overlay == PlayerOverlay.Menu,
            onDismiss = { overlay = null },
            entries = listOf(
                "Playlist" to { overlay = null; addToPlaylist = song != null },
                "Radio" to { overlay = null; song?.let { startRadio(container, context, it) }; Unit },
                "Sleep" to { overlay = PlayerOverlay.Sleep },
                (if (isDownloaded) "Delete file" else "Download") to {
                    overlay = null
                    song?.let {
                        if (isDownloaded) container.appScope.launch { container.downloads.remove(it.id) } else download(listOf(it))
                    }
                    Unit
                },
                "Stats" to { overlay = if (song != null) PlayerOverlay.Stats else null },
                "Customize" to { overlay = null; onOpenCustomize() },
                "Share" to { overlay = null; song?.let { shareSong(context, it) }; Unit },
            ),
        )
        SleepTimerOverlay(visible = overlay == PlayerOverlay.Sleep, onDismiss = { overlay = null })
        if (song != null) {
            LyricsOverlay(
                visible = overlay == PlayerOverlay.Lyrics,
                song = song,
                positionMs = position,
                onSeek = player::seekTo,
                onClose = { overlay = null },
            )
            SongStatsOverlay(
                visible = overlay == PlayerOverlay.Stats,
                song = song,
                onClose = { overlay = null },
                onAllStats = { overlay = null; onOpenStats() },
            )
        }
    }

    if (addToPlaylist && song != null) {
        AddToPlaylistDialog(song, onDismiss = { addToPlaylist = false })
    }
}

/** A mode toggle (shuffle / repeat) with its current state spelled out underneath. */
@Composable
private fun ModeButton(label: String, active: Boolean, icon: @Composable () -> Unit) {
    val colors = LocalPersonaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Box(
            Modifier
                .offset(y = (-2).dp)
                .rotate(-6f)
                .background(if (active) colors.accent else Color.Black)
                .padding(horizontal = 5.dp),
        ) {
            Text(label, color = if (active) colors.onAccent else Color(0xFFBDBDBD), style = PersonaType.label.copy(fontSize = 11.sp))
        }
    }
}

/** Slanted red/black track with the grinning-note thumb, times on either side. */
@Composable
fun PersonaSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
    val timeStyle = PersonaType.title.copy(fontSize = 17.sp, fontFamily = PersonaFonts.rubik)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(shown), color = Color.White, style = timeStyle, modifier = Modifier.width(52.dp))
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
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
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val top = h * 0.36f
                val bottom = h * 0.66f
                val track = Path().apply {
                    moveTo(0f, top); lineTo(w, top); lineTo(w - 18f, bottom); lineTo(14f, bottom); close()
                }
                drawPath(track, Color.Black)
                val fx = (w - 18f) * fraction + 9f
                val fill = Path().apply {
                    moveTo(4f, top + 4f); lineTo(fx, top + 4f); lineTo(fx, bottom - 4f); lineTo(16f, bottom - 4f); close()
                }
                drawPath(fill, colors.accent)
                drawPath(track, Color.White, style = Stroke(5f))
                drawGrinningNote(Offset(fx, h * 0.48f), h * 0.42f, colors.accent)
            }
        }
        if (durationMs <= 0 && positionMs > 0) {
            // Live stations have no end.
            Text("● LIVE", color = colors.accent, style = timeStyle.copy(fontSize = 14.sp), modifier = Modifier.padding(start = 8.dp).width(52.dp))
        } else {
            Text(formatDuration(durationMs), color = Color.White, style = timeStyle, modifier = Modifier.padding(start = 8.dp).width(52.dp))
        }
    }
}
