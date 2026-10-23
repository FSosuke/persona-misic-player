package com.fsosuke.personamusic.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Take your time": stops playback after a while. The UI sets it, [PlaybackService] (same
 * process) carries it out, so it also works with the app closed.
 */
class SleepTimer {
    sealed interface Mode {
        data object Off : Mode
        data class At(val endsAtElapsed: Long, val totalMinutes: Int) : Mode
        data object EndOfSong : Mode
    }

    private val _mode = MutableStateFlow<Mode>(Mode.Off)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun start(minutes: Int) {
        _mode.value = Mode.At(SystemClock.elapsedRealtime() + minutes * 60_000L, minutes)
    }

    fun startEndOfSong() {
        _mode.value = Mode.EndOfSong
    }

    fun cancel() {
        _mode.value = Mode.Off
    }

    /** Remaining milliseconds for a timed mode, null otherwise. */
    fun remainingMs(): Long? = (mode.value as? Mode.At)?.let { (it.endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
}
