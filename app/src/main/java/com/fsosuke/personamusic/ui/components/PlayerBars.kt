package com.fsosuke.personamusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.playback.PlayerConnection
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

/**
 * Mini player: the red slash on top doubles as the progress bar, like the screenshots.
 */
@Composable
fun MiniPlayer(
    state: PlayerConnection.State,
    positionMs: Long,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val colors = LocalPersonaColors.current
    val progress = if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier.fillMaxWidth().background(colors.background)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val w = size.width
            val h = size.height
            drawPath(Path().apply { moveTo(0f, h * 0.2f); lineTo(w, 0f); lineTo(w, h * 0.45f); lineTo(0f, h * 0.65f); close() }, Color.White)
            drawPath(
                Path().apply {
                    moveTo(0f, h * 0.4f); lineTo(w * progress, h * 0.4f - h * 0.2f * progress)
                    lineTo(w * progress, h); lineTo(0f, h); close()
                },
                colors.accent,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artworkUri, Modifier.size(52.dp).border(2.dp, colors.accent))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, style = PersonaType.title.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ChunkyIcon(if (state.isPlaying) Chunky.Pause else Chunky.Play, size = 36.dp, onClick = onTogglePlay)
            Spacer(Modifier.width(18.dp))
            ChunkyIcon(Chunky.Next, size = 34.dp, onClick = onNext)
        }
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Color.Black, size = size.copy(height = h * 0.3f))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.45f, h * 0.55f); lineTo(0f, h * 0.55f); close() }, colors.accent)
            drawPath(Path().apply { moveTo(0f, h * 0.55f); lineTo(w, h * 0.2f); lineTo(w, h * 0.55f); lineTo(0f, h); close() }, Color.White)
        }
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Library("library", "Library", Icons.Filled.LibraryMusic),
    Playlists("playlists", "Playlist", Icons.Filled.Folder),
    Radio("radio", "Radio", Icons.Filled.Radio),
}

@Composable
fun PersonaBottomBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    Row(
        modifier
            .fillMaxWidth()
            .starPattern(colors.patternBackground, colors.pattern, cell = 110f)
            .navigationBarsPadding()
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            val scale by animateFloatAsState(if (isSelected) 1.2f else 1f, label = "tabScale")
            Box(
                Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Canvas(Modifier.size(width = 96.dp, height = 70.dp)) {
                        val w = size.width
                        val h = size.height
                        drawPath(Path().apply { moveTo(w * 0.05f, h * 0.35f); lineTo(w * 0.85f, h * 0.05f); lineTo(w * 0.7f, h * 0.95f); close() }, colors.highlight)
                        drawPath(Path().apply { moveTo(w * 0.2f, h * 0.2f); lineTo(w, h * 0.45f); lineTo(w * 0.35f, h); close() }, colors.accent)
                    }
                }
                Column(
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = if (isSelected) -6f else 0f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = Color.White, modifier = Modifier.size(28.dp))
                    Box(Modifier.background(Color.Black).padding(horizontal = 4.dp)) {
                        Text(tab.label.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 12.sp))
                    }
                    JapaneseSubtitles.of(tab.label)?.let {
                        Text(it, color = Color.White.copy(alpha = 0.75f), style = PersonaType.caption.copy(fontSize = 8.sp))
                    }
                }
            }
        }
    }
}
