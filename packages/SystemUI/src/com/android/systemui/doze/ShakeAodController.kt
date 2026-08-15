/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.android.systemui.doze

import android.app.AlarmManager
import android.content.Context
import android.database.ContentObserver
import android.hardware.display.AmbientDisplayConfiguration
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.SystemClock
import android.os.UserHandle
import android.pocket.IPocketCallback
import android.pocket.PocketManager
import android.provider.Settings
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.util.AlarmTimeout
import com.android.systemui.util.settings.SecureSettings
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class ShakeAodController @Inject constructor(
    private val context: Context,
    @Main private val mainHandler: Handler,
    @Main private val mainExecutor: Executor,
    private val secureSettings: SecureSettings,
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val statusBarStateController: StatusBarStateController,
    private val userTracker: UserTracker,
    private val batteryController: BatteryController,
    private val shakeSensorController: ShakeSensorController,
    alarmManager: AlarmManager,
) : CoreStartable {

    interface Callback {
        fun onShakeAodPolicyChanged() {}
    }

    companion object {
        private const val TAG = "ShakeAodController"
    }

    private val callbacks = mutableSetOf<Callback>()

    private var started = false
    private var shakeEnabled = false
    private var shakeShowDurationMs = 10_000L
    private var inPocket = false
    private var dozing = false
    private var shakeWindowUntilElapsed = 0L

    private val pocketManager = context.getSystemService(Context.POCKET_SERVICE) as? PocketManager

    private val pocketCallback = object : IPocketCallback.Stub() {
        override fun onStateChanged(isDeviceInPocket: Boolean, reason: Int) {
            mainHandler.post {
                handlePocketStateChanged(isDeviceInPocket)
            }
        }
    }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?, userId: Int) {
            Log.d(TAG, "settingsObserver.onChange: selfChange=$selfChange, uri=$uri, userId=$userId")
            handleSettingsChanged()
        }
    }

    private val userTrackerCallback = object : UserTracker.Callback {
        override fun onUserChanged(newUser: Int, userContext: Context) {
            Log.d(TAG, "onUserChanged: newUser=$newUser")
            secureSettings.unregisterContentObserverAsync(settingsObserver)
            registerSettingsObserver()
            handleSettingsChanged()
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onDozingChanged(isDozing: Boolean) {
            dozing = isDozing
            if (dozing) {
                readSettings()
            } else {
                clearShakeWindow()
            }
            updateShakeSensor()
            dispatchPolicyChanged()
        }
    }

    private val batteryStateChangeCallback = object : BatteryStateChangeCallback {
        override fun onPowerSaveChanged(isPowerSave: Boolean) {
            handlePolicyEnvironmentChanged()
            updateShakeSensor()
        }
    }

    private val clearShakeWindowTimeout = AlarmTimeout(alarmManager, {
        clearShakeWindow()
        dispatchPolicyChanged()
    }, "ShakeAodController.shakeWindow", mainHandler)

    init {
        shakeSensorController.setOnShakeListener(::handleShakeDetected)
    }

    override fun start() {
        if (started) return
        started = true
        readSettings()
        registerSettingsObserver()

        statusBarStateController.addCallback(statusBarStateListener)
        batteryController.addCallback(batteryStateChangeCallback)
        userTracker.addCallback(userTrackerCallback, mainExecutor)

        pocketManager?.let { pm ->
            pm.addCallback(pocketCallback)
            inPocket = pm.isDeviceInPocket
        }

        dozing = statusBarStateController.isDozing
        updateShakeSensor()
    }

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun isAodAllowed(baseAlwaysOn: Boolean): Boolean {
        if (!started) {
            readSettings()
        }
        if (inPocket) return false
        if (baseAlwaysOn) {
            return true
        }
        return shakeEnabled && isShakeWindowActive()
    }

    fun onDozeMachineStarted() {
        dozing = true
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    fun onDozeMachineFinished() {
        dozing = false
        clearShakeWindow()
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    fun dump(pw: PrintWriter) {
        pw.println("ShakeAodController:")
        pw.println(" shakeEnabled=$shakeEnabled")
        pw.println(" shakeShowDurationMs=$shakeShowDurationMs")
        pw.println(" inPocket=$inPocket")
        pw.println(" dozing=$dozing")
        pw.println(" shakeWindowActive=${isShakeWindowActive()}")
        shakeSensorController.dump(pw)
    }

    private fun handlePocketStateChanged(isDeviceInPocket: Boolean) {
        if (inPocket == isDeviceInPocket) return
        inPocket = isDeviceInPocket
        if (inPocket) {
            clearShakeWindow()
        }
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    private fun handleSettingsChanged() {
        readSettings()
        if (!isShakeModeAvailable()) {
            clearShakeWindow()
        }
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    private fun handlePolicyEnvironmentChanged() {
        if (!isShakeModeAvailable()) {
            clearShakeWindow()
            dispatchPolicyChanged()
        }
    }

    private fun registerSettingsObserver() {
        val keys = arrayOf(
            Settings.Secure.DOZE_SHAKE_TO_SHOW,
            Settings.Secure.DOZE_SHAKE_TO_SHOW_DURATION,
            Settings.Secure.DOZE_SHAKE_INTENSITY,
            Settings.Secure.DOZE_ALWAYS_ON,
        )
        keys.forEach { key ->
            secureSettings.registerContentObserverForUserAsync(key, settingsObserver, userTracker.userId)
        }
    }

    private fun readSettings() {
        val userId = userTracker.userId
        shakeEnabled = secureSettings.getIntForUser(
            Settings.Secure.DOZE_SHAKE_TO_SHOW,
            0,
            userId,
        ) == 1
        val durationSec = secureSettings.getIntForUser(
            Settings.Secure.DOZE_SHAKE_TO_SHOW_DURATION,
            5,
            userId,
        )
        shakeShowDurationMs = durationSec * 1000L
        val intensity = secureSettings.getIntForUser(
            Settings.Secure.DOZE_SHAKE_INTENSITY,
            1,
            userId,
        )
        shakeSensorController.setIntensity(intensity)
    }

    private fun updateShakeSensor() {
        shakeSensorController.setListening(dozing && isShakeModeAvailable())
    }

    private fun handleShakeDetected() {
        if (!isShakeModeAvailable()) return
        showShakeWindow()
        dispatchPolicyChanged()
    }

    private fun showShakeWindow() {
        val now = SystemClock.elapsedRealtime()
        shakeWindowUntilElapsed = now + shakeShowDurationMs
        clearShakeWindowTimeout.schedule(
            shakeShowDurationMs,
            AlarmTimeout.MODE_RESCHEDULE_IF_SCHEDULED,
        )
    }

    private fun dispatchPolicyChanged() {
        callbacks.toList().forEach { it.onShakeAodPolicyChanged() }
    }

    private fun clearShakeWindow() {
        shakeWindowUntilElapsed = 0L
        clearShakeWindowTimeout.cancel()
    }

    fun isShakeWindowActive(): Boolean =
        shakeWindowUntilElapsed > SystemClock.elapsedRealtime()

    private fun isShakeModeAvailable(): Boolean =
        shakeEnabled &&
            !inPocket &&
            !ambientDisplayConfiguration.alwaysOnEnabled(userTracker.userId) &&
            !batteryController.isAodPowerSave
}
