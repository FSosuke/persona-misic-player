package com.fsosuke.personamusic.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Playlists that fill themselves from your listening history. */
enum class SmartPlaylist(val id: String, val title: String, val japanese: String, val description: String) {
    OnRepeat("on-repeat", "On repeat", "リピート中", "Your most played songs of the last 30 days."),
    Forgotten("forgotten", "Forgotten favourites", "忘れたお気に入り", "Hearted songs you haven't played in 60 days."),
    LateNight("late-night", "Late-night mix", "深夜ミックス", "What you play between 22:00 and 5:00."),
    FreshFinds("fresh-finds", "Fresh finds", "新しい発見", "Songs you heard for the first time in the last 2 weeks.");

    fun songs(dao: MusicDao): Flow<List<Song>> {
        val now = System.currentTimeMillis()
        return when (this) {
            OnRepeat -> dao.onRepeat(now - 30 * DAY).map { list -> list.map { it.song } }
            Forgotten -> dao.forgottenFavourites(now - 60 * DAY)
            LateNight -> dao.lateNight().map { list -> list.map { it.song } }
            FreshFinds -> dao.freshFinds(now - 14 * DAY)
        }
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}
