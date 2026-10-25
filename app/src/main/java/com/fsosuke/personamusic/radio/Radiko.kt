package com.fsosuke.personamusic.radio

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** A radiko broadcast area, with the GPS point we report for it. */
enum class RadikoArea(val id: String, val title: String, val japanese: String, val lat: Double, val lon: Double) {
    Miyagi("JP4", "Sendai", "宮城", 38.268837, 140.8721),
    Yamagata("JP6", "Yamagata", "山形", 38.240436, 140.363633),
    Tokyo("JP13", "Tokyo", "東京", 35.689488, 139.691706),
    Osaka("JP27", "Osaka", "大阪", 34.686297, 135.519661);

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

data class RadikoProgram(
    val title: String,
    val performer: String?,
    val image: String?,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
) {
    val timeRange: String?
        get() = if (start != null && end != null) "${start.format(CLOCK)}–${end.format(CLOCK)}" else null

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class RadikoStation(
    val id: String,
    val name: String,
    val asciiName: String?,
    val logo: String,
    val area: RadikoArea,
    val program: RadikoProgram? = null,
)

/**
 * Live radio from radiko.jp, also outside Japan – the approach of rajiko
 * (https://github.com/jackyzy823/rajiko, public domain): log in like the radiko Android app,
 * whose auth checks a GPS location instead of the IP address, and report a point in the chosen
 * area. The partial key comes from the app's key file (assets/radiko/key.bin).
 *
 * Tokens live 90 minutes; we renew them after 60.
 */
class Radiko(private val context: Context, private val http: OkHttpClient) {

    private class Token(val value: String, val createdAt: Long)

    private val tokens = ConcurrentHashMap<String, Token>()
    private val streamUrls = ConcurrentHashMap<String, String>()

    /** Area of the station currently streaming; its token goes on every stream request. */
    @Volatile
    var activeArea: RadikoArea = RadikoArea.Miyagi

    private val fullKey: ByteArray by lazy { context.assets.open("radiko/key.bin").use(InputStream::readBytes) }

    // Device identity reported to radiko, stable for the app's lifetime like a real install.
    private val appVersion = APP_VERSIONS.random()
    private val device = "${SDK_VERSIONS.random()}.${MODELS.random()}"
    private val userId = randomHex(32)

    /** Valid token for [area], logging in again when missing or older than an hour. Blocking. */
    @Synchronized
    fun token(area: RadikoArea): String {
        tokens[area.id]?.takeIf { System.currentTimeMillis() - it.createdAt < TOKEN_TTL_MS }?.let { return it.value }
        val token = authenticate(area)
        tokens[area.id] = Token(token, System.currentTimeMillis())
        return token
    }

    /** Headers radiko's stream servers expect. Blocking (may log in). */
    fun streamHeaders(area: RadikoArea = activeArea): Map<String, String> =
        mapOf("X-Radiko-AuthToken" to token(area), "X-Radiko-AreaId" to area.id)

    private fun authenticate(area: RadikoArea): String {
        val appHeaders = mapOf(
            "X-Radiko-App" to APP_ID,
            "X-Radiko-App-Version" to appVersion,
            "X-Radiko-Device" to device,
            "X-Radiko-User" to userId,
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 14; ${device.substringAfter('.')})",
        )
        val auth1 = get("https://api.radiko.jp/v2/api/auth1", appHeaders)
        val (token, offset, length) = auth1.use { response ->
            if (!response.isSuccessful) throw IOException("radiko login failed (HTTP ${response.code})")
            Triple(
                response.header("X-Radiko-AuthToken") ?: throw IOException("radiko sent no token"),
                response.header("X-Radiko-KeyOffset")?.toIntOrNull() ?: 0,
                response.header("X-Radiko-KeyLength")?.toIntOrNull() ?: 16,
            )
        }
        val partialKey = Base64.encodeToString(fullKey.copyOfRange(offset, offset + length), Base64.NO_WRAP)
        val auth2 = get(
            "https://api.radiko.jp/v2/api/auth2",
            appHeaders + mapOf(
                "X-Radiko-AuthToken" to token,
                "X-Radiko-Partialkey" to partialKey,
                "X-Radiko-Location" to location(area),
                "X-Radiko-Connection" to "wifi",
            ),
        )
        auth2.use { response ->
            if (!response.isSuccessful) throw IOException("radiko rejected the login (HTTP ${response.code})")
        }
        return token
    }

    /** Somewhere within ~2.5 km of the area's capital, like rajiko does. */
    private fun location(area: RadikoArea): String {
        fun jitter() = Random.nextDouble(-0.025, 0.025)
        return "%.6f,%.6f,gps".format(java.util.Locale.US, area.lat + jitter(), area.lon + jitter())
    }

    suspend fun stations(area: RadikoArea): List<RadikoStation> = withContext(Dispatchers.IO) {
        val stations = get("https://radiko.jp/v3/station/list/${area.id}.xml").use { response ->
            if (!response.isSuccessful) throw IOException("radiko station list: HTTP ${response.code}")
            parseStations(response.body!!.byteStream(), area)
        }
        val programs = runCatching { nowOnAir(area) }.getOrDefault(emptyMap())
        stations.map { it.copy(program = programs[it.id]) }
    }

    /** What every station of [area] is broadcasting right now. */
    suspend fun nowOnAir(area: RadikoArea): Map<String, RadikoProgram> = withContext(Dispatchers.IO) {
        get("https://radiko.jp/v3/program/now/${area.id}.xml").use { response ->
            if (!response.isSuccessful) throw IOException("radiko programs: HTTP ${response.code}")
            parsePrograms(response.body!!.byteStream())
        }
    }

    /** HLS playlist for a live station. Blocking. */
    fun streamUrl(stationId: String): String = streamUrls.getOrPut(stationId) {
        val base = runCatching {
            get("https://radiko.jp/v3/station/stream/pc_html5/$stationId.xml").use { response ->
                if (response.isSuccessful) parseLiveUrl(response.body!!.byteStream()) else null
            }
        }.getOrNull()
        if (base == null) {
            "https://f-radiko.smartstream.ne.jp/$stationId/_definst_/simul-stream.stream/playlist.m3u8"
        } else {
            Uri.parse(base).buildUpon()
                .appendQueryParameter("station_id", stationId)
                .appendQueryParameter("l", "15")
                .appendQueryParameter("lsid", randomHex(32))
                .appendQueryParameter("type", "b")
                .build()
                .toString()
        }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()) =
        http.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()).execute()

    companion object {
        const val SCHEME = "radiko"
        const val ID_PREFIX = "radiko:"
        private const val APP_ID = "aSmartPhone8"
        private const val TOKEN_TTL_MS = 60 * 60 * 1000L
        private val APP_VERSIONS = listOf("8.4.5", "8.4.4", "8.4.3", "8.4.2", "8.4.1", "8.3.12", "8.3.10", "8.3.8")
        private val SDK_VERSIONS = listOf("31", "32", "33", "34")
        private val MODELS = listOf("SM-G960F", "SM-G965F", "SM-G973F", "SM-G991B", "SC-02J", "SO-03L", "Pixel 6", "Pixel 7")
        private val PROGRAM_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /** `radiko:<stationId>@<areaId>` – the playable uri of a station. */
        fun uriFor(station: RadikoStation): Uri = Uri.parse("$SCHEME:${station.id}@${station.area.id}")

        fun parseUri(uri: Uri): Pair<String, RadikoArea>? {
            if (uri.scheme != SCHEME) return null
            val (station, area) = uri.schemeSpecificPart.split('@').takeIf { it.size == 2 } ?: return null
            return station to (RadikoArea.byId(area) ?: return null)
        }

        fun isStreamHost(host: String?) = host != null && (host.endsWith("smartstream.ne.jp") || host.endsWith("radiko.jp"))

        private fun randomHex(length: Int) = buildString { repeat(length) { append("0123456789abcdef"[Random.nextInt(16)]) } }

        internal fun parseStations(input: InputStream, area: RadikoArea): List<RadikoStation> {
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            val result = mutableListOf<RadikoStation>()
            var fields = mutableMapOf<String, String>()
            var inStation = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when {
                        parser.name == "station" -> { inStation = true; fields = mutableMapOf() }
                        inStation && parser.name in STATION_FIELDS && parser.name !in fields ->
                            fields[parser.name] = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "station" && inStation) {
                        inStation = false
                        val id = fields["id"] ?: continue
                        result += RadikoStation(
                            id = id,
                            name = fields["name"] ?: id,
                            asciiName = fields["ascii_name"],
                            logo = fields["logo"] ?: "https://radiko.jp/v2/static/station/logo/$id/224x100.png",
                            area = area,
                        )
                    }
                }
            }
            return result
        }

        private val STATION_FIELDS = setOf("id", "name", "ascii_name", "logo")

        internal fun parsePrograms(input: InputStream, now: LocalDateTime = LocalDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))): Map<String, RadikoProgram> {
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            val result = mutableMapOf<String, RadikoProgram>()
            var stationId: String? = null
            var start: LocalDateTime? = null
            var end: LocalDateTime? = null
            var fields = mutableMapOf<String, String>()
            var inProgram = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "station" -> stationId = parser.getAttributeValue(null, "id")
                        "prog" -> {
                            inProgram = true
                            fields = mutableMapOf()
                            start = parser.getAttributeValue(null, "ft")?.let { runCatching { LocalDateTime.parse(it, PROGRAM_TIME) }.getOrNull() }
                            end = parser.getAttributeValue(null, "to")?.let { runCatching { LocalDateTime.parse(it, PROGRAM_TIME) }.getOrNull() }
                        }
                        "title", "pfm", "img" -> if (inProgram) fields[parser.name] = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "prog" && inProgram) {
                        inProgram = false
                        val id = stationId ?: continue
                        val program = RadikoProgram(
                            title = fields["title"].orEmpty(),
                            performer = fields["pfm"]?.takeIf { it.isNotBlank() },
                            image = fields["img"]?.takeIf { it.isNotBlank() },
                            start = start,
                            end = end,
                        )
                        // The feed may list neighbouring programs too: keep the one on air.
                        val onAir = (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end))
                        if (onAir || id !in result) result[id] = program
                    }
                }
            }
            return result
        }

        /** The live (not time-free) playlist_create_url, preferring regional over area-free servers. */
        internal fun parseLiveUrl(input: InputStream): String? {
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            val candidates = mutableListOf<Pair<Boolean, String>>()
            var areafree = false
            var timefree = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "url" -> {
                        areafree = parser.getAttributeValue(null, "areafree") == "1"
                        timefree = parser.getAttributeValue(null, "timefree") == "1"
                    }
                    "playlist_create_url" -> {
                        val url = parser.nextText().trim()
                        if (!timefree && url.isNotEmpty()) candidates += areafree to url
                    }
                }
            }
            return (candidates.firstOrNull { !it.first } ?: candidates.firstOrNull())?.second
        }
    }
}
