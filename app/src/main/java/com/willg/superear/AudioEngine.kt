package com.willg.superear

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * Captures live microphone audio and plays it back, amplified, through the
 * device's current audio output (expected to be headphones) with minimal
 * delay. All processing happens on-device in memory; no audio is written to
 * disk or sent over the network by this class.
 */
class AudioEngine {

    @Volatile
    var gain: Float = DEFAULT_GAIN
        set(value) {
            field = value.coerceIn(MIN_GAIN, MAX_GAIN)
        }

    @Volatile
    var noiseReductionEnabled: Boolean = true

    @Volatile
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var workerThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(onError: (Throwable) -> Unit) {
        if (isRunning) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBufferSize > 0) { "Microphone is not available on this device" }
            val bufferSize = max(minBufferSize, minBufferSize * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            require(record.state == AudioRecord.STATE_INITIALIZED) {
                "Could not initialize the microphone"
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // This app's entire purpose is live accessibility
                        // playback of ambient sound, hence this usage flag.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)
            }

            audioRecord = record
            audioTrack = track
            isRunning = true

            record.startRecording()
            track.play()

            workerThread = thread(name = "SuperEarAudioLoop") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                processingLoop(record, track, bufferSize)
            }
        } catch (t: Throwable) {
            stop()
            onError(t)
        }
    }

    private fun processingLoop(record: AudioRecord, track: AudioTrack, bufferSize: Int) {
        val shortBuffer = ShortArray(bufferSize / 2)
        var previousSample = 0f

        while (isRunning) {
            val read = record.read(shortBuffer, 0, shortBuffer.size)
            if (read <= 0) continue

            val currentGain = gain
            val smoothing = if (noiseReductionEnabled) NOISE_GATE_SMOOTHING else 0f

            for (i in 0 until read) {
                var sample = shortBuffer[i] / Short.MAX_VALUE.toFloat()

                if (smoothing > 0f) {
                    // Very light one-pole low-pass smoothing acts as a cheap
                    // de-hiss filter on top of the platform NoiseSuppressor.
                    sample = previousSample + smoothing * (sample - previousSample)
                    previousSample = sample
                }

                val amplified = (sample * currentGain).coerceIn(-1f, 1f)
                shortBuffer[i] = (amplified * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            track.write(shortBuffer, 0, read)
        }
    }

    fun stop() {
        isRunning = false
        workerThread?.let {
            try {
                it.join(500)
            } catch (_: InterruptedException) {
                // Best effort: fall through to release resources regardless.
            }
        }
        workerThread = null

        noiseSuppressor?.release()
        noiseSuppressor = null
        echoCanceler?.release()
        echoCanceler = null

        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null

        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
    }

    fun isActive(): Boolean = isRunning

    companion object {
        private const val SAMPLE_RATE_HZ = 44_100
        const val MIN_GAIN = 1.0f
        const val MAX_GAIN = 6.0f
        const val DEFAULT_GAIN = 2.5f
        private const val NOISE_GATE_SMOOTHING = 0.6f

        /** Clamp helper kept local to avoid pulling in extra dependencies. */
        fun clampGainPercent(percent: Int): Float =
            min(MAX_GAIN, max(MIN_GAIN, percent / 100f))
    }
}
