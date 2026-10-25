package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.ui.theme.PersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import com.fsosuke.personamusic.ui.theme.PersonaType
import kotlin.random.Random

// Hand-drawn Persona 5 style decorations. Everything is vector code: no game assets are shipped.

/** Black base with big red diagonal slashes, like the P5 phone / pause screens. */
fun Modifier.phantomSlashes(colors: PersonaColors): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    fun poly(vararg p: Pair<Float, Float>) = Path().apply {
        p.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) }
        close()
    }
    val dark = colors.accent.copy(red = colors.accent.red * 0.55f, green = colors.accent.green * 0.4f, blue = colors.accent.blue * 0.4f)
    val slashes = listOf(
        poly(0f to 0.05f, 0.55f to 0f, 0.15f to 0.38f, 0f to 0.42f) to dark,
        poly(0.6f to 0f, 1f to 0f, 1f to 0.12f, 0.25f to 0.62f, 0.12f to 0.6f) to colors.accent,
        poly(1f to 0.35f, 1f to 0.55f, 0.2f to 1f, 0f to 1f, 0f to 0.92f) to dark,
        poly(0.7f to 0.7f, 1f to 0.62f, 1f to 0.8f, 0.55f to 1f, 0.4f to 1f) to colors.accent,
        poly(0f to 0.55f, 0.35f to 0.48f, 0f to 0.72f) to colors.accent.copy(alpha = 0.8f),
    )
    onDrawBehind {
        drawRect(Color(0xFF070707))
        slashes.forEach { (path, color) -> drawPath(path, color) }
    }
}

/** Accent background with big darker swooshes (queue screen). */
fun Modifier.redSwirls(colors: PersonaColors): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val swirl = colors.accent.copy(red = colors.accent.red * 0.72f, green = colors.accent.green * 0.6f, blue = colors.accent.blue * 0.6f)
    val paths = (0 until 4).map { i ->
        val top = h * (0.15f + i * 0.24f)
        Path().apply {
            moveTo(w * 0.35f, top)
            cubicTo(w * 1.1f, top - h * 0.12f, w * 1.15f, top + h * 0.22f, w * 0.55f, top + h * 0.2f)
            cubicTo(w * 0.85f, top + h * 0.12f, w * 0.9f, top + h * 0.02f, w * 0.35f, top)
            close()
        }
    }
    onDrawBehind {
        drawRect(colors.accent)
        paths.forEach { drawPath(it, swirl) }
    }
}

/** A four-pointed comic sparkle (yellow with a dark core) as seen around P5 artwork. */
@Composable
fun Sparkle(modifier: Modifier, color: Color = Color(0xFFFFC233)) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        fun star(scale: Float) = Path().apply {
            val inner = r * 0.16f * scale
            val outer = r * scale
            moveTo(c.x, c.y - outer)
            lineTo(c.x + inner, c.y - inner); lineTo(c.x + outer, c.y)
            lineTo(c.x + inner, c.y + inner); lineTo(c.x, c.y + outer)
            lineTo(c.x - inner, c.y + inner); lineTo(c.x - outer, c.y)
            lineTo(c.x - inner, c.y - inner); close()
        }
        drawPath(star(1f), Brush.radialGradient(listOf(Color.White, color, Color(0xFFE0561B)), c, r))
        drawPath(star(0.32f), Color(0xFF1A1F4A))
    }
}

/** Spray-painted "CHANGE ↗" (or any word) with a black outline. */
@Composable
fun Graffiti(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 44.sp, color: Color = Color.White) {
    val measurer = rememberTextMeasurer()
    val width = (fontSize.value * 0.62f * text.length + 70).dp
    val height = (fontSize.value * 1.5f).dp
    Canvas(modifier.rotate(-14f).size(width, height)) {
        val style = PersonaType.graffiti.copy(fontSize = fontSize)
        val layout = measurer.measure(text, style)
        drawText(layout, color = Color.Black, topLeft = Offset(4f, 5f))
        drawText(layout, color = color, topLeft = Offset.Zero)
        // Arrow flourish after the word.
        val x = layout.size.width.toFloat() + 6f
        val y = layout.size.height * 0.55f
        val arrow = Path().apply {
            moveTo(x, y); lineTo(x + 70f, y - 34f)
            moveTo(x + 40f, y - 38f); lineTo(x + 72f, y - 35f); lineTo(x + 62f, y - 4f)
        }
        drawPath(arrow, Color.Black, style = Stroke(16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(arrow, color, style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A grey band with torn top and bottom edges, the backing for player controls. */
fun Modifier.tornBand(color: Color = Color(0xFF6B6B6B), seed: Int = 5): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val random = Random(seed)
    val path = Path().apply {
        moveTo(0f, 18f)
        var x = 0f
        while (x < w) {
            x += 14f + random.nextFloat() * 26f
            lineTo(x.coerceAtMost(w), 4f + random.nextFloat() * 24f)
        }
        lineTo(w, h - 20f)
        while (x > 0f) {
            x -= 14f + random.nextFloat() * 26f
            lineTo(x.coerceAtLeast(0f), h - 4f - random.nextFloat() * 24f)
        }
        close()
    }
    val edge = Path().apply { addPath(path) }
    onDrawBehind {
        translate(0f, 6f) { drawPath(edge, Color.White) }
        drawPath(path, color)
    }
}

/** Jagged white vertical rips at the left and right edge (song stats screen). */
fun Modifier.rippedSides(seed: Int = 11): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val random = Random(seed)
    fun rip(baseX: Float, dir: Float) = Path().apply {
        moveTo(baseX, 0f)
        var y = 0f
        var x = baseX
        while (y < h) {
            y += 20f + random.nextFloat() * 50f
            x = baseX + dir * (random.nextFloat() * 36f)
            lineTo(x, y)
        }
        lineTo(baseX + dir * 48f, h)
        var y2 = h
        while (y2 > 0f) {
            y2 -= 20f + random.nextFloat() * 50f
            lineTo(baseX + dir * (18f + random.nextFloat() * 40f), y2.coerceAtLeast(0f))
        }
        close()
    }
    val left = rip(w * 0.05f, 1f)
    val right = rip(w * 0.95f, -1f)
    onDrawBehind {
        drawRect(Color.Black)
        drawPath(left, Color.White)
        drawPath(right, Color.White)
    }
}

/** Two-piece tilted stamp, e.g. "SONG" / "LOG" or "SPEED" / "DIAL". */
@Composable
fun StampLabel(top: String, bottom: String?, modifier: Modifier = Modifier, tilt: Float = -8f) {
    Column(modifier.rotate(tilt)) {
        Box(
            Modifier
                .background(Color.White, slantShape(0.06f))
                .border(3.dp, Color.Black, slantShape(0.06f))
                .padding(horizontal = 18.dp, vertical = 2.dp),
        ) {
            Text(top.uppercase(), color = Color.Black, style = TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = 30.sp, letterSpacing = 3.sp))
        }
        if (bottom != null) {
            Box(
                Modifier
                    .padding(start = 36.dp)
                    .rotate(10f)
                    .background(Color.Black, slantShape(0.08f))
                    .border(2.dp, Color.White, slantShape(0.08f))
                    .padding(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(bottom.uppercase(), color = Color.White, style = TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = 24.sp, letterSpacing = 2.sp))
            }
        }
    }
}

/** Small black box label in comic lettering ("PLAYING", "NEXT"). */
@Composable
fun ShoutLabel(text: String, modifier: Modifier = Modifier, tilt: Float = -4f) {
    Column(
        modifier
            .rotate(tilt)
            .background(Color.White)
            .padding(2.dp)
            .background(Color.Black)
            .padding(horizontal = 8.dp),
    ) {
        Text(text.uppercase(), color = Color.White, style = PersonaType.shout)
        JapaneseSubtitles.of(text)?.let { Text(it, color = Color.White.copy(alpha = 0.7f), style = PersonaType.caption.copy(fontSize = 9.sp)) }
    }
}

/** White glove silhouettes gripping a card from both sides (lyrics screen). */
fun Modifier.glovesHoldingCard(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val left = Path().apply {
        moveTo(0f, h * 0.25f)
        cubicTo(w * 0.12f, h * 0.22f, w * 0.16f, h * 0.3f, w * 0.13f, h * 0.38f)
        lineTo(w * 0.09f, h * 0.55f)
        cubicTo(w * 0.16f, h * 0.6f, w * 0.12f, h * 0.7f, w * 0.06f, h * 0.68f)
        lineTo(0f, h * 0.72f)
        close()
    }
    val right = Path().apply {
        moveTo(w, h * 0.48f)
        cubicTo(w * 0.86f, h * 0.47f, w * 0.83f, h * 0.53f, w * 0.88f, h * 0.56f)
        cubicTo(w * 0.82f, h * 0.58f, w * 0.83f, h * 0.64f, w * 0.9f, h * 0.65f)
        cubicTo(w * 0.85f, h * 0.68f, w * 0.88f, h * 0.73f, w * 0.94f, h * 0.72f)
        lineTo(w, h * 0.76f)
        close()
    }
    onDrawBehind {
        drawRect(Color.Black.copy(alpha = 0.92f))
        drawPath(left, Color.White)
        drawPath(right, Color.White)
        drawPath(right, Color.Black, style = Stroke(5f))
    }
}

/** The grinning music-note badge used as seek-bar thumb. */
fun DrawScope.drawGrinningNote(center: Offset, radius: Float, accent: Color) {
    val headCenter = Offset(center.x - radius * 0.15f, center.y + radius * 0.25f)
    val headR = radius * 0.62f
    // Stem and flag.
    val stem = Path().apply {
        moveTo(headCenter.x + headR * 0.75f, headCenter.y - headR * 0.2f)
        lineTo(headCenter.x + headR * 0.9f, center.y - radius * 1.05f)
        lineTo(headCenter.x + headR * 1.9f, center.y - radius * 0.55f)
        lineTo(headCenter.x + headR * 1.7f, center.y - radius * 0.3f)
        lineTo(headCenter.x + headR * 1.15f, center.y - radius * 0.6f)
        lineTo(headCenter.x + headR * 1.1f, headCenter.y)
        close()
    }
    drawPath(stem, accent, style = Stroke(radius * 0.32f, join = StrokeJoin.Round))
    drawPath(stem, Color.Black)
    drawCircle(accent, headR + radius * 0.14f, headCenter)
    drawCircle(Color.Black, headR, headCenter)
    // Zig-zag grin.
    val teeth = Path().apply {
        val left = headCenter.x - headR * 0.7f
        val right = headCenter.x + headR * 0.7f
        val top = headCenter.y - headR * 0.1f
        val bottom = headCenter.y + headR * 0.45f
        moveTo(left, top)
        val n = 6
        for (i in 1..n) {
            val x = left + (right - left) * i / n
            lineTo(x - (right - left) / n / 2, if (i % 2 == 1) bottom else top + headR * 0.15f)
            lineTo(x, top)
        }
        lineTo(right, top)
        quadraticTo(headCenter.x, headCenter.y + headR * 0.95f, left, top)
        close()
    }
    drawPath(teeth, Color.White)
}

/** Diagonal yellow lightning bolt linking a cover to its title bar (queue screen). */
fun DrawScope.drawConnectorBolt(from: Offset, width: Float, height: Float) {
    val bolt = Path().apply {
        moveTo(from.x, from.y - height * 0.3f)
        lineTo(from.x + width * 0.45f, from.y - height * 0.5f)
        lineTo(from.x + width * 0.35f, from.y - height * 0.05f)
        lineTo(from.x + width, from.y - height * 0.2f)
        lineTo(from.x + width * 0.55f, from.y + height * 0.5f)
        lineTo(from.x + width * 0.62f, from.y + height * 0.05f)
        lineTo(from.x, from.y + height * 0.3f)
        close()
    }
    drawPath(bolt, Color.White)
    drawPath(bolt, Color.Black, style = Stroke(3f))
}

