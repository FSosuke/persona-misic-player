package com.fsosuke.personamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs != null }

    /** Index of the line being sung at [positionMs], or -1 before the first one. */
    fun indexAt(positionMs: Long): Int = lines.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
}

/** Lyrics from LRCLIB (lrclib.net) – free, community-run, synced LRC where available. */
class LyricsRepository(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val title = cleanTitle(song.title)
        val artist = song.artist.removeSuffix(" - Topic")
        val exact = runCatching { get(title, artist, song.durationMs) }.getOrNull()
        val found = exact ?: runCatching { search(title, artist) }.getOrNull()
        found?.also { cache[song.id] = it }
    }

    private fun get(title: String, artist: String, durationMs: Long): Lyrics? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString()) }
            .build()
        return request(url.toString())?.let { parse(JSONObject(it)) }
    }

    private fun search(title: String, artist: String): Lyrics? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist")
            .build()
        val results = JSONArray(request(url.toString()) ?: return null)
        val objects = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        val best = objects.firstOrNull { !it.optString("syncedLyrics").isNullOrBlank() } ?: objects.firstOrNull()
        return best?.let { parse(it) }
    }

    private fun request(url: String): String? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "PersonaMusicPlayer/0.2 (https://github.com/FSosuke/persona-music-player)")
            .build()
        return client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun parse(json: JSONObject): Lyrics? {
        if (json.optBoolean("instrumental")) return Lyrics(listOf(LyricLine(null, "♪ Instrumental ♪")))
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return Lyrics(lines)
        }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return Lyrics(plain.lines().map { LyricLine(null, it.trim()) })
    }

    /** "Song (Official Video) [HD]" → "Song" so the lookup has a chance. */
    private fun cleanTitle(title: String) = title
        .replace(Regex("\\s*[(\\[][^)\\]]*(official|video|audio|lyrics?|mv|hd|4k|remaster)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .trim()

    companion object {
        private const val BASE = "https://lrclib.net/api"
        private val LRC_LINE = Regex("^\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]\\s*(.*)$")

        /** Parses "[mm:ss.xx] text" lines; lines without a timestamp are skipped. */
        fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().mapNotNull { line ->
            val match = LRC_LINE.find(line) ?: return@mapNotNull null
            val (min, sec, frac, text) = match.destructured
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, text.trim())
        }
    }
}
