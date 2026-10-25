package com.fsosuke.personamusic.radio

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** The seven big regions radiko groups its areas into. */
enum class RadikoRegion(val title: String, val japanese: String) {
    HokkaidoTohoku("Hokkaido & Tohoku", "北海道・東北"),
    Kanto("Kanto", "関東"),
    HokurikuKoshinetsu("Hokuriku & Koshinetsu", "北陸・甲信越"),
    Chubu("Chubu", "中部"),
    Kinki("Kinki", "近畿"),
    ChugokuShikoku("Chugoku & Shikoku", "中国・四国"),
    Kyushu("Kyushu & Okinawa", "九州・沖縄"),
}

/** A radiko broadcast area (prefecture), with the GPS point we report for it (from rajiko). */
data class RadikoArea(
    val id: String,
    val title: String,
    val japanese: String,
    val region: RadikoRegion,
    val lat: Double,
    val lon: Double,
) {
    companion object {
        val all = listOf(
        RadikoArea("JP1", "Hokkaido", "北海道", RadikoRegion.HokkaidoTohoku, 43.064615, 141.346807),
        RadikoArea("JP2", "Aomori", "青森県", RadikoRegion.HokkaidoTohoku, 40.824308, 140.739998),
        RadikoArea("JP3", "Iwate", "岩手県", RadikoRegion.HokkaidoTohoku, 39.703619, 141.152684),
        RadikoArea("JP4", "Sendai", "宮城県", RadikoRegion.HokkaidoTohoku, 38.268837, 140.8721),
        RadikoArea("JP5", "Akita", "秋田県", RadikoRegion.HokkaidoTohoku, 39.718614, 140.102364),
        RadikoArea("JP6", "Yamagata", "山形県", RadikoRegion.HokkaidoTohoku, 38.240436, 140.363633),
        RadikoArea("JP7", "Fukushima", "福島県", RadikoRegion.HokkaidoTohoku, 37.750299, 140.467551),
        RadikoArea("JP8", "Ibaraki", "茨城県", RadikoRegion.Kanto, 36.341811, 140.446793),
        RadikoArea("JP9", "Tochigi", "栃木県", RadikoRegion.Kanto, 36.565725, 139.883565),
        RadikoArea("JP10", "Gunma", "群馬県", RadikoRegion.Kanto, 36.390668, 139.060406),
        RadikoArea("JP11", "Saitama", "埼玉県", RadikoRegion.Kanto, 35.856999, 139.648849),
        RadikoArea("JP12", "Chiba", "千葉県", RadikoRegion.Kanto, 35.605057, 140.123306),
        RadikoArea("JP13", "Tokyo", "東京都", RadikoRegion.Kanto, 35.689488, 139.691706),
        RadikoArea("JP14", "Kanagawa", "神奈川県", RadikoRegion.Kanto, 35.447507, 139.642345),
        RadikoArea("JP15", "Niigata", "新潟県", RadikoRegion.HokurikuKoshinetsu, 37.902552, 139.023095),
        RadikoArea("JP16", "Toyama", "富山県", RadikoRegion.HokurikuKoshinetsu, 36.695291, 137.211338),
        RadikoArea("JP17", "Ishikawa", "石川県", RadikoRegion.HokurikuKoshinetsu, 36.594682, 136.625573),
        RadikoArea("JP18", "Fukui", "福井県", RadikoRegion.HokurikuKoshinetsu, 36.065178, 136.221527),
        RadikoArea("JP19", "Yamanashi", "山梨県", RadikoRegion.HokurikuKoshinetsu, 35.664158, 138.568449),
        RadikoArea("JP20", "Nagano", "長野県", RadikoRegion.HokurikuKoshinetsu, 36.651299, 138.180956),
        RadikoArea("JP21", "Gifu", "岐阜県", RadikoRegion.Chubu, 35.391227, 136.722291),
        RadikoArea("JP22", "Shizuoka", "静岡県", RadikoRegion.Chubu, 34.97712, 138.383084),
        RadikoArea("JP23", "Aichi", "愛知県", RadikoRegion.Chubu, 35.180188, 136.906565),
        RadikoArea("JP24", "Mie", "三重県", RadikoRegion.Chubu, 34.730283, 136.508588),
        RadikoArea("JP25", "Shiga", "滋賀県", RadikoRegion.Kinki, 35.004531, 135.86859),
        RadikoArea("JP26", "Kyoto", "京都府", RadikoRegion.Kinki, 35.021247, 135.755597),
        RadikoArea("JP27", "Osaka", "大阪府", RadikoRegion.Kinki, 34.686297, 135.519661),
        RadikoArea("JP28", "Hyogo", "兵庫県", RadikoRegion.Kinki, 34.691269, 135.183071),
        RadikoArea("JP29", "Nara", "奈良県", RadikoRegion.Kinki, 34.685334, 135.832742),
        RadikoArea("JP30", "Wakayama", "和歌山県", RadikoRegion.Kinki, 34.225987, 135.167509),
        RadikoArea("JP31", "Tottori", "鳥取県", RadikoRegion.ChugokuShikoku, 35.503891, 134.237736),
        RadikoArea("JP32", "Shimane", "島根県", RadikoRegion.ChugokuShikoku, 35.472295, 133.0505),
        RadikoArea("JP33", "Okayama", "岡山県", RadikoRegion.ChugokuShikoku, 34.661751, 133.934406),
        RadikoArea("JP34", "Hiroshima", "広島県", RadikoRegion.ChugokuShikoku, 34.39656, 132.459622),
        RadikoArea("JP35", "Yamaguchi", "山口県", RadikoRegion.ChugokuShikoku, 34.185956, 131.470649),
        RadikoArea("JP36", "Tokushima", "徳島県", RadikoRegion.ChugokuShikoku, 34.065718, 134.55936),
        RadikoArea("JP37", "Kagawa", "香川県", RadikoRegion.ChugokuShikoku, 34.340149, 134.043444),
        RadikoArea("JP38", "Ehime", "愛媛県", RadikoRegion.ChugokuShikoku, 33.841624, 132.765681),
        RadikoArea("JP39", "Kochi", "高知県", RadikoRegion.ChugokuShikoku, 33.559706, 133.531079),
        RadikoArea("JP40", "Fukuoka", "福岡県", RadikoRegion.Kyushu, 33.606576, 130.418297),
        RadikoArea("JP41", "Saga", "佐賀県", RadikoRegion.Kyushu, 33.249442, 130.299794),
        RadikoArea("JP42", "Nagasaki", "長崎県", RadikoRegion.Kyushu, 32.744839, 129.873756),
        RadikoArea("JP43", "Kumamoto", "熊本県", RadikoRegion.Kyushu, 32.789827, 130.741667),
        RadikoArea("JP44", "Oita", "大分県", RadikoRegion.Kyushu, 33.238172, 131.612619),
        RadikoArea("JP45", "Miyazaki", "宮崎県", RadikoRegion.Kyushu, 31.911096, 131.423893),
        RadikoArea("JP46", "Kagoshima", "鹿児島県", RadikoRegion.Kyushu, 31.560146, 130.557978),
        RadikoArea("JP47", "Okinawa", "沖縄県", RadikoRegion.Kyushu, 26.2124, 127.680932),
        )

        val Miyagi = all[3]
        val Yamagata = all[5]

        fun byId(id: String?) = all.firstOrNull { it.id == id }
    }
}

/** The song a station is playing, from radiko's "now on air" music feed. */
data class RadikoSong(val title: String, val artist: String?, val image: String?)

data class RadikoProgram(
    val title: String,
    val performer: String?,
    val image: String?,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
) {
    fun isOnAir(now: LocalDateTime = Radiko.nowInJapan()) =
        (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end))

    fun isOver(now: LocalDateTime = Radiko.nowInJapan()) = end != null && !now.isBefore(end)

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
    /** Broadcast in (almost) every area, like NHK or Radio NIKKEI. */
    val nationwide: Boolean = false,
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

    private var allCache: Pair<Long, List<RadikoStation>>? = null

    /**
     * Every station in Japan, each with one area it can be heard in. Loads all 47 area lists
     * (a few at a time) and keeps them for ten minutes.
     */
    suspend fun allStations(): List<RadikoStation> = withContext(Dispatchers.IO) {
        allCache?.takeIf { System.currentTimeMillis() - it.first < ALL_TTL_MS }?.let { return@withContext it.second }
        val gate = Semaphore(8)
        val perArea = coroutineScope {
            RadikoArea.all.map { area -> async { gate.withPermit { runCatching { stations(area) }.getOrDefault(emptyList()) } } }.awaitAll()
        }
        if (perArea.all { it.isEmpty() }) throw IOException("radiko station lists couldn't be loaded")
        val counts = perArea.flatten().groupingBy { it.id }.eachCount()
        val result = perArea.flatten()
            .distinctBy { it.id }
            .map { it.copy(nationwide = (counts[it.id] ?: 0) >= NATIONWIDE_AREAS) }
        allCache = System.currentTimeMillis() to result
        result
    }

    /** The song on air at [stationId], or null during talk, news or ads. */
    suspend fun currentSong(stationId: String): RadikoSong? = withContext(Dispatchers.IO) {
        val fromApi = runCatching {
            get("https://api.radiko.jp/music/api/v1/noas/$stationId/latest?size=1").use { response ->
                if (response.isSuccessful) RadikoSongs.fromJson(response.body!!.string()) else null
            }
        }.getOrNull()
        fromApi ?: runCatching {
            get("https://radiko.jp/v3/feed/pc/noa/$stationId.xml").use { response ->
                if (response.isSuccessful) RadikoSongs.fromXml(response.body!!.byteStream()) else null
            }
        }.getOrNull()
    }

    /** A station's programme for one broadcast day ([Radiko.broadcastDay]). */
    suspend fun schedule(stationId: String, day: java.time.LocalDate): List<RadikoProgram> = withContext(Dispatchers.IO) {
        val date = day.format(DateTimeFormatter.BASIC_ISO_DATE)
        get("https://radiko.jp/v3/program/station/date/$date/$stationId.xml").use { response ->
            if (!response.isSuccessful) throw IOException("radiko programme: HTTP ${response.code}")
            parseProgramList(response.body!!.byteStream()).filter { it.first == stationId }.map { it.second }
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
        private const val ALL_TTL_MS = 10 * 60 * 1000L
        private const val NATIONWIDE_AREAS = 20
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

        /** All programmes in a radiko programme XML, in order, with their station ids. */
        internal fun parseProgramList(input: InputStream): List<Pair<String, RadikoProgram>> {
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            val result = mutableListOf<Pair<String, RadikoProgram>>()
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
                        result += id to RadikoProgram(
                            title = fields["title"].orEmpty(),
                            performer = fields["pfm"]?.takeIf { it.isNotBlank() },
                            image = fields["img"]?.takeIf { it.isNotBlank() },
                            start = start,
                            end = end,
                        )
                    }
                }
            }
            return result
        }

        internal fun parsePrograms(input: InputStream, now: LocalDateTime = nowInJapan()): Map<String, RadikoProgram> {
            val result = mutableMapOf<String, RadikoProgram>()
            parseProgramList(input).forEach { (id, program) ->
                // The feed may list neighbouring programmes too: keep the one on air.
                if (program.isOnAir(now) || id !in result) result[id] = program
            }
            return result
        }

        fun nowInJapan(): LocalDateTime = LocalDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))

        /** radiko's broadcast day runs from 5:00 to 29:00 JST. */
        fun broadcastDay(now: LocalDateTime = nowInJapan()): java.time.LocalDate =
            if (now.hour < 5) now.toLocalDate().minusDays(1) else now.toLocalDate()

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
