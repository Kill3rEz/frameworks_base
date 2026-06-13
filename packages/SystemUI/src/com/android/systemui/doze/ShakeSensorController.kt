/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.doze

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

@SysUISingleton
class ShakeSensorController @Inject constructor(
    context: Context,
    @Main private val mainHandler: Handler,
) {
    companion object {
        private const val SHAKE_COOLDOWN_MS = 200L
        private const val SHAKE_TOTAL_THRESHOLD = 0.0f
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listening = false
    private var lastShakeElapsed = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var haveLastSample = false
    private var onShake: (() -> Unit)? = null
    private var shakeDeltaThreshold = 0.55f

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val sensor = event?.sensor ?: return
            if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (!haveLastSample) {
                    lastX = x
                    lastY = y
                    lastZ = z
                    haveLastSample = true
                    return
                }

                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                val total = sqrt(x * x + y * y + z * z)
                lastX = x
                lastY = y
                lastZ = z

                val now = SystemClock.elapsedRealtime()
                if (delta >= shakeDeltaThreshold &&
                        total >= SHAKE_TOTAL_THRESHOLD &&
                        now - lastShakeElapsed >= SHAKE_COOLDOWN_MS) {
                    lastShakeElapsed = now
                    onShake?.invoke()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun setOnShakeListener(listener: (() -> Unit)?) {
        onShake = listener
    }

    fun setIntensity(intensity: Int) {
        shakeDeltaThreshold = when (intensity) {
            0 -> 0.25f // Low intensity (gentle shake)
            1 -> 0.55f // Medium intensity (standard shake)
            2 -> 1.2f // High intensity (firm/hard shake)
            else -> 0.55f
        }
    }

    fun setListening(enabled: Boolean) {
        if (enabled == listening) return
        if (enabled) {
            val sensor = accelerometer ?: return
            resetSamples()
            sensorManager?.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                mainHandler,
            )
            listening = true
        } else {
            sensorManager?.unregisterListener(sensorListener)
            listening = false
            resetSamples()
        }
    }

    fun dump(pw: PrintWriter) {
        pw.println(" ShakeSensorController:")
        pw.println("  available=${accelerometer != null}")
        pw.println("  listening=$listening")
        pw.println("  shakeDeltaThreshold=$shakeDeltaThreshold")
    }

    private fun resetSamples() {
        haveLastSample = false
    }
}
