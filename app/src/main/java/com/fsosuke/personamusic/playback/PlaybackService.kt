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
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.collect.ImmutableList
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import com.fsosuke.personamusic.MainActivity
import com.fsosuke.personamusic.PersonaApp
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.youtube.LiveStreamException
import com.fsosuke.personamusic.radio.Radiko
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
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
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
            } else if (spec.uri.scheme == Radiko.SCHEME) {
                val (station, area) = Radiko.parseUri(spec.uri) ?: throw IOException("Bad radiko uri ${spec.uri}")
                container.radiko.activeArea = area
                spec.withUri(Uri.parse(container.radiko.streamUrl(station))).withAdditionalHeaders(container.radiko.streamHeaders(area))
            } else if (Radiko.isStreamHost(spec.uri.host)) {
                // Variant playlists and segments of a radiko stream need the token too.
                spec.withAdditionalHeaders(container.radiko.streamHeaders())
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

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
        pushWidget(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerWidget.push(this, null, false)
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

    /**
     * Session callback + media library for Android Auto (and other browsers): Recent,
     * Favourites, Downloads, Playlists and the local library as browsable folders.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /** Media3 strips the playback uri when items travel through a controller; restore it. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.map { item ->
                item.requestMetadata.mediaUri?.let { uri ->
                    item.buildUpon().setUri(uri)
                        .apply { if (uri.scheme == Radiko.SCHEME) setMimeType(MimeTypes.APPLICATION_M3U8) }
                        .build()
                }
                    // Items picked in Android Auto only carry their id: look the song up.
                    ?: container.repository.song(item.mediaId)?.toMediaItem()
                    ?: item
            }.toMutableList()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Persona Music"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(children(parentId), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            container.repository.song(mediaId)?.let { LibraryResult.ofItem(it.toMediaItem(), null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        /** Bluetooth / widget "play" with nothing loaded: resume with the recent songs. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val recent = container.repository.recent(30).first()
            MediaSession.MediaItemsWithStartPosition(recent.map { it.toMediaItem() }, 0, 0)
        }
    }

    private suspend fun children(parentId: String): List<MediaItem> {
        val repo = container.repository
        return when {
            parentId == ROOT -> listOf(
                folder("recent", "Recently played"),
                folder("favourites", "Favourites"),
                folder("downloads", "Downloads"),
                folder("playlists", "Playlists"),
                folder("library", "Library"),
            )
            parentId == "recent" -> repo.recent(50).first().map { it.toMediaItem() }
            parentId == "favourites" -> repo.likedSongs.first().map { it.toMediaItem() }
            parentId == "downloads" -> container.downloads.downloadedSongs.first().map { it.toMediaItem() }
            parentId == "library" -> repo.localSongs.first().take(500).map { it.toMediaItem() }
            parentId == "playlists" -> repo.playlists.first().map { folder("playlist:${it.id}", it.name) }
            parentId.startsWith("playlist:") ->
                parentId.removePrefix("playlist:").toLongOrNull()?.let { id -> repo.playlistSongs(id).first().map { it.toMediaItem() } }.orEmpty()
            else -> emptyList()
        }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    /** Sends the current song and play state to the home-screen widget. */
    private fun pushWidget(player: Player) {
        val song = player.currentMediaItem?.toSong()
        PlayerWidget.push(this, song, player.isPlaying)
        val art = song?.artworkUri ?: return
        if (PlayerWidget.hasArtFor(song.id)) return
        scope.launch {
            val request = ImageRequest.Builder(this@PlaybackService).data(art).size(256).allowHardware(false).build()
            val bitmap = (imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(256, 256)
            if (bitmap != null) PlayerWidget.setArt(this@PlaybackService, song.id, bitmap)
        }
    }

    private var radioJob: Job? = null
    private var radioMediaId: String? = null

    /**
     * While a radiko station plays, keep its metadata current: the song on air (title, artist,
     * cover) or else the programme – so the player, notification and widget show what's on.
     */
    private suspend fun followRadio(player: ExoPlayer, mediaId: String) {
        val stationId = mediaId.removePrefix(Radiko.ID_PREFIX)
        while (true) {
            val item = player.currentMediaItem?.takeIf { it.mediaId == mediaId } ?: return
            val area = item.requestMetadata.mediaUri?.let { Radiko.parseUri(it)?.second }
            val stationName = item.mediaMetadata.albumTitle?.toString() ?: stationId
            val logo = "https://radiko.jp/v2/static/station/logo/$stationId/224x100.png"
            val song = runCatching { container.radiko.currentSong(stationId) }.getOrNull()
            val program = area?.let { runCatching { container.radiko.nowOnAir(it)[stationId] }.getOrNull() }
            val programText = program?.let { p -> listOfNotNull(p.title.ifBlank { null }, p.performer).joinToString(" · ") }
            val (title, artist, art) = if (song != null) {
                Triple(song.title, listOfNotNull(song.artist, stationName).joinToString(" · "), song.image ?: program?.image ?: logo)
            } else {
                Triple(stationName, programText?.ifBlank { null } ?: item.mediaMetadata.artist?.toString(), program?.image ?: logo)
            }
            val old = item.mediaMetadata
            if (old.title?.toString() != title || old.artist?.toString() != artist || old.artworkUri?.toString() != art) {
                val index = player.currentMediaItemIndex
                player.replaceMediaItem(
                    index,
                    item.buildUpon().setMediaMetadata(
                        old.buildUpon().setTitle(title).setArtist(artist).setArtworkUri(android.net.Uri.parse(art)).build(),
                    ).build(),
                )
            }
            delay(RADIO_POLL_MS)
        }
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
            if (mediaItem.mediaId.startsWith(Radiko.ID_PREFIX)) {
                if (radioJob?.isActive != true || radioMediaId != mediaItem.mediaId) {
                    radioJob?.cancel()
                    radioMediaId = mediaItem.mediaId
                    radioJob = scope.launch { followRadio(player, mediaItem.mediaId) }
                }
                return
            }
            radioJob?.cancel()
            radioMediaId = null
            currentEvent = scope.async(Dispatchers.IO) {
                container.repository.logPlay(mediaItem.mediaId).also {
                    container.repository.rankUpAfterPlay(mediaItem.toSong())?.let { container.pendingRankUp.value = it }
                    container.checkStatUps()
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

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) pushWidget(player)
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
        const val RADIO_POLL_MS = 30_000L
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val ROOT = "root"
        const val HISTORY_AFTER_MS = 30_000L
        const val FADE_MS = 20_000L
        const val AUTOPLAY_THRESHOLD = 2
        const val AUTOPLAY_BATCH = 20
    }
}
