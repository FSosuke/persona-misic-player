package com.fsosuke.personamusic.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.StatValue
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.phantomSlashes
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The five P5 social stats with the pentagon chart from the game's status screen. */
@Composable
fun SocialStatsScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val stats by produceState<List<StatValue>>(emptyList()) { value = container.socialStats.compute() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Social stats")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (stats.isNotEmpty()) {
            item { StatPentagon(stats, Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp)) }
        }
        items(stats, key = { it.stat.name }) { StatRow(it) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StatPentagon(stats: List<StatValue>, modifier: Modifier = Modifier) {
    val colors = LocalPersonaColors.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2 * 0.72f
        fun point(i: Int, scale: Float): Offset {
            val angle = -PI / 2 + i * 2 * PI / stats.size
            return Offset(c.x + (cos(angle) * r * scale).toFloat(), c.y + (sin(angle) * r * scale).toFloat())
        }
        // Grid rings for the 5 levels.
        for (ring in 1..5) {
            val path = Path().apply {
                stats.indices.forEach { i -> val p = point(i, ring / 5f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
            drawPath(path, Color.White.copy(alpha = if (ring == 5) 0.9f else 0.25f), style = Stroke(if (ring == 5) 4f else 2f))
        }
        stats.indices.forEach { i -> drawLine(Color.White.copy(alpha = 0.25f), c, point(i, 1f), 2f) }
        // The player's shape: level plus progress inside the level.
        val shape = Path().apply {
            stats.forEachIndexed { i, s ->
                val scale = ((s.level - 1 + if (s.isMax) 1f else s.progress) / 5f).coerceIn(0.2f, 1f)
                val p = point(i, scale)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(shape, colors.accent.copy(alpha = 0.75f))
        drawPath(shape, Color.White, style = Stroke(5f))
        stats.forEachIndexed { i, s ->
            val layout = measurer.measure(s.stat.title.uppercase(), TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = 12.sp, color = Color.White))
            val p = point(i, 1.28f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }
    }
}

@Composable
private fun StatRow(value: StatValue) {
    val colors = LocalPersonaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .rotate(if (value.stat.ordinal % 2 == 0) -0.8f else 0.8f)
            .background(Color.White, slantShape(0.02f))
            .padding(3.dp)
            .background(Color.Black, slantShape(0.02f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.stat.title.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 20.sp))
            Text(
                "  ${value.stat.japanese}",
                color = colors.muted,
                style = PersonaType.caption,
                modifier = Modifier.weight(1f),
            )
            Text(value.levelTitle, color = colors.accent, style = PersonaType.label.copy(fontSize = 16.sp))
        }
        Text("★".repeat(value.level) + "☆".repeat(5 - value.level), color = colors.accent, style = PersonaType.title)
        if (!value.isMax) {
            LinearProgressIndicator(
                progress = { value.progress },
                color = colors.accent,
                trackColor = Color(0xFF333333),
                modifier = Modifier.fillMaxWidth().height(5.dp),
            )
            Text("${value.value} / ${value.nextAt} ${value.stat.unit}", color = colors.muted, style = PersonaType.caption)
        } else {
            Text("${value.value} ${value.stat.unit} · MAX", color = colors.muted, style = PersonaType.caption)
        }
        Text(value.stat.howTo, color = Color(0xFF9A9A9A), style = PersonaType.caption.copy(fontSize = 11.sp))
    }
}

/** "KNOWLEDGE UP!" – shown for each social stat that just reached a new level. */
@Composable
fun StatUpOverlay() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val queue by container.pendingStatUps.collectAsStateWithLifecycle()
    val current = queue.firstOrNull()
    fun dismiss() {
        container.pendingStatUps.value = container.pendingStatUps.value.drop(1)
    }
    AnimatedVisibility(current != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
        val up = current ?: return@AnimatedVisibility
        val slide = remember(up) { Animatable(-1200f) }
        LaunchedEffect(up) {
            slide.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            delay(4_000)
            dismiss()
        }
        Box(
            Modifier
                .fillMaxSize()
                .phantomSlashes(colors)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.graphicsLayer { translationX = slide.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RansomText("${up.stat.stat.title} up!", fontSize = 40.sp)
                Text(up.stat.stat.japanese, color = Color.White, style = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 30.sp))
                Box(Modifier.rotate(-3f).background(Color.Black).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Text(
                        "${up.stat.stat.levels[up.previousLevel - 1]}  →  ${up.stat.levelTitle}",
                        color = colors.accent,
                        style = PersonaType.label.copy(fontSize = 18.sp),
                    )
                }
                Text("★".repeat(up.stat.level) + "☆".repeat(5 - up.stat.level), color = Color.White, style = PersonaType.title.copy(fontSize = 28.sp))
                Text("Your ${up.stat.stat.title.lowercase()} has grown.", color = Color.White.copy(alpha = 0.8f), style = PersonaType.caption)
            }
        }
    }
}
