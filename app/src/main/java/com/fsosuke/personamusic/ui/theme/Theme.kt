package com.fsosuke.personamusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.PersonaTheme

@Immutable
data class PersonaColors(
    val accent: Color,
    val onAccent: Color,
    val highlight: Color,
    val background: Color = Color(0xFF0B0B0B),
    val surface: Color = Color(0xFF161616),
    val pattern: Color = Color(0xFF2B2B2B),
    val patternBackground: Color = Color(0xFF1A1A1A),
    val ink: Color = Color.White,
    val muted: Color = Color(0xFFB8B8B8),
)

fun PersonaTheme.colors(): PersonaColors = when (this) {
    PersonaTheme.P5 -> PersonaColors(accent = Color(0xFFE60012), onAccent = Color.White, highlight = Color(0xFF2DD4E8))
    PersonaTheme.ROYAL -> PersonaColors(accent = Color(0xFFC8102E), onAccent = Color.White, highlight = Color(0xFFFFC72C))
    PersonaTheme.P4 -> PersonaColors(accent = Color(0xFFFFD400), onAccent = Color.Black, highlight = Color(0xFFFF7A00))
    PersonaTheme.ZERO_ONE -> PersonaColors(
        accent = Color(0xFFFFE100),
        onAccent = Color.Black,
        highlight = Color(0xFF00D1FF),
        background = Color(0xFF050608),
        surface = Color(0xFF101214),
        pattern = Color(0xFF1C2B30),
        patternBackground = Color(0xFF0A0F11),
        muted = Color(0xFFA9B4B9),
    )
    PersonaTheme.P3 -> PersonaColors(
        accent = Color(0xFF1C7CF4),
        onAccent = Color.White,
        highlight = Color(0xFF8CF2FF),
        background = Color(0xFF060B16),
        surface = Color(0xFF0E1628),
        pattern = Color(0xFF1C2A44),
        patternBackground = Color(0xFF0C1424),
    )
}

val LocalPersonaColors = staticCompositionLocalOf { PersonaTheme.P5.colors() }

/** Text styles; they follow the active skin (Persona or Zero-One). */
object PersonaType {
    val title get() = if (Skin.zeroOne) ZeroOne.title else Persona.title
    val body get() = if (Skin.zeroOne) ZeroOne.body else Persona.body
    val caption get() = if (Skin.zeroOne) ZeroOne.caption else Persona.caption
    val label get() = if (Skin.zeroOne) ZeroOne.label else Persona.label
    /** Big condensed headline, e.g. "NEXT", "STATS". */
    val display get() = if (Skin.zeroOne) ZeroOne.display else Persona.display
    /** Comic-style shout, e.g. "PLAYING". */
    val shout get() = if (Skin.zeroOne) ZeroOne.shout else Persona.shout
    /** Spray-painted graffiti, e.g. "CHANGE". */
    val graffiti get() = if (Skin.zeroOne) ZeroOne.graffiti else Persona.graffiti

    private object Persona {
        val title = TextStyle(fontFamily = PersonaFonts.rubik, fontWeight = FontWeight.Black, fontSize = 18.sp)
        val body = TextStyle(fontFamily = PersonaFonts.rubik, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        val caption = TextStyle(fontFamily = PersonaFonts.rubik, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        val label = TextStyle(
            fontFamily = PersonaFonts.archivoBlack,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            letterSpacing = 1.sp,
        )
        val display = TextStyle(fontFamily = PersonaFonts.anton, fontSize = 44.sp, letterSpacing = 1.sp)
        val shout = TextStyle(fontFamily = PersonaFonts.bangers, fontSize = 22.sp, letterSpacing = 1.sp)
        val graffiti = TextStyle(fontFamily = PersonaFonts.marker, fontSize = 40.sp)
    }

    /** Stencil, slanted forward like the Zero-One logo lettering. */
    private object ZeroOne {
        val title = TextStyle(fontFamily = ZeroOneFonts.saira, fontWeight = FontWeight.Black, fontSize = 19.sp)
        val body = TextStyle(fontFamily = ZeroOneFonts.saira, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val caption = TextStyle(fontFamily = ZeroOneFonts.saira, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        val label = TextStyle(fontFamily = ZeroOneFonts.stencil, fontStyle = FontStyle.Italic, fontSize = 16.sp, letterSpacing = 1.5.sp)
        val display = TextStyle(fontFamily = ZeroOneFonts.stencil, fontStyle = FontStyle.Italic, fontSize = 44.sp, letterSpacing = 1.sp)
        val shout = TextStyle(fontFamily = ZeroOneFonts.stencil, fontStyle = FontStyle.Italic, fontSize = 22.sp, letterSpacing = 1.sp)
        val graffiti = TextStyle(fontFamily = ZeroOneFonts.stencil, fontStyle = FontStyle.Italic, fontSize = 40.sp)
    }
}

@Composable
fun PersonaMusicTheme(theme: PersonaTheme, content: @Composable () -> Unit) {
    SideEffect { Skin.zeroOne = theme == PersonaTheme.ZERO_ONE }
    val colors = theme.colors()
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.highlight,
        background = colors.background,
        surface = colors.surface,
        onSurface = colors.ink,
        onBackground = colors.ink,
        surfaceContainerHigh = Color(0xFF111111),
        surfaceContainer = Color(0xFF111111),
    )
    CompositionLocalProvider(LocalPersonaColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography().withFont(if (theme == PersonaTheme.ZERO_ONE) ZeroOneFonts.saira else PersonaFonts.rubik),
            content = content,
        )
    }
}

private fun Typography.withFont(family: androidx.compose.ui.text.font.FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
