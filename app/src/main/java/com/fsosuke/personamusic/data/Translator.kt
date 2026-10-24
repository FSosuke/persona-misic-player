package com.fsosuke.personamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Line-by-line lyric translation into the phone's language, using Google Translate's public
 * web endpoint (no key needed). Results are cached per song.
 */
class Translator(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, List<String>>()

    val targetLanguage: String get() = Locale.getDefault().language.ifEmpty { "en" }

    suspend fun translate(cacheKey: String, lines: List<String>): List<String> = withContext(Dispatchers.IO) {
        val key = "$cacheKey|$targetLanguage"
        cache[key]?.let { return@withContext it }
        // Translate everything in one request, one line per line, so the lines stay aligned.
        val text = lines.joinToString("\n") { it.ifBlank { "♪" } }
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage&dt=t")
            .post(FormBody.Builder().add("q", text).build())
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Translation failed (HTTP ${response.code})")
            response.body?.string() ?: throw IOException("Empty translation")
        }
        val segments = JSONArray(body).getJSONArray(0)
        val translated = buildString {
            for (i in 0 until segments.length()) append(segments.getJSONArray(i).optString(0))
        }
        val result = translated.split("\n").map { it.trim() }
        // If the service merged lines, fall back to whatever aligns.
        val aligned = List(lines.size) { i -> if (lines[i].isBlank()) "" else result.getOrElse(i) { "" } }
        cache[key] = aligned
        aligned
    }
}
