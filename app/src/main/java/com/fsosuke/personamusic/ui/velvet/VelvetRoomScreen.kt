package com.fsosuke.personamusic.ui.velvet

import com.fsosuke.personamusic.ui.theme.displayName
import com.fsosuke.personamusic.ui.theme.Skin
import com.fsosuke.personamusic.ui.theme.Lingo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsosuke.personamusic.data.ListeningReading
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.VelvetRoom
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.RansomStyle
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.components.ShoutLabel
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.popIn
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.velvet.IgorPicks
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// Velvet blue and gold – or Zea's dark teal and Zero-One yellow in that skin.
private val VelvetBlue get() = if (Skin.zeroOne) Color(0xFF0B2A30) else Color(0xFF0B1E5B)
private val VelvetDeep get() = if (Skin.zeroOne) Color(0xFF030A0C) else Color(0xFF050B24)
private val VelvetGold get() = if (Skin.zeroOne) Color(0xFFFFE100) else Color(0xFFE8C766)

/** The Velvet Room: Igor reads your listening habits and hands out picks. */
@Composable
fun VelvetRoomScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerState by container.player.state.collectAsStateWithLifecycle()

    val reading by produceState<ListeningReading?>(null) {
        val now = System.currentTimeMillis()
        val plays = container.repository.playsBetween(now - 90L * 24 * 60 * 60 * 1000, now)
        value = VelvetRoom.read(plays, container.repository.likedSongIds().size, now)
    }
    var picksId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { picksId = container.repository.playlistIdByName(IgorPicks.PLAYLIST) }
    val picks by remember(picksId) { picksId?.let { container.repository.playlistSongs(it) } ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val working by container.igorPicks.working.collectAsStateWithLifecycle()
    val refreshed by container.igorPicks.lastRefresh.collectAsStateWithLifecycle()

    fun refreshPicks() = scope.launch {
        val result = runCatching { container.igorPicks.refresh() }
        // The playlist only exists after the first picks.
        if (picksId == null) picksId = container.repository.playlistIdByName(IgorPicks.PLAYLIST)
        val text = result.fold(
            { if (it.isEmpty()) "Igor needs more listening history (or a YouTube Music connection)." else "Igor chose ${it.size} songs for you." },
            { "Igor couldn't reach YouTube Music: ${it.localizedMessage}" },
        )
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // First visit or older than a week: Igor prepares new picks on his own.
    LaunchedEffect(Unit) { if (container.igorPicks.isStale && !working) refreshPicks() }

    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(VelvetBlue, VelvetDeep))),
        contentPadding = contentPadding,
    ) {
        item { VelvetHeader(onBack) }
        item {
            val r = reading
            if (r == null) {
                EmptyState(
                    "The room is quiet",
                    "Igor needs at least ${VelvetRoom.MIN_PLAYS} plays from the last 90 days to read your fate. Keep listening.",
                )
            } else {
                ArcanaCard(r)
            }
        }
        reading?.let { r ->
            item { SectionLabel("Traits", "資質") }
            item { Traits(r) }
            item { SectionLabel("Patterns", "傾向") }
            itemsIndexed(r.patterns) { _, line ->
                Text("◆ $line", color = Color.White, style = PersonaType.body, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            item { IgorSpeaks(r) }
        }

        item { SectionLabel("Igor's picks", "推薦") }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "Songs YouTube Music plays next to several of your favourites – none of them heard yet." +
                        if (refreshed > 0) " Last chosen ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(refreshed))}." else "",
                    color = Color(0xFFB8C2E8),
                    style = PersonaType.caption,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { PersonaButton("Play", onClick = { container.player.play(picks, 0) }) }
                    item { PersonaButton("Shuffle", onClick = { container.player.play(picks, 0, shuffle = true) }, inverted = true) }
                    item { PersonaButton(if (working) "Choosing…" else "New picks", onClick = { if (!working) refreshPicks() }, inverted = true) }
                }
            }
        }
        if (working && picks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VelvetGold)
                }
            }
        }
        itemsIndexed(picks, key = { i, s -> "$i-${s.id}" }) { index, song ->
            SongRow(song, onClick = { container.player.play(picks, index) }, showArtwork = true, isCurrent = song.id == playerState.current?.id)
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun VelvetHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.align(Alignment.Center).popIn("velvet").rotate(-3f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.background(Color.Black).padding(horizontal = 12.dp, vertical = 2.dp)) {
                RansomText("Velvet Room", fontSize = 38.sp, style = RansomStyle.Outline)
            }
            Box(Modifier.background(VelvetGold).padding(horizontal = 8.dp)) {
                Text(if (Skin.zeroOne) "ゼア解析" else "ベルベットルーム", color = VelvetDeep, style = PersonaType.caption.copy(fontSize = 11.sp, letterSpacing = 2.sp))
            }
        }
    }
}

@Composable
private fun ArcanaCard(reading: ListeningReading) {
    val arcana = reading.arcana
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .popIn(arcana, fromRotation = -14f)
            .rotate(-1.5f)
            .background(VelvetDeep, slantShape(0.03f))
            .border(3.dp, VelvetGold, slantShape(0.03f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Lingo.of("Your arcana").uppercase(), color = VelvetGold, style = PersonaType.label.copy(fontSize = 13.sp, letterSpacing = 3.sp))
        Text(arcana.numeral, color = VelvetGold, style = PersonaType.title.copy(fontSize = 44.sp))
        RansomText(arcana.displayName, fontSize = 30.sp)
        if (!Skin.zeroOne) Text(arcana.japanese, color = Color(0xFFB8C2E8), style = PersonaType.body) else Text("PROGRISE KEY", color = Color(0xFFB8C2E8), style = PersonaType.label)
        Spacer(Modifier.height(10.dp))
        Text("\"${arcana.reading}\"", color = Color.White, style = PersonaType.body)
        reading.runnerUp?.let {
            Spacer(Modifier.height(8.dp))
            Text("Also stirring: ${it.displayName}" + if (Skin.zeroOne) "" else " (${it.japanese})", color = Color(0xFFB8C2E8), style = PersonaType.caption)
        }
        Spacer(Modifier.height(6.dp))
        Text("From ${reading.plays} plays in the last 90 days", color = Color(0xFF7F8AB8), style = PersonaType.caption.copy(fontSize = 11.sp))
    }
}

@Composable
private fun SectionLabel(title: String, japanese: String) {
    Row(Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ShoutLabel(title)
        Spacer(Modifier.width(10.dp))
        Text(japanese, color = VelvetGold, style = PersonaType.caption)
    }
}

@Composable
private fun Traits(reading: ListeningReading) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reading.traits.forEach { trait ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    Text(trait.label.uppercase(), color = Color.White, style = PersonaType.label.copy(fontSize = 13.sp))
                    Text(trait.japanese, color = Color(0xFF7F8AB8), style = PersonaType.caption.copy(fontSize = 10.sp))
                }
                Box(Modifier.weight(1f).height(14.dp).background(Color(0xFF1A2A66), slantShape(0.1f))) {
                    Box(Modifier.fillMaxWidth(trait.value.coerceIn(0.02f, 1f)).height(14.dp).background(VelvetGold, slantShape(0.1f)))
                }
            }
        }
    }
}

/** Optional Claude reading with the user's own API key. */
@Composable
private fun IgorSpeaks(reading: ListeningReading) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val narrator = container.igorNarrator
    val apiKey by narrator.apiKey.collectAsStateWithLifecycle()
    val last by narrator.last.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var loadingSuggestions by remember { mutableStateOf(false) }

    SectionLabel("Igor speaks", "語り")
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (apiKey.isBlank()) {
            Text(
                "Optional: with your own Anthropic API key, Igor (Claude) writes a personal reading and names songs for you. " +
                    "Only your music stats (artists, songs, times) are sent; each reading costs a few cents on your account.",
                color = Color(0xFFB8C2E8),
                style = PersonaType.caption,
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                singleLine = true,
                label = { Text("Anthropic API key (sk-ant-…)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            PersonaButton("Save key", onClick = { if (keyInput.isNotBlank()) narrator.setApiKey(keyInput) })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonaButton(if (asking) "Igor is reading…" else "Hear Igor", onClick = {
                    if (!asking) scope.launch {
                        asking = true
                        narrator.speak(reading).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        asking = false
                    }
                })
                PersonaButton("Remove key", onClick = { narrator.setApiKey("") }, inverted = true)
            }
        }
        last?.let { speech ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f), slantShape(0.02f))
                    .border(1.dp, VelvetGold, slantShape(0.02f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(speech.text, color = Color.White, style = PersonaType.body)
                if (speech.suggestions.isNotEmpty()) {
                    Text("IGOR SUGGESTS", color = VelvetGold, style = PersonaType.label.copy(fontSize = 13.sp))
                    speech.suggestions.forEach { Text("♪ $it", color = Color(0xFFDDE3FF), style = PersonaType.body) }
                    PersonaButton(if (loadingSuggestions) "Searching…" else "Play these", onClick = {
                        if (!loadingSuggestions) scope.launch {
                            loadingSuggestions = true
                            val songs: List<Song> = speech.suggestions.mapNotNull { query ->
                                runCatching {
                                    val parts = query.split(" - ", limit = 2)
                                    if (parts.size == 2) container.ytLibrary.findSong(parts[1], parts[0]) else container.ytLibrary.findSong(query, null)
                                }.getOrNull()
                            }
                            loadingSuggestions = false
                            if (songs.isEmpty()) Toast.makeText(context, "Couldn't find them on YouTube Music.", Toast.LENGTH_SHORT).show()
                            else container.player.play(songs, 0)
                        }
                    })
                }
            }
        }
    }
}
