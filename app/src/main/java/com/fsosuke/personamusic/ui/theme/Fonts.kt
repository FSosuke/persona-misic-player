package com.fsosuke.personamusic.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fsosuke.personamusic.R

/**
 * Free (OFL / Apache 2.0) Google Fonts picked to echo Persona 5's menus: heavy condensed
 * display faces for the cut-out letters, a marker for graffiti, and Rubik for UI text.
 */
object PersonaFonts {
    val anton = FontFamily(Font(R.font.p5_anton))
    val bangers = FontFamily(Font(R.font.p5_bangers))
    val marker = FontFamily(Font(R.font.p5_marker))
    val archivoBlack = FontFamily(Font(R.font.p5_archivo_black))
    val abril = FontFamily(Font(R.font.p5_abril))
    val specialElite = FontFamily(Font(R.font.p5_special_elite))
    val rubikMono = FontFamily(Font(R.font.p5_rubik_mono))
    val rubik = FontFamily(
        Font(R.font.p5_rubik_medium, FontWeight.Medium),
        Font(R.font.p5_rubik_medium, FontWeight.Normal),
        Font(R.font.p5_rubik_bold, FontWeight.Bold),
        Font(R.font.p5_rubik_bold, FontWeight.SemiBold),
        Font(R.font.p5_rubik_black, FontWeight.Black),
        Font(R.font.p5_rubik_black, FontWeight.ExtraBold),
    )

    /** Faces mixed letter-by-letter in ransom-note titles. */
    val ransom = listOf(anton, archivoBlack, abril, bangers, rubikMono, specialElite, anton, archivoBlack)
}
