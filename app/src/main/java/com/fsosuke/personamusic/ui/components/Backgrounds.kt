package com.fsosuke.personamusic.ui.components

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
fun Modifier.starPattern(background: Color, starColor: Color, cell: Float = 140f, strokeWidth: Float = 5f): Modifier = drawWithCache {
    val star = unitStarPath()
    val random = Random(42)
    val width = size.width
    val height = size.height
    val stars = buildList {
        var y = -cell / 2
        var row = 0
        while (y < height + cell) {
            var x = if (row % 2 == 0) 0f else cell / 2
            while (x < width + cell) {
                add(Triple(Offset(x, y), cell * (0.35f + random.nextFloat() * 0.35f), random.nextFloat() * 72f))
                x += cell
            }
            y += cell * 0.8f
            row++
        }
    }
    onDrawBehind {
        drawRect(background)
        stars.forEach { (center, radius, angle) ->
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
    }
}

/** Comic-book halftone dots, used on the red playlist board. */
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
