package com.fsosuke.personamusic.ui.radio

import androidx.compose.foundation.background
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
import com.fsosuke.personamusic.playback.toSong
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

/** Japanese live radio from radiko, for the chosen area. */
@Composable
fun RadioScreen(contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val areaId by container.settings.radikoArea.collectAsStateWithLifecycle()
    val area = RadikoArea.byId(areaId) ?: RadikoArea.Miyagi
    val playerState by container.player.state.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(area) { mutableStateOf<RadioState>(RadioState.Loading) }

    LaunchedEffect(area, attempt) {
        state = RadioState.Loading
        state = runCatching { container.radiko.stations(area) }
            .fold({ RadioState.Loaded(it) }, { RadioState.Failed(it.localizedMessage ?: it.javaClass.simpleName) })
        // Keep "on air" fresh while the tab is open.
        while (state is RadioState.Loaded) {
            delay(60_000)
            val programs = runCatching { container.radiko.nowOnAir(area) }.getOrNull() ?: continue
            val current = state as? RadioState.Loaded ?: break
            state = RadioState.Loaded(current.stations.map { it.copy(program = programs[it.id] ?: it.program) })
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { PersonaHeader("Radio") }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadikoArea.entries.forEach { option ->
                    val selected = option == area
                    Box(
                        Modifier
                            .rotate(if (selected) -3f else 0f)
                            .background(if (selected) colors.accent else Color(0xFF222222), slantShape(0.08f))
                            .clickable { container.settings.setRadikoArea(option.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(option.title, color = if (selected) colors.onAccent else Color.White, style = PersonaType.body)
                            Text(option.japanese, color = (if (selected) colors.onAccent else Color.White).copy(alpha = 0.7f), style = PersonaType.caption)
                        }
                    }
                }
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
                item { ShoutLabel("On air", Modifier.padding(start = 24.dp, top = 6.dp, bottom = 6.dp)) }
                itemsIndexed(s.stations, key = { _, st -> st.id }) { index, station ->
                    val playing = playerState.current?.id == station.toSong().id
                    StationCard(station, index, playing) { container.player.playRadio(station) }
                }
                item {
                    Text(
                        "Live from radiko.jp · location: ${area.title} (${area.japanese})",
                        color = colors.muted,
                        style = PersonaType.caption,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StationCard(station: RadikoStation, index: Int, playing: Boolean, onClick: () -> Unit) {
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
            if (program != null && program.title.isNotBlank()) {
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
