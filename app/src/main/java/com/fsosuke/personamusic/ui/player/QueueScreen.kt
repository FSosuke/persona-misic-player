package com.fsosuke.personamusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.Chunky
import com.fsosuke.personamusic.ui.components.ChunkyIcon
import com.fsosuke.personamusic.ui.components.Graffiti
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PolygonShape
import com.fsosuke.personamusic.ui.components.SavePlaylistDialog
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.drawConnectorBolt
import com.fsosuke.personamusic.ui.components.redSwirls
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlin.math.roundToInt

private val RowHeight = 96.dp

/** "NEXT": the queue as cut-out cards hanging off a zig-zag spine on the red board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val player = container.player
    val colors = LocalPersonaColors.current
    val state by player.state.collectAsStateWithLifecycle()
    val likedIds by container.repository.likedIds.collectAsStateWithLifecycle(initialValue = emptyList())
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }

    val current = state.currentIndex
    val queue = state.queue

    Box(Modifier.fillMaxSize().redSwirls(colors).zigZagSpine()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item { QueueHeader(subtitle = "Your queue · ${queue.size} songs", onClose = onClose, onSave = { saving = true }) }

            if (current > 0 && current - 1 < queue.size) {
                item {
                    QueueCard(queue[current - 1], liked = queue[current - 1].id in likedIds, indent = 0, modifier = Modifier.alpha(0.45f)) {
                        player.skipToQueueItem(current - 1)
                    }
                }
            }
            if (current in queue.indices) {
                item { ShoutLabel("Playing", Modifier.padding(start = 28.dp, top = 18.dp, bottom = 4.dp)) }
                item {
                    QueueCard(queue[current], liked = queue[current].id in likedIds, indent = 0, highlighted = true) {}
                }
            }
            val upcoming = if (current + 1 < queue.size) queue.subList(current + 1, queue.size) else emptyList()
            if (upcoming.isNotEmpty()) {
                item { ShoutLabel("Next", Modifier.padding(start = 28.dp, top = 22.dp, bottom = 4.dp), tilt = 3f) }
            }
            itemsIndexed(upcoming, key = { i, song -> "${current + 1 + i}-${song.id}" }) { i, song ->
                val queueIndex = current + 1 + i
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) player.removeQueueItem(queueIndex)
                        value != SwipeToDismissBoxValue.Settled
                    },
                )
                val isDragged = dragging == queueIndex
                SwipeToDismissBox(
                    state = dismiss,
                    backgroundContent = {},
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffset
                                scaleX = 1.04f
                                scaleY = 1.04f
                            }
                        },
                ) {
                    QueueCard(
                        song,
                        liked = song.id in likedIds,
                        indent = (i % 3) * 14,
                        handle = Modifier.pointerInput(queueIndex) {
                            detectDragGestures(
                                onDragStart = { dragging = queueIndex; dragOffset = 0f },
                                onDragEnd = {
                                    val target = (queueIndex + (dragOffset / rowPx).roundToInt()).coerceIn(current + 1, queue.lastIndex)
                                    if (target != queueIndex) player.moveQueueItem(queueIndex, target)
                                    dragging = null
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragging = null; dragOffset = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            }
                        },
                    ) { player.skipToQueueItem(queueIndex) }
                }
            }
        }
    }
    if (saving) SavePlaylistDialog(queue, "Queue", onDismiss = { saving = false })
}

@Composable
private fun QueueHeader(subtitle: String, onClose: () -> Unit, onSave: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(230.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF0A0A0A))
            val white = Path().apply {
                moveTo(0f, h * 0.38f); lineTo(w * 0.62f, h * 0.48f); lineTo(w * 0.66f, h * 0.72f); lineTo(0f, h * 0.74f); close()
            }
            val black = Path().apply {
                moveTo(0f, h * 0.72f); lineTo(w, h * 0.68f); lineTo(w, h * 0.86f); lineTo(0f, h * 0.98f); close()
            }
            val stripe = Path().apply {
                moveTo(0f, h * 0.98f); lineTo(w, h * 0.86f); lineTo(w, h * 0.9f); lineTo(0f, h * 1.02f); close()
            }
            drawPath(white, Color.White)
            drawPath(black, Color.Black)
            drawPath(stripe, Color.White)
        }
        Graffiti(
            "CHANGE",
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 40.dp).alpha(0.25f),
            fontSize = 40.sp,
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 60.dp, end = 12.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        PersonaButton(
            "Save",
            onClick = onSave,
            inverted = true,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 40.dp),
        )
        Row(Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 92.dp).rotate(3f)) {
            val big = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 74.sp)
            Text("N", color = Color.Black, style = big)
            Box(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp).border(5.dp, Color.Black).padding(horizontal = 4.dp)) {
                Text("E", color = Color.Black, style = big.copy(fontSize = 60.sp))
            }
            Text("XT", color = Color.Black, style = big)
        }
        Text(
            subtitle,
            color = Color.White,
            style = PersonaType.title.copy(fontSize = 19.sp),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp).rotate(-2f),
        )
    }
}

@Composable
private fun QueueCard(
    song: Song,
    liked: Boolean,
    indent: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    handle: Modifier? = null,
    onClick: () -> Unit,
) {
    val colors = LocalPersonaColors.current
    val barShape = PolygonShape(listOf(Offset(0.04f, 0.08f), Offset(1f, 0f), Offset(0.95f, 1f), Offset(0f, 0.9f)))
    Box(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = onClick)
            .padding(start = (24 + indent).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).rotate(-6f)) {
                Box(Modifier.matchParentSize().offset(5.dp, 5.dp).background(Color.Black))
                Artwork(song.artworkUri, Modifier.matchParentSize().background(Color.White).padding(5.dp))
            }
            Canvas(Modifier.width(26.dp).height(40.dp)) {
                drawConnectorBolt(Offset(-6f, size.height / 2), size.width + 14f, size.height)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(Color.White, barShape)
                    .padding(4.dp)
                    .background(Color.Black, barShape)
                    .padding(start = 22.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = if (highlighted) colors.accent else Color.White,
                        style = PersonaType.title.copy(fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(song.artist, color = Color(0xFF9E9E9E), style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (handle != null) {
                    Text("═", color = Color(0xFFBDBDBD), fontSize = 22.sp, modifier = handle.padding(8.dp))
                }
            }
        }
        if (liked) {
            ChunkyIcon(
                Chunky.HeartFilled,
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).rotate(14f),
                size = 30.dp,
                fill = Color.Black,
                outline = Color.White,
            )
        }
    }
}

/** Thick black zig-zag running down the left edge, the "spine" the cards hang from. */
private fun Modifier.zigZagSpine(): Modifier = drawWithCache {
    val path = Path().apply {
        var y = 240.dp.toPx()
        var left = true
        moveTo(30.dp.toPx(), y)
        while (y < size.height) {
            y += 70.dp.toPx()
            lineTo(if (left) 70.dp.toPx() else 18.dp.toPx(), y)
            left = !left
        }
    }
    onDrawBehind {
        drawPath(path, Color.Black, style = Stroke(26.dp.toPx()))
    }
}
