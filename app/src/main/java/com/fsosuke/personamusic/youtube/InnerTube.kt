package com.fsosuke.personamusic.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/**
 * Talks to the private API behind music.youtube.com (the same one the website uses), authenticated
 * with the browser cookies of the signed-in account. Only used for personal data (home feed,
 * library); audio still comes from NewPipeExtractor.
 */
class InnerTube(private val client: OkHttpClient, context: Context) {

    private val prefs = context.getSharedPreferences("innertube", Context.MODE_PRIVATE)

    @Volatile
    private var clientVersion: String? = null

    suspend fun post(
        endpoint: String,
        cookie: String?,
        body: JSONObject = JSONObject(),
        query: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val version = clientVersion(cookie)
        body.put("context", clientContext(version))
        val url = "$ORIGIN/youtubei/v1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false")
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .headers(headers(cookie, version))
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube Music antwortet mit HTTP ${response.code}")
            JSONObject(text)
        }
    }

    /** Authenticated GET, used for playback-statistics pings. */
    suspend fun ping(url: String, cookie: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).headers(headers(cookie, clientVersion(cookie))).get().build()
        client.newCall(request).execute().close()
    }

    private fun clientContext(version: String): JSONObject {
        val locale = Locale.getDefault()
        return JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", version)
                .put("hl", locale.language.ifEmpty { "en" })
                .put("gl", locale.country.ifEmpty { "US" }),
        ).put("user", JSONObject())
    }

    private fun headers(cookie: String?, version: String): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", NewPipeDownloader.USER_AGENT)
            .add("Origin", ORIGIN)
            .add("X-Origin", ORIGIN)
            .add("Referer", "$ORIGIN/")
            .add("X-Goog-AuthUser", "0")
            .add("X-YouTube-Client-Name", "67")
            .add("X-YouTube-Client-Version", version)
        if (cookie != null) {
            builder.add("Cookie", cookie)
            sapisidHash(cookie)?.let { builder.add("Authorization", it) }
        }
        return builder.build()
    }

    /** The website's current client version; YouTube rejects very old ones over time. */
    private fun clientVersion(cookie: String?): String {
        clientVersion?.let { return it }
        val fetched = runCatching {
            val request = Request.Builder().url(ORIGIN)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .apply { if (cookie != null) header("Cookie", cookie) }
                .build()
            client.newCall(request).execute().use { response ->
                VERSION_REGEX.find(response.body?.string().orEmpty())?.groupValues?.get(1)
            }
        }.getOrNull()
        val version = fetched ?: prefs.getString(KEY_VERSION, null) ?: FALLBACK_VERSION
        if (fetched != null) prefs.edit().putString(KEY_VERSION, fetched).apply()
        clientVersion = version
        return version
    }

    companion object {
        const val ORIGIN = "https://music.youtube.com"
        private const val KEY_VERSION = "client_version"
        private const val FALLBACK_VERSION = "1.20250310.01.00"
        private val VERSION_REGEX = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"")
        private val JSON = "application/json".toMediaType()

        fun parseCookies(cookie: String): Map<String, String> = cookie.split(";")
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }
            .toMap()

        fun hasAuthCookie(cookie: String?): Boolean {
            val cookies = parseCookies(cookie ?: return false)
            return "SAPISID" in cookies || "__Secure-3PAPISID" in cookies
        }

        /** Google's cookie-session auth header: SHA1 over "<time> <SAPISID> <origin>". */
        private fun sapisidHash(cookie: String): String? {
            val cookies = parseCookies(cookie)
            val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: return null
            val time = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1").digest("$time $sapisid $ORIGIN".toByteArray())
            return "SAPISIDHASH ${time}_" + digest.joinToString("") { "%02x".format(it) }
        }
    }
}
