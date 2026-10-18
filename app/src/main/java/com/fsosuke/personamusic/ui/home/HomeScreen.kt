package com.fsosuke.personamusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fsosuke.personamusic.data.LoggedPlay
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongWithPlays
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.CalloutBanner
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.DuotoneImage
import com.fsosuke.personamusic.ui.components.StampLabel
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.PersonaHeader
import com.fsosuke.personamusic.ui.components.PersonaSearchField
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.TopResultCard
import com.fsosuke.personamusic.ui.components.jaggedCardShape
import com.fsosuke.personamusic.ui.components.starPattern
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.youtube.YtShelf

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenYtPlaylist: (String) -> Unit,
    onSignIn: () -> Unit,
    contentPadding: PaddingValues,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository, container.youTube) }
    val query by vm.query.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val songLog by vm.songLog.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val currentId = playerState.current?.id
    val ytAccount by container.ytAccounts.account.collectAsStateWithLifecycle()
    val ytFeed by container.ytLibrary.feed.collectAsStateWithLifecycle()

    LaunchedEffect(ytAccount) { container.ytLibrary.refresh() }

    fun play(songs: List<Song>, index: Int) {
        vm.remember(songs)
        container.player.play(songs, index)
    }

    val homeBackground by container.settings.homeBackground.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
    homeBackground?.let { DuotoneImage(it, tint = null, modifier = Modifier.fillMaxSize(), alpha = 0.3f) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            PersonaHeader(
                title = "Home",
                action = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Tune, contentDescription = "Customize", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                },
            ) {
                PersonaSearchField(value = query, onValueChange = vm::setQuery, placeholder = "Search YouTube Music & library")
            }
        }

        if (query.isBlank()) {
            if (ytAccount != null) {
                if (ytFeed.home.isEmpty() && ytFeed.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LocalPersonaColors.current.accent)
                        }
                    }
                }
                ytFeed.error?.let { error ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "YouTube Music: $error",
                                color = LocalPersonaColors.current.muted,
                                style = PersonaType.caption,
                                modifier = Modifier.weight(1f),
                            )
                            PersonaButton("Retry", onClick = { container.ytLibrary.refresh(force = true) })
                        }
                    }
                }
                itemsIndexed(ytFeed.home, key = { i, shelf -> "shelf-$i-${shelf.title}" }) { i, shelf ->
                    YtShelf(
                        shelf = shelf,
                        index = i,
                        currentSongId = currentId,
                        onPlay = { songs, index -> play(songs, index) },
                        onOpenCollection = onOpenYtPlaylist,
                    )
                }
            }
            if (recent.isEmpty() && ytAccount == null) {
                item {
                    EmptyState(
                        title = "Take your time",
                        message = "Search YouTube Music above or open your Library to steal your first heart… er, play your first song.",
                    )
                }
            } else {
                item { RecentSection(recent, currentId, isPlaying = playerState.isPlaying) { play(recent, it) } }
            }
            if (mostPlayed.isNotEmpty()) {
                item { MostPlayedSection(mostPlayed) { play(mostPlayed.map { it.song }, it) } }
            }
            if (songLog.isNotEmpty() || mostPlayed.isNotEmpty()) {
                item {
                    // The monochrome star-burst board from the P5 phone menus.
                    Column(Modifier.fillMaxWidth().starPattern(Color.Black, Color(0xFFEDEDED), cell = 230f, strokeWidth = 16f)) {
                        if (songLog.isNotEmpty()) SongLogSection(songLog) { entry -> play(listOf(entry.song), 0) }
                        if (mostPlayed.isNotEmpty()) SpeedDialSection(mostPlayed) { play(mostPlayed.map { it.song }, it) }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (ytAccount == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        PersonaButton("Connect YouTube Music", onClick = onSignIn, inverted = true)
                    }
                }
            }
        } else {
            when (val result = online) {
                OnlineResults.Idle, OnlineResults.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LocalPersonaColors.current.accent)
                    }
                }
                is OnlineResults.Failed -> item {
                    EmptyState("Mission failed", "YouTube search failed: ${result.message}")
                }
                is OnlineResults.Loaded -> {
                    val songs = result.songs
                    if (songs.isNotEmpty()) {
                        item { DashedSectionTitle("Top result") }
                        item { TopResultCard(songs.first(), onPlay = { play(songs, 0) }) }
                    }
                    if (songs.size > 1) {
                        item { DashedSectionTitle("Songs") }
                        itemsIndexed(songs.drop(1), key = { _, s -> "yt-" + s.id }) { i, song ->
                            SongRow(song, onClick = { play(songs, i + 1) }, isCurrent = song.id == currentId)
                        }
                    }
                }
            }
            if (local.isNotEmpty()) {
                item { DashedSectionTitle("Library") }
                itemsIndexed(local, key = { _, s -> "local-" + s.id }) { i, song ->
                    SongRow(song, onClick = { play(local, i) }, showArtwork = true, isCurrent = song.id == currentId)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
}

@Composable
private fun RecentSection(songs: List<Song>, currentId: String?, isPlaying: Boolean, onPlay: (Int) -> Unit) {
    val colors = LocalPersonaColors.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            CalloutBanner("Recent", Modifier.padding(end = 12.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                val isCurrent = song.id == currentId
                Column(Modifier.width(180.dp).clickable { onPlay(index) }) {
                    Box {
                        Artwork(
                            song.artworkUri,
                            Modifier
                                .rotate(if (index % 2 == 0) -3f else 2f)
                                .size(180.dp)
                                .border(3.dp, if (isCurrent) colors.accent else Color.White, jaggedCardShape(index))
                                .padding(3.dp),
                            shape = jaggedCardShape(index),
                        )
                        if (isCurrent && isPlaying) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        song.title,
                        color = if (isCurrent) colors.accent else Color.White,
                        style = PersonaType.title.copy(fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(song.artist, color = colors.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MostPlayedSection(entries: List<SongWithPlays>, onPlay: (Int) -> Unit) {
    val colors = LocalPersonaColors.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        CalloutBanner("Most played", Modifier.padding(start = 12.dp), pointLeft = true)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.song.id }) { index, entry ->
                Column(Modifier.width(130.dp).clickable { onPlay(index) }) {
                    Box {
                        Artwork(entry.song.artworkUri, Modifier.rotate(-2f).size(130.dp).border(2.dp, Color.White))
                        Box(
                            Modifier
                                .rotate(-8f)
                                .background(colors.accent)
                                .border(2.dp, Color.White)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text("${index + 1}", color = colors.onAccent, style = PersonaType.label.copy(fontSize = 22.sp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(entry.song.title, color = Color.White, style = PersonaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.plays}× played", color = colors.muted, style = PersonaType.caption)
                }
            }
        }
    }
}

/** The "SONG LOG" paper card: artist chip • title, slightly fanned out. */
@Composable
private fun SongLogSection(entries: List<LoggedPlay>, onPlay: (LoggedPlay) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.CenterEnd) {
            StampLabel("Song", "Log", Modifier.padding(end = 20.dp), tilt = -10f)
        }
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .rotate(-2f)
                .background(Color.White)
                .padding(10.dp)
                .background(Color.Black)
                .padding(vertical = 8.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(entry) }
                        .padding(start = (8 + index * 4).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(
                            entry.song.artist.take(10).let { if (entry.song.artist.length > 10) "$it…" else it },
                            color = Color.Black,
                            style = PersonaType.caption,
                            maxLines = 1,
                        )
                    }
                    Text(
                        " • ${entry.song.title}",
                        color = Color.White,
                        style = PersonaType.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index != entries.lastIndex) {
                    Box(Modifier.padding(start = (8 + index * 4).dp, end = 12.dp).fillMaxWidth().height(1.dp).background(Color(0xFF555555)))
                }
            }
        }
    }
}

/** 3×3 "SPEED DIAL" grid of favourites-by-playcount. */
@Composable
private fun SpeedDialSection(entries: List<SongWithPlays>, onPlay: (Int) -> Unit) {
    val colors = LocalPersonaColors.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.CenterEnd) {
            StampLabel("Speed", "Dial", Modifier.padding(end = 28.dp), tilt = -12f)
        }
        Column(
            Modifier
                .padding(20.dp)
                .rotate(1.5f)
                .background(Color.White)
                .padding(12.dp)
                .starPattern(Color.Black, Color(0xFF1C1C1C), cell = 90f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            entries.take(9).chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunk.forEachIndexed { col, entry ->
                        val index = row * 3 + col
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .rotate(((index * 37) % 7 - 3).toFloat())
                                .clickable { onPlay(index) },
                        ) {
                            Artwork(entry.song.artworkUri, Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
                            )
                            Text(
                                entry.song.title,
                                color = Color.White,
                                style = PersonaType.caption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                            )
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(4.dp).background(colors.background))
    }
}
