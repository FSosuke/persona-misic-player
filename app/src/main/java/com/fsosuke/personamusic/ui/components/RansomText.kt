package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaFonts
import kotlin.random.Random

enum class RansomStyle {
    /** Cut-out letters on black and white paper scraps, like Persona 5 calling cards. */
    Collage,
    /** Mixed fonts in plain white, no paper scraps (screen titles). */
    Outline,
}

private enum class Paper { Black, White, Accent, None }

private data class RansomLetter(
    val char: Char,
    val paper: Paper,
    val family: FontFamily,
    val weight: FontWeight,
    val italic: Boolean,
    val rotation: Float,
    val scale: Float,
    val lift: Float,
)

private val families = PersonaFonts.ransom

/**
 * The Persona 5 "ransom note" look: every letter gets its own font, size, tilt and paper scrap.
 * Randomness is seeded by the text so a title never flickers between recompositions.
 */
@Composable
fun RansomText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    style: RansomStyle = RansomStyle.Collage,
    useAccent: Boolean = true,
) {
    val colors = LocalPersonaColors.current
    val letters = remember(text, style, useAccent) {
        val random = Random(text.hashCode())
        text.uppercase().map { c ->
            val paper = when (style) {
                RansomStyle.Outline -> Paper.None
                RansomStyle.Collage -> when (random.nextInt(10)) {
                    in 0..4 -> Paper.Black
                    in 5..7 -> Paper.White
                    8 -> if (useAccent) Paper.Accent else Paper.Black
                    else -> Paper.None
                }
            }
            RansomLetter(
                char = c,
                paper = paper,
                family = families[random.nextInt(families.size)],
                weight = if (random.nextBoolean()) FontWeight.Black else FontWeight.Bold,
                italic = random.nextInt(4) == 0,
                rotation = random.nextFloat() * 14f - 7f,
                scale = 0.85f + random.nextFloat() * 0.3f,
                lift = random.nextFloat() * 4f - 2f,
            )
        }
    }
    val spaceWidth = with(LocalDensity.current) { (fontSize * 0.4f).toDp() }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        letters.forEach { letter ->
            if (letter.char == ' ') {
                Spacer(Modifier.width(spaceWidth))
                return@forEach
            }
            val (bg, fg) = when (letter.paper) {
                Paper.Black -> Color.Black to Color.White
                Paper.White -> Color.White to Color.Black
                Paper.Accent -> colors.accent to colors.onAccent
                Paper.None -> Color.Transparent to Color.White
            }
            Box(
                Modifier
                    .graphicsLayer {
                        rotationZ = letter.rotation
                        translationY = letter.lift * density
                    }
                    .background(bg)
                    .padding(horizontal = 2.dp),
            ) {
                Text(
                    text = letter.char.toString(),
                    color = fg,
                    fontSize = fontSize * letter.scale,
                    fontFamily = letter.family,
                    fontWeight = letter.weight,
                    fontStyle = if (letter.italic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = fontSize * 1.05f,
                )
            }
        }
    }
}
