package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.Arcana
import com.fsosuke.personamusic.data.PlayRecord
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.VelvetRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class VelvetRoomTest {
    private fun song(i: Int, artist: String) = Song(
        id = "yt:$i", title = "Song $i", artist = artist, album = null, artworkUri = null,
        durationMs = 200_000, source = SongSource.YOUTUBE, uri = "",
    )

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun tooFewPlays() {
        assertNull(VelvetRoom.read(List(5) { PlayRecord(song(it, "A"), at(1, 12), 200_000) }, 0, zone = ZoneOffset.UTC))
    }

    @Test
    fun nightListenerIsTheMoon() {
        val plays = List(40) { PlayRecord(song(it % 20, "Artist ${it % 10}"), at(1 + it % 20, 23), 200_000) }
        val reading = VelvetRoom.read(plays, likedCount = 0, now = at(25, 0), zone = ZoneOffset.UTC)!!
        assertEquals(Arcana.Moon, reading.arcana)
    }

    @Test
    fun oneArtistIsTheLovers() {
        val plays = List(40) { PlayRecord(song(it % 30, if (it % 5 == 0) "Other $it" else "LiSA"), at(1 + it % 20, 15), 200_000) }
        val reading = VelvetRoom.read(plays, likedCount = 0, now = at(25, 0), zone = ZoneOffset.UTC)!!
        assertEquals(Arcana.Lovers, reading.arcana)
    }
}
