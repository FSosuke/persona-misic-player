package com.fsosuke.personamusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.ui.components.DashedSectionTitle
import com.fsosuke.personamusic.ui.components.EmptyState
import com.fsosuke.personamusic.ui.components.PersonaButton
import com.fsosuke.personamusic.ui.components.SongRow
import com.fsosuke.personamusic.ui.components.slantShape
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.ui.youtube.YtShelf
import com.fsosuke.personamusic.youtube.HomeShelf
import com.fsosuke.personamusic.youtube.RegionFeed
import com.fsosuke.personamusic.youtube.YtItem

enum class HomeTab(val label: String) { ForYou("For you"), Japan("Japan") }

/** Slanted tab switch under the search field. */
fun LazyListScope.homeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    item(key = "home-tabs") {
        val colors = LocalPersonaColors.current
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    Modifier
                        .rotate(if (isSelected) -5f else 0f)
                        .background(if (isSelected) colors.accent else Color.Black, slantShape(0.1f))
                        .border(2.dp, if (isSelected) Color.White else Color(0xFF444444), slantShape(0.1f))
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                ) {
                    Text(
                        (if (tab == HomeTab.Japan) "🇯🇵 " else "") + tab.label.uppercase(),
                        color = if (isSelected) colors.onAccent else Color.White,
                        style = PersonaType.label.copy(fontSize = if (isSelected) 18.sp else 15.sp),
                    )
                }
            }
        }
    }
}

/**
 * Japan's YouTube Music charts and new releases. The first song chart is shown as a ranked
 * list, everything else as card shelves.
 */
fun LazyListScope.japanTab(
    feed: RegionFeed,
    currentId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenCollection: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val allShelves = feed.charts + feed.newReleases
    if (allShelves.isEmpty()) {
        when {
            feed.loading || feed.loadedAt == 0L -> item(key = "jp-loading") {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalPersonaColors.current.accent)
                }
            }
            else -> item(key = "jp-empty") {
                EmptyState("No intel", feed.error?.let { "Couldn't load the Japanese charts: $it" } ?: "No charts available right now.") {
                    PersonaButton("Retry", onClick = onRetry)
                }
            }
        }
        return
    }

    // The chart with the most songs becomes the ranked "Top songs" list.
    val ranking: HomeShelf? = feed.charts
        .filter { shelf -> shelf.items.count { it is YtItem.SongItem } >= 10 }
        .maxByOrNull { shelf -> shelf.items.count { it is YtItem.SongItem } }

    if (ranking != null) {
        val songs = ranking.items.filterIsInstance<YtItem.SongItem>().map { it.song }.take(TOP_COUNT)
        item(key = "jp-top-title") { DashedSectionTitle("Top songs Japan") }
        item(key = "jp-top-play") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PersonaButton("Play top ${songs.size}", onClick = { onPlay(songs, 0) })
            }
        }
        itemsIndexed(songs, key = { i, s -> "jp-top-$i-${s.id}" }) { index, song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankNumber(index + 1)
                Box(Modifier.weight(1f)) {
                    SongRow(song, onClick = { onPlay(songs, index) }, showArtwork = true, isCurrent = song.id == currentId)
                }
            }
        }
    }

    val others = allShelves.filter { it !== ranking }
    itemsIndexed(others, key = { i, shelf -> "jp-shelf-$i-${shelf.title}" }) { i, shelf ->
        YtShelf(
            shelf = shelf,
            index = i,
            currentSongId = currentId,
            onPlay = onPlay,
            onOpenCollection = onOpenCollection,
        )
    }
}

@Composable
private fun RankNumber(rank: Int) {
    val colors = LocalPersonaColors.current
    val top = rank <= 3
    Box(Modifier.padding(start = 14.dp).width(36.dp), contentAlignment = Alignment.Center) {
        Text(
            "$rank",
            color = if (top) colors.accent else Color.White,
            style = PersonaType.label.copy(fontSize = if (top) 24.sp else 18.sp),
        )
    }
    Spacer(Modifier.width(0.dp))
}

private const val TOP_COUNT = 50
