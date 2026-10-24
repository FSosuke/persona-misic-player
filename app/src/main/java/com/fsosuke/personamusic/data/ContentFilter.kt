package com.fsosuke.personamusic.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Decides which recommended / auto-played songs the user gets to see: hidden songs and artists
 * are dropped, and with "Japanese only" just Japanese music (and artists already listened to).
 */
class ContentFilter(
    private val dao: MusicDao,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    data class Rules(
        val blockedSongs: Set<String> = emptySet(),
        val blockedArtists: Set<String> = emptySet(),
        val japaneseOnly: Boolean = false,
        val knownArtists: Set<String> = emptySet(),
    ) {
        fun allowsArtist(artist: String): Boolean {
            val names = artistNames(artist)
            if (names.any { it in blockedArtists }) return false
            if (!japaneseOnly) return true
            return isJapanese(artist) || names.any { it in knownArtists }
        }

        fun allows(song: Song): Boolean {
            if (song.id in blockedSongs) return false
            val names = artistNames(song.artist)
            if (names.any { it in blockedArtists }) return false
            if (!japaneseOnly) return true
            return isJapanese(song.title) || isJapanese(song.artist) || names.any { it in knownArtists }
        }
    }

    val rules: StateFlow<Rules> = combine(dao.blocked(), dao.knownArtists(), settings.japaneseOnly) { blocked, known, japanese ->
        Rules(
            blockedSongs = blocked.filter { it.type == BlockType.SONG }.mapTo(HashSet()) { it.key.removePrefix("song:") },
            blockedArtists = blocked.filter { it.type == BlockType.ARTIST }.mapTo(HashSet()) { it.key.removePrefix("artist:") },
            japaneseOnly = japanese,
            knownArtists = known.flatMapTo(HashSet()) { artistNames(it) },
        )
    }.stateIn(scope, SharingStarted.Eagerly, Rules())

    val blocked = dao.blocked()

    suspend fun hideSong(song: Song) = dao.block(BlockedItem.song(song))
    suspend fun hideArtist(name: String) = dao.block(BlockedItem.artist(name))
    suspend fun unhide(item: BlockedItem) = dao.unblock(item.key)

    companion object {
        /** Recommendation shelves without hidden / filtered-out songs and artists. */
        fun filterShelves(shelves: List<com.fsosuke.personamusic.youtube.HomeShelf>, rules: Rules) = shelves
            .map { shelf ->
                shelf.copy(
                    items = shelf.items.filter { item ->
                        when (item) {
                            is com.fsosuke.personamusic.youtube.YtItem.SongItem -> rules.allows(item.song)
                            is com.fsosuke.personamusic.youtube.YtItem.ArtistItem -> rules.allowsArtist(item.name)
                            is com.fsosuke.personamusic.youtube.YtItem.Collection -> true
                        }
                    },
                )
            }
            .filter { it.items.isNotEmpty() }

        private val separators = Regex("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b|/|、)\\s*", RegexOption.IGNORE_CASE)

        /** Hiragana, Katakana (incl. half-width) and CJK ideographs. */
        private val japaneseScript = Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

        fun isJapanese(text: String) = japaneseScript.containsMatchIn(text)

        /** All credited artists, lowercased: "Lyn, ATLUS Sound Team" → [lyn, atlus sound team]. */
        fun artistNames(artist: String): List<String> =
            artist.split(separators).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }
}
