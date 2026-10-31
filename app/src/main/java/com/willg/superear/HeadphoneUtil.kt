package com.willg.superear

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Detects whether audio is currently routed to headphones (wired or
 * Bluetooth) rather than the phone's built-in speaker. Super Ear strongly
 * recommends headphones because speaker playback of amplified microphone
 * audio causes a feedback loop (a loud squeal), the same way a PA system
 * squeals if a microphone points at its own speaker.
 */
object HeadphoneUtil {

    private val HEADPHONE_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET
    )

    fun isHeadsetConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type in HEADPHONE_TYPES }
    }
}
