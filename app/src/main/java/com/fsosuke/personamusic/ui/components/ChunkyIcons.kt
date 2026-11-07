package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fsosuke.personamusic.ui.theme.PersonaFonts

enum class Chunky { Play, Pause, Next, Previous, Shuffle, Repeat, RepeatOne, Lyrics, Close, Heart, HeartFilled, Queue }

/**
 * Hand-drawn control icons in the P5 style: white shapes with a thick black outline,
 * drawn on a 24×24 grid.
 */
@Composable
fun ChunkyIcon(
    icon: Chunky,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fill: Color = Color.White,
    outline: Color = Color.Black,
    onClick: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = size * 0.7f),
            role = Role.Button,
            onClick = onClick,
        )
    } else Modifier
    Canvas(modifier.size(size).then(clickable)) {
        val u = this.size.minDimension / 24f
        fun p(vararg pts: Float) = Path().apply {
            for (i in pts.indices step 2) {
                if (i == 0) moveTo(pts[i] * u, pts[i + 1] * u) else lineTo(pts[i] * u, pts[i + 1] * u)
            }
            close()
        }
        fun DrawScope.chunk(vararg paths: Path) {
            paths.forEach { drawPath(it, outline, style = Stroke(3.2f * u, join = StrokeJoin.Round)) }
            paths.forEach { drawPath(it, fill) }
        }
        when (icon) {
            Chunky.Play -> chunk(p(6f, 3f, 21f, 12.5f, 5f, 21f))
            Chunky.Pause -> chunk(p(7f, 3f, 12f, 3f, 9.5f, 21f, 4.5f, 21f), p(16f, 3f, 21f, 3f, 18.5f, 21f, 13.5f, 21f))
            Chunky.Next -> chunk(
                p(2f, 5f, 6f, 4f, 12.5f, 12f, 6f, 20f, 2f, 19f, 7f, 12f),
                p(11f, 5f, 15f, 4f, 21.5f, 12f, 15f, 20f, 11f, 19f, 16f, 12f),
            )
            Chunky.Previous -> chunk(
                p(22f, 5f, 18f, 4f, 11.5f, 12f, 18f, 20f, 22f, 19f, 17f, 12f),
                p(13f, 5f, 9f, 4f, 2.5f, 12f, 9f, 20f, 13f, 19f, 8f, 12f),
            )
            Chunky.Lyrics -> chunk(p(10f, 2f, 17f, 3f, 13f, 15f, 8.5f, 14f), p(8f, 17f, 12f, 17.5f, 11f, 22f, 6.5f, 21f))
            Chunky.Close -> {
                val x = p(4f, 7f, 7f, 4f, 12f, 9f, 17f, 4f, 20f, 7f, 15f, 12f, 20f, 17f, 17f, 20f, 12f, 15f, 7f, 20f, 4f, 17f, 9f, 12f)
                drawPath(x, fill, style = Stroke(2.4f * u, join = StrokeJoin.Miter))
            }
            Chunky.Heart, Chunky.HeartFilled -> {
                val heart = Path().apply {
                    moveTo(12f * u, 21f * u)
                    cubicTo(2f * u, 14f * u, 1f * u, 6f * u, 6.5f * u, 4f * u)
                    cubicTo(9.5f * u, 3f * u, 11.5f * u, 5f * u, 12f * u, 7f * u)
                    cubicTo(12.5f * u, 5f * u, 15f * u, 2.5f * u, 18.5f * u, 4f * u)
                    cubicTo(23f * u, 6f * u, 22f * u, 14f * u, 12f * u, 21f * u)
                    close()
                }
                if (icon == Chunky.HeartFilled) chunk(heart) else {
                    drawPath(heart, outline, style = Stroke(4.4f * u, join = StrokeJoin.Round))
                    drawPath(heart, fill, style = Stroke(2f * u, join = StrokeJoin.Round))
                }
            }
            Chunky.Repeat, Chunky.RepeatOne -> {
                val loop = Path().apply {
                    moveTo(5f * u, 15f * u)
                    cubicTo(2f * u, 8f * u, 9f * u, 3f * u, 17f * u, 6.5f * u)
                    moveTo(19f * u, 9f * u)
                    cubicTo(22f * u, 16f * u, 15f * u, 21f * u, 7f * u, 17.5f * u)
                }
                drawPath(loop, outline, style = Stroke(5.5f * u, cap = StrokeCap.Round))
                drawPath(loop, fill, style = Stroke(2.6f * u, cap = StrokeCap.Round))
                chunk(p(14f, 2.5f, 21f, 6.5f, 14.5f, 10.5f), p(10f, 13.5f, 3f, 17.5f, 9.5f, 21.5f))
                if (icon == Chunky.RepeatOne) {
                    // Big "1" badge so repeat-one never looks like repeat-all.
                    val badge = Offset(17.5f * u, 17.5f * u)
                    drawCircle(outline, 7.2f * u, badge)
                    drawCircle(Color.White, 6f * u, badge)
                    val layout = measurer.measure("1", TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = (9.5f * u).toSp()))
                    drawText(
                        layout,
                        color = Color.Black,
                        topLeft = Offset(badge.x - layout.size.width / 2f, badge.y - layout.size.height / 2f),
                    )
                }
            }
            Chunky.Shuffle -> {
                val layout = measurer.measure("?", TextStyle(fontFamily = PersonaFonts.bangers, fontSize = (26f * u).toSp()))
                val origin = Offset((this.size.width - layout.size.width) / 2, (this.size.height - layout.size.height) / 2)
                for (dx in -2..2) for (dy in -2..2) {
                    drawText(layout, color = outline, topLeft = origin + Offset(dx * 0.9f * u, dy * 0.9f * u))
                }
                drawText(layout, color = fill, topLeft = origin)
            }
            Chunky.Queue -> chunk(
                p(3f, 4f, 21f, 3f, 20f, 7f, 3.5f, 8f),
                p(3f, 10.5f, 21f, 9.5f, 20f, 13.5f, 3.5f, 14.5f),
                p(3f, 17f, 15f, 16f, 14f, 20f, 3.5f, 21f),
            )
        }
    }
}
