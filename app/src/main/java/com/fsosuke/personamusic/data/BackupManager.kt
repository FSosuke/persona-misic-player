package com.fsosuke.personamusic.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Exports the library state (songs, favourites, playlists, play history, look & feel) to a JSON
 * file and merges it back in. The YouTube login is deliberately not part of a backup.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: AppSettings,
) {
    private val dao = database.musicDao()

    data class Summary(val songs: Int, val playlists: Int, val plays: Int)

    suspend fun export(target: Uri): Summary = withContext(Dispatchers.IO) {
        val songs = dao.allSongs()
        val playlists = dao.allPlaylists()
        val events = dao.allEvents()
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("songs", JSONArray(songs.map { it.toJson() }))
            .put("liked", JSONArray(dao.allLiked().map { JSONObject().put("songId", it.songId).put("likedAt", it.likedAt) }))
            .put("playlists", JSONArray(playlists.map { JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt) }))
            .put(
                "entries",
                JSONArray(dao.allEntries().map { JSONObject().put("playlistId", it.playlistId).put("songId", it.songId).put("position", it.position) }),
            )
            .put(
                "events",
                JSONArray(events.map { JSONObject().put("songId", it.songId).put("playedAt", it.playedAt).put("listenedMs", it.listenedMs) }),
            )
            .put(
                "settings",
                JSONObject()
                    .put("theme", settings.theme.value.name)
                    .put("quality", settings.quality.value.name)
                    .put("skipNonMusic", settings.skipNonMusic.value),
            )
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toString().toByteArray()) }
            ?: throw IOException("Datei konnte nicht geschrieben werden")
        Summary(songs.size, playlists.size, events.size)
    }

    /** Merges a backup into the current library; existing data is kept, duplicates are skipped. */
    suspend fun import(source: Uri): Summary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Datei konnte nicht gelesen werden")
        val json = JSONObject(text)
        if (json.optString("format") != FORMAT) throw IOException("Das ist kein Persona-Music-Backup")

        val songs = json.optJSONArray("songs").objects().mapNotNull { it.toSong() }
        val playlists = json.optJSONArray("playlists").objects()
        val events = json.optJSONArray("events").objects()
        var addedPlays = 0

        database.withTransaction {
            dao.upsertSongs(songs)
            dao.insertLiked(json.optJSONArray("liked").objects().map { LikedSong(it.getString("songId"), it.optLong("likedAt")) })

            // Playlists are matched by name so importing twice doesn't duplicate them.
            val idMap = playlists.associate { p ->
                val name = p.getString("name")
                p.getLong("id") to (dao.playlistIdByName(name) ?: dao.insertPlaylist(Playlist(name = name, createdAt = p.optLong("createdAt"))))
            }
            dao.insertEntries(
                json.optJSONArray("entries").objects().mapNotNull { e ->
                    val id = idMap[e.getLong("playlistId")] ?: return@mapNotNull null
                    PlaylistEntry(id, e.getString("songId"), e.optInt("position"))
                },
            )

            val known = dao.allEvents().mapTo(HashSet()) { it.songId to it.playedAt }
            val newEvents = events
                .map { PlayEvent(songId = it.getString("songId"), playedAt = it.getLong("playedAt"), listenedMs = it.optLong("listenedMs")) }
                .filter { (it.songId to it.playedAt) !in known }
            dao.insertEvents(newEvents)
            addedPlays = newEvents.size
        }

        json.optJSONObject("settings")?.let { s ->
            runCatching { settings.setTheme(PersonaTheme.valueOf(s.getString("theme"))) }
            runCatching { settings.setQuality(AudioQuality.valueOf(s.getString("quality"))) }
            if (s.has("skipNonMusic")) settings.setSkipNonMusic(s.getBoolean("skipNonMusic"))
        }
        Summary(songs.size, playlists.size, addedPlays)
    }

    private fun Song.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("artworkUri", artworkUri)
        .put("durationMs", durationMs)
        .put("source", source.name)
        .put("uri", uri)

    private fun JSONObject.toSong(): Song? = runCatching {
        Song(
            id = getString("id"),
            title = getString("title"),
            artist = getString("artist"),
            album = optString("album").takeIf { has("album") && !isNull("album") },
            artworkUri = optString("artworkUri").takeIf { has("artworkUri") && !isNull("artworkUri") },
            durationMs = optLong("durationMs"),
            source = SongSource.valueOf(getString("source")),
            uri = getString("uri"),
        )
    }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val FORMAT = "persona-music-backup"
    }
}
