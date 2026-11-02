package com.fsosuke.personamusic.awa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** The AWA lists we show; some have fixed ids, others are re-published monthly under a dated id. */
enum class AwaList(val label: String, val japanese: String, val ranked: Boolean, val fixedId: String? = null, val latestPrefix: String? = null) {
    Daily("Top 100 new", "新着楽曲トップ100", ranked = true, fixedId = "arrival.tracks"),
    JPop("New J-Pop", "邦楽 今週の最新曲", ranked = false, fixedId = "overlay.thumb.newmusic.japanese"),
    KPop("New K-Pop", "K-POP 今週の新着曲", ranked = false, fixedId = "overlay.thumb.subgenre.kpop"),
    Global("New international", "洋楽 今週の最新曲", ranked = false, fixedId = "overlay.thumb.newmusic.global"),
    Picks("Most picked", "プレイリスト採用楽曲ランキング", ranked = true, latestPrefix = "spine.thumb.ranking.topsongs."),
    Internet("Internet-born", "ネット発アーティスト", ranked = false, latestPrefix = "overlay.thumb.artistsfromtheinternet."),
}

data class AwaTrack(val title: String, val artist: String, val album: String?, val cover: String?, val durationSec: Int) {
    /** What to look for on YouTube Music. */
    val query get() = "$title $artist"
}

data class AwaChart(val name: String, val description: String?, val updatedAtSec: Long, val tracks: List<AwaTrack>)

/**
 * Charts and new releases from AWA (Japanese streaming service). There is no public API, so this
 * reads the data embedded in AWA's public playlist pages (s.awa.fm) – experimental: it breaks if
 * AWA changes those pages. Results are kept for a few hours.
 */
class AwaCharts(private val http: OkHttpClient) {
    private val cache = ConcurrentHashMap<AwaList, Pair<Long, AwaChart>>()

    suspend fun load(list: AwaList, force: Boolean = false): AwaChart = withContext(Dispatchers.IO) {
        cache[list]?.takeIf { !force && System.currentTimeMillis() - it.first < CACHE_MS }?.let { return@withContext it.second }
        val id = list.fixedId ?: latestId(list.latestPrefix!!) ?: throw IOException("AWA has no current \"${list.label}\" list")
        val chart = parsePlaylist(page("$BASE/playlist/$id")) ?: throw IOException("AWA's page layout changed")
        cache[list] = System.currentTimeMillis() to chart
        chart
    }

    /** Newest playlist of the official AWA account whose id starts with [prefix] (ids end in a date). */
    private fun latestId(prefix: String): String? {
        val html = page("$BASE/user/awa_official")
        return Regex("\"id\"\\s*:\\s*\"(${Regex.escape(prefix)}[^\"]+)\"").findAll(html).map { it.groupValues[1] }.maxOrNull()
    }

    private fun page(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept-Language", "ja,en;q=0.8").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AWA answered HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        private const val BASE = "https://s.awa.fm"
        private const val CACHE_MS = 3 * 60 * 60 * 1000L
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private val NEXT_DATA = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)

        /** Reads the playlist from the page's embedded Next.js data. */
        internal fun parsePlaylist(html: String): AwaChart? {
            val json = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
            val map = runCatching {
                JSONObject(json).getJSONObject("props").getJSONObject("pageProps").getJSONObject("dehydrated")
                    .getJSONObject("context").getJSONObject("dispatcher").getJSONObject("stores")
                    .getJSONObject("PlaylistStore").getJSONObject("playlist").getJSONObject("map")
            }.getOrNull() ?: return null
            val playlist = map.keys().asSequence().firstOrNull()?.let { map.getJSONObject(it) } ?: return null
            val tracks = playlist.optJSONArray("tracks") ?: JSONArray()
            return AwaChart(
                name = playlist.optString("name"),
                description = playlist.optString("description").takeIf { it.isNotBlank() },
                updatedAtSec = playlist.optLong("updatedAt"),
                tracks = (0 until tracks.length()).mapNotNull { i ->
                    val t = tracks.optJSONObject(i) ?: return@mapNotNull null
                    if (t.optBoolean("isDeleted")) return@mapNotNull null
                    val album = t.optJSONObject("album")
                    AwaTrack(
                        title = t.optString("name").ifBlank { return@mapNotNull null },
                        artist = t.optJSONObject("artist")?.optString("name").orEmpty(),
                        album = album?.optString("name")?.takeIf { it.isNotBlank() },
                        cover = album?.optString("id")?.takeIf { it.isNotBlank() }?.let { id ->
                            val version = album.optLong("version")
                            "https://pimg.awa.io/v2/jacket/$id.w300.h300" + (if (version > 0) ".v$version" else "") + ".jpg"
                        },
                        durationSec = t.optInt("playbackTime"),
                    )
                },
            )
        }
    }
}
