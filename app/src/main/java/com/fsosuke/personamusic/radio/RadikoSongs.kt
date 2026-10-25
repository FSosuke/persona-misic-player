package com.fsosuke.personamusic.radio

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads radiko's "now on air" song feeds. Their format isn't documented, so this looks for
 * anything shaped like a song (a title plus an artist) and takes the newest one – and drops it
 * when it started so long ago that the song must be over.
 */
internal object RadikoSongs {

    private val TITLE_KEYS = listOf("title", "music_title", "song_title", "name")
    private val TIME_KEYS = listOf("displayed_start_time", "start_time", "started_at", "played_at", "stamp", "time")
    private val IMAGE_KEYS = listOf("img_large", "image_large", "jacket_url", "image_url", "img", "image", "jacket")
    private const val MAX_AGE_MINUTES = 12L
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    private class Candidate(val song: RadikoSong, val time: LocalDateTime?)

    fun fromJson(text: String): RadikoSong? {
        val root: Any = text.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) }
        val found = mutableListOf<Candidate>()
        collect(root, found)
        return pick(found)
    }

    private fun collect(node: Any?, out: MutableList<Candidate>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
            is JSONObject -> {
                val title = TITLE_KEYS.firstNotNullOfOrNull { k -> node.optString(k).takeIf { it.isNotBlank() } }
                val artist = artistOf(node)
                if (title != null && artist != null) {
                    out += Candidate(
                        RadikoSong(title, artist, imageOf(node)),
                        TIME_KEYS.firstNotNullOfOrNull { k -> node.optString(k).takeIf { it.isNotBlank() }?.let(::parseTime) },
                    )
                } else {
                    node.keys().forEach { collect(node.opt(it), out) }
                }
            }
        }
    }

    private fun artistOf(node: JSONObject): String? {
        node.keys().asSequence().filter { "artist" in it.lowercase() }.forEach { key ->
            when (val value = node.opt(key)) {
                is String -> if (value.isNotBlank()) return value
                is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> (0 until value.length()).mapNotNull { i ->
                    when (val a = value.opt(i)) {
                        is String -> a
                        is JSONObject -> a.optString("name").ifBlank { null }
                        else -> null
                    }
                }.takeIf { it.isNotEmpty() }?.let { return it.joinToString(", ") }
            }
        }
        return null
    }

    private fun imageOf(node: JSONObject): String? {
        IMAGE_KEYS.forEach { key ->
            when (val value = node.opt(key)) {
                is String -> if (value.startsWith("http")) return value
                is JSONObject -> value.optString("url").takeIf { it.startsWith("http") }?.let { return it }
            }
        }
        return null
    }

    fun fromXml(input: InputStream): RadikoSong? {
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        val found = mutableListOf<Candidate>()
        var children = mutableMapOf<String, String>()
        var itemAttrs: Map<String, String>? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val attrs = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                    when {
                        attrs.containsKey("title") && attrs.containsKey("artist") -> addXml(attrs, found)
                        parser.name == "item" -> { itemAttrs = attrs; children = mutableMapOf() }
                        itemAttrs != null && parser.depth > 0 -> runCatching { parser.nextText() }.getOrNull()
                            ?.let { children[parser.name ?: ""] = it.trim() }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && itemAttrs != null) {
                    addXml(itemAttrs!! + children, found)
                    itemAttrs = null
                }
            }
        }
        return pick(found)
    }

    private fun addXml(fields: Map<String, String>, out: MutableList<Candidate>) {
        val title = fields["title"]?.takeIf { it.isNotBlank() } ?: return
        val artist = fields["artist"]?.takeIf { it.isNotBlank() } ?: return
        out += Candidate(
            RadikoSong(title, artist, IMAGE_KEYS.firstNotNullOfOrNull { k -> fields[k]?.takeIf { it.startsWith("http") } }),
            TIME_KEYS.firstNotNullOfOrNull { k -> fields[k]?.takeIf { it.isNotBlank() }?.let(::parseTime) },
        )
    }

    private fun pick(found: List<Candidate>): RadikoSong? {
        if (found.isEmpty()) return null
        val newest = found.filter { it.time != null }.maxByOrNull { it.time!! } ?: return found.first().song
        val age = java.time.Duration.between(newest.time, LocalDateTime.now(JST)).toMinutes()
        return newest.song.takeIf { age in -5..MAX_AGE_MINUTES }
    }

    private val PLAIN = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    )

    private fun parseTime(text: String): LocalDateTime? {
        runCatching { return OffsetDateTime.parse(text).atZoneSameInstant(JST).toLocalDateTime() }
        PLAIN.forEach { f -> runCatching { return LocalDateTime.parse(text, f) } }
        text.toLongOrNull()?.let { epoch ->
            val millis = if (epoch > 10_000_000_000L) epoch else epoch * 1000
            return java.time.Instant.ofEpochMilli(millis).atZone(JST).toLocalDateTime()
        }
        return null
    }
}
