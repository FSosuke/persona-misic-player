package com.fsosuke.personamusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.awa.AwaChart
import com.fsosuke.personamusic.awa.AwaList
import com.fsosuke.personamusic.awa.AwaTrack
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import java.text.DateFormat
import java.util.Date

/** Loading state of the AWA list on screen. */
sealed interface AwaState {
    data object Loading : AwaState
    data class Loaded(val chart: AwaChart) : AwaState
    data class Failed(val message: String) : AwaState
}

/**
 * AWA charts and new releases ("sneak peeks"). AWA songs can't be played here – a tap finds the
 * song on YouTube Music instead.
 */
fun LazyListScope.awaTab(
    selected: AwaList,
    state: AwaState,
    showAll: Boolean,
    resolving: Boolean,
    onSelect: (AwaList) -> Unit,
    onRetry: () -> Unit,
    onPlay: (AwaTrack) -> Unit,
    onPlayAll: (List<AwaTrack>) -> Unit,
    onSearch: (AwaTrack) -> Unit,
    onShowAll: () -> Unit,
) {
    item(key = "awa-lists") {
        val colors = LocalPersonaColors.current
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AwaList.entries.forEach { list ->
                val isSelected = list == selected
                Box(
                    Modifier
                        .rotate(if (isSelected) -3f else 0f)
                        .background(if (isSelected) colors.accent else Color(0xFF222222), slantShape(0.08f))
                        .clickable { onSelect(list) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(list.label, color = if (isSelected) colors.onAccent else Color.White, style = PersonaType.body)
                        Text(
                            list.japanese,
                            color = (if (isSelected) colors.onAccent else Color.White).copy(alpha = 0.7f),
                            style = PersonaType.caption.copy(fontSize = 10.sp),
                        )
                    }
                }
            }
        }
    }
    when (state) {
        AwaState.Loading -> item(key = "awa-loading") {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalPersonaColors.current.accent)
            }
        }
        is AwaState.Failed -> item(key = "awa-failed") {
            EmptyState("No intel from AWA", "${state.message}\nThis source is experimental – AWA may have changed its pages.") {
                PersonaButton("Retry", onClick = onRetry)
            }
        }
        is AwaState.Loaded -> {
            val chart = state.chart
            val tracks = if (showAll) chart.tracks else chart.tracks.take(PREVIEW)
            item(key = "awa-head") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    DashedSectionTitle(chart.name)
                    val updated = if (chart.updatedAtSec > 0) {
                        "Updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chart.updatedAtSec * 1000))
                    } else null
                    listOfNotNull(chart.description?.lineSequence()?.firstOrNull(), updated, "${chart.tracks.size} songs · from AWA").forEach {
                        Text(it, color = LocalPersonaColors.current.muted, style = PersonaType.caption)
                    }
                    Spacer(Modifier.size(8.dp))
                    PersonaButton(
                        if (resolving) "Finding on YouTube…" else "Play top ${minOf(PLAY_ALL, chart.tracks.size)} via YouTube",
                        onClick = { if (!resolving) onPlayAll(chart.tracks.take(PLAY_ALL)) },
                    )
                }
            }
            itemsIndexed(tracks, key = { i, t -> "awa-$i-${t.title}" }) { index, track ->
                AwaRow(track, rank = if (selected.ranked) index + 1 else null, onClick = { onPlay(track) }, onSearch = { onSearch(track) })
            }
            if (!showAll && chart.tracks.size > PREVIEW) {
                item(key = "awa-more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        PersonaButton("Show all ${chart.tracks.size}", onClick = onShowAll, inverted = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun AwaRow(track: AwaTrack, rank: Int?, onClick: () -> Unit, onSearch: () -> Unit) {
    val colors = LocalPersonaColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                "$rank",
                color = if (rank <= 3) colors.accent else Color.White,
                style = PersonaType.label.copy(fontSize = if (rank <= 3) 22.sp else 16.sp),
                modifier = Modifier.width(36.dp),
            )
        }
        Artwork(track.cover, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.artist.ifBlank { null }, track.album).joinToString(" · "),
                color = colors.muted,
                style = PersonaType.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = "Search on YouTube Music", tint = Color.White)
        }
    }
}

private const val PREVIEW = 30
private const val PLAY_ALL = 25
