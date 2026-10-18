package com.fsosuke.personamusic.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SongSource { LOCAL, YOUTUBE }

/**
 * A playable track. Local tracks use `local:<mediaStoreId>` as id and a content:// uri,
 * YouTube tracks use `yt:<videoId>` and the watch url.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0,
    val source: SongSource,
    val uri: String,
) {
    companion object {
        const val YT_PREFIX = "yt:"
        const val LOCAL_PREFIX = "local:"
    }
}

val Song.youtubeId: String?
    get() = if (source == SongSource.YOUTUBE) id.removePrefix(Song.YT_PREFIX) else null

@Entity(tableName = "liked_songs")
data class LikedSong(
    @PrimaryKey val songId: String,
    val likedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "songId"])
data class PlaylistEntry(
    val playlistId: Long,
    val songId: String,
    val position: Int,
)

@Entity(tableName = "play_events", indices = [Index("songId"), Index("playedAt")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis(),
    /** How long this play actually ran (paused time excluded). */
    @ColumnInfo(defaultValue = "0") val listenedMs: Long = 0,
)

data class SongWithPlays(
    @Embedded val song: Song,
    val plays: Int,
)

data class LoggedPlay(
    @Embedded val song: Song,
    val playedAt: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverUri: String?,
)

data class ArtistPlays(
    val name: String,
    val plays: Int,
)

data class SongStats(
    val plays: Int,
    val listenedMs: Long,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
)
