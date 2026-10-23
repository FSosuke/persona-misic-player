package com.fsosuke.personamusic.ui.confidant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Arcana
import com.fsosuke.personamusic.data.Confidants
import com.fsosuke.personamusic.ui.components.unitStarPath
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts

/**
 * A tarot-style arcana card: roman numeral on top, a drawn emblem, arcana name at the bottom.
 * [textScale] sizes all text, so the same card works as a thumbnail and as the big rank-up card.
 */
@Composable
fun ArcanaCard(arcana: Arcana, modifier: Modifier = Modifier, textScale: Float = 1f) {
    val colors = LocalPersonaColors.current
    Box(
        modifier
            .aspectRatio(0.62f)
            .background(Color.White)
            .padding((4 * textScale).dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1B1B), Color.Black)))
            .border((1.5f * textScale).dp, colors.accent),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = (8 * textScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(arcana.numeral, color = Color.White, style = TextStyle(fontFamily = PersonaFonts.abril, fontSize = (20 * textScale).sp))
            Canvas(Modifier.fillMaxWidth(0.7f).aspectRatio(1f)) {
                val r = size.minDimension / 2
                drawCircle(colors.accent, r, style = Stroke(r * 0.06f))
                // A star rotated by the arcana number, so every card looks a little different.
                val star = unitStarPath(innerRatio = 0.42f)
                translate(center.x, center.y) {
                    rotate(arcana.ordinal * 16f, pivot = Offset.Zero) {
                        scale(r * 0.82f, r * 0.82f, pivot = Offset.Zero) { drawPath(star, Color.White) }
                        scale(r * 0.4f, r * 0.4f, pivot = Offset.Zero) { drawPath(star, colors.accent) }
                    }
                }
            }
            Text(
                arcana.title.uppercase(),
                color = Color.White,
                style = TextStyle(fontFamily = PersonaFonts.archivoBlack, fontSize = (11 * textScale).sp, letterSpacing = (1 * textScale).sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ten rank pips like the confidant menu; filled up to [rank]. */
@Composable
fun RankPips(rank: Int, modifier: Modifier = Modifier, pip: Dp = 8.dp) {
    val colors = LocalPersonaColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(pip / 3)) {
        repeat(Confidants.MAX_RANK) { i ->
            Box(
                Modifier
                    .size(width = pip, height = pip * 1.5f)
                    .background(if (i < rank) colors.accent else Color(0xFF333333))
                    .border(1.dp, Color.White.copy(alpha = if (i < rank) 1f else 0.3f)),
            )
        }
    }
}
