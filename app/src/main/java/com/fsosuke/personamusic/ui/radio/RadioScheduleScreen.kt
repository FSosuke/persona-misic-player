package com.fsosuke.personamusic.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fsosuke.personamusic.radio.Radiko
import com.fsosuke.personamusic.radio.RadikoProgram
import com.fsosuke.personamusic.radio.RadikoStation
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface ScheduleState {
    data object Loading : ScheduleState
    data class Loaded(val programs: List<RadikoProgram>) : ScheduleState
    data class Failed(val message: String) : ScheduleState
}

/** 番組表: a station's programme for today or tomorrow, with reminders. */
@Composable
fun RadioScheduleScreen(station: RadikoStation, onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val reminders by container.radioReminders.keys.collectAsStateWithLifecycle()
    val today = remember { Radiko.broadcastDay() }
    var dayOffset by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ScheduleState>(ScheduleState.Loading) }
    val listState = rememberLazyListState()
    val day = today.plusDays(dayOffset.toLong())

    LaunchedEffect(dayOffset, attempt) {
        state = ScheduleState.Loading
        state = runCatching { container.radiko.schedule(station.id, day) }
            .fold({ ScheduleState.Loaded(it) }, { ScheduleState.Failed(it.localizedMessage ?: it.javaClass.simpleName) })
        // Jump to what's on now.
        val programs = (state as? ScheduleState.Loaded)?.programs ?: return@LaunchedEffect
        val onAir = programs.indexOfFirst { it.isOnAir() }
        if (onAir > 0) listState.scrollToItem(onAir + 2)
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader(station.name)
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(0 to "Today" to "今日", 1 to "Tomorrow" to "明日").forEach { (pair, japanese) ->
                    val (offset, label) = pair
                    val selected = offset == dayOffset
                    Box(
                        Modifier
                            .rotate(if (selected) -3f else 0f)
                            .background(if (selected) colors.accent else Color(0xFF222222), slantShape(0.08f))
                            .clickable { dayOffset = offset }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = if (selected) colors.onAccent else Color.White, style = PersonaType.body)
                            Text(japanese, color = (if (selected) colors.onAccent else Color.White).copy(alpha = 0.7f), style = PersonaType.caption)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    day.format(DateTimeFormatter.ofPattern("M/d EEE", Locale.ENGLISH)).uppercase(),
                    color = Color.White,
                    style = PersonaType.title,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        when (val s = state) {
            ScheduleState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            is ScheduleState.Failed -> item {
                EmptyState("No programme", "radiko couldn't be reached: ${s.message}") {
                    PersonaButton("Retry", onClick = { attempt++ })
                }
            }
            is ScheduleState.Loaded -> {
                if (s.programs.isEmpty()) item { EmptyState("No programme", "radiko has no programme for this day yet.") }
                itemsIndexed(s.programs, key = { i, p -> "$i-${p.start}" }) { _, program ->
                    ProgramRow(
                        program,
                        reminded = container.radioReminders.key(station, program) in reminders,
                        onRemind = { container.radioReminders.toggle(station, program) },
                        onListen = { container.player.playRadio(station.copy(program = program)) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProgramRow(program: RadikoProgram, reminded: Boolean, onRemind: () -> Unit, onListen: () -> Unit) {
    val colors = LocalPersonaColors.current
    val onAir = program.isOnAir()
    val over = program.isOver()
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (over) 0.45f else 1f)
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(if (onAir) colors.accent else Color.Black, slantShape(0.02f))
            .border(if (onAir) 3.dp else 1.dp, Color.White, slantShape(0.02f))
            .clickable(enabled = onAir, onClick = onListen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textColor = if (onAir) colors.onAccent else Color.White
        Column(Modifier.width(58.dp)) {
            Text(program.start?.format(CLOCK) ?: "", color = textColor, style = PersonaType.title)
            Text(program.end?.format(CLOCK) ?: "", color = textColor.copy(alpha = 0.7f), style = PersonaType.caption)
        }
        if (program.image != null) {
            AsyncImage(
                program.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).background(Color(0xFF222222)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (onAir) Text("ON AIR · 放送中", color = colors.onAccent, style = PersonaType.label)
            Text(program.title, color = textColor, style = PersonaType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            program.performer?.let {
                Text(it, color = if (onAir) textColor.copy(alpha = 0.8f) else colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!over && !onAir) {
            IconButton(onClick = onRemind) {
                Icon(
                    if (reminded) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = if (reminded) "Cancel reminder" else "Remind me",
                    tint = if (reminded) colors.accent else Color.White,
                )
            }
        }
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
