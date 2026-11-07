package com.fsosuke.personamusic.monitor

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Where the sound goes: "Galaxy Buds3 · Bluetooth" plus what Android tells us about it. */
data class OutputRoute(val name: String, val kind: String, val details: List<String>)

/**
 * The output Android most likely plays through (a connected headset or DAC wins over the
 * speaker). Bluetooth codecs (LDAC, aptX …) are only reported to system apps, so they're not shown.
 */
fun currentRoute(context: Context): OutputRoute {
    val audio = context.getSystemService(AudioManager::class.java)
    val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    val device = PRIORITY.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
        ?: return OutputRoute("Phone speaker", "Built-in", emptyList())
    val kind = when (device.type) {
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "Built-in"
    }
    val details = buildList {
        device.sampleRates.maxOrNull()?.let { add("up to ${it / 1000.0} kHz") }
        val bits = device.encodings.toList().mapNotNull { encodingBits(it) }.maxOrNull()
        if (bits != null) add("$bits-bit")
        if (device.channelCounts.isNotEmpty()) add("${device.channelCounts.max()} ch")
        audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.let { add("mixer ${it / 1000.0} kHz") }
    }
    val name = device.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL } ?: kind
    return OutputRoute(name, kind, details)
}

private fun encodingBits(encoding: Int) = when (encoding) {
    android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
    android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    android.media.AudioFormat.ENCODING_PCM_32BIT, android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> null
}

private val PRIORITY = listOf(
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_HDMI,
)

/** [currentRoute], refreshed whenever a device is plugged in or removed. */
@Composable
fun rememberOutputRoute(): OutputRoute {
    val context = LocalContext.current
    var route by remember { mutableStateOf(currentRoute(context)) }
    DisposableEffect(Unit) {
        val audio = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { route = currentRoute(context) }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { route = currentRoute(context) }
        }
        audio.registerAudioDeviceCallback(callback, null)
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return route
}
