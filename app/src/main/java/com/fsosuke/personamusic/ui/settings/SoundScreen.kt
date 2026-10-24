package com.fsosuke.personamusic.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.playback.AudioEffects
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

/** Equalizer, loudness normalization and fades between songs. */
@Composable
fun SoundScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val settings = container.settings
    val effects = container.audioEffects
    val colors = LocalPersonaColors.current
    val info by effects.info.collectAsStateWithLifecycle()
    val eqEnabled by settings.eqEnabled.collectAsStateWithLifecycle()
    val levelsText by settings.eqLevels.collectAsStateWithLifecycle()
    val normalize by settings.normalize.collectAsStateWithLifecycle()
    val crossfade by settings.crossfadeSeconds.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Sound")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }

        item { CalloutBanner("Volume", Modifier.padding(start = 12.dp, top = 12.dp), pointLeft = true) }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SoundSwitch(
                    "Normalize volume",
                    "Brings quiet songs up and keeps loud ones from blasting, so YouTube and local files sound equally loud. (Android 9+)",
                    normalize,
                    settings::setNormalize,
                )
                Text("Fade between songs", color = Color.White, style = PersonaType.title)
                Text("Fades out the end of a song and fades in the next one.", color = colors.muted, style = PersonaType.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0, 3, 5, 8).forEach { seconds ->
                        Choice(if (seconds == 0) "Off" else "${seconds}s", crossfade == seconds) { settings.setCrossfadeSeconds(seconds) }
                    }
                }
            }
        }

        item { CalloutBanner("Equalizer", Modifier.padding(start = 12.dp, top = 12.dp), pointLeft = true) }
        val eq = info
        if (eq == null) {
            item { EmptyState("Play something first", "The equalizer appears once music has played since the app started.") }
        } else {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SoundSwitch("Equalizer", "Shape the sound with the bands below.", eqEnabled, settings::setEqEnabled)
                }
            }
            if (eq.presets.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Choice("Flat", levelsText.isEmpty()) { settings.setEqLevels("") } }
                        itemsIndexed(eq.presets) { index, name ->
                            Choice(name, false) {
                                effects.presetLevels(index)?.let { settings.setEqLevels(AudioEffects.formatLevels(it)) }
                                settings.setEqEnabled(true)
                            }
                        }
                    }
                }
            }
            val levels = AudioEffects.parseLevels(levelsText)
            itemsIndexed(eq.bandCentersHz) { band, hz ->
                val level = levels.getOrNull(band) ?: 0
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hz >= 1000) "${hz / 1000}k" else "$hz",
                        color = Color.White,
                        style = PersonaType.label.copy(fontSize = 13.sp),
                        modifier = Modifier.width(48.dp),
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { value ->
                            val next = List(eq.bandCentersHz.size) { i -> if (i == band) value.toInt() else levels.getOrNull(i) ?: 0 }
                            settings.setEqLevels(AudioEffects.formatLevels(next))
                        },
                        valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                        enabled = eqEnabled,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%+.1f".format(level / 100f),
                        color = colors.muted,
                        style = PersonaType.caption,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SoundSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalPersonaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = PersonaType.title)
            Text(description, color = colors.muted, style = PersonaType.caption)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPersonaColors.current
    Box(
        Modifier
            .rotate(if (selected) -4f else 0f)
            .background(if (selected) colors.accent else Color(0xFF222222), slantShape(0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label.uppercase(), color = if (selected) colors.onAccent else Color.White, style = PersonaType.label.copy(fontSize = 13.sp))
    }
}
