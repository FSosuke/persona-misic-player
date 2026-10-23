package com.fsosuke.personamusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** The last searches, newest first. Stored on the device only. */
class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        save(listOf(trimmed) + _entries.value.filterNot { it.equals(trimmed, ignoreCase = true) })
    }

    fun remove(query: String) = save(_entries.value - query)

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        val limited = list.take(MAX)
        prefs.edit().putString(KEY, JSONArray(limited).toString()).apply()
        _entries.value = limited
    }

    private fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "entries"
        const val MAX = 15
    }
}
