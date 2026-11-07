package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.ui.theme.Lingo
import com.fsosuke.personamusic.ui.theme.PersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.theme.Skin
import kotlin.random.Random

/** The torn paper label behind section titles ("POPULAR", "SINGLES", "ALBUM"). */
private val stickerShape = PolygonShape(
    listOf(
        Offset(0f, 0.16f), Offset(0.07f, 0f), Offset(0.62f, 0.04f), Offset(0.66f, 0f), Offset(0.97f, 0.06f),
        Offset(1f, 0.72f), Offset(0.94f, 1f), Offset(0.35f, 0.94f), Offset(0.3f, 1f), Offset(0.02f, 0.9f),
    ),
)

/**
 * Big black condensed title on a white torn label, like the section headers of the P5 menus.
 * In the Zero-One skin it becomes a stencil title.
 */
@Composable
fun StickerTitle(text: String, modifier: Modifier = Modifier, tilt: Float = -2f, fontSize: TextUnit = 46.sp) {
    val style = if (Skin.zeroOne) PersonaType.display.copy(fontSize = fontSize * 0.8f)
    else TextStyle(fontFamily = PersonaFonts.abril, fontStyle = FontStyle.Italic, fontSize = fontSize, letterSpacing = (-1).sp)
    Box(
        modifier
            .rotate(tilt)
            .background(Color.Black, stickerShape)
            .padding(5.dp)
            .background(Color.White, stickerShape)
            .padding(horizontal = 28.dp, vertical = 2.dp),
    ) {
        Text(
            Lingo.of(text).uppercase(),
            color = Color.Black,
            style = style,
            // A touch narrower, like the condensed serif in the game.
            modifier = Modifier.graphicsLayer { scaleX = if (Skin.zeroOne) 1f else 0.82f },
        )
    }
}

/** Black panel whose white frame steps in and out at the sides (the "POPULAR" list). */
private val jaggedOuter = PolygonShape(
    listOf(
        Offset(0.07f, 0f), Offset(0.97f, 0.02f), Offset(0.95f, 0.22f), Offset(1f, 0.26f), Offset(0.96f, 0.55f),
        Offset(0.99f, 0.6f), Offset(0.95f, 0.82f), Offset(0.98f, 1f), Offset(0f, 0.97f), Offset(0.03f, 0.7f),
        Offset(0f, 0.66f), Offset(0.04f, 0.3f), Offset(0.01f, 0.26f),
    ),
)

@Composable
fun JaggedPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(Color.Black, jaggedOuter)
            .padding(4.dp)
            .background(Color.White, jaggedOuter)
            .padding(7.dp)
            .background(Color.Black, jaggedOuter)
            .padding(horizontal = 26.dp, vertical = 18.dp),
        content = content,
    )
}

/** Deep red with large darker stars – the backdrop of the P5 status screens. */
fun Modifier.redStarField(colors: PersonaColors): Modifier {
    val accent = colors.accent
    val base = Color(accent.red * 0.55f, accent.green * 0.45f, accent.blue * 0.45f)
    val star = Color(accent.red * 0.72f, accent.green * 0.55f, accent.blue * 0.55f)
    return starPattern(base, star, cell = 260f, strokeWidth = 18f, drifting = true)
}

/** Light grey and red diagonal wedges over black (between "POPULAR" and "SINGLES"). */
fun Modifier.wedgeBands(colors: PersonaColors): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val grey = Path().apply { moveTo(0f, h * 0.25f); lineTo(w * 0.7f, h * 0.05f); lineTo(w, h * 0.35f); lineTo(w, h * 0.75f); lineTo(0f, h); close() }
    val red = Path().apply { moveTo(0f, 0f); lineTo(w * 0.45f, 0f); lineTo(0f, h * 0.4f); close() }
    onDrawBehind {
        drawRect(Color.Black)
        drawPath(grey, Color(0xFFD9D9D9))
        drawPath(red, colors.accent.copy(alpha = 0.85f))
    }
}

/**
 * Now-playing backdrop: dark halftone at the top, a torn red field with ink splatter and two
 * chains running across it – the "MORE" screen.
 */
fun Modifier.chainsBackground(colors: PersonaColors): Modifier = composed {
    val drift = rememberDrift(periodMs = 30_000)
    drawWithCache {
        val w = size.width
        val h = size.height
        val dark = Color(0xFF242424)
        val redTop = h * 0.08f
        val torn = Path().apply {
            moveTo(0f, redTop + h * 0.04f)
            val random = Random(3)
            var x = 0f
            while (x < w) {
                x += w / 14f
                lineTo(x, redTop + random.nextFloat() * h * 0.05f)
            }
            lineTo(w, h * 0.5f); lineTo(0f, h * 0.55f); close()
        }
        val lowerRed = Path().apply {
            moveTo(0f, h * 0.62f)
            val random = Random(9)
            var x = 0f
            while (x < w) {
                x += w / 16f
                lineTo(x, h * 0.6f + random.nextFloat() * h * 0.04f)
            }
            lineTo(w, h * 0.9f); lineTo(0f, h * 0.95f); close()
        }
        val splats = Random(21).let { r -> List(18) { Triple(Offset(r.nextFloat() * w, redTop + r.nextFloat() * h * 0.85f), 4f + r.nextFloat() * 26f, r.nextFloat()) } }
        val link = Size(46f, 26f)
        onDrawBehind {
            drawRect(dark)
            // Halftone dots on the dark parts.
            var y = 0f
            while (y < h) {
                var x = if ((y / 14f).toInt() % 2 == 0) 0f else 7f
                while (x < w) { drawCircle(Color(0xFF333333), 2.2f, Offset(x, y)); x += 14f }
                y += 14f
            }
            drawPath(torn, colors.accent)
            drawPath(lowerRed, colors.accent)
            splats.forEach { (c, r, a) -> drawCircle(Color(0xFF3A0006).copy(alpha = 0.5f + a * 0.4f), r, c) }
            // Two chains, slowly sliding along their own line.
            listOf(Triple(h * 0.18f, h * 0.36f, 0f), Triple(h * 0.66f, h * 0.58f, 0.5f)).forEach { (y0, y1, phase) ->
                val dx = w
                val dy = y1 - y0
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val step = link.width * 0.82f
                val count = (length / step).toInt() + 2
                val shift = ((drift.value + phase) % 1f) * step * 2
                for (i in -1 until count) {
                    val t = (i * step + shift) / length
                    val center = Offset(dx * t, y0 + dy * t)
                    rotate(angle + if (i % 2 == 0) 0f else 0f, center) {
                        val linkSize = if (i % 2 == 0) link else Size(link.width, link.height * 0.45f)
                        drawRoundRect(
                            Color(0xFF151515),
                            topLeft = Offset(center.x - linkSize.width / 2, center.y - linkSize.height / 2),
                            size = linkSize,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(linkSize.height / 2),
                            style = Stroke(9f),
                        )
                        drawRoundRect(
                            Color(0xFF5A5A5A),
                            topLeft = Offset(center.x - linkSize.width / 2, center.y - linkSize.height / 2),
                            size = linkSize,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(linkSize.height / 2),
                            style = Stroke(2f),
                        )
                    }
                }
            }
        }
    }
}

/** White paper tab peeking from the bottom edge ("LYRICS"). */
@Composable
fun PaperTab(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .rotate(6f)
            .background(Color.Black, slantShape(0.06f))
            .padding(3.dp)
            .background(Color(0xFFF2F2F2), slantShape(0.06f))
            .clickable(onClick = onClick)
            .padding(start = 54.dp, end = 70.dp, top = 12.dp, bottom = 22.dp),
    ) {
        Text(Lingo.of(text).uppercase(), color = Color.Black, style = PersonaType.label.copy(fontSize = 20.sp))
    }
}

/** Thick white progress bar with a black outline and an inner track, as on the "MORE" screen. */
fun Modifier.thickBar(progress: Float): Modifier = drawWithCache {
    onDrawBehind {
        val h = size.height
        drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRoundRect(
            Color.Black,
            topLeft = Offset(h * 0.22f, h * 0.22f),
            size = Size(size.width - h * 0.44f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 3),
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(h * 0.3f, h * 0.3f),
            size = Size(((size.width - h * 0.6f) * progress.coerceIn(0f, 1f)).coerceAtLeast(0f), h * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 4),
        )
        drawLine(Color.Black, Offset(0f, h), Offset(size.width, h), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}
