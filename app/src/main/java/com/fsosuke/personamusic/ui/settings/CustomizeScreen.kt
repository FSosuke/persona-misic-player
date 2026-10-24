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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.fsosuke.personamusic.update.UpdateState

@Composable
fun CustomizeScreen(onBack: () -> Unit, onSignIn: () -> Unit, onOpenHidden: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val settings = container.settings
    val ytAccount by container.ytAccounts.account.collectAsStateWithLifecycle()
    val colors = LocalPersonaColors.current
    val theme by settings.theme.collectAsStateWithLifecycle()
    val quality by settings.quality.collectAsStateWithLifecycle()
    val skipNonMusic by settings.skipNonMusic.collectAsStateWithLifecycle()
    val playerBackground by settings.playerBackground.collectAsStateWithLifecycle()
    val autoplay by settings.autoplay.collectAsStateWithLifecycle()
    val syncLikes by settings.syncLikes.collectAsStateWithLifecycle()
    val reportHistory by settings.reportHistory.collectAsStateWithLifecycle()
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

        item { CalloutBanner("Recommendations", Modifier.padding(start = 12.dp, top = 20.dp), pointLeft = true) }
        item { RecommendationsSection(onOpenHidden) }
        item { ReleaseRadarSection() }

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
                SettingSwitch(
                    title = "Autoplay",
                    description = "When the queue ends, keep going with a radio of similar songs.",
                    checked = autoplay,
                    onChange = settings::setAutoplay,
                )
                SettingSwitch(
                    title = "Sync likes",
                    description = "Hearting a YouTube song also likes it on your YouTube Music account.",
                    checked = syncLikes,
                    onChange = settings::setSyncLikes,
                    enabled = ytAccount != null,
                )
                SettingSwitch(
                    title = "Add to YouTube history",
                    description = "Songs you listen to (30 s+) show up in your history, so YouTube Music recommendations keep learning.",
                    checked = reportHistory,
                    onChange = settings::setReportHistory,
                    enabled = ytAccount != null,
                )
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
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CalloutBanner("Updates & backup", Modifier.padding(end = 12.dp, top = 20.dp))
            }
        }
        item { DownloadFolderSection() }
        item { UpdatesSection() }
        item { BackupSection() }
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

@Composable
private fun UpdatesSection() {
    val updates = LocalAppContainer.current.updates
    val colors = LocalPersonaColors.current
    val state by updates.state.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Version ${updates.currentVersion}", color = Color.White, style = PersonaType.title)
            Text(
                when (val s = state) {
                    UpdateState.Checking -> "Checking…"
                    UpdateState.UpToDate -> "You're up to date."
                    is UpdateState.Available -> "Version ${s.update.version} is available – see Home."
                    is UpdateState.Downloading -> "Downloading ${(s.progress * 100).toInt()} %"
                    is UpdateState.Ready -> "Ready to install."
                    is UpdateState.Failed -> "Check failed: ${s.message}"
                    UpdateState.Idle -> "Checks automatically every few hours."
                },
                color = colors.muted,
                style = PersonaType.caption,
            )
        }
        Spacer(Modifier.width(12.dp))
        PersonaButton("Check", onClick = { updates.check() }, inverted = true)
    }
}

@Composable
private fun BackupSection() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { container.backup.export(uri) }.fold(
                { "Saved: ${it.songs} songs, ${it.playlists} playlists, ${it.plays} plays." },
                { "Export failed: ${it.localizedMessage}" },
            )
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { container.backup.import(uri) }.fold(
                { "Restored: ${it.songs} songs, ${it.playlists} playlists, ${it.plays} new plays." },
                { "Import failed: ${it.localizedMessage}" },
            )
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Backup", color = Color.White, style = PersonaType.title)
        Text(
            "Saves favourites, playlists, play history and theme to a file. Your YouTube login is not included.",
            color = colors.muted,
            style = PersonaType.caption,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonaButton("Export", onClick = {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                exporter.launch("persona-music-backup-$date.json")
            })
            PersonaButton("Import", onClick = { importer.launch(arrayOf("application/json", "*/*")) }, inverted = true)
        }
        message?.let { Text(it, color = Color.White, style = PersonaType.caption) }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = LocalPersonaColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = PersonaType.title)
            Text(description, color = colors.muted, style = PersonaType.caption)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

@Composable
private fun DownloadFolderSection() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val folder by container.settings.downloadFolder.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) container.downloads.setFolder(uri)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Download folder", color = Color.White, style = PersonaType.title)
            Text(
                if (folder == null) "Not chosen yet – you can also create a new folder in the picker."
                else container.downloads.folderLabel() ?: folder.orEmpty(),
                color = colors.muted,
                style = PersonaType.caption,
            )
        }
        Spacer(Modifier.width(12.dp))
        PersonaButton(if (folder == null) "Choose" else "Change", onClick = { picker.launch(null) }, inverted = true)
    }
}

@Composable
private fun RecommendationsSection(onOpenHidden: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val japaneseOnly by container.settings.japaneseOnly.collectAsStateWithLifecycle()
    val hidden by container.contentFilter.blocked.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingSwitch(
            title = "Japanese music only",
            description = "Home recommendations, radio and autoplay only keep songs with Japanese titles or artists – " +
                "plus artists you already listen to or like.",
            checked = japaneseOnly,
            onChange = container.settings::setJapaneseOnly,
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenHidden).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Hidden songs & artists", color = Color.White, style = PersonaType.title)
                Text(
                    if (hidden.isEmpty()) "Use \"Not interested\" or \"Hide artist\" in any song menu."
                    else "${hidden.size} hidden – tap to manage",
                    color = colors.muted,
                    style = PersonaType.caption,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF888888))
        }
    }
}

@Composable
private fun ReleaseRadarSection() {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    val enabled by container.settings.releaseRadar.collectAsStateWithLifecycle()
    val checking by container.releaseRadar.checking.collectAsStateWithLifecycle()
    val followed by container.releaseRadar.followed.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitch(
            title = "Release Radar",
            description = "Notifies you about new albums and singles of your confidants (rank 2+) and of artists you follow " +
                "(${followed.size} followed – use \"Follow\" on an artist page).",
            checked = enabled,
            onChange = container.settings::setReleaseRadar,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaButton(if (checking) "Checking…" else "Check now", onClick = {
                if (!checking) scope.launch {
                    val found = runCatching { container.releaseRadar.check() }
                    result = found.fold(
                        { if (it.isEmpty()) "Nothing new right now." else "${it.size} new: " + it.take(3).joinToString { r -> r.title } },
                        { "Check failed: ${it.localizedMessage}" },
                    )
                }
            }, inverted = true)
            Spacer(Modifier.width(12.dp))
            result?.let { Text(it, color = colors.muted, style = PersonaType.caption, modifier = Modifier.weight(1f)) }
        }
    }
}
