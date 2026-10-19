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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.fsosuke.personamusic.ui.components.DuotoneImage
import com.fsosuke.personamusic.ui.components.importBackground
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.AudioQuality
import com.fsosuke.personamusic.data.PersonaTheme
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.theme.colors

@Composable
fun CustomizeScreen(onBack: () -> Unit, onSignIn: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val settings = container.settings
    val ytAccount by container.ytAccounts.account.collectAsStateWithLifecycle()
    val colors = LocalPersonaColors.current
    val theme by settings.theme.collectAsStateWithLifecycle()
    val quality by settings.quality.collectAsStateWithLifecycle()
    val skipNonMusic by settings.skipNonMusic.collectAsStateWithLifecycle()
    val playerBackground by settings.playerBackground.collectAsStateWithLifecycle()
    val homeBackground by settings.homeBackground.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = picking
        picking = null
        if (uri != null && target != null) {
            scope.launch {
                val path = importBackground(context, uri, target) ?: return@launch
                if (target == "player") settings.setPlayerBackground(path) else settings.setHomeBackground(path)
            }
        }
    }
    fun pick(target: String) {
        picking = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

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
                CalloutBanner("Backgrounds", Modifier.padding(end = 12.dp, top = 20.dp))
            }
        }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Use your own artwork (e.g. a Persona 5 wallpaper). It is inked in black & white and, " +
                        "in the player, tinted in the theme colour so it fits the style.",
                    color = colors.muted,
                    style = PersonaType.caption,
                )
                BackgroundRow("Player", playerBackground, colors.accent, onPick = { pick("player") }, onRemove = { settings.setPlayerBackground(null) })
                BackgroundRow("Home", homeBackground, null, onPick = { pick("home") }, onRemove = { settings.setHomeBackground(null) })
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CalloutBanner("YouTube Music", Modifier.padding(end = 12.dp, top = 20.dp))
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(Color.White, slantShape(0.02f))
                    .padding(3.dp)
                    .background(Color.Black, slantShape(0.02f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val account = ytAccount
                if (account != null) {
                    Artwork(account.photoUrl, Modifier.size(48.dp), shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, color = Color.White, style = PersonaType.title)
                        account.handle?.let { Text(it, color = colors.muted, style = PersonaType.caption) }
                    }
                    PersonaButton("Sign out", onClick = container.ytAccounts::signOut, inverted = true)
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Not connected", color = Color.White, style = PersonaType.title)
                        Text(
                            "Sign in for your recommendations and playlists on Home and the playlist board.",
                            color = colors.muted,
                            style = PersonaType.caption,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PersonaButton("Sign in", onClick = onSignIn)
                }
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
        item { Watermark() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BackgroundRow(label: String, path: String?, tint: Color?, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 64.dp, height = 96.dp).border(3.dp, Color.White).background(Color(0xFF1A1A1A))) {
            if (path != null) DuotoneImage(path, tint = tint, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, style = PersonaType.title, modifier = Modifier.weight(1f))
        if (path != null) {
            Text(
                "REMOVE",
                color = Color(0xFFAAAAAA),
                style = PersonaType.label,
                modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
            )
        }
        PersonaButton(if (path == null) "Pick" else "Change", onClick = onPick)
    }
}

/** The author's calling-card signature at the bottom of the settings. */
@Composable
private fun Watermark() {
    val context = LocalContext.current
    val colors = LocalPersonaColors.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .rotate(-4f)
                .alpha(0.85f)
                .background(Color.White)
                .padding(3.dp)
                .background(Color.Black)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("crafted by", color = colors.muted, style = PersonaType.caption.copy(fontSize = 11.sp))
            Text("FSosuke", color = Color.White, style = PersonaType.graffiti.copy(fontSize = 30.sp))
            Text(
                "PERSONA MUSIC $version",
                color = colors.accent,
                style = PersonaType.label.copy(fontSize = 11.sp, letterSpacing = 2.sp),
            )
        }
    }
}
