package com.fsosuke.personamusic.data

import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Japanese → Hepburn romaji, fully offline: Kuromoji finds word boundaries and readings
 * (so kanji work too), then katakana readings are spelled out in Latin letters.
 */
object Romaji {

    // The dictionary takes a moment and some memory to load, so only on first use.
    private val tokenizer by lazy { Tokenizer() }

    private val japanese = Regex("[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

    fun containsJapanese(text: String) = japanese.containsMatchIn(text)

    /** One word of a lyric line, with furigana for its kanji and dictionary info. */
    data class Word(
        val surface: String,
        /** Pieces of the surface; `ruby` is the hiragana reading for kanji pieces. */
        val parts: List<Pair<String, String?>>,
        val reading: String?,
        val baseForm: String,
        val partOfSpeech: String,
    ) {
        val isLookupable: Boolean get() = containsJapanese(surface) && partOfSpeech != "Particle" && partOfSpeech != "Symbol"
    }

    /** Splits each line into words with furigana (non-Japanese lines become a single plain word). */
    suspend fun furigana(lines: List<String>): List<List<Word>> = withContext(Dispatchers.Default) {
        lines.map { line ->
            if (!containsJapanese(line)) listOf(Word(line, listOf(line to null), null, line, "Other"))
            else tokenizer.tokenize(line).map { token ->
                val surface = token.surface
                val reading = token.reading.takeIf { it.isNotEmpty() && it != "*" }?.let { toHiragana(it) }
                val parts = if (reading != null && KANJI.containsMatchIn(surface)) rubyParts(surface, reading) else listOf(surface to null)
                Word(
                    surface = surface,
                    parts = parts,
                    reading = reading,
                    baseForm = token.baseForm.takeIf { it.isNotEmpty() && it != "*" } ?: surface,
                    partOfSpeech = POS[token.partOfSpeechLevel1] ?: "Other",
                )
            }
        }
    }

    /** "走る" + "はしる" → [走/はし, る]: kana around the kanji isn't repeated in the ruby. */
    private fun rubyParts(surface: String, reading: String): List<Pair<String, String?>> {
        val hiraSurface = toHiragana(surface)
        var prefix = 0
        while (prefix < surface.length && prefix < reading.length && !KANJI.matches(surface[prefix].toString()) &&
            hiraSurface[prefix] == reading[prefix]
        ) prefix++
        var suffix = 0
        while (suffix < surface.length - prefix && suffix < reading.length - prefix &&
            !KANJI.matches(surface[surface.length - 1 - suffix].toString()) &&
            hiraSurface[surface.length - 1 - suffix] == reading[reading.length - 1 - suffix]
        ) suffix++
        val middle = surface.substring(prefix, surface.length - suffix)
        val middleReading = reading.substring(prefix, reading.length - suffix)
        return buildList {
            if (prefix > 0) add(surface.substring(0, prefix) to null)
            add(middle to middleReading.ifEmpty { null })
            if (suffix > 0) add(surface.substring(surface.length - suffix) to null)
        }
    }

    fun toHiragana(text: String) = buildString {
        text.forEach { c -> append(if (c in 'ァ'..'ヶ') c - 0x60 else c) }
    }

    private val KANJI = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF々]")

    private val POS = mapOf(
        "名詞" to "Noun", "動詞" to "Verb", "形容詞" to "Adjective", "副詞" to "Adverb", "助詞" to "Particle",
        "助動詞" to "Auxiliary", "連体詞" to "Pre-noun", "接続詞" to "Conjunction", "感動詞" to "Interjection",
        "記号" to "Symbol", "接頭詞" to "Prefix", "フィラー" to "Filler",
    )

    suspend fun convert(lines: List<String>): List<String> = withContext(Dispatchers.Default) {
        lines.map { line -> if (containsJapanese(line)) convertLine(line) else line }
    }

    private fun convertLine(line: String): String {
        val words = tokenizer.tokenize(line).mapNotNull { token ->
            val surface = token.surface
            if (surface.isBlank()) return@mapNotNull null
            if (!containsJapanese(surface)) return@mapNotNull surface
            // Particles are written by sound, not by spelling.
            if (token.partOfSpeechLevel1 == "助詞") {
                when (surface) {
                    "は" -> return@mapNotNull "wa"
                    "へ" -> return@mapNotNull "e"
                    "を" -> return@mapNotNull "o"
                }
            }
            val reading = token.reading.takeIf { it.isNotEmpty() && it != "*" } ?: surface
            kanaToRomaji(reading)
        }
        return words.joinToString(" ")
            .replace(Regex("\\s+([,.!?、。！？」』）)])"), "$1")
            .replace(Regex("([「『（(])\\s+"), "$1")
            .trim()
    }

    /** Converts hiragana/katakana to romaji; anything else is passed through. */
    fun kanaToRomaji(text: String): String {
        val kata = toKatakana(text)
        val out = StringBuilder()
        var i = 0
        var doubleNext = false
        while (i < kata.length) {
            val c = kata[i]
            // Two-character combinations like キャ, シュ, ティ.
            if (i + 1 < kata.length) {
                val combo = COMBOS[kata.substring(i, i + 2)]
                if (combo != null) {
                    out.append(if (doubleNext) combo.first() + combo else combo)
                    doubleNext = false
                    i += 2
                    continue
                }
            }
            when (c) {
                'ッ' -> doubleNext = true
                'ー' -> out.lastOrNull { it in "aeiou" }?.let { out.append(it) }
                'ン' -> {
                    val next = kata.getOrNull(i + 1)
                    out.append(if (next != null && (KANA[next]?.firstOrNull() in listOf('a', 'i', 'u', 'e', 'o', 'y'))) "n'" else "n")
                }
                else -> {
                    val romaji = KANA[c]
                    if (romaji != null) {
                        out.append(if (doubleNext && romaji.first() !in "aeiou") romaji.first() + romaji else romaji)
                    } else {
                        out.append(fullWidthToAscii(c))
                    }
                    doubleNext = false
                }
            }
            i++
        }
        return out.toString()
    }

    private fun toKatakana(text: String) = buildString {
        text.forEach { c -> append(if (c in 'ぁ'..'ゖ') c + 0x60 else c) }
    }

    private fun fullWidthToAscii(c: Char): Char = when (c) {
        in '！'..'～' -> c - 0xFEE0
        '、' -> ','
        '。' -> '.'
        '　' -> ' '
        '「', '『' -> '"'
        '」', '』' -> '"'
        '・' -> ' '
        else -> c
    }

    private val KANA: Map<Char, String> = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ヴ' to "vu",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo", 'ヮ' to "wa",
    )

    private val COMBOS: Map<String, String> = buildMap {
        val y = mapOf('ャ' to "a", 'ュ' to "u", 'ョ' to "o")
        val bases = mapOf(
            'キ' to "ky", 'ギ' to "gy", 'ニ' to "ny", 'ヒ' to "hy", 'ビ' to "by", 'ピ' to "py",
            'ミ' to "my", 'リ' to "ry", 'シ' to "sh", 'ジ' to "j", 'チ' to "ch", 'ヂ' to "j",
        )
        for ((base, prefix) in bases) for ((small, vowel) in y) put("$base$small", prefix + vowel)
        putAll(
            mapOf(
                "シェ" to "she", "ジェ" to "je", "チェ" to "che", "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
                "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
                "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo", "ツァ" to "tsa", "ツェ" to "tse", "ツォ" to "tso",
                "デュ" to "dyu", "テュ" to "tyu", "フュ" to "fyu", "イェ" to "ye",
            )
        )
    }
}
