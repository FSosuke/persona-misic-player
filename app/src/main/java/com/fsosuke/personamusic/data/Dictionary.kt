package com.fsosuke.personamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** English meanings of Japanese words from Jisho.org (JMdict). */
class Dictionary(private val client: OkHttpClient) {

    data class Entry(val word: String, val reading: String?, val meanings: List<String>, val tags: List<String>)

    private val cache = ConcurrentHashMap<String, List<Entry>>()

    suspend fun lookup(word: String): List<Entry> = withContext(Dispatchers.IO) {
        cache[word]?.let { return@withContext it }
        val url = "https://jisho.org/api/v1/search/words".toHttpUrl().newBuilder().addQueryParameter("keyword", word).build()
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Dictionary unavailable (HTTP ${response.code})")
            response.body?.string() ?: throw IOException("Empty dictionary response")
        }
        val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        val entries = (0 until minOf(data.length(), 3)).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            val japanese = item.optJSONArray("japanese")?.optJSONObject(0)
            val senses = item.optJSONArray("senses") ?: return@mapNotNull null
            val meanings = (0 until minOf(senses.length(), 4)).mapNotNull { s ->
                senses.optJSONObject(s)?.optJSONArray("english_definitions")?.let { defs ->
                    (0 until defs.length()).joinToString("; ") { defs.optString(it) }
                }
            }
            val tags = buildList {
                if (item.optBoolean("is_common")) add("common")
                item.optJSONArray("jlpt")?.let { j -> (0 until j.length()).forEach { add(j.optString(it).uppercase().replace("-", " ")) } }
            }
            Entry(japanese?.optString("word")?.ifEmpty { null } ?: word, japanese?.optString("reading"), meanings, tags)
        }
        cache[word] = entries
        entries
    }
}
