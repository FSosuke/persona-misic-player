package com.fsosuke.personamusic.youtube

import android.net.Uri
import com.fsosuke.personamusic.data.AppSettings
import com.fsosuke.personamusic.data.AudioQuality
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.data.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube Music access without the official client.
 *
 * Like ReVanced-patched YouTube Music, playback never touches the ad pipeline: we extract the raw
 * audio stream of a video with NewPipeExtractor and hand it to ExoPlayer directly, so there is
 * nothing to block — ads are simply never requested.
 */
class YouTubeMusic(client: OkHttpClient, private val settings: AppSettings) {

    private data class CachedStream(val url: String, val expiresAt: Long)

    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    init {
        NewPipe.init(NewPipeDownloader(client))
    }

    /** Searches the YouTube Music "Songs" shelf. */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSong() }
    }

    /**
     * Returns a directly playable audio url for [videoId]. Blocking: called from ExoPlayer's
     * loader thread through a ResolvingDataSource.
     */
    fun resolveStreamUrl(videoId: String): String {
        streamCache[videoId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }

        val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId))
        val candidates = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val original = candidates.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { candidates }
        val stream: AudioStream = when (settings.quality.value) {
            AudioQuality.BEST -> original.maxByOrNull { it.averageBitrate }
            AudioQuality.DATA_SAVER -> original.minByOrNull { it.averageBitrate }
        } ?: throw IOException("No playable audio stream for $videoId")

        // Stream urls are signed for ~6h; refresh a bit earlier.
        streamCache[videoId] = CachedStream(stream.content, System.currentTimeMillis() + STREAM_TTL_MS)
        return stream.content
    }

    fun invalidate(videoId: String) {
        streamCache.remove(videoId)
    }

    private fun StreamInfoItem.toSong(): Song? {
        val id = videoIdFromUrl(url) ?: return null
        return Song(
            id = Song.YT_PREFIX + id,
            title = name,
            artist = uploaderName?.removeSuffix(" - Topic") ?: "Unknown artist",
            artworkUri = bestThumbnail(thumbnails),
            durationMs = if (duration > 0) duration * 1000 else 0,
            source = SongSource.YOUTUBE,
            uri = watchUrl(id),
        )
    }

    companion object {
        const val SCHEME = "ytm"
        private const val STREAM_TTL_MS = 4 * 60 * 60 * 1000L

        fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

        fun videoIdFromUrl(url: String): String? {
            val uri = Uri.parse(url)
            return uri.getQueryParameter("v") ?: uri.lastPathSegment?.takeIf { uri.host == "youtu.be" }
        }

        /** YouTube Music serves tiny 60px covers; googleusercontent urls can be resized in place. */
        private fun bestThumbnail(images: List<Image>): String? {
            val url = images.maxByOrNull { it.height }?.url ?: return null
            return if ("googleusercontent.com" in url) {
                url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
            } else {
                url
            }
        }
    }
}
