package com.fsosuke.personamusic.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.DayPhase
import com.fsosuke.personamusic.data.PlayRecord
import com.fsosuke.personamusic.data.WrappedPeriod
import com.fsosuke.personamusic.data.isRealArtist
import com.fsosuke.personamusic.data.primaryArtist
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.components.starPattern
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/**
 * The Persona calendar: a month of listening as a heat map, and the selected day's
 * "after school" log split into the game's times of day.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenWrapped: (WrappedPeriod) -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val history = container.history
    val colors = LocalPersonaColors.current
    val today = remember { LocalDate.now() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(dayText)

    val stamps by remember(month) { history.monthStamps(month) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val perDay = remember(stamps) { stamps.groupingBy { history.dateOf(it.playedAt) }.eachCount() }
    val dayPlays by remember(selected) { history.dayPlays(selected) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by container.player.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().background(colors.background), contentPadding = contentPadding) {
        item { CalendarHeader(selected, onBack) }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = Color.White)
                }
                Text(
                    month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()).uppercase() + " ${month.year}",
                    color = Color.White,
                    style = PersonaType.label.copy(fontSize = 20.sp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(today)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = Color.White)
                }
            }
        }
        item {
            MonthGrid(month, today, selected, perDay) { dayText = it.toString() }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonaButton("${month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())} Wrapped", onClick = {
                    onOpenWrapped(WrappedPeriod.Month(month))
                })
                PersonaButton("${month.year} Wrapped", onClick = { onOpenWrapped(WrappedPeriod.Year(month.year)) }, inverted = true)
            }
        }

        item { DaySummary(selected, dayPlays) }
        if (dayPlays.isEmpty()) {
            item { EmptyState("Free day", "No music on this day. Take your time.") }
        }
        val byPhase = dayPlays.groupBy { DayPhase.of(history.hourOf(it.playedAt)) }
        DayPhase.entries.filter { it in byPhase }.forEach { phase ->
            val plays = byPhase.getValue(phase)
            item(key = "phase-${phase.name}") {
                ShoutLabel(phase.label, Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp), tilt = if (phase.ordinal % 2 == 0) -4f else 3f)
            }
            val songs = plays.map { it.song }
            itemsIndexed(plays, key = { i, p -> "${phase.name}-$i-${p.playedAt}" }) { index, play ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        LocalTime.of(history.hourOf(play.playedAt), minuteOf(play)).toString(),
                        color = colors.muted,
                        style = PersonaType.caption,
                        modifier = Modifier.padding(start = 20.dp).width(44.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        SongRow(
                            play.song,
                            onClick = { container.player.play(songs, index) },
                            showArtwork = true,
                            isCurrent = play.song.id == playerState.current?.id,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun minuteOf(play: PlayRecord): Int =
    java.time.Instant.ofEpochMilli(play.playedAt).atZone(java.time.ZoneId.systemDefault()).minute

/** Big "10/24 Sa – Daytime" date plate like the P5 HUD. */
@Composable
private fun CalendarHeader(selected: LocalDate, onBack: () -> Unit) {
    val colors = LocalPersonaColors.current
    val phase = if (selected == LocalDate.now()) DayPhase.of(LocalTime.now().hour) else null
    Box(Modifier.fillMaxWidth().height(230.dp).starPattern(colors.patternBackground, colors.pattern)) {
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Row(
            Modifier.align(Alignment.Center).padding(top = 30.dp).rotate(-6f),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "${selected.monthValue}/${selected.dayOfMonth}",
                color = Color.White,
                style = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 84.sp),
            )
            Column(Modifier.padding(start = 10.dp, bottom = 16.dp)) {
                Box(Modifier.background(colors.accent).padding(horizontal = 10.dp)) {
                    Text(
                        selected.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()).uppercase(),
                        color = colors.onAccent,
                        style = TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = 26.sp),
                    )
                }
                if (phase != null) {
                    Box(Modifier.padding(top = 6.dp).background(Color.Black).padding(horizontal = 8.dp)) {
                        Text(phase.label.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, selected: LocalDate, perDay: Map<LocalDate, Int>, onSelect: (LocalDate) -> Unit) {
    val colors = LocalPersonaColors.current
    val max = (perDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val firstWeekday = DayOfWeek.MONDAY
    val offset = (month.atDay(1).dayOfWeek.value - firstWeekday.value + 7) % 7
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .background(Color.White, slantShape(0.01f))
            .padding(4.dp)
            .background(Color.Black, slantShape(0.01f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            (0 until 7).forEach { i ->
                Text(
                    firstWeekday.plus(i.toLong()).getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                    color = if (i >= 5) colors.accent else colors.muted,
                    style = PersonaType.label.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val plays = perDay[day] ?: 0
                        val isSelected = day == selected
                        val heat = if (plays == 0) 0f else 0.25f + 0.75f * plays / max
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .rotate(if (isSelected) -6f else 0f)
                                .background(if (plays > 0) colors.accent.copy(alpha = heat) else Color(0xFF161616))
                                .border(
                                    if (isSelected) 3.dp else if (day == today) 2.dp else 0.dp,
                                    if (isSelected) Color.White else colors.highlight,
                                )
                                .clickable(enabled = !day.isAfter(today)) { onSelect(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${day.dayOfMonth}",
                                color = if (day.isAfter(today)) Color(0xFF555555) else Color.White,
                                style = PersonaType.title.copy(fontSize = 15.sp),
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DaySummary(day: LocalDate, plays: List<PlayRecord>) {
    val colors = LocalPersonaColors.current
    if (plays.isEmpty()) return
    val minutes = plays.sumOf { it.listenedMs } / 60_000
    val topArtist = plays.map { primaryArtist(it.song.artist) }.filter { isRealArtist(it) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryTile("${plays.size}", "plays", Modifier.weight(1f), -2f)
        SummaryTile("$minutes", "minutes", Modifier.weight(1f), 1.5f)
        SummaryTile(topArtist ?: "–", "top artist", Modifier.weight(1.4f), -1f)
    }
    Text(
        day.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) + ", ${day.dayOfMonth}. " +
            day.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
        color = colors.muted,
        style = PersonaType.caption,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier, tilt: Float) {
    val colors = LocalPersonaColors.current
    Column(
        modifier
            .rotate(tilt)
            .background(Color.White)
            .padding(3.dp)
            .background(Color.Black)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = colors.accent, style = PersonaType.label.copy(fontSize = 20.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label.uppercase(), color = Color.White, style = PersonaType.caption.copy(fontSize = 11.sp))
    }
}
