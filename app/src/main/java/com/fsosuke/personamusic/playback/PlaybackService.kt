package com.fsosuke.personamusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fsosuke.personamusic.MainActivity
import com.fsosuke.personamusic.PersonaApp
import com.fsosuke.personamusic.youtube.NewPipeDownloader
import com.fsosuke.personamusic.youtube.SponsorBlock
import com.fsosuke.personamusic.youtube.YouTubeMusic
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Background playback (no YouTube Premium needed) via a Media3 session + system media notification. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var skipJob: Job? = null
    private var historyJob: Job? = null
    private var extendingQueue = false
    private var consecutiveErrors = 0

    // Listening-time bookkeeping for the play event of the current item.
    private var currentEvent: Deferred<Long>? = null
    private var listenedMs = 0L
    private var playingSince: Long? = null

    private val container get() = (application as PersonaApp).container

    override fun onCreate() {
        super.onCreate()
        val youTube = container.youTube

        val httpFactory = OkHttpDataSource.Factory(container.httpClient)
            .setUserAgent(NewPipeDownloader.USER_AGENT)
        val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, httpFactory)) { spec ->
            if (spec.uri.scheme == YouTubeMusic.SCHEME) {
                spec.withUri(Uri.parse(youTube.resolveStreamUrl(spec.uri.schemeSpecificPart)))
            } else {
                spec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(PlayerListener(player))

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        flushListened()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /** Adds the running stretch to the current play event and persists the total. */
    private fun flushListened() {
        playingSince?.let { listenedMs += SystemClock.elapsedRealtime() - it }
        playingSince = if (session?.player?.isPlaying == true) SystemClock.elapsedRealtime() else null
        val event = currentEvent ?: return
        val total = listenedMs
        container.appScope.launch {
            runCatching { container.repository.setListened(event.await(), total) }
        }
    }

    /** Media3 strips the playback uri when items travel through a controller; restore it. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item
            }.toMutableList()
        )
    }

    private inner class PlayerListener(private val player: ExoPlayer) : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            skipJob?.cancel()
            flushListened()
            currentEvent = null
            listenedMs = 0
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else null
            historyJob?.cancel()
            mediaItem ?: return
            currentEvent = scope.async(Dispatchers.IO) { container.repository.logPlay(mediaItem.mediaId) }
            maybeExtendQueue()

            val videoId = mediaItem.requestMetadata.mediaUri
                ?.takeIf { it.scheme == YouTubeMusic.SCHEME }
                ?.schemeSpecificPart
            if (videoId != null && container.settings.skipNonMusic.value) {
                skipJob = scope.launch { skipNonMusic(videoId) }
            }
            if (videoId != null && container.settings.reportHistory.value && container.ytAccounts.cookie != null) {
                // Like YouTube itself, only count it once the song has really been listened to.
                historyJob = scope.launch {
                    delay(HISTORY_AFTER_MS)
                    if (player.currentMediaItem?.mediaId == mediaItem.mediaId) {
                        runCatching { container.ytLibrary.registerPlayback(videoId) }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playingSince = SystemClock.elapsedRealtime()
            } else {
                flushListened()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            // Drop a possibly expired stream url, then move on instead of getting stuck.
            player.currentMediaItem?.requestMetadata?.mediaUri
                ?.takeIf { it.scheme == YouTubeMusic.SCHEME }
                ?.let { container.youTube.invalidate(it.schemeSpecificPart) }
            consecutiveErrors++
            if (consecutiveErrors <= MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        /** Autoplay: when the queue is about to run out, append a radio of the current song. */
        private fun maybeExtendQueue() {
            if (!container.settings.autoplay.value || extendingQueue) return
            if (player.repeatMode != Player.REPEAT_MODE_OFF) return
            if (player.mediaItemCount - player.currentMediaItemIndex > AUTOPLAY_THRESHOLD) return
            val seed = player.currentMediaItem?.toSong() ?: return
            extendingQueue = true
            scope.launch {
                val queued = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
                val more = runCatching { container.ytLibrary.radio(seed) }.getOrDefault(emptyList())
                    .filter { it.id !in queued }
                    .take(AUTOPLAY_BATCH)
                if (more.isNotEmpty()) {
                    container.repository.remember(more)
                    player.addMediaItems(more.map { it.toMediaItem() })
                }
                extendingQueue = false
            }
        }

        private suspend fun skipNonMusic(videoId: String) {
            val segments: List<SponsorBlock.Segment> = container.sponsorBlock.nonMusicSegments(videoId)
            if (segments.isEmpty()) return
            while (scope.isActive) {
                val position = player.currentPosition
                val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - 500 }
                if (segment != null) {
                    val duration = player.duration
                    if (duration != C.TIME_UNSET && segment.endMs >= duration - 1000 && player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        return
                    }
                    player.seekTo(segment.endMs)
                }
                delay(500)
            }
        }
    }

    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val HISTORY_AFTER_MS = 30_000L
        const val AUTOPLAY_THRESHOLD = 2
        const val AUTOPLAY_BATCH = 20
    }
}
