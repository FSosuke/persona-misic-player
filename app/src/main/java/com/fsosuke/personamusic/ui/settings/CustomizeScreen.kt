package com.fsosuke.personamusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.AudioQuality
import com.fsosuke.personamusic.data.PersonaTheme
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.theme.colors

@Composable
fun CustomizeScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val settings = LocalAppContainer.current.settings
    val colors = LocalPersonaColors.current
    val theme by settings.theme.collectAsStateWithLifecycle()
    val quality by settings.quality.collectAsStateWithLifecycle()
    val skipNonMusic by settings.skipNonMusic.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Box {
                PersonaHeader("Customize")
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }

        item { CalloutBanner("Theme", Modifier.padding(start = 12.dp, top = 12.dp), pointLeft = true) }
        items(PersonaTheme.entries) { option ->
            val optionColors = option.colors()
            val selected = option == theme
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .rotate(if (selected) -1.5f else 0f)
                    .background(if (selected) Color.White else Color(0xFF222222), slantShape(0.02f))
                    .padding(3.dp)
                    .background(Color.Black, slantShape(0.02f))
                    .clickable { settings.setTheme(option) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).background(optionColors.accent).border(2.dp, Color.White))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(28.dp).background(optionColors.highlight).border(2.dp, Color.White))
                Spacer(Modifier.width(14.dp))
                Text(option.label, color = Color.White, style = PersonaType.body, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = optionColors.accent)
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CalloutBanner("YouTube Music", Modifier.padding(end = 12.dp, top = 20.dp))
            }
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Audio quality", color = Color.White, style = PersonaType.title)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AudioQuality.entries.forEach { option ->
                        val selected = option == quality
                        Box(
                            Modifier
                                .rotate(if (selected) -3f else 0f)
                                .background(if (selected) colors.accent else Color(0xFF222222), slantShape(0.08f))
                                .clickable { settings.setQuality(option) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(option.label, color = if (selected) colors.onAccent else Color.White, style = PersonaType.body)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip non-music parts", color = Color.White, style = PersonaType.title)
                        Text(
                            "Uses SponsorBlock to jump over intros, skits and talking in music videos — like ReVanced.",
                            color = colors.muted,
                            style = PersonaType.caption,
                        )
                    }
                    Switch(
                        checked = skipNonMusic,
                        onCheckedChange = settings::setSkipNonMusic,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                    )
                }
            }
        }

        item {
            Column(
                Modifier
                    .padding(20.dp)
                    .rotate(-1f)
                    .background(Color.White)
                    .padding(4.dp)
                    .background(Color.Black)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("NO ADS. EVER.", color = colors.accent, style = PersonaType.label.copy(fontSize = 20.sp))
                Text(
                    "Songs are streamed straight from YouTube's audio servers via NewPipeExtractor. " +
                        "The ad pipeline of the official app is never touched, so there is nothing to skip. " +
                        "Playback keeps running in the background and on the lock screen.",
                    color = Color.White,
                    style = PersonaType.caption,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
