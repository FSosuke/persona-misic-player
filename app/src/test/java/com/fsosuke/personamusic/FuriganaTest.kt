package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.Romaji
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaTest {
    @Test
    fun rubyOnlyOverTheKanji() = runBlocking {
        val words = Romaji.furigana(listOf("走る")).single()
        val parts = words.flatMap { it.parts }
        assertEquals(listOf("走" to "はし", "る" to null), parts)
    }

    @Test
    fun plainLinesStayPlain() = runBlocking {
        val words = Romaji.furigana(listOf("Last Surprise")).single()
        assertEquals(1, words.size)
        assertEquals("Last Surprise", words.first().surface)
    }

    @Test
    fun particlesAreNotLookupable() = runBlocking {
        val words = Romaji.furigana(listOf("夜に駆ける")).single()
        assertTrue(words.any { it.surface == "に" && !it.isLookupable })
        assertTrue(words.any { it.surface == "夜" && it.isLookupable })
    }

    @Test
    fun katakanaToHiragana() {
        assertEquals("はしる", Romaji.toHiragana("ハシル"))
    }
}
