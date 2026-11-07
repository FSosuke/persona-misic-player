package com.fsosuke.personamusic.velvet

import android.content.Context
import com.fsosuke.personamusic.data.ContentFilter
import com.fsosuke.personamusic.data.MusicRepository
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.primaryArtist
import com.fsosuke.personamusic.youtube.YtMusicLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * "Igor's picks": recommendations the way the big services do it – songs that YouTube Music
 * plays next to several of your favourites at once (co-occurrence), minus everything you've
 * already heard, liked or hidden. Saved as a local playlist.
 */
class IgorPicks(
    context: Context,
    private val repository: MusicRepository,
    private val ytLibrary: YtMusicLibrary,
    private val contentFilter: ContentFilter,
) {
    private val prefs = context.getSharedPreferences("velvet_room", Context.MODE_PRIVATE)

    private val _lastRefresh = MutableStateFlow(prefs.getLong(KEY_REFRESHED, 0L))
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    val isStale get() = System.currentTimeMillis() - _lastRefresh.value > WEEK_MS

    /** Builds new picks and stores them in the "Igor's picks" playlist; returns them. */
    suspend fun refresh(): List<Song> = withContext(Dispatchers.IO) {
        _working.value = true
        try {
            val seeds = seeds()
            if (seeds.isEmpty()) return@withContext emptyList()
            val heard = repository.playedSongIds() + repository.likedSongIds()
            val rules = contentFilter.rules.value
            val gate = Semaphore(3)
            val radios = coroutineScope {
                seeds.map { seed -> async { gate.withPermit { runCatching { ytLibrary.radio(seed).take(RADIO_SIZE) }.getOrDefault(emptyList()) } } }
                    .awaitAll()
            }
            val seedArtists = seeds.map { primaryArtist(it.artist).lowercase() }.toSet()
            val byId = LinkedHashMap<String, Song>()
            val score = HashMap<String, Float>()
            radios.forEach { list ->
                list.distinctBy { it.id }.forEachIndexed { position, song ->
                    if (song.id in heard || !rules.allows(song)) return@forEachIndexed
                    byId.putIfAbsent(song.id, song)
                    // Showing up next to several favourites counts most; early positions a bit more.
                    score[song.id] = (score[song.id] ?: 0f) + 1f + (RADIO_SIZE - position) / (RADIO_SIZE * 4f)
                }
            }
            val ranked = byId.values.sortedByDescending { song ->
                val s = score[song.id] ?: 0f
                // A little nudge towards artists you don't know yet.
                if (primaryArtist(song.artist).lowercase() in seedArtists) s else s + 0.3f
            }
            val perArtist = HashMap<String, Int>()
            val picks = ranked.filter { song ->
                val artist = primaryArtist(song.artist).lowercase()
                val count = perArtist.getOrDefault(artist, 0)
                if (count >= MAX_PER_ARTIST) false else { perArtist[artist] = count + 1; true }
            }.take(PICKS)
            if (picks.isNotEmpty()) {
                repository.replacePlaylist(PLAYLIST, picks)
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_REFRESHED, now).apply()
                _lastRefresh.value = now
            }
            picks
        } finally {
            _working.value = false
        }
    }

    /** Your most played songs of the month plus some favourites. */
    private suspend fun seeds(): List<Song> {
        val month = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val top = repository.smartPlaylist(com.fsosuke.personamusic.data.SmartPlaylist.OnRepeat).first().take(6)
        val liked = repository.likedSongs.first().filter { it.source == SongSource.YOUTUBE }.shuffled().take(4)
        return (top + liked).distinctBy { it.id }.take(SEEDS).ifEmpty {
            repository.playsBetween(month, System.currentTimeMillis()).map { it.song }.distinctBy { it.id }.takeLast(SEEDS)
        }
    }

    companion object {
        const val PLAYLIST = "Igor's picks"
        private const val KEY_REFRESHED = "picks_refreshed"
        private const val SEEDS = 8
        private const val RADIO_SIZE = 40
        private const val PICKS = 30
        private const val MAX_PER_ARTIST = 3
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
