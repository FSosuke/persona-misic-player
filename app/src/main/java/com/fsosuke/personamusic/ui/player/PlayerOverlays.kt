package com.fsosuke.personamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Lyrics
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.ArtistText
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.Chunky
import com.fsosuke.personamusic.ui.components.ChunkyIcon
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.glovesHoldingCard
import com.fsosuke.personamusic.ui.components.rippedSides
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import java.text.DateFormat
import java.util.Date

private sealed interface LyricsState {
    data object Loading : LyricsState
    data object Missing : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

/** Lyrics on a calling card held up by white gloves; the sung line sits on a red slash. */
@Composable
fun LyricsOverlay(visible: Boolean, song: Song, positionMs: Long, onSeek: (Long) -> Unit, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    AnimatedVisibility(visible, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.9f), exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.95f)) {
        val state by produceState<LyricsState>(LyricsState.Loading, song.id) {
            value = LyricsState.Loading
            value = container.lyrics.lyricsFor(song)?.let { LyricsState.Found(it) } ?: LyricsState.Missing
        }
        Box(
            Modifier
                .fillMaxSize()
                .glovesHoldingCard()
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.8f)
                    .rotate(-1.2f)
                    .background(Color.White)
                    .padding(9.dp)
                    .background(Color(0xFF0E0E0E)),
            ) {
                when (val s = state) {
                    LyricsState.Loading -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                    LyricsState.Missing -> Text(
                        "No lyrics found for\n\"${song.title}\"",
                        color = Color(0xFFAAAAAA),
                        style = PersonaType.body,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    is LyricsState.Found -> LyricsList(s.lyrics, positionMs, onSeek)
                }
                ChunkyIcon(Chunky.Lyrics, Modifier.align(Alignment.TopStart).padding(10.dp), size = 34.dp)
                ChunkyIcon(
                    Chunky.Close,
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    size = 38.dp,
                    fill = colors.accent,
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val colors = LocalPersonaColors.current
    val listState = rememberLazyListState()
    val current = if (lyrics.synced) lyrics.indexAt(positionMs) else -1
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 4).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == current
            val text = line.text.ifBlank { "♪" }
            val modifier = Modifier.clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
            if (isCurrent) {
                Box(
                    modifier
                        .rotate(-1.5f)
                        .background(colors.accent, slantShape(0.06f))
                        .padding(horizontal = 26.dp, vertical = 4.dp),
                ) {
                    Text(text, color = colors.onAccent, style = PersonaType.title.copy(fontSize = 19.sp), textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    text,
                    color = if (current >= 0 && index < current) Color(0xFF6E6E6E) else Color(0xFFB5B5B5),
                    style = PersonaType.body.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center,
                    modifier = modifier,
                )
            }
        }
    }
}

/** Per-song stats on a ripped black page. */
@Composable
fun SongStatsOverlay(visible: Boolean, song: Song, onClose: () -> Unit, onAllStats: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        val stats by remember(song.id) { container.repository.songStats(song.id) }.collectAsState(initial = null)
        Box(
            Modifier
                .fillMaxSize()
                .rippedSides()
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("STATS", color = Color.White, style = TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = 34.sp, letterSpacing = 4.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(song.artworkUri, Modifier.size(84.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(song.title, color = Color.White, style = PersonaType.title.copy(fontSize = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ArtistText(song.artist, color = Color(0xFFBBBBBB), style = PersonaType.body)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val s = stats
                val plays = s?.plays ?: 0
                val listened = s?.listenedMs ?: 0L
                StatLine("Times played", "$plays ${if (plays == 1) "time" else "times"}")
                StatLine("Time listened", formatListened(listened))
                StatLine("Average per play", if (plays > 0) formatListened(listened / plays) else "–")
                StatLine("Track length", formatDuration(song.durationMs))
                s?.firstPlayed?.let { StatLine("First played", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "ALL STATS",
                        color = Color.White,
                        style = PersonaType.label.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable(onClick = onAllStats).padding(8.dp),
                    )
                    Text(
                        "CLOSE",
                        color = Color(0xFFFFB3AE),
                        style = PersonaType.label.copy(fontSize = 20.sp),
                        modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFC8C8C8), style = PersonaType.title.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        Text(value, color = Color.White, style = PersonaType.title.copy(fontSize = 18.sp))
    }
}

private fun formatListened(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        ms <= 0 -> "0 min"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes >= 1 -> "$minutes min"
        else -> "${ms / 1000} s"
    }
}
