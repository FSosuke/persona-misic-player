package com.fsosuke.personamusic.ui.wrapped

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.WrappedData
import com.fsosuke.personamusic.data.WrappedPeriod
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.Graffiti
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.Sparkle
import com.fsosuke.personamusic.ui.components.halftone
import com.fsosuke.personamusic.ui.components.phantomSlashes
import com.fsosuke.personamusic.ui.components.redSwirls
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.components.starPattern
import com.fsosuke.personamusic.ui.confidant.ArcanaCard
import com.fsosuke.personamusic.ui.confidant.RankPips
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** "Wrapped" for a month or year: a story-style slideshow of your listening, P5 style. */
@Composable
fun WrappedScreen(period: WrappedPeriod, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val data by produceState<WrappedData?>(null, period) { value = container.history.wrapped(period) }

    val d = data
    when {
        d == null -> Box(Modifier.fillMaxSize().phantomSlashes(colors), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        d.isEmpty -> Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
            EmptyState("Nothing to report", "No music in ${period.title} yet. Come back after a few heists.")
            CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
        else -> Slides(d, onClose)
    }
}

@Composable
private fun Slides(data: WrappedData, onClose: () -> Unit) {
    val colors = LocalPersonaColors.current
    val slides = buildList<@Composable () -> Unit> {
        add { IntroSlide(data) }
        add { TimeSlide(data) }
        if (data.topArtists.isNotEmpty()) add { TopArtistSlide(data) }
        if (data.topArtists.size > 1) add { ArtistsSlide(data) }
        if (data.topSongs.isNotEmpty()) add { TopSongSlide(data) }
        if (data.topSongs.size > 1) add { SongsSlide(data) }
        add { HabitsSlide(data) }
        add { OutroSlide(data, onClose) }
    }
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val backgrounds: List<Modifier> = listOf(
        Modifier.phantomSlashes(colors),
        Modifier.redSwirls(colors),
        Modifier.starPattern(Color.Black, Color(0xFFEDEDED), cell = 230f, strokeWidth = 16f),
        Modifier.halftone(colors.accent, Color.Black.copy(alpha = 0.2f)),
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .then(backgrounds[page % backgrounds.size])
                    .pointerInput(page) {
                        // Story controls: tap right half = next, left half = back.
                        detectTapGestures { offset ->
                            scope.launch {
                                val target = if (offset.x > size.width / 2) page + 1 else page - 1
                                if (target in slides.indices) pager.animateScrollToPage(target)
                            }
                        }
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) { slides[page]() }
        }
        // Story progress bars.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 56.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(slides.size) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(if (i <= pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f), slantShape(0.2f)),
                )
            }
        }
        CloseButton(onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding())
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onClose, modifier = modifier.padding(4.dp)) {
        Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

// --- Slides ------------------------------------------------------------------------------

@Composable
private fun IntroSlide(data: WrappedData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Graffiti("TAKE YOUR TIME", fontSize = 26.sp)
        RansomText("Your heist report", fontSize = 30.sp)
        Box(Modifier.rotate(-4f).background(Color.Black).border(3.dp, Color.White).padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text(data.period.title.uppercase(), color = Color.White, style = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 44.sp))
        }
        Text("Tap or swipe to reveal ›", color = Color.White.copy(alpha = 0.8f), style = PersonaType.body)
    }
}

@Composable
private fun TimeSlide(data: WrappedData) {
    val colors = LocalPersonaColors.current
    val minutes = data.listenedMs / 60_000
    Column(
        Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShoutLabel("Time stolen", tilt = -6f)
        Text("$minutes", color = Color.White, style = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 96.sp))
        Box(Modifier.background(Color.Black).padding(horizontal = 10.dp)) {
            Text("MINUTES OF MUSIC", color = colors.accent, style = PersonaType.label.copy(fontSize = 20.sp))
        }
        Text(
            "That's ${"%.1f".format(minutes / 60.0)} hours, ${data.plays} plays,\n${data.distinctSongs} songs from ${data.distinctArtists} artists.",
            color = Color.White,
            style = PersonaType.body.copy(fontSize = 17.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopArtistSlide(data: WrappedData) {
    val colors = LocalPersonaColors.current
    val top = data.topArtists.first()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RansomText("Top confidant", fontSize = 30.sp)
        ArcanaCard(top.confidant.arcana, Modifier.width(150.dp).rotate(-4f), textScale = 1.4f)
        Text(top.confidant.artist, color = Color.White, style = PersonaType.title.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
        Box(Modifier.background(Color.Black).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text(
                "${top.playsInPeriod} plays · ${top.confidant.arcana.title} · " + if (top.confidant.isMax) "MAX" else "Rank ${top.confidant.rank}",
                color = colors.accent,
                style = PersonaType.label.copy(fontSize = 15.sp),
            )
        }
        RankPips(top.confidant.rank, pip = 12.dp)
    }
}

@Composable
private fun ArtistsSlide(data: WrappedData) {
    RankedList(
        title = "Your confidants",
        rows = data.topArtists.map { Triple(it.confidant.artist, "${it.playsInPeriod} plays · ${it.confidant.arcana.title}", null) },
    )
}

@Composable
private fun TopSongSlide(data: WrappedData) {
    val top = data.topSongs.first()
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RansomText("Song of the period", fontSize = 26.sp)
        Box {
            Box(Modifier.size(230.dp).rotate(-4f).padding(start = 10.dp, top = 10.dp).background(Color.Black))
            Artwork(top.song.artworkUri, Modifier.size(220.dp).rotate(-4f).background(Color.White).padding(8.dp))
            Sparkle(Modifier.align(Alignment.TopEnd).size(70.dp))
        }
        Text(top.song.title, color = Color.White, style = PersonaType.title.copy(fontSize = 24.sp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${top.song.artist} · ${top.plays} plays", color = Color.White.copy(alpha = 0.8f), style = PersonaType.body)
    }
}

@Composable
private fun SongsSlide(data: WrappedData) {
    RankedList(
        title = "Top songs",
        rows = data.topSongs.map { Triple(it.song.title, "${it.song.artist} · ${it.plays} plays", it.song.artworkUri) },
    )
}

@Composable
private fun HabitsSlide(data: WrappedData) {
    val colors = LocalPersonaColors.current
    val lines = buildList {
        data.favoritePhase?.let { add("Favourite time" to "${it.label} – a true ${it.label.lowercase()} phantom.") }
        data.busiestDay?.let { day ->
            add(
                "Busiest day" to "${day.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())}, ${day.dayOfMonth}. " +
                    "${day.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())} – ${data.busiestDayPlays} plays",
            )
        }
        if (data.longestStreakDays > 1) add("Longest streak" to "${data.longestStreakDays} days in a row")
        if (data.newArtists.isNotEmpty()) {
            add("New confidants" to "${data.newArtists.size} new artists, e.g. ${data.newArtists.take(3).joinToString(", ")}")
        }
    }
    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        RansomText("Your habits", fontSize = 30.sp)
        lines.forEachIndexed { i, (label, text) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .rotate(if (i % 2 == 0) -1.5f else 1.5f)
                    .background(Color.White, slantShape(0.02f))
                    .padding(3.dp)
                    .background(Color.Black, slantShape(0.02f))
                    .padding(14.dp),
            ) {
                Text(label.uppercase(), color = colors.accent, style = PersonaType.label.copy(fontSize = 14.sp))
                Text(text, color = Color.White, style = PersonaType.body)
            }
        }
    }
}

@Composable
private fun OutroSlide(data: WrappedData, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val colors = LocalPersonaColors.current
    Column(
        Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CallingCard(data, colors)
        PersonaButton("Play my top songs", onClick = {
            container.player.play(data.topSongs.map { it.song })
            onClose()
        })
        PersonaButton("Share", onClick = {
            val text = buildString {
                appendLine("My ${data.period.title} Wrapped – Persona Music")
                appendLine("${data.listenedMs / 60_000} minutes · ${data.plays} plays")
                data.topArtists.firstOrNull()?.let { appendLine("Top confidant: ${it.confidant.artist} (${it.confidant.arcana.title})") }
                data.topSongs.take(3).forEachIndexed { i, s -> appendLine("${i + 1}. ${s.song.title} – ${s.song.artist}") }
            }
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Wrapped")
            )
        }, inverted = true)
    }
}

/** The closing "calling card". */
@Composable
private fun CallingCard(data: WrappedData, colors: PersonaColors) {
    Column(
        Modifier
            .rotate(-3f)
            .background(Color.White)
            .padding(5.dp)
            .background(Color.Black)
            .border(2.dp, colors.accent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RansomText("Calling card", fontSize = 24.sp)
        Text(
            "You stole ${data.listenedMs / 60_000} minutes of music in ${data.period.title}.\n" +
                "We will take your heart again next time.",
            color = Color.White,
            style = PersonaType.body,
            textAlign = TextAlign.Center,
        )
        Text("— The Phantom Thieves of Persona Music", color = colors.accent, style = PersonaType.caption)
    }
}

/** A numbered top-5 list with optional cover art. */
@Composable
private fun RankedList(title: String, rows: List<Triple<String, String, String?>>) {
    val colors = LocalPersonaColors.current
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RansomText(title, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { i, (name, sub, art) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .rotate(if (i % 2 == 0) -1f else 1f)
                    .background(Color.Black, slantShape(0.02f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}",
                    color = if (i == 0) colors.accent else Color.White,
                    style = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 34.sp),
                    modifier = Modifier.width(44.dp),
                )
                if (art != null) {
                    Artwork(art, Modifier.size(48.dp).border(2.dp, Color.White))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, style = PersonaType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
