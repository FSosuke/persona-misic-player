package com.fsosuke.personamusic.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

@Composable
fun StatsScreen(onBack: () -> Unit, onOpenConfidants: () -> Unit, onOpenCalendar: () -> Unit, contentPadding: PaddingValues) {
    val repo = LocalAppContainer.current.repository
    val colors = LocalPersonaColors.current
    val totalPlays by repo.totalPlays.collectAsStateWithLifecycle(initialValue = 0)
    val listenedMs by repo.totalListenedMs.collectAsStateWithLifecycle(initialValue = 0L)
    val distinct by repo.distinctSongsPlayed.collectAsStateWithLifecycle(initialValue = 0)
    val artists by remember { repo.topArtists(5) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val topSongs by remember { repo.mostPlayed(10) }.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Stats")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (totalPlays == 0) {
            item { EmptyState("No intel yet", "Play some music and your stats will show up here.") }
            return@LazyColumn
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("Plays", "$totalPlays", Modifier.weight(1f), -3f)
                StatCard("Hours", "%.1f".format(listenedMs / 3_600_000.0), Modifier.weight(1f), 2f)
                StatCard("Songs", "$distinct", Modifier.weight(1f), -1.5f)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonaButton("Confidants", onClick = onOpenConfidants)
                PersonaButton("Calendar", onClick = onOpenCalendar, inverted = true)
            }
        }
        if (artists.isNotEmpty()) {
            item { CalloutBanner("Top artists", Modifier.padding(start = 12.dp, top = 12.dp), pointLeft = true) }
            item {
                val max = artists.maxOf { it.plays }.coerceAtLeast(1)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    artists.forEach { artist ->
                        Column {
                            Text(artist.name, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.85f * artist.plays / max)
                                        .height(16.dp)
                                        .background(colors.accent, slantShape(0.04f)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${artist.plays}", color = colors.muted, style = PersonaType.caption)
                            }
                        }
                    }
                }
            }
        }
        if (topSongs.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    CalloutBanner("Top songs", Modifier.padding(end = 12.dp, top = 12.dp))
                }
            }
            itemsIndexed(topSongs, key = { _, e -> e.song.id }) { index, entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (index < 3) colors.accent else Color.White,
                        style = PersonaType.label.copy(fontSize = 24.sp),
                        modifier = Modifier.width(40.dp),
                    )
                    Artwork(entry.song.artworkUri, Modifier.size(44.dp).border(2.dp, Color.White))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.song.title, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.song.artist, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${entry.plays}×", color = colors.muted, style = PersonaType.body)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, tilt: Float) {
    val colors = LocalPersonaColors.current
    Column(
        modifier
            .rotate(tilt)
            .background(Color.White)
            .padding(4.dp)
            .background(Color.Black)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = colors.accent, style = PersonaType.label.copy(fontSize = 28.sp), maxLines = 1)
        Text(label.uppercase(), color = Color.White, style = PersonaType.caption)
    }
}
