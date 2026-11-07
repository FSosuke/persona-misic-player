package com.fsosuke.personamusic.ui.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.monitor.AudioMonitor
import com.fsosuke.personamusic.monitor.MeterFrame
import com.fsosuke.personamusic.monitor.OutputRoute
import com.fsosuke.personamusic.monitor.StreamInfo
import com.fsosuke.personamusic.monitor.rememberOutputRoute
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.LocalReduceMotion
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Monitor" – OTORA's audio monitor in Persona clothes: a turntable with the cover as label,
 * where the sound goes and what it is, analog VU needles with digital peak bars, and a
 * 12-band spectrum analyser – all measured from the actual decoded audio.
 */
@Composable
fun MonitorScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val context = LocalContext.current
    val monitor = container.audioMonitor
    val state by container.player.state.collectAsStateWithLifecycle()
    val position by container.player.positionMs.collectAsStateWithLifecycle()
    val frame by monitor.frames.collectAsStateWithLifecycle()
    val stream by monitor.stream.collectAsStateWithLifecycle()
    val route = rememberOutputRoute()

    // Measuring only runs while this screen is open.
    DisposableEffect(Unit) {
        monitor.active = true
        onDispose { monitor.active = false }
    }
    val shownFrame = if (state.isPlaying) frame else MeterFrame()

    LazyColumn(Modifier.fillMaxSize().background(colors.background), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Monitor") {
                    Text(
                        "AUDIO / CONNECTION",
                        color = colors.muted,
                        style = PersonaType.label.copy(fontSize = 12.sp, letterSpacing = 3.sp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(route.name, color = colors.accent, style = PersonaType.title)
                        Text(route.kind + route.details.joinToString("") { " · $it" }, color = Color.White, style = PersonaType.body)
                        Text(sourceLine(stream), color = colors.muted, style = PersonaType.caption)
                        stream.pcmEncoding?.let {
                            Text("Decoded: ${khz(stream.pcmSampleRate)} · $it", color = colors.muted, style = PersonaType.caption)
                        }
                        stream.decoder?.let { Text("Decoder: $it", color = colors.muted, style = PersonaType.caption.copy(fontSize = 11.sp)) }
                        Spacer(Modifier.height(6.dp))
                        state.current?.let { song ->
                            Text("${song.title} · ${song.artist}", color = Color.White, style = PersonaType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            song.album?.let { Text(it, color = colors.highlight, style = PersonaType.caption, maxLines = 1) }
                        }
                    }
                    Turntable(state.current, playing = state.isPlaying, modifier = Modifier.width(140.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("${formatDuration(position)} / ${formatDuration(state.durationMs)}", color = colors.muted, style = PersonaType.caption)
                Slider(
                    value = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                    onValueChange = { if (state.durationMs > 0) container.player.seekTo((it * state.durationMs).toLong()) },
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Filled.SkipPrevious, "Previous", onClick = container.player::previous)
                    RoundButton(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", big = true, onClick = container.player::togglePlay)
                    RoundButton(Icons.Filled.SkipNext, "Next", onClick = container.player::next)
                }
            }
        }
        item {
            Panel {
                SectionLabel("Meters", "アナログ＋デジタルピークメーター")
                PeakBars(shownFrame)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    VuMeter(shownFrame.rmsDb[0], "LEFT · RMS", Modifier.weight(1f))
                    VuMeter(shownFrame.rmsDb[1], "RIGHT · RMS", Modifier.weight(1f))
                }
                Text("L/R · capture dBFS", color = colors.muted, style = PersonaType.caption.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        item {
            Panel {
                SectionLabel("Spectrum", "スペクトラムアナライザー")
                Spectrum(shownFrame)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonaButton("Copy diagnostics", onClick = {
                    val text = diagnostics(route, stream, state.current, shownFrame)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Persona Music monitor", text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                })
            }
            Text(
                "Levels are measured from the decoded audio before volume and equalizer. " +
                    "Bluetooth codecs (LDAC, aptX …) are only visible to system apps.",
                color = colors.muted,
                style = PersonaType.caption.copy(fontSize = 11.sp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun khz(rate: Int) = if (rate > 0) "${rate / 1000.0} kHz" else "?"

private fun sourceLine(stream: StreamInfo): String {
    if (stream.sourceSampleRate <= 0 && stream.mimeType == null) return "Source: waiting for audio…"
    val codec = when {
        stream.codecs?.contains("opus", true) == true || stream.mimeType?.contains("opus") == true -> "Opus"
        stream.mimeType?.contains("mp4a") == true || stream.codecs?.startsWith("mp4a") == true -> "AAC"
        stream.mimeType?.contains("mpeg") == true -> "MP3"
        stream.mimeType?.contains("flac") == true -> "FLAC"
        stream.mimeType?.contains("vorbis") == true -> "Vorbis"
        stream.mimeType?.contains("raw") == true -> "PCM"
        else -> stream.mimeType?.substringAfter('/')?.uppercase() ?: "?"
    }
    val bitrate = if (stream.bitrate > 0) " · ${stream.bitrate / 1000} kbps" else ""
    return "Source: $codec · ${khz(stream.sourceSampleRate)} · ${stream.channels} ch$bitrate"
}

private fun diagnostics(route: OutputRoute, stream: StreamInfo, song: Song?, frame: MeterFrame) = buildString {
    appendLine("Output: ${route.name} (${route.kind}) ${route.details.joinToString()}")
    appendLine(sourceLine(stream))
    appendLine("Decoded: ${khz(stream.pcmSampleRate)} ${stream.pcmEncoding.orEmpty()} · decoder ${stream.decoder ?: "?"}")
    song?.let { appendLine("Track: ${it.title} – ${it.artist}") }
    appendLine("RMS L/R: %.1f / %.1f dBFS · peak hold %.1f / %.1f dBFS".format(frame.rmsDb[0], frame.rmsDb[1], frame.peakHoldDb[0], frame.peakHoldDb[1]))
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = LocalPersonaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(Color.White, slantShape(0.015f))
            .padding(2.dp)
            .background(colors.surface, slantShape(0.015f))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(title: String, japanese: String) {
    val colors = LocalPersonaColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        ShoutLabel(title)
        Spacer(Modifier.width(10.dp))
        Text(japanese, color = colors.highlight, style = PersonaType.caption)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 64.dp else 52.dp
    Box(
        Modifier.size(size).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 26.dp))
    }
}

/** A record with the cover as its label; it spins while playing and the tonearm swings in. */
@Composable
private fun Turntable(song: Song?, playing: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val spin = remember { Animatable(0f) }
    LaunchedEffect(playing, reduce) {
        if (playing && !reduce) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(1800, easing = LinearEasing))
                spin.snapTo(spin.value % 360f)
            }
        }
    }
    val arm by animateFloatAsState(if (playing) 24f else 0f, tween(700), label = "tonearm")
    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF0E0E0E), r)
            for (i in 1..9) drawCircle(Color(0xFF262626), r * (0.42f + i * 0.06f), style = Stroke(1f))
            drawCircle(Color(0xFF3A3A3A), r, style = Stroke(3f))
        }
        Artwork(
            song?.artworkUri,
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.42f)
                .graphicsLayer { rotationZ = spin.value }
                .clip(CircleShape),
            shape = CircleShape,
        )
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * 0.92f, size.height * 0.08f)
            rotate(arm, pivot) {
                val end = Offset(size.width * 0.86f, size.height * 0.70f)
                drawLine(Color(0xFFDADADA), pivot, end, strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Color(0xFFDADADA), end, Offset(end.x - 10f, end.y + 18f), strokeWidth = 12f, cap = StrokeCap.Round)
            }
            drawCircle(Color(0xFFBDBDBD), 12f, pivot)
            drawCircle(Color(0xFF555555), 5f, pivot)
        }
    }
}

/** dBFS (-60..0) to 0..1 along the meter, with more room for the loud end like OTORA's scale. */
private fun meterX(db: Float): Float {
    val clamped = db.coerceIn(-60f, 0f)
    return if (clamped < -10f) (clamped + 60f) / 50f * 0.75f else 0.75f + (clamped + 10f) / 10f * 0.25f
}

@Composable
private fun PeakBars(frame: MeterFrame) {
    val colors = LocalPersonaColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = PersonaType.caption.copy(fontSize = 11.sp, color = Color.White)
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val left = 22f
        val width = size.width - left - 8f
        listOf(-60f to "-∞", -40f to "-40", -30f to "-30", -20f to "-20", -10f to "-10", -5f to "-5", 0f to "0").forEach { (db, label) ->
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(left + meterX(db) * width - layout.size.width / 2f, 0f))
        }
        val segments = 48
        listOf("L", "R").forEachIndexed { ch, name ->
            val y = 28f + ch * 18f
            drawText(measurer.measure(name, labelStyle), topLeft = Offset(0f, y - 8f))
            val lit = meterX(frame.peakDb[ch])
            for (s in 0 until segments) {
                val f = s / segments.toFloat()
                val x = left + f * width
                val db = if (f < 0.75f) f / 0.75f * 50f - 60f else (f - 0.75f) / 0.25f * 10f - 10f
                val on = f <= lit && frame.peakDb[ch] > MeterFrame.SILENCE
                val color = when {
                    db >= -3f -> colors.accent
                    db >= -10f -> Color(0xFFF5C451)
                    else -> Color(0xFFE8F4F8)
                }
                drawLine(if (on) color else color.copy(alpha = 0.12f), Offset(x, y), Offset(x + width / segments * 0.7f, y), strokeWidth = 6f)
            }
            if (frame.peakHoldDb[ch] > -59f) {
                val hx = left + meterX(frame.peakHoldDb[ch]) * width
                drawLine(Color(0xFFF5C451), Offset(hx, y - 6f), Offset(hx, y + 6f), strokeWidth = 3f)
            }
        }
    }
}

/** Analog VU needle: 0 VU = -18 dBFS, scale -20…+3 VU, with a little needle inertia. */
@Composable
private fun VuMeter(rmsDb: Float, label: String, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    val measurer = rememberTextMeasurer()
    val vu = (rmsDb + 18f).coerceIn(-20f, 3f)
    val needle by animateFloatAsState(vu, spring(dampingRatio = 0.7f, stiffness = 120f), label = "needle")
    val small = PersonaType.caption.copy(fontSize = 10.sp, color = Color.White)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val pivot = Offset(size.width / 2, size.height * 0.98f)
            val radius = size.height * 0.82f
            fun angleFor(v: Float) = -50f + (v + 20f) / 23f * 100f
            fun point(v: Float, r: Float): Offset {
                val a = Math.toRadians((angleFor(v) - 90f).toDouble())
                return Offset(pivot.x + r * cos(a).toFloat(), pivot.y + r * sin(a).toFloat())
            }
            listOf(-20f, -10f, -7f, -5f, -3f, -1f, 0f, 1f, 2f, 3f).forEach { v ->
                drawLine(if (v >= 0f) colors.accent else Color.White, point(v, radius * 0.9f), point(v, radius), strokeWidth = 3f)
            }
            drawArc(
                colors.accent, startAngle = angleFor(0f) - 90f, sweepAngle = angleFor(3f) - angleFor(0f), useCenter = false,
                topLeft = Offset(pivot.x - radius * 0.86f, pivot.y - radius * 0.86f),
                size = androidx.compose.ui.geometry.Size(radius * 1.72f, radius * 1.72f), style = Stroke(5f),
            )
            listOf(-3f to "-3", 0f to "0").forEach { (v, t) ->
                val layout = measurer.measure(t, small)
                val p = point(v, radius * 1.08f)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height))
            }
            drawLine(Color.White, pivot, point(needle, radius * 0.95f), strokeWidth = 3f, cap = StrokeCap.Round)
        }
        Text(label, color = Color.White, style = PersonaType.label.copy(fontSize = 13.sp))
    }
}

/** Twelve striped columns with falling peak caps; the highest band glows in the accent colour. */
@Composable
private fun Spectrum(frame: MeterFrame) {
    val colors = LocalPersonaColors.current
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val bands = AudioMonitor.BANDS
        val gap = 8f
        val columnWidth = (size.width - gap * (bands.size - 1)) / bands.size
        val labelHeight = 26f
        val top = 4f
        val maxHeight = size.height - labelHeight - top
        val stripe = 7f
        bands.forEachIndexed { i, band ->
            val x = i * (columnWidth + gap)
            val isHigh = i == bands.lastIndex
            val color = if (isHigh) Color(0xFFF5C451) else Color(0xFFDDE8EE)
            val level = frame.bands[i]
            var y = top + maxHeight
            val litTop = top + maxHeight * (1f - level)
            while (y > top) {
                drawLine(if (y >= litTop) color else color.copy(alpha = 0.08f), Offset(x, y), Offset(x + columnWidth, y), strokeWidth = 3f)
                y -= stripe
            }
            val peakY = top + maxHeight * (1f - frame.bandPeaks[i])
            if (frame.bandPeaks[i] > 0.02f) drawLine(colors.accent, Offset(x, peakY), Offset(x + columnWidth, peakY), strokeWidth = 4f)
            val layout = measurer.measure(band.label, PersonaType.caption.copy(fontSize = 10.sp, color = if (isHigh) Color(0xFFF5C451) else Color.White))
            drawText(layout, topLeft = Offset(x + columnWidth / 2 - layout.size.width / 2f, size.height - labelHeight + 4f))
        }
    }
}
