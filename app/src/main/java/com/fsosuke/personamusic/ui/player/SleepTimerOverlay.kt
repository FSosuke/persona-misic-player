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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.playback.SleepTimer
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.formatDuration
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.delay

/** "Take your time" – pick when the music should stop. */
@Composable
fun SleepTimerOverlay(visible: Boolean, onDismiss: () -> Unit) {
    val timer = LocalAppContainer.current.sleepTimer
    val colors = LocalPersonaColors.current
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val mode by timer.mode.collectAsStateWithLifecycle()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RansomText("Take your time", fontSize = 32.sp)
                Text("Stop the music after…", color = colors.muted, style = PersonaType.body)
                listOf(15, 30, 45, 60, 90).chunked(3).forEachIndexed { row, minutes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        minutes.forEachIndexed { i, m ->
                            TimerChoice("$m min", tilt = if ((row + i) % 2 == 0) -4f else 3f) {
                                timer.start(m)
                                onDismiss()
                            }
                        }
                    }
                }
                TimerChoice("End of song", tilt = -2f, wide = true) {
                    timer.startEndOfSong()
                    onDismiss()
                }
                if (mode !is SleepTimer.Mode.Off) {
                    TimerChoice("Turn off", tilt = 2f, wide = true, inverted = true) {
                        timer.cancel()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerChoice(label: String, tilt: Float, wide: Boolean = false, inverted: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPersonaColors.current
    Box(
        Modifier
            .rotate(tilt)
            .background(if (inverted) Color.White else colors.accent, slantShape(0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 34.dp else 18.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), color = if (inverted) Color.Black else colors.onAccent, style = PersonaType.label.copy(fontSize = 18.sp))
    }
}

/** Small "zZ 12:34" badge while a sleep timer is running; tap to change it. */
@Composable
fun SleepTimerBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val timer = LocalAppContainer.current.sleepTimer
    val colors = LocalPersonaColors.current
    val mode by timer.mode.collectAsStateWithLifecycle()
    if (mode is SleepTimer.Mode.Off) return
    var remaining by remember { mutableLongStateOf(timer.remainingMs() ?: 0L) }
    LaunchedEffect(mode) {
        while (true) {
            remaining = timer.remainingMs() ?: 0L
            delay(1_000)
        }
    }
    Box(
        modifier
            .rotate(-4f)
            .background(Color.Black)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            if (mode is SleepTimer.Mode.EndOfSong) "zZ END OF SONG" else "zZ ${formatDuration(remaining)}",
            color = colors.accent,
            style = PersonaType.label.copy(fontSize = 13.sp),
        )
    }
}
