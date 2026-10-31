package com.willg.superear

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for the two user-adjustable settings.
 * Deliberately not using DataStore to keep this single-screen app simple.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var gainPercent: Int
        get() = prefs.getInt(KEY_GAIN_PERCENT, (AudioEngine.DEFAULT_GAIN * 100).toInt())
        set(value) = prefs.edit().putInt(KEY_GAIN_PERCENT, value).apply()

    var noiseReductionEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOISE_REDUCTION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOISE_REDUCTION, value).apply()

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, value).apply()

    companion object {
        private const val PREFS_NAME = "super_ear_prefs"
        private const val KEY_GAIN_PERCENT = "gain_percent"
        private const val KEY_NOISE_REDUCTION = "noise_reduction"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
    }
}
