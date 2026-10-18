package com.fsosuke.personamusic.youtube

import org.json.JSONArray
import org.json.JSONObject

// Minimal helpers for walking InnerTube's deeply nested JSON without a schema.

internal fun Any?.at(vararg path: Any): Any? {
    var current: Any? = this
    for (key in path) {
        current = when {
            key is String && current is JSONObject -> current.opt(key)
            key is Int && current is JSONArray -> current.opt(if (key < 0) current.length() + key else key)
            else -> null
        }
    }
    return current
}

internal fun Any?.obj(vararg path: Any): JSONObject? = at(*path) as? JSONObject
internal fun Any?.arr(vararg path: Any): JSONArray? = at(*path) as? JSONArray
internal fun Any?.str(vararg path: Any): String? = at(*path) as? String

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Joins the `runs` of an InnerTube text object. */
internal fun JSONObject?.runsText(): String? =
    this?.optJSONArray("runs")?.objects()?.joinToString("") { it.optString("text") }?.takeIf { it.isNotBlank() }
        ?: this?.optString("simpleText")?.takeIf { it.isNotBlank() }

/**
 * Collects every object stored under [key], in document order. Does not descend into matches,
 * so nested renderers of the same kind are not double-counted.
 */
internal fun findAll(root: Any?, key: String): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val value = node.opt(k)
                if (k == key && value is JSONObject) out += value else walk(value)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
        }
    }
    walk(root)
    return out
}

internal fun findFirst(root: Any?, key: String): JSONObject? = findAll(root, key).firstOrNull()

/** Picks the largest image of a `thumbnails` array and asks Google's image server for a bigger one. */
internal fun bestThumbnail(thumbnails: JSONArray?): String? {
    val url = thumbnails?.objects()?.maxByOrNull { it.optInt("width") }?.optString("url")?.takeIf { it.isNotBlank() }
        ?: return null
    return upscaleThumbnail(url)
}

internal fun upscaleThumbnail(url: String): String =
    if ("googleusercontent.com" in url || "ggpht.com" in url) url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj") else url
