package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.ContentFilter
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {
    private fun song(id: String, title: String, artist: String) =
        Song(id = id, title = title, artist = artist, source = SongSource.YOUTUBE, uri = "")

    @Test
    fun japaneseOnlyKeepsJapaneseAndKnownArtists() {
        val rules = ContentFilter.Rules(japaneseOnly = true, knownArtists = setOf("yoasobi"))
        assertTrue(rules.allows(song("1", "夜に駆ける", "Ayase")))
        assertTrue(rules.allows(song("2", "Idol", "YOASOBI")))
        assertFalse(rules.allows(song("3", "Shape of You", "Ed Sheeran")))
    }

    @Test
    fun blockedSongsAndArtistsAreDropped() {
        val rules = ContentFilter.Rules(blockedSongs = setOf("1"), blockedArtists = setOf("ed sheeran"))
        assertFalse(rules.allows(song("1", "Anything", "Someone")))
        assertFalse(rules.allows(song("2", "Perfect", "Ed Sheeran, Beyoncé")))
        assertTrue(rules.allows(song("3", "Rivers in the Desert", "Lyn")))
    }
}
