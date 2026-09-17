/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.pulse

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.log10
import kotlin.math.roundToInt

class PulseAudioProcessor(
    private val context: Context,
    private val settingsRepo: PulseSettingsRepository
) {
    companion object {
        private const val TAG = "PulseAudioProcessor"
    }

    enum class CaptureMode(val value: Int) {
        FFT(0),
        WAVEFORM(1);

        companion object {
            fun fromInt(value: Int): CaptureMode =
                entries.firstOrNull { it.value == value } ?: FFT
        }
    }

    @Volatile
    var captureMode: CaptureMode = CaptureMode.FFT
        set(value) {
            if (field == value) return
            field = value
            if (isProcessing) {
                val session = attachedSessionId
                releaseVisualizer()
                if (!attachVisualizer(session) && session != 0) {
                    attachVisualizer(0)
                }
                isProcessing = (visualizer != null)
            }
        }

    private var visualizer: Visualizer? = null
    private var dataListener: AudioDataListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    private var lastUpdateTime = 0L
    private var updateThrottle = 16L
    private var lastKnownRefreshRateHz: Float = 60f

    private var fftAverage: Array<FFTAverage>? = null
    private var waveformAverage: Array<FFTAverage>? = null
    private val fudgeFactor = 20f

    private var audioManager: AudioManager? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var attachedSessionId: Int = 0

    fun interface AudioDataListener {
        fun onAudioData(heights: FloatArray)
    }

    private var isCapturingRequested = false

    fun setDataListener(listener: AudioDataListener?) {
        dataListener = listener
    }

    fun startCapture() {
        if (isCapturingRequested && isProcessing) return
        isCapturingRequested = true

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        registerPlaybackCallback()

        val session = preferredAudioSessionId()
        if (!attachVisualizer(session) && session != 0) {
            attachVisualizer(0)
        }

        isProcessing = (visualizer != null)
    }

    fun stopCapture() {
        isCapturingRequested = false
        unregisterPlaybackCallback()
        releaseVisualizer()
        isProcessing = false
    }

    fun cleanup() {
        stopCapture()
        dataListener = null
    }

    private fun registerPlaybackCallback() {
        if (playbackCallback != null) return
        val am = audioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                mainHandler.post { maybeRetargetVisualizer(configs) }
            }
        }
        playbackCallback = cb
        try {
            am.registerAudioPlaybackCallback(cb, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "registerAudioPlaybackCallback", e)
            playbackCallback = null
        }
    }

    private fun unregisterPlaybackCallback() {
        val am = audioManager
        val cb = playbackCallback
        if (am != null && cb != null) {
            try {
                am.unregisterAudioPlaybackCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterAudioPlaybackCallback", e)
            }
        }
        playbackCallback = null
        audioManager = null
    }

    private fun maybeRetargetVisualizer(configs: List<AudioPlaybackConfiguration>) {
        if (!isCapturingRequested) return
        var want = 0
        for (c in configs) {
            if (c.sessionId > 0) {
                want = c.sessionId
                break
            }
        }
        if (want == attachedSessionId && visualizer != null) return

        releaseVisualizer()
        if (!attachVisualizer(want) && want != 0) {
            attachVisualizer(0)
        }
        isProcessing = (visualizer != null)
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.apply {
                enabled = false
                setDataCaptureListener(null, 0, false, false)
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "release visualizer", e)
        }
        visualizer = null
        attachedSessionId = 0
    }

    private fun preferredAudioSessionId(): Int {
        val am = audioManager
            ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?: return 0
        return try {
            val configs = am.activePlaybackConfigurations ?: return 0
            for (c in configs) {
                if (c.sessionId > 0) return c.sessionId
            }
            0
        } catch (e: Exception) {
            0
        }
    }

    private fun attachVisualizer(sessionId: Int): Boolean {
        return try {
            val wantWaveform = captureMode == CaptureMode.WAVEFORM
            val wantFft = captureMode == CaptureMode.FFT

            val v = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform != null && waveform.isNotEmpty()) {
                            processWaveform(waveform)
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft != null && fft.isNotEmpty()) {
                            processFFT(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, wantWaveform, wantFft)

                enabled = true
            }
            visualizer = v
            attachedSessionId = sessionId
            true
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer attach failed session=$sessionId", e)
            false
        }
    }

    private fun processWaveform(data: ByteArray) {
        val currentTime = System.currentTimeMillis()
        updateThrottle()
        if (currentTime - lastUpdateTime < updateThrottle) {
            return
        }
        lastUpdateTime = currentTime

        val barCount = settingsRepo.getBarCount()
        var averages = waveformAverage
        if (averages == null || averages.size != barCount) {
            averages = Array(barCount) { FFTAverage() }
            waveformAverage = averages
        }

        val output = FloatArray(barCount)
        val samplesPerBar = (data.size / barCount).coerceAtLeast(1)
        val heightMultiplier = settingsRepo.getHeightMultiplier()

        for (i in 0 until barCount) {
            val start = i * samplesPerBar
            val end = (start + samplesPerBar).coerceAtMost(data.size)
            if (start >= data.size) continue

            var sum = 0
            for (j in start until end) {
                val centered = (data[j].toInt() and 0xFF) - 128
                sum += kotlin.math.abs(centered)
            }
            val avgAmplitude = if (end > start) sum / (end - start) else 0

            val smoothed = averages[i].average(avgAmplitude)
            output[i] = smoothed * fudgeFactor * heightMultiplier
        }

        mainHandler.post {
            dataListener?.onAudioData(output)
        }
    }

    private fun processFFT(data: ByteArray) {
        val currentTime = System.currentTimeMillis()
        updateThrottle()
        if (currentTime - lastUpdateTime < updateThrottle) {
            return
        }
        lastUpdateTime = currentTime

        val barCount = settingsRepo.getBarCount()
        var averages = fftAverage
        if (averages == null || averages.size != barCount) {
            averages = Array(barCount) { FFTAverage() }
            fftAverage = averages
        }

        val heightMultiplier = settingsRepo.getHeightMultiplier()
        val output = FloatArray(barCount)

        for (i in 0 until barCount) {
            val realIndex = i * 2 + 2
            val imagIndex = i * 2 + 3
            if (realIndex >= data.size || imagIndex >= data.size) continue
            val rfk = data[realIndex].toInt()
            val ifk = data[imagIndex].toInt()
            val magnitude = (rfk * rfk + ifk * ifk).toFloat()
            var dbValue = if (magnitude > 0) (10 * log10(magnitude.toDouble())).toInt() else 0
            dbValue = averages[i].average(dbValue)
            output[i] = dbValue * fudgeFactor * heightMultiplier
        }

        mainHandler.post {
            dataListener?.onAudioData(output)
        }
    }

    private fun updateThrottle() {
        val display = context.display ?: return
        val refreshRate = display.refreshRate
        if (refreshRate != lastKnownRefreshRateHz) {
            lastKnownRefreshRateHz = refreshRate
            updateThrottle = (1000f / refreshRate).toLong()
        }
    }

    fun isCapturing(): Boolean = isProcessing

    private class FFTAverage {
        companion object {
            private const val WINDOW_LENGTH = 2
        }

        private val window = ArrayDeque<Float>(WINDOW_LENGTH)
        private var average = 0f

        fun average(db: Int): Int {
            if (window.size >= WINDOW_LENGTH) {
                val removed = window.removeFirst()
                average -= removed
            }

            val newVal = db / WINDOW_LENGTH.toFloat()
            average += newVal
            window.addLast(newVal)

            return average.roundToInt()
        }
    }
}
