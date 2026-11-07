package com.fsosuke.personamusic.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import com.fsosuke.personamusic.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Equalizer and volume normalization on the player's audio session. The service attaches the
 * session; the sound screen reads [info] and changes the settings, which are applied live.
 */
class AudioEffects(private val settings: AppSettings, scope: CoroutineScope) {

    data class EqInfo(
        val bandCentersHz: List<Int>,
        val minLevel: Int,
        val maxLevel: Int,
        val presets: List<String>,
    )

    private var equalizer: Equalizer? = null
    private var dynamics: DynamicsProcessing? = null

    private val _info = MutableStateFlow<EqInfo?>(null)
    /** Band layout of this device's equalizer; null until playback has started once. */
    val info: StateFlow<EqInfo?> = _info.asStateFlow()

    init {
        scope.launch {
            combine(settings.eqEnabled, settings.eqLevels, settings.normalize) { _, _, _ -> }.collect { apply() }
        }
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        release()
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()?.also { eq ->
            val range = eq.bandLevelRange
            _info.value = EqInfo(
                bandCentersHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics = runCatching { createDynamics(audioSessionId) }.getOrNull()
        }
        apply()
    }

    /** Band levels a device preset would set (to fill the sliders). */
    @Synchronized
    fun presetLevels(index: Int): List<Int>? = equalizer?.let { eq ->
        runCatching {
            eq.usePreset(index.toShort())
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        }.getOrNull()
    }

    @Synchronized
    fun apply() {
        equalizer?.let { eq ->
            runCatching {
                val levels = parseLevels(settings.eqLevels.value)
                for (band in 0 until eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), (levels.getOrNull(band) ?: 0).toShort())
                }
                eq.enabled = settings.eqEnabled.value
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics?.let { runCatching { it.enabled = settings.normalize.value } }
        }
    }

    @Synchronized
    fun release() {
        equalizer?.release()
        equalizer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dynamics?.release()
        dynamics = null
    }

    /** One compressor band + limiter: quiet songs come up, loud ones don't clip. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamics(session: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            false, 0,
            true, 1,
            false, 0,
            true,
        ).build()
        return DynamicsProcessing(0, session, config).apply {
            setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(true, 20_000f, 5f, 200f, 3f, -28f, 8f, -90f, 1f, 0f, 7f),
            )
            setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f))
        }
    }

    companion object {
        fun parseLevels(text: String): List<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }
        fun formatLevels(levels: List<Int>): String = levels.joinToString(",")
    }
}
