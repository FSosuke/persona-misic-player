package com.fsosuke.personamusic.ui.radio

import androidx.compose.foundation.background
import com.fsosuke.personamusic.radio.RadikoRegion
import com.fsosuke.personamusic.radio.Radiko
import com.fsosuke.personamusic.data.Song
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fsosuke.personamusic.radio.RadikoArea
import com.fsosuke.personamusic.radio.RadikoStation
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.delay

private sealed interface RadioState {
    data object Loading : RadioState
    data class Loaded(val stations: List<RadikoStation>) : RadioState
    data class Failed(val message: String) : RadioState
}

private const val ALL_JAPAN = "ALL"

/** Japanese live radio from radiko: one prefecture or all of Japan. */
@Composable
fun RadioScreen(contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val areaId by container.settings.radikoArea.collectAsStateWithLifecycle()
    val allJapan = areaId == ALL_JAPAN
    val area = RadikoArea.byId(areaId) ?: RadikoArea.Miyagi
    val playerState by container.player.state.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(areaId) { mutableStateOf<RadioState>(RadioState.Loading) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(areaId, attempt) {
        state = RadioState.Loading
        state = runCatching { if (allJapan) container.radiko.allStations() else container.radiko.stations(area) }
            .fold({ RadioState.Loaded(it) }, { RadioState.Failed(it.localizedMessage ?: it.javaClass.simpleName) })
        if (allJapan) return@LaunchedEffect
        // Keep "on air" fresh while the tab is open.
        while (state is RadioState.Loaded) {
            delay(60_000)
            val programs = runCatching { container.radiko.nowOnAir(area) }.getOrNull() ?: continue
            val current = state as? RadioState.Loaded ?: break
            state = RadioState.Loaded(current.stations.map { it.copy(program = programs[it.id] ?: it.program) })
        }
    }

    // Favourite areas first, then whichever prefecture was picked last.
    val chips = buildList {
        add(RadikoArea.Miyagi.id to RadikoArea.Miyagi)
        add(RadikoArea.Yamagata.id to RadikoArea.Yamagata)
        if (!allJapan && area != RadikoArea.Miyagi && area != RadikoArea.Yamagata) add(area.id to area)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { PersonaHeader("Radio") }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                chips.forEach { (id, option) ->
                    AreaChip(option.title, option.japanese, selected = id == areaId) { container.settings.setRadikoArea(id) }
                }
                AreaChip("All Japan", "全国", selected = allJapan) { container.settings.setRadikoArea(ALL_JAPAN) }
                AreaChip("Prefecture…", "都道府県", selected = false) { picking = true }
            }
        }
        when (val s = state) {
            RadioState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            is RadioState.Failed -> item {
                EmptyState("No signal", "radiko couldn't be reached: ${s.message}") {
                    PersonaButton("Retry", onClick = { attempt++ })
                }
            }
            is RadioState.Loaded -> {
                val groups: List<Pair<String, List<RadikoStation>>> = if (allJapan) {
                    val (nationwide, regional) = s.stations.partition { it.nationwide }
                    listOf("Nationwide" to nationwide) +
                        RadikoRegion.entries.map { region -> region.title to regional.filter { it.area.region == region } }
                } else {
                    listOf("On air" to s.stations)
                }
                groups.filter { it.second.isNotEmpty() }.forEach { (title, stations) ->
                    item(key = "header-$title") {
                        ShoutLabel(title, Modifier.padding(start = 24.dp, top = 10.dp, bottom = 6.dp))
                    }
                    itemsIndexed(stations, key = { _, st -> "$title-${st.id}" }) { index, station ->
                        val playing = playerState.current?.id == Radiko.ID_PREFIX + station.id
                        StationCard(
                            station,
                            index,
                            playing,
                            nowPlaying = playerState.current?.takeIf { playing && it.title != station.name },
                            showArea = allJapan && !station.nationwide,
                        ) { container.player.playRadio(station) }
                    }
                }
                item {
                    Text(
                        if (allJapan) "Live from radiko.jp · all ${s.stations.size} stations"
                        else "Live from radiko.jp · location: ${area.title} (${area.japanese})",
                        color = colors.muted,
                        style = PersonaType.caption,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (picking) {
        PrefecturePicker(
            selected = areaId,
            onPick = { container.settings.setRadikoArea(it.id); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AreaChip(title: String, japanese: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPersonaColors.current
    val textColor = if (selected) colors.onAccent else Color.White
    Box(
        Modifier
            .rotate(if (selected) -3f else 0f)
            .background(if (selected) colors.accent else Color(0xFF222222), slantShape(0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = textColor, style = PersonaType.body)
            Text(japanese, color = textColor.copy(alpha = 0.7f), style = PersonaType.caption)
        }
    }
}

@Composable
private fun PrefecturePicker(selected: String, onPick: (RadikoArea) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPersonaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        shape = RectangleShape,
        modifier = Modifier.border(3.dp, Color.White),
        title = { Text("Prefecture · 都道府県", color = Color.White, style = PersonaType.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                RadikoRegion.entries.forEach { region ->
                    item(key = region.name) {
                        Text(
                            "${region.title} · ${region.japanese}",
                            color = colors.accent,
                            style = PersonaType.label,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(RadikoArea.all.filter { it.region == region }, key = { it.id }) { option ->
                        val isSelected = option.id == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) colors.accent else Color.Transparent)
                                .clickable { onPick(option) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(option.title, color = if (isSelected) colors.onAccent else Color.White, style = PersonaType.body, modifier = Modifier.weight(1f))
                            Text(option.japanese, color = if (isSelected) colors.onAccent else colors.muted, style = PersonaType.body)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StationCard(
    station: RadikoStation,
    index: Int,
    playing: Boolean,
    nowPlaying: Song?,
    showArea: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonaColors.current
    val program = station.program
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .rotate(if (index % 2 == 0) -0.8f else 0.8f)
            .background(if (playing) colors.accent else Color.Black, slantShape(0.03f))
            .border(2.dp, Color.White, slantShape(0.03f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // radiko logos are made for a white background.
        Box(Modifier.size(width = 96.dp, height = 44.dp).background(Color.White, slantShape(0.06f)).padding(4.dp)) {
            AsyncImage(station.logo, contentDescription = station.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                color = if (playing) colors.onAccent else Color.White,
                style = PersonaType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowPlaying != null) {
                Text(
                    "♪ ${nowPlaying.title}",
                    color = colors.onAccent,
                    style = PersonaType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying.artist.removeSuffix(" · ${station.name}"),
                    color = colors.onAccent.copy(alpha = 0.8f),
                    style = PersonaType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showArea) {
                Text(
                    "${station.area.title} · ${station.area.japanese}",
                    color = if (playing) colors.onAccent.copy(alpha = 0.8f) else colors.accent,
                    style = PersonaType.caption,
                )
            }
            if (program != null && program.title.isNotBlank() && nowPlaying == null) {
                Text(
                    program.title,
                    color = if (playing) colors.onAccent else Color.White.copy(alpha = 0.85f),
                    style = PersonaType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(program.timeRange, program.performer).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        color = if (playing) colors.onAccent.copy(alpha = 0.8f) else colors.muted,
                        style = PersonaType.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (playing) {
            Spacer(Modifier.width(8.dp))
            Text("LIVE", color = colors.onAccent, style = PersonaType.label)
        }
    }
}
