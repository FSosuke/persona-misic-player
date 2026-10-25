package com.fsosuke.personamusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fsosuke.personamusic.data.Song
import com.fsosuke.personamusic.radio.RadikoStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** UI-side handle on [PlaybackService], exposing the player as Compose-friendly state flows. */
class PlayerConnection(private val context: Context) {

    data class State(
        val current: Song? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val durationMs: Long = 0,
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val queue: List<Song> = emptyList(),
        val currentIndex: Int = 0,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var pending: (MediaController.() -> Unit)? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.localizedMessage ?: error.errorCodeName)
        }
    }

    fun connect() {
        if (controller != null || connectJob?.isActive == true) return
        connectJob = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val mediaController = runCatching { MediaController.Builder(context, token).buildAsync().await() }
                .getOrNull() ?: return@launch
            controller = mediaController
            mediaController.addListener(listener)
            refresh(mediaController)
            pending?.invoke(mediaController)
            pending = null
            while (isActive) {
                _positionMs.value = mediaController.currentPosition
                delay(250)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
    }

    private fun withController(block: MediaController.() -> Unit) {
        val c = controller
        if (c != null) {
            c.block()
        } else {
            pending = block
            connect()
        }
    }

    private fun refresh(player: Player) {
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
        val duration = player.duration
        _state.value = State(
            current = player.currentMediaItem?.toSong(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = if (duration == C.TIME_UNSET) player.currentMediaItem?.toSong()?.durationMs ?: 0 else duration,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            currentIndex = player.currentMediaItemIndex,
            error = player.playerError?.let { it.localizedMessage ?: it.errorCodeName },
        )
        _positionMs.value = player.currentPosition
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) = withController {
        if (songs.isEmpty()) return@withController
        shuffleModeEnabled = shuffle
        setMediaItems(songs.map { it.toMediaItem() }, if (shuffle) C.INDEX_UNSET else startIndex, 0)
        prepare()
        play()
    }

    /** Replaces the queue with a live radiko station. */
    fun playRadio(station: RadikoStation) = withController {
        shuffleModeEnabled = false
        setMediaItem(station.toSong().toMediaItem())
        prepare()
        play()
    }

        fun playNext(song: Song) = withController {
        if (mediaItemCount == 0) {
            setMediaItem(song.toMediaItem())
            prepare()
            play()
        } else {
            addMediaItem(currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun addToQueue(song: Song) = withController {
        addMediaItem(song.toMediaItem())
        if (mediaItemCount == 1) {
            prepare()
            play()
        }
    }

    fun togglePlay() = withController {
        if (playbackState == Player.STATE_IDLE) prepare()
        if (isPlaying) pause() else play()
    }

    fun next() = withController { seekToNext() }
    fun previous() = withController { seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { seekTo(positionMs) }
    fun skipToQueueItem(index: Int) = withController { seekToDefaultPosition(index); play() }
    fun removeQueueItem(index: Int) = withController { removeMediaItem(index) }
    fun moveQueueItem(from: Int, to: Int) = withController { moveMediaItem(from, to) }
    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
