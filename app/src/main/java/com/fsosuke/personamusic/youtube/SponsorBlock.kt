package com.fsosuke.personamusic.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * SponsorBlock's "music_offtopic" category marks intros, skits and talking in music videos —
 * the same data ReVanced uses to auto-skip non-music parts.
 */
class SponsorBlock(private val client: OkHttpClient) {

    data class Segment(val startMs: Long, val endMs: Long)

    suspend fun nonMusicSegments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("categories", "[\"music_offtopic\"]")
            .build()
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val array = JSONArray(response.body?.string() ?: return@use emptyList())
                (0 until array.length()).map { i ->
                    val segment = array.getJSONObject(i).getJSONArray("segment")
                    Segment((segment.getDouble(0) * 1000).toLong(), (segment.getDouble(1) * 1000).toLong())
                }
            }
        }.getOrDefault(emptyList())
    }
}
