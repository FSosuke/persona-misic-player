package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.Lyrics
import com.fsosuke.personamusic.data.LyricsRepository
import com.fsosuke.personamusic.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test
    fun versionsCompareNumerically() {
        assertTrue(UpdateManager.isNewer("0.10.0", "0.9.3"))
        assertTrue(UpdateManager.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateManager.isNewer("0.5.1", "0.5.1"))
        assertFalse(UpdateManager.isNewer("0.4.0", "0.5.0"))
    }

    @Test
    fun parsesLrcTimestamps() {
        val lines = LyricsRepository.parseLrc("[00:12.34] Hello\nno timestamp\n[01:02.5]World\n[02:00]End")
        assertEquals(listOf(12_340L, 62_500L, 120_000L), lines.map { it.timeMs })
        assertEquals("Hello", lines.first().text)
        val lyrics = Lyrics(lines)
        assertEquals(-1, lyrics.indexAt(1_000))
        assertEquals(1, lyrics.indexAt(70_000))
    }
}
