/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.pulse

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
        private const val INVALID_SESSION = Int.MIN_VALUE
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
                val session = if (attachedSessionId != INVALID_SESSION) attachedSessionId else 0
                releaseVisualizer()
                attachedSessionId = INVALID_SESSION
                if (!attachVisualizer(session) && session != 0) {
                    attachVisualizer(0)
                }
                isProcessing = (visualizer != null)
            }
        }

    private var visualizer: Visualizer? = null
    private var dataListener: AudioDataListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isProcessing = false
    private var attachedSessionId: Int = INVALID_SESSION

    private var lastUpdateTime = 0L
    private var updateThrottle = 16L
    private var lastKnownRefreshRateHz: Float = 60f

    private var fftAverage: Array<FFTAverage>? = null
    private var waveformAverage: Array<FFTAverage>? = null
    private val fudgeFactor = 20f

    private var audioManager: AudioManager? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    fun interface AudioDataListener {
        fun onAudioData(heights: FloatArray)
    }

    fun interface FftDataListener {
        fun onFftData(fft: ByteArray)
    }

    private var fftListener: FftDataListener? = null
    private var isCapturingRequested = false

    fun setDataListener(listener: AudioDataListener?) {
        dataListener = listener
    }

    fun setFftListener(listener: FftDataListener?) {
        fftListener = listener
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

        if (visualizer != null) {
            isProcessing = true
        } else {
            unregisterPlaybackCallback()
        }
    }

    fun stopCapture() {
        isCapturingRequested = false
        unregisterPlaybackCallback()

        if (!isProcessing && visualizer == null) {
            attachedSessionId = INVALID_SESSION
            return
        }

        releaseVisualizer()
        attachedSessionId = INVALID_SESSION
        isProcessing = false
    }

    fun cleanup() {
        stopCapture()
        dataListener = null
        fftListener = null
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
        if (am == null || cb == null) {
            if (cb == null) audioManager = null
            return
        }
        try {
            am.unregisterAudioPlaybackCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterAudioPlaybackCallback", e)
        }
        playbackCallback = null
        audioManager = null
    }

    private fun maybeRetargetVisualizer(configs: List<AudioPlaybackConfiguration>) {
        if (!isCapturingRequested) return
        val want = pickSessionIdFromConfigs(configs)
        val target = if (want > 0) want else 0
        if (target == attachedSessionId && visualizer != null) return

        releaseVisualizer()
        attachedSessionId = INVALID_SESSION

        if (!attachVisualizer(target) && target != 0) {
            attachVisualizer(0)
        }
        if (visualizer == null) {
            isProcessing = false
        }
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
    }

    private fun preferredAudioSessionId(): Int {
        val am = audioManager
            ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?: return 0
        return try {
            val configs = am.activePlaybackConfigurations ?: return 0
            pickSessionIdFromConfigs(configs)
        } catch (e: SecurityException) {
            Log.w(TAG, "activePlaybackConfigurations", e)
            0
        }
    }

    private fun pickSessionIdFromConfigs(
        configs: List<AudioPlaybackConfiguration>
    ): Int {
        if (configs.isEmpty()) return 0

        val targetPkg = activeLocalPlayingMediaPackage()

        if (targetPkg != null) {
            for (c in configs) {
                val sid = c.sessionId
                if (sid <= 0) continue
                val uid = c.clientUid
                if (uid <= 0) continue
                val pkgs = context.packageManager.getPackagesForUid(uid)
                if (pkgs != null && pkgs.any { it == targetPkg }) {
                    return sid
                }
            }
        }

        for (c in configs) {
            val sid = c.sessionId
            if (sid > 0 && isLikelyMusicPlayback(c.audioAttributes)) return sid
        }

        for (c in configs) {
            val sid = c.sessionId
            if (sid > 0) return sid
        }
        return 0
    }

    private fun activeLocalPlayingMediaPackage(): String? {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val controllers: List<MediaController> = try {
            msm.getActiveSessions(null)
        } catch (e: SecurityException) {
            Log.w(TAG, "getActiveSessions", e)
            return null
        }
        if (controllers.isEmpty()) return null

        val playing = controllers.filter {
            val s = it.playbackState?.state
            s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
        }
        val pool = if (playing.isNotEmpty()) playing else controllers

        val local = pool.filter {
            it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
        }.ifEmpty { pool }

        val best = local.maxByOrNull {
            it.playbackState?.lastPositionUpdateTime ?: 0L
        } ?: return null

        return best.packageName
    }

    private fun isLikelyMusicPlayback(attrs: AudioAttributes): Boolean {
        return when (attrs.usage) {
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_UNKNOWN -> true
            AudioAttributes.USAGE_ASSISTANT,
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            AudioAttributes.USAGE_NOTIFICATION,
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
            AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
            AudioAttributes.USAGE_NOTIFICATION_EVENT,
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
            AudioAttributes.USAGE_ALARM -> false
            else -> false
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
            output[i] = smoothed * fudgeFactor * (heightMultiplier * 0.6f)
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

        fftListener?.onFftData(data)

        mainHandler.post {
            dataListener?.onAudioData(output)
        }
    }

    private fun updateThrottle() {
        val refreshRate = currentRefreshRateHz()
        if (refreshRate > 0f && refreshRate != lastKnownRefreshRateHz) {
            lastKnownRefreshRateHz = refreshRate
            updateThrottle = (1000f / refreshRate).toLong().coerceAtLeast(1L)
        }
    }

    private fun currentRefreshRateHz(): Float {
        return try {
            context.display?.refreshRate ?: lastKnownRefreshRateHz
        } catch (e: UnsupportedOperationException) {
            lastKnownRefreshRateHz
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
