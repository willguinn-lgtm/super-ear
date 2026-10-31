package com.willg.superear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.gms.ads.MobileAds

/**
 * Application entry point: sets up the AdMob SDK and the notification
 * channel used by [AudioAmplifierService] for its mandatory foreground
 * notification (shown any time the microphone is active).
 */
class SuperEarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AudioAmplifierService.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        /** Keep in sync with app/build.gradle.kts applicationId. */
        const val PACKAGE_NAME = "com.willg.superear"
    }
}
