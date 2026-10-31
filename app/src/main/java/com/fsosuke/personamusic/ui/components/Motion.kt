package com.fsosuke.personamusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** "Reduce animations" from Customize: skip wipes, splashes and wobbles. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Entrance like a Persona menu card: drops in slightly rotated and too big, then springs into
 * place. Replays whenever [key] changes.
 */
fun Modifier.popIn(key: Any? = Unit, delayMs: Long = 0, fromRotation: Float = -10f): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs)
        progress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        val p = progress.value
        val s = 1.25f - 0.25f * p
        scaleX = s
        scaleY = s
        rotationZ = fromRotation * (1f - p)
        alpha = p.coerceIn(0f, 1f)
    }
}

/** Gentle sway and pulse for the cover while music plays. */
fun Modifier.breathing(active: Boolean): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    val amount = remember { Animatable(0f) }
    LaunchedEffect(active) { amount.animateTo(if (active) 1f else 0f, tween(500)) }
    graphicsLayer {
        val wave = sin(phase * 2 * PI).toFloat()
        val beat = (cos(phase * 4 * PI).toFloat() + 1f) / 2f
        val s = 1f + 0.018f * beat * amount.value
        scaleX = s
        scaleY = s
        rotationZ = 1.2f * wave * amount.value
    }
}

/**
 * Red/black/white slash that sweeps over the screen whenever [trigger] changes (the first
 * value is ignored) – Persona 5's menu transition.
 */
@Composable
fun SlashWipe(trigger: Any?, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) return
    val colors = LocalPersonaColors.current
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = progress.value
        // The band travels from off-screen left to off-screen right.
        val x = -w * 0.9f + p * w * 2.4f
        fun band(offset: Float, width: Float) = Path().apply {
            moveTo(x + offset, -h * 0.05f)
            lineTo(x + offset + width, -h * 0.05f)
            lineTo(x + offset + width - w * 0.45f, h * 1.05f)
            lineTo(x + offset - w * 0.45f, h * 1.05f)
            close()
        }
        drawPath(band(-w * 0.18f, w * 0.12f), Color.Black)
        drawPath(band(0f, w * 0.55f), colors.accent)
        drawPath(band(w * 0.57f, w * 0.05f), Color.White)
    }
}

/**
 * "TAKE YOUR HEART": a short All-Out-Attack style splash when a song gets hearted.
 */
@Composable
fun TakeYourHeartSplash() {
    if (LocalReduceMotion.current) return
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    var song by remember { mutableStateOf<Song?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        container.likeEvents.collect { liked ->
            song = liked
            progress.snapTo(0f)
            launch { progress.animateTo(1f, tween(1300, easing = LinearEasing)) }.join()
            song = null
        }
    }
    val current = song ?: return
    val p = progress.value
    // 0-0.2 burst in, 0.2-0.75 hold, 0.75-1 fade out.
    val alpha = when {
        p < 0.15f -> p / 0.15f
        p > 0.75f -> (1f - p) / 0.25f
        else -> 1f
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.55f))
            val center = Offset(size.width / 2, size.height * 0.45f)
            val radius = size.maxDimension
            rotate(p * 40f, center) {
                for (i in 0 until 14) {
                    val a0 = (i * 2 * PI / 14).toFloat()
                    val a1 = a0 + 0.16f
                    val ray = Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(center.x + cos(a0) * radius, center.y + sin(a0) * radius)
                        lineTo(center.x + cos(a1) * radius, center.y + sin(a1) * radius)
                        close()
                    }
                    drawPath(ray, if (i % 2 == 0) colors.accent else Color.White.copy(alpha = 0.85f))
                }
            }
        }
        val slide = (1f - (p / 0.2f).coerceIn(0f, 1f))
        Column(
            Modifier.graphicsLayer {
                translationX = -slide * size.width * 1.4f
                rotationZ = -8f
                val s = 1f + 0.08f * (1f - slide)
                scaleX = s
                scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(4.dp)) { RansomText("TAKE YOUR", fontSize = 40.sp) }
            Box(Modifier.padding(4.dp)) { RansomText("HEART", fontSize = 64.sp, style = RansomStyle.Outline) }
            Text(
                current.title,
                color = Color.White,
                style = PersonaType.title.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}
