package com.fsosuke.personamusic.ui.components

import androidx.compose.runtime.State
import com.fsosuke.personamusic.ui.theme.Skin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A star path with outer radius 1 centred on the origin. */
fun unitStarPath(innerRatio: Float = 0.45f, points: Int = 5): Path = Path().apply {
    for (i in 0 until points * 2) {
        val angle = -PI / 2 + i * PI / points
        val r = if (i % 2 == 0) 1f else innerRatio
        val x = (cos(angle) * r).toFloat()
        val y = (sin(angle) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The nested-star wallpaper behind Persona 5 menus. */
fun Modifier.starPattern(
    background: Color,
    starColor: Color,
    cell: Float = 140f,
    strokeWidth: Float = 5f,
    /** Let the stars wander slowly to the right (menu headers). */
    drifting: Boolean = false,
): Modifier = composed {
    val drift = if (drifting) rememberDrift(periodMs = 40_000) else null
    if (Skin.zeroOne) return@composed hexCircuit(background, starColor, cell * 0.55f, drift)
    drawWithCache {
        val star = unitStarPath()
        val random = Random(42)
        val width = size.width
        val height = size.height
        val stars = buildList {
            var y = -cell / 2
            var row = 0
            while (y < height + cell) {
                var x = if (row % 2 == 0) 0f else cell / 2
                // A drifting pattern repeats every screen width, so it stops at the edge.
                while (x < width + if (drift == null) cell else 0f) {
                    add(Triple(Offset(x, y), cell * (0.35f + random.nextFloat() * 0.35f), random.nextFloat() * 72f))
                    x += cell
                }
                y += cell * 0.8f
                row++
            }
        }
        fun DrawScope.drawStars() = stars.forEach { (center, radius, angle) ->
            translate(center.x, center.y) {
                rotate(angle, pivot = Offset.Zero) {
                    for (ring in 0 until 3) {
                        val r = radius * (1f - ring * 0.3f)
                        scale(r, r, pivot = Offset.Zero) {
                            drawPath(star, starColor, style = Stroke(width = strokeWidth / r))
                        }
                    }
                }
            }
        }
        onDrawBehind {
            drawRect(background)
            if (drift == null) drawStars() else drawDrifting(drift.value * width, width) { drawStars() }
        }
    }
}

fun Modifier.halftone(background: Color, dotColor: Color, step: Float = 16f, dotSize: Float = 6f): Modifier =
    drawWithCache {
        val width = size.width
        val height = size.height
        val points = buildList {
            var y = 0f
            var row = 0
            while (y < height + step) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < width + step) {
                    add(Offset(x, y))
                    x += step
                }
                y += step * 0.866f
                row++
            }
        }
        onDrawBehind {
            drawRect(background)
            drawPoints(points, PointMode.Points, dotColor, strokeWidth = dotSize, cap = StrokeCap.Round)
        }
    }

/** A polygon whose corners are given as fractions (0..1) of the layout size. */
class PolygonShape(private val points: List<Offset>) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = p.x * size.width
            val y = p.y * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Parallelogram leaning right by [skew] of the width. */
fun slantShape(skew: Float = 0.05f) = PolygonShape(
    listOf(Offset(skew, 0f), Offset(1f, 0f), Offset(1f - skew, 1f), Offset(0f, 1f))
)

/** A slightly irregular quadrilateral, like a torn-out card. */
fun jaggedCardShape(seed: Int): PolygonShape {
    val r = Random(seed)
    fun j() = r.nextFloat() * 0.05f
    return PolygonShape(listOf(Offset(j(), j()), Offset(1f - j(), j()), Offset(1f - j(), 1f - j()), Offset(j(), 1f - j())))
}

/**
 * Zero-One skin backdrop: a honeycomb of thin hexagons with a few glowing circuit traces, like
 * the Hiden Intelligence screens. Repeats every screen width when [drift] moves it.
 */
private fun Modifier.hexCircuit(background: Color, lineColor: Color, radius: Float, drift: State<Float>?): Modifier = drawWithCache {
    val width = size.width
    val height = size.height
    val hexWidth = radius * kotlin.math.sqrt(3f)
    val hexes = Path().apply {
        var row = 0
        var y = 0f
        while (y < height + radius * 2) {
            var x = if (row % 2 == 0) 0f else hexWidth / 2
            while (x < width) {
                for (i in 0..6) {
                    val angle = Math.toRadians((60.0 * i) - 30.0)
                    val px = x + radius * kotlin.math.cos(angle).toFloat()
                    val py = y + radius * kotlin.math.sin(angle).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                x += hexWidth
            }
            y += radius * 1.5f
            row++
        }
    }
    val random = Random(7)
    val traces = (0 until 6).map {
        Path().apply {
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height
            moveTo(x, y)
            repeat(4) { step ->
                if (step % 2 == 0) x += (random.nextFloat() - 0.3f) * width * 0.4f else y += (random.nextFloat() - 0.5f) * height * 0.3f
                lineTo(x, y)
            }
        }
    }
    // Hiden-blue circuit traces.
    val traceColor = Color(0xFF00D1FF).copy(alpha = 0.35f)
    onDrawBehind {
        drawRect(background)
        val draw: DrawScope.() -> Unit = {
            drawPath(hexes, lineColor, style = Stroke(width = 2f))
            traces.forEach { drawPath(it, traceColor, style = Stroke(width = 3f)) }
        }
        if (drift == null) draw() else drawDrifting(drift.value * width, width, draw)
    }
}
