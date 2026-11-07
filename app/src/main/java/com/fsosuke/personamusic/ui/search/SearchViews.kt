package com.fsosuke.personamusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.Artwork
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.TopResultCard
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.home.Load
import com.fsosuke.personamusic.ui.home.SearchFilter
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.youtube.YtShelf
import com.fsosuke.personamusic.youtube.HomeShelf
import com.fsosuke.personamusic.youtube.SearchResults
import com.fsosuke.personamusic.youtube.YtItem

/** Callbacks shared by all search views. */
class SearchActions(
    val play: (songs: List<Song>, index: Int) -> Unit,
    val openCollection: (id: String) -> Unit,
    val openArtist: (name: String, browseId: String?) -> Unit,
)

/** While typing: past searches and live suggestions from YouTube Music. */
fun LazyListScope.searchSuggestions(
    query: String,
    history: List<String>,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val q = query.trim()
    val matchingHistory = if (q.isEmpty()) history else history.filter { it.contains(q, ignoreCase = true) && it != q }.take(4)
    if (q.isEmpty() && matchingHistory.isNotEmpty()) {
        item(key = "history-header") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT SEARCHES", color = LocalPersonaColors.current.accent, style = PersonaType.label, modifier = Modifier.weight(1f))
                Text(
                    "CLEAR",
                    color = Color(0xFF9A9A9A),
                    style = PersonaType.label.copy(fontSize = 13.sp),
                    modifier = Modifier.clickable(onClick = onClearHistory).padding(8.dp),
                )
            }
        }
    }
    items(matchingHistory, key = { "h-$it" }) { entry ->
        SuggestionRow(entry, isHistory = true, onPick = onPick, onFill = onFill, onRemove = { onRemoveHistory(entry) })
    }
    items(suggestions.filterNot { s -> matchingHistory.any { it.equals(s, ignoreCase = true) } }, key = { "s-$it" }) { entry ->
        SuggestionRow(entry, isHistory = false, onPick = onPick, onFill = onFill, onRemove = null)
    }
    if (q.isNotEmpty()) {
        item(key = "search-for") {
            SuggestionRow("Search \"$q\"", isHistory = false, onPick = { onPick(q) }, onFill = null, onRemove = null, highlight = true)
        }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    isHistory: Boolean,
    onPick: (String) -> Unit,
    onFill: ((String) -> Unit)?,
    onRemove: (() -> Unit)?,
    highlight: Boolean = false,
) {
    val colors = LocalPersonaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(text) }
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isHistory) Icons.Filled.History else Icons.Filled.Search,
            contentDescription = null,
            tint = if (highlight) colors.accent else Color(0xFF8A8A8A),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            color = if (highlight) colors.accent else Color.White,
            style = PersonaType.body.copy(fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove", tint = Color(0xFF777777), modifier = Modifier.size(20.dp)) }
        }
        if (onFill != null) {
            IconButton(onClick = { onFill(text) }) { Icon(Icons.Filled.NorthWest, "Use", tint = Color(0xFF777777), modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Slanted category tabs under the search field. */
fun LazyListScope.searchFilterTabs(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    item(key = "filters") {
        val colors = LocalPersonaColors.current
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(SearchFilter.entries) { filter ->
                val isSelected = filter == selected
                Box(
                    Modifier
                        .rotate(if (isSelected) -5f else 0f)
                        .background(if (isSelected) colors.accent else Color.Black, slantShape(0.12f))
                        .border(2.dp, if (isSelected) Color.White else Color(0xFF444444), slantShape(0.12f))
                        .clickable { onSelect(filter) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        filter.label.uppercase(),
                        color = if (isSelected) colors.onAccent else Color.White,
                        style = PersonaType.label.copy(fontSize = if (isSelected) 16.sp else 14.sp),
                    )
                }
            }
        }
    }
}

/** "All" tab: top result, a few songs, then artist / album / playlist rows and library hits. */
fun LazyListScope.searchOverview(
    results: Load<SearchResults>,
    local: List<Song>,
    currentId: String?,
    actions: SearchActions,
    onShowAll: (SearchFilter) -> Unit,
) {
    when (results) {
        Load.Idle, Load.Loading -> item(key = "loading") { Loading() }
        is Load.Failed -> item(key = "failed") { EmptyState("Mission failed", "YouTube search failed: ${results.message}") }
        is Load.Loaded -> {
            val r = results.value
            val songs = r.songs
            val top = r.top ?: songs.firstOrNull()?.let { YtItem.SongItem(it) }
            if (top != null) {
                item(key = "top-title") { DashedSectionTitle("Top result") }
                item(key = "top") {
                    when (top) {
                        is YtItem.SongItem -> {
                            val list = listOf(top.song) + songs.filter { it.id != top.song.id }
                            TopResultCard(top.song, onPlay = { actions.play(list, 0) })
                        }
                        else -> TopItemCard(top, actions)
                    }
                }
            }
            val shownSongs = songs.filter { (top as? YtItem.SongItem)?.song?.id != it.id }.take(4)
            if (shownSongs.isNotEmpty()) {
                item(key = "songs-title") { DashedSectionTitle("Songs") }
                itemsIndexed(shownSongs, key = { _, s -> "song-${s.id}" }) { i, song ->
                    SongRow(song, onClick = { actions.play(shownSongs, i) }, showArtwork = true, isCurrent = song.id == currentId)
                }
                item(key = "songs-more") { MoreLink("All songs") { onShowAll(SearchFilter.Songs) } }
            }
            if (r.artists.isNotEmpty()) {
                item(key = "artists") { ShelfWithMore("Artists", r.artists, 0, currentId, actions) { onShowAll(SearchFilter.Artists) } }
            }
            if (r.albums.isNotEmpty()) {
                item(key = "albums") { ShelfWithMore("Albums", r.albums, 1, currentId, actions) { onShowAll(SearchFilter.Albums) } }
            }
            if (r.playlists.isNotEmpty()) {
                item(key = "playlists") { ShelfWithMore("Playlists", r.playlists, 2, currentId, actions) { onShowAll(SearchFilter.Playlists) } }
            }
            if (top == null && songs.isEmpty() && r.artists.isEmpty() && r.albums.isEmpty() && r.playlists.isEmpty() && local.isEmpty()) {
                item(key = "nothing") { EmptyState("No trace", "Nothing found on YouTube Music.") }
            }
        }
    }
    if (local.isNotEmpty()) {
        item(key = "local-title") { DashedSectionTitle("Library") }
        val shown = local.take(5)
        itemsIndexed(shown, key = { _, s -> "local-${s.id}" }) { i, song ->
            SongRow(song, onClick = { actions.play(local, i) }, showArtwork = true, isCurrent = song.id == currentId)
        }
        if (local.size > shown.size) item(key = "local-more") { MoreLink("All ${local.size} library songs") { onShowAll(SearchFilter.Library) } }
    }
}

/** A single category tab: the full list from YouTube Music, or all library matches. */
fun LazyListScope.searchCategory(
    filter: SearchFilter,
    filtered: Load<List<YtItem>>,
    local: List<Song>,
    currentId: String?,
    actions: SearchActions,
) {
    if (filter == SearchFilter.Library) {
        if (local.isEmpty()) item(key = "lib-empty") { EmptyState("Nothing here", "No matching songs in your library.") }
        itemsIndexed(local, key = { _, s -> "lib-${s.id}" }) { i, song ->
            SongRow(song, onClick = { actions.play(local, i) }, showArtwork = true, isCurrent = song.id == currentId)
        }
        return
    }
    when (filtered) {
        Load.Idle, Load.Loading -> item(key = "cat-loading") { Loading() }
        is Load.Failed -> item(key = "cat-failed") { EmptyState("Mission failed", filtered.message) }
        is Load.Loaded -> {
            val items = filtered.value
            if (items.isEmpty()) item(key = "cat-empty") { EmptyState("No trace", "Nothing found in this category.") }
            val songs = items.filterIsInstance<YtItem.SongItem>().map { it.song }
            itemsIndexed(items, key = { i, item -> "cat-$i" }) { _, item ->
                when (item) {
                    is YtItem.SongItem -> SongRow(
                        item.song,
                        onClick = { actions.play(songs, songs.indexOf(item.song)) },
                        showArtwork = true,
                        isCurrent = item.song.id == currentId,
                    )
                    else -> ItemRow(item, actions)
                }
            }
        }
    }
}

@Composable
private fun ShelfWithMore(
    title: String,
    items: List<YtItem>,
    index: Int,
    currentId: String?,
    actions: SearchActions,
    onMore: () -> Unit,
) {
    Column {
        YtShelf(
            shelf = HomeShelf(title, items),
            index = index,
            currentSongId = currentId,
            onPlay = actions.play,
            onOpenCollection = actions.openCollection,
        )
        MoreLink("All ${title.lowercase()}", onMore)
    }
}

@Composable
private fun MoreLink(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), color = LocalPersonaColors.current.accent, style = PersonaType.label.copy(fontSize = 14.sp))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = LocalPersonaColors.current.accent, modifier = Modifier.size(18.dp))
    }
}

/** Row for an album, playlist or artist in a category list. */
@Composable
private fun ItemRow(item: YtItem, actions: SearchActions) {
    val (title, subtitle, thumb, round) = describe(item)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open(item, actions) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            thumb,
            Modifier.size(56.dp).then(if (round) Modifier.clip(CircleShape) else Modifier).border(2.dp, Color.White, if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape),
            shape = if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = PersonaType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = LocalPersonaColors.current.muted, style = PersonaType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF777777))
    }
}

/** Top result when it is an artist, album or playlist: same red card, different action. */
@Composable
private fun TopItemCard(item: YtItem, actions: SearchActions) {
    val colors = LocalPersonaColors.current
    val (title, subtitle, thumb, round) = describe(item)
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier
                .rotate(-1f)
                .fillMaxWidth()
                .background(colors.accent, slantShape(0.02f))
                .padding(3.dp)
                .background(Color(0xFF141414), slantShape(0.02f))
                .clickable { open(item, actions) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(thumb, Modifier.size(84.dp), shape = if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.accent, style = PersonaType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = colors.muted, style = PersonaType.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(colors.accent).clickable { open(item, actions) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item is YtItem.ArtistItem) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.PlayArrow,
                    contentDescription = "Open",
                    tint = colors.onAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPersonaColors.current.accent)
    }
}

private data class Described(val title: String, val subtitle: String?, val thumbnail: String?, val round: Boolean)

private fun describe(item: YtItem): Described = when (item) {
    is YtItem.SongItem -> Described(item.song.title, item.song.artist, item.song.artworkUri, false)
    is YtItem.Collection -> Described(item.playlist.title, item.playlist.subtitle, item.playlist.thumbnail, false)
    is YtItem.ArtistItem -> Described(item.name, "Artist", item.thumbnail, true)
}

private fun open(item: YtItem, actions: SearchActions) {
    when (item) {
        is YtItem.SongItem -> actions.play(listOf(item.song), 0)
        is YtItem.Collection -> actions.openCollection(item.playlist.id)
        is YtItem.ArtistItem -> actions.openArtist(item.name, item.browseId)
    }
}
