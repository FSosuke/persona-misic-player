package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.ArtistPlays
import com.fsosuke.personamusic.data.Confidants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidantsTest {
    @Test
    fun ranksFollowThresholds() {
        assertEquals(0, Confidants.rankFor(0))
        assertEquals(1, Confidants.rankFor(1))
        assertEquals(2, Confidants.rankFor(3))
        assertEquals(10, Confidants.rankFor(100))
        assertEquals(10, Confidants.rankFor(1000))
    }

    @Test
    fun progressTowardsNextRank() {
        val c = Confidants.of("Lyn", 4) // rank 2 (3..5), next at 6
        assertEquals(2, c.rank)
        assertEquals(2, c.playsToNext)
        assertEquals(1f / 3f, c.progress, 0.001f)
        val max = Confidants.of("Lyn", 150)
        assertTrue(max.isMax)
        assertNull(max.playsToNext)
    }

    @Test
    fun featuredCreditsCountForTheMainArtist() {
        val confidants = Confidants.fromCounts(
            listOf(ArtistPlays("Lyn", 3), ArtistPlays("Lyn, ATLUS Sound Team", 2), ArtistPlays("Unknown artist", 9))
        )
        assertEquals(1, confidants.size)
        assertEquals("Lyn", confidants.first().artist)
        assertEquals(5, confidants.first().plays)
    }

    @Test
    fun arcanaIsStable() {
        assertEquals(Confidants.arcanaFor("YOASOBI"), Confidants.arcanaFor("yoasobi"))
    }
}
