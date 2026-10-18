package com.fsosuke.personamusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.youtubeId
import com.fsosuke.personamusic.youtube.YouTubeMusic

private const val EXTRA_SOURCE = "source"
private const val EXTRA_URI = "uri"
private const val EXTRA_DURATION = "duration"

/**
 * YouTube songs get an opaque `ytm:<videoId>` uri; [PlaybackService] resolves it to a fresh stream
 * url only when ExoPlayer actually starts loading it.
 */
fun Song.toMediaItem(): MediaItem {
    val playUri = if (source == SongSource.YOUTUBE) Uri.parse("${YouTubeMusic.SCHEME}:$youtubeId") else Uri.parse(uri)
    val extras = Bundle().apply {
        putString(EXTRA_SOURCE, source.name)
        putString(EXTRA_URI, uri)
        putLong(EXTRA_DURATION, durationMs)
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(playUri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(playUri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build()
        )
        .build()
}

fun MediaItem.toSong(): Song {
    val extras = mediaMetadata.extras
    return Song(
        id = mediaId,
        title = mediaMetadata.title?.toString() ?: "Unknown",
        artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
        album = mediaMetadata.albumTitle?.toString(),
        artworkUri = mediaMetadata.artworkUri?.toString(),
        durationMs = extras?.getLong(EXTRA_DURATION) ?: 0,
        source = extras?.getString(EXTRA_SOURCE)?.let { SongSource.valueOf(it) }
            ?: if (mediaId.startsWith(Song.YT_PREFIX)) SongSource.YOUTUBE else SongSource.LOCAL,
        uri = extras?.getString(EXTRA_URI) ?: requestMetadata.mediaUri?.toString().orEmpty(),
    )
}
