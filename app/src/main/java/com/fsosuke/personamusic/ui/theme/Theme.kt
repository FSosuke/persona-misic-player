package com.fsosuke.personamusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

object PersonaType {
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 18.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
fun PersonaMusicTheme(theme: PersonaTheme, content: @Composable () -> Unit) {
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
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
