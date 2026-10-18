package com.fsosuke.personamusic.data

class MusicRepository(
    private val dao: MusicDao,
    private val scanner: LocalMusicScanner,
) {
    val localSongs = dao.localSongs()
    val likedSongs = dao.likedSongs()
    val likedIds = dao.likedIds()
    val playlists = dao.playlistSummaries()

    fun recent(limit: Int = 12) = dao.recentSongs(limit)
    fun mostPlayed(limit: Int = 9) = dao.mostPlayed(limit)
    fun songLog(limit: Int = 8) = dao.songLog(limit)
    fun searchLocal(query: String) = dao.searchLocal(query)
    fun isLiked(songId: String) = dao.isLiked(songId)
    fun playlist(id: Long) = dao.playlist(id)
    fun playlistSongs(id: Long) = dao.playlistSongs(id)
    fun playCount(songId: String) = dao.playCount(songId)

    val totalPlays = dao.totalPlays()
    val totalListenedMs = dao.totalListenedMs()
    val distinctSongsPlayed = dao.distinctSongsPlayed()
    fun topArtists(limit: Int = 5) = dao.topArtists(limit)

    /** Rescans MediaStore and returns the number of local songs found. */
    suspend fun rescanLocal(): Int {
        val songs = scanner.scan()
        dao.upsertSongs(songs)
        val found = songs.mapTo(HashSet()) { it.id }
        // Keep songs that are only gone from disk out of the library; history rows stay harmless.
        dao.localSongIds().filterNot { it in found }.chunked(500).forEach { dao.deleteSongs(it) }
        return songs.size
    }

    suspend fun remember(songs: List<Song>) = dao.upsertSongs(songs)

    suspend fun song(id: String) = dao.song(id)

    suspend fun setLiked(song: Song, liked: Boolean) {
        if (liked) {
            dao.upsertSongs(listOf(song))
            dao.like(LikedSong(song.id))
        } else {
            dao.unlike(song.id)
        }
    }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(Playlist(name = name))
    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name)
    suspend fun deletePlaylist(id: Long) {
        dao.clearPlaylist(id)
        dao.deletePlaylistRow(id)
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        dao.upsertSongs(listOf(song))
        dao.insertEntry(PlaylistEntry(playlistId, song.id, dao.lastPosition(playlistId) + 1))
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) = dao.removeEntry(playlistId, songId)

    suspend fun logPlay(songId: String) = dao.insertPlayEvent(PlayEvent(songId = songId))
}
