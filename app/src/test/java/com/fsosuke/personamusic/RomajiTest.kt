package com.fsosuke.personamusic

import com.fsosuke.personamusic.data.Romaji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiTest {
    @Test
    fun basicKana() {
        assertEquals("konnichiha", Romaji.kanaToRomaji("こんにちは"))
        assertEquals("sakura", Romaji.kanaToRomaji("さくら"))
    }

    @Test
    fun smallTsuDoublesTheConsonant() {
        assertEquals("gakkou", Romaji.kanaToRomaji("がっこう"))
        assertEquals("chotto", Romaji.kanaToRomaji("ちょっと"))
        assertEquals("kyatto", Romaji.kanaToRomaji("キャット"))
    }

    @Test
    fun longVowelAndN() {
        assertEquals("raamen", Romaji.kanaToRomaji("ラーメン"))
        assertEquals("shin'ichi", Romaji.kanaToRomaji("しんいち"))
    }

    @Test
    fun detectsJapanese() {
        assertTrue(Romaji.containsJapanese("夜に駆ける"))
        assertTrue(Romaji.containsJapanese("YOASOBI「アイドル」"))
        assertFalse(Romaji.containsJapanese("Last Surprise"))
    }
}
