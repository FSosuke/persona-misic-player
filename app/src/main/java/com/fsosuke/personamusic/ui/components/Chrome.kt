package com.fsosuke.personamusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType

/** Star-pattern top area with a ransom-note title, optional action and extra content (e.g. search). */
@Composable
fun PersonaHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalPersonaColors.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().starPattern(colors.patternBackground, colors.pattern)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(72.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .rotate(-3f)
                            .background(Color.Black)
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        RansomText(title, fontSize = 40.sp, style = RansomStyle.Outline)
                    }
                    if (action != null) {
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { action() }
                    }
                }
                content()
            }
        }
        TornDivider()
    }
}

/** The slanted red/white slash that separates the header from the content. */
@Composable
fun TornDivider(modifier: Modifier = Modifier, flipped: Boolean = false) {
    val colors = LocalPersonaColors.current
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val w = size.width
        val h = size.height
        fun x(f: Float) = if (flipped) w * (1 - f) else w * f
        val red = Path().apply {
            moveTo(x(0.35f), 0f); lineTo(x(1f), 0f); lineTo(x(1f), h * 0.9f); close()
        }
        val white = Path().apply {
            moveTo(x(0f), h * 0.55f); lineTo(x(1f), h * 0.05f); lineTo(x(1f), h * 0.3f); lineTo(x(0f), h * 0.8f); close()
        }
        drawRect(colors.background)
        drawPath(red, colors.accent)
        drawPath(white, Color.White)
    }
}

/** Slanted black search box with a red "SEARCH" tag. */
@Composable
fun PersonaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val colors = LocalPersonaColors.current
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .padding(start = 18.dp)
                .rotate(-4f)
                .background(colors.accent, slantShape(0.12f))
                .padding(horizontal = 18.dp, vertical = 2.dp),
        ) {
            RansomText("Search", fontSize = 18.sp, useAccent = false)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .rotate(-1.2f)
                .background(Color.White, slantShape(0.015f))
                .padding(4.dp)
                .background(Color.Black, slantShape(0.015f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF8A8A8A), style = PersonaType.body)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFFAAAAAA))
                }
            }
        }
    }
}

/** "— — — TOP RESULT — — —" */
@Composable
fun DashedSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("—  —  —", color = Color.White, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.rotate(-2f).border(2.dp, Color.White).background(Color.Black).padding(horizontal = 6.dp, vertical = 2.dp)) {
            RansomText(title, fontSize = 24.sp, style = RansomStyle.Outline)
        }
        Spacer(Modifier.width(12.dp))
        Text("—  —  —", color = Color.White, fontWeight = FontWeight.Black)
    }
}

/** The angular speech-bubble labels ("RECENT", "MOST PLAYED") from the P5 menus. */
@Composable
fun CalloutBanner(text: String, modifier: Modifier = Modifier, pointLeft: Boolean = false) {
    val outer = if (pointLeft) {
        PolygonShape(listOf(Offset(0.08f, 0.1f), Offset(1f, 0f), Offset(0.95f, 0.85f), Offset(0.25f, 0.8f), Offset(0f, 1f), Offset(0.12f, 0.75f)))
    } else {
        PolygonShape(listOf(Offset(0f, 0f), Offset(0.92f, 0.1f), Offset(0.88f, 0.75f), Offset(1f, 1f), Offset(0.75f, 0.8f), Offset(0.05f, 0.85f)))
    }
    Box(
        modifier
            .rotate(if (pointLeft) -6f else 6f)
            .background(Color.White, outer)
            .padding(5.dp)
            .background(Color.Black, outer)
            .padding(start = 28.dp, end = 36.dp, top = 10.dp, bottom = 18.dp),
    ) {
        Text(text.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 20.sp))
    }
}

@Composable
fun PersonaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, inverted: Boolean = false) {
    val colors = LocalPersonaColors.current
    Box(
        modifier
            .rotate(-2f)
            .background(if (inverted) Color.White else colors.accent, slantShape(0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (inverted) Color.Black else colors.onAccent,
            style = PersonaType.label,
        )
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RansomText(title, fontSize = 26.sp)
        Text(message, color = LocalPersonaColors.current.muted, style = PersonaType.body)
        action?.invoke()
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
