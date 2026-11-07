package com.fsosuke.personamusic.monitor

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** One snapshot of the meters: levels in dBFS, spectrum bands 0..1. */
data class MeterFrame(
    val rmsDb: FloatArray = floatArrayOf(SILENCE, SILENCE),
    val peakDb: FloatArray = floatArrayOf(SILENCE, SILENCE),
    val peakHoldDb: FloatArray = floatArrayOf(SILENCE, SILENCE),
    val bands: FloatArray = FloatArray(AudioMonitor.BANDS.size),
    val bandPeaks: FloatArray = FloatArray(AudioMonitor.BANDS.size),
) {
    companion object {
        const val SILENCE = -90f
    }
}

/** What is being decoded and where the PCM goes. */
data class StreamInfo(
    val mimeType: String? = null,
    val codecs: String? = null,
    val sourceSampleRate: Int = 0,
    val channels: Int = 0,
    val bitrate: Int = 0,
    val decoder: String? = null,
    val pcmSampleRate: Int = 0,
    val pcmEncoding: String? = null,
)

/**
 * OTORA-style audio monitor. Taps the decoded PCM inside ExoPlayer (a [TeeAudioProcessor] sink,
 * so no microphone permission is needed) and computes per-channel RMS / peak levels and a
 * 12-band spectrum. Only works while [active] (the monitor screen is open); otherwise the tap
 * costs next to nothing.
 */
@OptIn(UnstableApi::class)
class AudioMonitor : TeeAudioProcessor.AudioBufferSink {

    @Volatile
    var active = false

    private val _frames = MutableStateFlow(MeterFrame())
    val frames: StateFlow<MeterFrame> = _frames.asStateFlow()

    private val _stream = MutableStateFlow(StreamInfo())
    val stream: StateFlow<StreamInfo> = _stream.asStateFlow()

    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    // Mono ring buffer for the FFT and running sums for the level meters.
    private val ring = FloatArray(FFT_SIZE)
    private var ringPos = 0
    private val sumSq = DoubleArray(2)
    private val peak = FloatArray(2)
    private var counted = 0
    private var lastPublish = 0L
    private val hold = floatArrayOf(MeterFrame.SILENCE, MeterFrame.SILENCE)
    private var holdAt = LongArray(2)
    private val smoothBands = FloatArray(BANDS.size)
    private val bandPeaks = FloatArray(BANDS.size)

    private val window = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2 * PI * i / (FFT_SIZE - 1))).toFloat() }
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)

    /** Called by the player with the decoded input format. */
    fun setSource(mimeType: String?, codecs: String?, sampleRate: Int, channels: Int, bitrate: Int) {
        _stream.value = _stream.value.copy(
            mimeType = mimeType,
            codecs = codecs,
            sourceSampleRate = sampleRate,
            channels = channels,
            bitrate = bitrate,
        )
    }

    fun setDecoder(name: String) {
        _stream.value = _stream.value.copy(decoder = name)
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount
        this.encoding = encoding
        _stream.value = _stream.value.copy(
            pcmSampleRate = sampleRateHz,
            pcmEncoding = when (encoding) {
                C.ENCODING_PCM_16BIT -> "16-bit PCM"
                C.ENCODING_PCM_FLOAT -> "32-bit float PCM"
                C.ENCODING_PCM_24BIT -> "24-bit PCM"
                C.ENCODING_PCM_32BIT -> "32-bit PCM"
                else -> "PCM"
            },
        )
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!active) return
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        val frameSize = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_24BIT -> 3
            else -> return
        } * channels
        if (channels <= 0) return
        while (data.remaining() >= frameSize) {
            var mono = 0f
            for (ch in 0 until channels) {
                val sample = read(data)
                if (ch < 2) {
                    sumSq[ch] += (sample * sample).toDouble()
                    val a = kotlin.math.abs(sample)
                    if (a > peak[ch]) peak[ch] = a
                }
                mono += sample
            }
            if (channels == 1) {
                sumSq[1] = sumSq[0]
                peak[1] = peak[0]
            }
            ring[ringPos] = mono / channels
            ringPos = (ringPos + 1) % FFT_SIZE
            counted++
        }
        val now = System.nanoTime()
        if (now - lastPublish >= PUBLISH_NS && counted > 0) {
            lastPublish = now
            publish(now)
        }
    }

    private fun read(data: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> data.short / 32768f
        C.ENCODING_PCM_FLOAT -> data.float
        C.ENCODING_PCM_32BIT -> data.int / 2147483648f
        C.ENCODING_PCM_24BIT -> {
            val b0 = data.get().toInt() and 0xFF
            val b1 = data.get().toInt() and 0xFF
            val b2 = data.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
        }
        else -> 0f
    }

    private fun publish(now: Long) {
        val rms = FloatArray(2) { ch -> toDb(sqrt(sumSq[ch] / counted).toFloat()) }
        val peaks = FloatArray(2) { ch -> toDb(peak[ch]) }
        for (ch in 0..1) {
            if (peaks[ch] >= hold[ch] || now - holdAt[ch] > HOLD_NS) {
                hold[ch] = peaks[ch]
                holdAt[ch] = now
            }
            sumSq[ch] = 0.0
            peak[ch] = 0f
        }
        counted = 0
        spectrum()
        _frames.value = MeterFrame(rms, peaks, hold.copyOf(), smoothBands.copyOf(), bandPeaks.copyOf())
    }

    /** Hann-windowed FFT of the latest samples, summed into the meter's bands (0..1, -60..0 dB). */
    private fun spectrum() {
        for (i in 0 until FFT_SIZE) {
            re[i] = ring[(ringPos + i) % FFT_SIZE] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        BANDS.forEachIndexed { b, band ->
            val from = max(1, (band.lowHz / binHz).toInt())
            val to = minOf(FFT_SIZE / 2 - 1, max(from, (band.highHz / binHz).toInt()))
            var energy = 0f
            for (k in from..to) energy += re[k] * re[k] + im[k] * im[k]
            val magnitude = sqrt(energy / (to - from + 1)) / (FFT_SIZE / 4f)
            val level = ((toDb(magnitude) + 60f) / 60f).coerceIn(0f, 1f)
            // Fast attack, slow release, like a hardware analyser.
            smoothBands[b] = if (level > smoothBands[b]) level else smoothBands[b] * 0.85f + level * 0.15f
            bandPeaks[b] = if (smoothBands[b] > bandPeaks[b]) smoothBands[b] else (bandPeaks[b] - 0.01f).coerceAtLeast(0f)
        }
    }

    data class Band(val label: String, val lowHz: Float, val highHz: Float)

    companion object {
        private const val FFT_SIZE = 2048
        private const val PUBLISH_NS = 33_000_000L
        private const val HOLD_NS = 1_500_000_000L

        val BANDS = listOf(
            Band("50", 30f, 75f), Band("100", 75f, 130f), Band("160", 130f, 200f), Band("250", 200f, 370f),
            Band("500", 370f, 620f), Band("750", 620f, 870f), Band("1K", 870f, 1_400f), Band("2K", 1_400f, 2_800f),
            Band("4K", 2_800f, 5_600f), Band("8K", 5_600f, 11_000f), Band("16K", 11_000f, 17_000f), Band("HIGH", 17_000f, 22_000f),
        )

        fun toDb(value: Float) = if (value <= 1e-5f) MeterFrame.SILENCE else (20 * log10(value)).coerceAtLeast(MeterFrame.SILENCE)

        /** In-place iterative radix-2 FFT. */
        internal fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wr = cos(angle).toFloat()
                val wi = kotlin.math.sin(angle).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val ur = re[i + k]; val ui = im[i + k]
                        val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                        val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                        re[i + k] = ur + vr; im[i + k] = ui + vi
                        re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                        val ncr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = ncr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
