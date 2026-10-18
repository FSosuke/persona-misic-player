package com.fsosuke.personamusic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Upsert
    suspend fun upsertSongs(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): Song?

    @Query("SELECT * FROM songs WHERE source = 'LOCAL' ORDER BY title COLLATE NOCASE")
    fun localSongs(): Flow<List<Song>>

    @Query("SELECT id FROM songs WHERE source = 'LOCAL'")
    suspend fun localSongIds(): List<String>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<String>)

    @Query(
        "SELECT * FROM songs WHERE source = 'LOCAL' AND " +
            "(title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') " +
            "ORDER BY title COLLATE NOCASE LIMIT 30"
    )
    fun searchLocal(query: String): Flow<List<Song>>

    // --- Likes -----------------------------------------------------------------------------

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)")
    fun isLiked(songId: String): Flow<Boolean>

    @Query("SELECT songId FROM liked_songs")
    fun likedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun like(liked: LikedSong)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun unlike(songId: String)

    @Query("SELECT s.* FROM liked_songs l JOIN songs s ON s.id = l.songId ORDER BY l.likedAt DESC")
    fun likedSongs(): Flow<List<Song>>

    // --- Playlists -------------------------------------------------------------------------

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<Playlist?>

    @Query(
        "SELECT p.id AS id, p.name AS name, " +
            "(SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount, " +
            "(SELECT s.artworkUri FROM playlist_entries e JOIN songs s ON s.id = e.songId " +
            "  WHERE e.playlistId = p.id AND s.artworkUri IS NOT NULL ORDER BY e.position LIMIT 1) AS coverUri " +
            "FROM playlists p ORDER BY p.createdAt"
    )
    fun playlistSummaries(): Flow<List<PlaylistSummary>>

    @Query(
        "SELECT s.* FROM playlist_entries e JOIN songs s ON s.id = e.songId " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position"
    )
    fun playlistSongs(playlistId: Long): Flow<List<Song>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeEntry(playlistId: Long, songId: String)

    // --- History & stats -------------------------------------------------------------------

    @Insert
    suspend fun insertPlayEvent(event: PlayEvent)

    @Query(
        "SELECT s.* FROM songs s JOIN " +
            "(SELECT songId, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY songId) p " +
            "ON s.id = p.songId ORDER BY p.lastPlayed DESC LIMIT :limit"
    )
    fun recentSongs(limit: Int): Flow<List<Song>>

    @Query(
        "SELECT s.*, p.plays AS plays FROM songs s JOIN " +
            "(SELECT songId, COUNT(*) AS plays FROM play_events GROUP BY songId) p " +
            "ON s.id = p.songId ORDER BY p.plays DESC LIMIT :limit"
    )
    fun mostPlayed(limit: Int): Flow<List<SongWithPlays>>

    @Query(
        "SELECT s.*, e.playedAt AS playedAt FROM play_events e JOIN songs s ON s.id = e.songId " +
            "ORDER BY e.playedAt DESC LIMIT :limit"
    )
    fun songLog(limit: Int): Flow<List<LoggedPlay>>

    @Query("SELECT COUNT(*) FROM play_events")
    fun totalPlays(): Flow<Int>

    @Query("SELECT COALESCE(SUM(s.durationMs), 0) FROM play_events e JOIN songs s ON s.id = e.songId")
    fun totalListenedMs(): Flow<Long>

    @Query("SELECT COUNT(DISTINCT songId) FROM play_events")
    fun distinctSongsPlayed(): Flow<Int>

    @Query(
        "SELECT s.artist AS name, COUNT(*) AS plays FROM play_events e JOIN songs s ON s.id = e.songId " +
            "GROUP BY s.artist ORDER BY plays DESC LIMIT :limit"
    )
    fun topArtists(limit: Int): Flow<List<ArtistPlays>>

    @Query("SELECT COUNT(*) FROM play_events WHERE songId = :songId")
    fun playCount(songId: String): Flow<Int>
}
