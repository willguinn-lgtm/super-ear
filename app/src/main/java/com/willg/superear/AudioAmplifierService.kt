package com.willg.superear

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the live microphone-to-headphone amplification loop as a visible
 * foreground service. A persistent, non-swipeable notification is shown the
 * entire time the microphone is active so the device's owner always knows
 * amplification is running, with a one-tap Stop action.
 */
class AudioAmplifierService : Service() {

    private val engine = AudioEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAmplifying()
                return START_NOT_STICKY
            }

            ACTION_SET_GAIN -> {
                engine.gain = AudioEngine.clampGainPercent(intent.getIntExtra(EXTRA_GAIN_PERCENT, 250))
                return START_NOT_STICKY
            }

            ACTION_SET_NOISE_REDUCTION -> {
                engine.noiseReductionEnabled = intent.getBooleanExtra(EXTRA_NOISE_REDUCTION, true)
                return START_NOT_STICKY
            }

            else -> {
                val prefs = PreferencesManager(this)
                engine.gain = AudioEngine.clampGainPercent(prefs.gainPercent)
                engine.noiseReductionEnabled = prefs.noiseReductionEnabled
                startAmplifying()
                return START_NOT_STICKY
            }
        }
    }

    private fun startAmplifying() {
        startForeground(NOTIFICATION_ID, buildNotification())
        engine.start { _ ->
            isRunning.value = false
            stopSelf()
        }
        isRunning.value = engine.isActive()
    }

    private fun stopAmplifying() {
        engine.stop()
        isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine.stop()
        isRunning.value = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioAmplifierService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_stop_action), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "super_ear_amplifier"
        private const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.willg.superear.action.START"
        const val ACTION_STOP = "com.willg.superear.action.STOP"
        const val ACTION_SET_GAIN = "com.willg.superear.action.SET_GAIN"
        const val ACTION_SET_NOISE_REDUCTION = "com.willg.superear.action.SET_NOISE_REDUCTION"
        const val EXTRA_GAIN_PERCENT = "gain_percent"
        const val EXTRA_NOISE_REDUCTION = "noise_reduction"

        /** Observed by the UI so it reflects service state even if the
         *  activity was recreated while the service kept running. */
        val isRunning = MutableStateFlow(false)
        val isRunningState get() = isRunning.asStateFlow()
    }
}
