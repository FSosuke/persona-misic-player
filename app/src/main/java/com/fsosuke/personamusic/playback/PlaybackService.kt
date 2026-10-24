package com.fsosuke.personamusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.youtube.LiveStreamException
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
import kotlinx.coroutines.flow.collectLatest
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

    /** Volume parts: the sleep timer's fade-out and the between-songs fade, multiplied. */
    private var sleepVolume = 1f

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
                val videoId = spec.uri.schemeSpecificPart
                // Downloaded songs play from the saved file: offline and without using data.
                val file = container.downloads.uriFor(Song.YT_PREFIX + videoId)
                spec.withUri(Uri.parse(file ?: youTube.resolveStreamUrl(videoId)))
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

        scope.launch { runSleepTimer(player) }
        scope.launch { runVolume(player) }
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) container.audioEffects.attach(player.audioSessionId)

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
        container.audioEffects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /** Applies sleep fade and the "fade between songs" envelope to the player volume. */
    private suspend fun runVolume(player: ExoPlayer) {
        while (true) {
            val fadeMs = container.settings.crossfadeSeconds.value * 1000L
            var fade = 1f
            if (fadeMs > 0 && player.isPlaying && player.repeatMode != Player.REPEAT_MODE_ONE) {
                val position = player.currentPosition
                val duration = player.duration
                if (position < fadeMs) fade = (position.toFloat() / fadeMs).coerceIn(0.05f, 1f)
                if (duration != C.TIME_UNSET && duration > fadeMs * 2 && player.hasNextMediaItem()) {
                    val remaining = duration - position
                    if (remaining < fadeMs) fade = minOf(fade, (remaining.toFloat() / fadeMs).coerceIn(0.05f, 1f))
                }
            }
            val target = sleepVolume * fade
            if (player.volume != target) player.volume = target
            delay(100)
        }
    }

    /** Executes the [SleepTimer]: fade out and pause at the deadline, or pause at the song's end. */
    private suspend fun runSleepTimer(player: ExoPlayer) {
        container.sleepTimer.mode.collectLatest { mode ->
            player.pauseAtEndOfMediaItems = mode is SleepTimer.Mode.EndOfSong
            if (mode !is SleepTimer.Mode.At) {
                sleepVolume = 1f
                return@collectLatest
            }
            val untilFade = mode.endsAtElapsed - SystemClock.elapsedRealtime() - FADE_MS
            if (untilFade > 0) delay(untilFade)
            val steps = 30
            for (i in steps downTo 0) {
                sleepVolume = i / steps.toFloat()
                delay(FADE_MS / steps)
            }
            player.pause()
            sleepVolume = 1f
            container.sleepTimer.cancel()
        }
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
            currentEvent = scope.async(Dispatchers.IO) {
                container.repository.logPlay(mediaItem.mediaId).also {
                    container.repository.rankUpAfterPlay(mediaItem.toSong())?.let { container.pendingRankUp.value = it }
                }
            }
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

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.audioEffects.attach(audioSessionId)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // "End of song" sleep timer fired.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                container.sleepTimer.mode.value is SleepTimer.Mode.EndOfSong
            ) {
                container.sleepTimer.cancel()
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
            // A live station can't be played as a file: swap the item for its HLS stream and retry.
            val live = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<LiveStreamException>().firstOrNull()
            val current = player.currentMediaItem
            if (live != null && current != null) {
                val index = player.currentMediaItemIndex
                player.replaceMediaItem(
                    index,
                    current.buildUpon().setUri(live.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
                )
                player.seekToDefaultPosition(index)
                player.prepare()
                player.play()
                return
            }
            // Drop a possibly expired stream url, then move on instead of getting stuck.
            player.currentMediaItem?.requestMetadata?.mediaUri
                ?.takeIf { it.scheme == YouTubeMusic.SCHEME }
                ?.let {
                    container.youTube.invalidate(it.schemeSpecificPart)
                    container.appScope.launch { container.downloads.verify(Song.YT_PREFIX + it.schemeSpecificPart) }
                }
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
                val rules = container.contentFilter.rules.value
                val more = runCatching { container.ytLibrary.radio(seed) }.getOrDefault(emptyList())
                    .filter { it.id !in queued && rules.allows(it) }
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
        const val FADE_MS = 20_000L
        const val AUTOPLAY_THRESHOLD = 2
        const val AUTOPLAY_BATCH = 20
    }
}
