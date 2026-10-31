package com.fsosuke.personamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tempo/pitch presets; pitch follows the tempo like on a turntable. */
enum class SpeedPreset(val label: String, val japanese: String, val speed: Float, val pitch: Float) {
    Normal("Normal", "ノーマル", 1f, 1f),
    SpedUp("Sped up", "スピードアップ", 1.15f, 1.15f),
    Nightcore("Nightcore", "ナイトコア", 1.3f, 1.3f),
    Slowed("Slowed", "スロー", 0.85f, 0.85f),
    Daycore("Daycore", "デイコア", 0.75f, 0.75f);

    companion object {
        fun of(speed: Float, pitch: Float) = entries.firstOrNull { abs(it.speed - speed) < 0.01f && abs(it.pitch - pitch) < 0.01f }
    }
}

/** Text for the player's badge, e.g. "1.30× NIGHTCORE"; null at normal speed. */
fun speedLabel(speed: Float, pitch: Float): String? {
    if (abs(speed - 1f) < 0.01f && abs(pitch - 1f) < 0.01f) return null
    val preset = SpeedPreset.of(speed, pitch)
    return "%.2f×".format(speed) + (preset?.let { " ${it.label.uppercase()}" } ?: "")
}

/** "Change the tempo": speed and pitch presets plus fine-tuning. */
@Composable
fun SpeedOverlay(visible: Boolean, onDismiss: () -> Unit) {
    val player = LocalAppContainer.current.player
    val colors = LocalPersonaColors.current
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val state by player.state.collectAsStateWithLifecycle()
        var keepPitch by remember { mutableStateOf(abs(state.pitch - 1f) < 0.01f && abs(state.speed - 1f) > 0.01f) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    // Taps on the controls shouldn't close the overlay.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RansomText("Change the tempo", fontSize = 30.sp)
                Text("テンポ変更", color = colors.muted, style = PersonaType.caption)
                val current = SpeedPreset.of(state.speed, state.pitch)
                SpeedPreset.entries.chunked(3).forEachIndexed { row, presets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        presets.forEachIndexed { i, preset ->
                            PresetChoice(preset, selected = preset == current, tilt = if ((row + i) % 2 == 0) -4f else 3f) {
                                keepPitch = false
                                player.setSpeed(preset.speed, preset.pitch)
                            }
                        }
                    }
                }

                SliderRow("Speed", "×%.2f".format(state.speed), state.speed, 0.5f..2f) { value ->
                    val speed = (value * 20).roundToInt() / 20f
                    player.setSpeed(speed, if (keepPitch) 1f else speed)
                }
                SliderRow(
                    "Pitch",
                    "%+d".format(semitones(state.pitch)) + " st",
                    state.pitch,
                    0.5f..2f,
                    enabled = !keepPitch,
                ) { value -> player.setSpeed(state.speed, (value * 20).roundToInt() / 20f) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep original pitch", color = Color.White, style = PersonaType.title)
                        Text("Only the tempo changes, voices sound natural.", color = colors.muted, style = PersonaType.caption)
                    }
                    Switch(
                        checked = keepPitch,
                        onCheckedChange = {
                            keepPitch = it
                            player.setSpeed(state.speed, if (it) 1f else state.speed)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                    )
                }
                Text("Applies to the whole queue until you change it back.", color = colors.muted, style = PersonaType.caption)
            }
        }
    }
}

/** Pitch factor as semitones (2^(n/12)). */
private fun semitones(pitch: Float) = (12 * kotlin.math.ln(pitch.toDouble()) / kotlin.math.ln(2.0)).roundToInt()

@Composable
private fun SliderRow(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val colors = LocalPersonaColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 15.sp), modifier = Modifier.width(64.dp))
        Slider(
            value = current.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            modifier = Modifier.weight(1f),
        )
        Text(value, color = colors.muted, style = PersonaType.caption, modifier = Modifier.width(56.dp).padding(start = 6.dp))
    }
}

@Composable
private fun PresetChoice(preset: SpeedPreset, selected: Boolean, tilt: Float, onClick: () -> Unit) {
    val colors = LocalPersonaColors.current
    Box(
        Modifier
            .rotate(if (selected) tilt * 1.6f else tilt)
            .background(if (selected) Color.White else colors.accent, slantShape(0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(preset.label.uppercase(), color = if (selected) Color.Black else colors.onAccent, style = PersonaType.label.copy(fontSize = 16.sp))
            Text(preset.japanese, color = (if (selected) Color.Black else colors.onAccent).copy(alpha = 0.7f), style = PersonaType.caption.copy(fontSize = 10.sp))
        }
    }
}
