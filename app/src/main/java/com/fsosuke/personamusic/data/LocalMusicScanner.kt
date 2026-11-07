package com.fsosuke.personamusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads every music file known to Android's MediaStore. */
class LocalMusicScanner(
    private val context: Context,
    /** MediaStore relative path (e.g. "Music/Persona/") whose files are skipped – the download folder. */
    private val excludedRelativePath: () -> String? = { null },
) {

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(MediaStore.Audio.Media.RELATIVE_PATH) else emptyArray()
        val excluded = excludedRelativePath()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = mutableListOf<Song>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                if (excluded != null && pathCol >= 0 && cursor.getString(pathCol)?.startsWith(excluded, ignoreCase = true) == true) continue
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val artist = cursor.getString(artistCol)
                songs += Song(
                    id = Song.LOCAL_PREFIX + id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = if (artist.isNullOrBlank() || artist == MediaStore.UNKNOWN_STRING) "Unknown artist" else artist,
                    album = cursor.getString(albumCol),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART, albumId).toString(),
                    durationMs = cursor.getLong(durationCol),
                    source = SongSource.LOCAL,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                )
            }
        }
        songs
    }

    private companion object {
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
