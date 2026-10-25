package com.fsosuke.personamusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import com.fsosuke.personamusic.data.youtubeId
import com.fsosuke.personamusic.radio.Radiko
import com.fsosuke.personamusic.radio.RadikoStation
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
        // radiko stations are live HLS behind a custom uri.
        .apply { if (isRadio) setMimeType(MimeTypes.APPLICATION_M3U8) }
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

/** A radiko live station rather than a track: no stats, lyrics or queue radio. */
val Song.isRadio get() = id.startsWith(Radiko.ID_PREFIX)

fun RadikoStation.toSong() = Song(
    id = Radiko.ID_PREFIX + id,
    title = name,
    artist = program?.let { p -> listOfNotNull(p.title.ifBlank { null }, p.performer).joinToString(" · ") }?.ifBlank { null }
        ?: "radiko · ${area.title}",
    album = "radiko ${area.title}",
    artworkUri = program?.image ?: logo,
    durationMs = 0,
    source = SongSource.LOCAL,
    uri = Radiko.uriFor(this).toString(),
)

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
