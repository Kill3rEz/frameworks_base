/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.ax

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.SensorPrivacyManager
import android.hardware.display.ColorDisplayManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.android.internal.util.ScreenshotHelper
import android.media.projection.StopReason
import android.net.TetheringManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.os.RemoteException
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.service.dreams.IDreamManager
import android.util.Log
import com.android.axion.platform.AxPlatformClient
import com.android.axion.platform.AxPlatformFeature
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.connectivity.NetworkController
import com.android.systemui.statusbar.phone.ManagedProfileController
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.statusbar.policy.CastController
import com.android.systemui.statusbar.policy.DataSaverController
import com.android.systemui.statusbar.policy.FlashlightController
import com.android.systemui.statusbar.policy.HotspotController
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController
import com.android.systemui.statusbar.policy.LocationController
import com.android.systemui.statusbar.policy.RotationLockController
import com.android.systemui.screenrecord.ScreenRecordUxController
import com.android.systemui.statusbar.policy.SecurityController
import com.android.systemui.statusbar.policy.ZenModeController
import com.android.wifitrackerlib.WifiEntry

import javax.inject.Inject

@SysUISingleton
class AxPlatformFeatureController @Inject constructor(
    private val context: Context,
    private val stateManager: AxPlatformStateManager,
    private val networkController: NetworkController,
    private val accessPointController: AccessPointController,
    private val bluetoothController: BluetoothController,
    private val hotspotController: HotspotController,
    private val flashlightController: FlashlightController,
    private val locationController: LocationController,
    private val rotationLockController: RotationLockController,
    private val batteryController: BatteryController,
    private val zenModeController: ZenModeController,
    private val dataSaverController: DataSaverController,
    private val localBluetoothManager: LocalBluetoothManager?,
    private val sensorPrivacyController: IndividualSensorPrivacyController,
    private val managedProfileController: ManagedProfileController,
    private val securityController: SecurityController,
    private val castController: CastController,
    private val screenRecordUxController: ScreenRecordUxController,
    powerManager: PowerManager
) {

    internal val wakeLock: PowerManager.WakeLock =
        powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "AxPlatform:Caffeine")


    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val uiModeManager: UiModeManager = context.getSystemService(UiModeManager::class.java)
    private val colorDisplayManager: ColorDisplayManager =
        context.getSystemService(ColorDisplayManager::class.java)
    private val tetheringManager: TetheringManager? =
        context.getSystemService(TetheringManager::class.java)
    internal val dreamManager: IDreamManager? = try {
        IDreamManager.Stub.asInterface(ServiceManager.getService("dreams"))
    } catch (e: Exception) { null }


    private val screenshotHelper = ScreenshotHelper(context)
    private val screenshotHandler = Handler(Looper.getMainLooper())

    var latestAccessPoints: List<WifiEntry> = emptyList()

    val supportedFeatures: Array<String> by lazy {
        buildList {
            addAll(BASE_FEATURES)
            if (nfcAdapter != null)
                add(AxPlatformFeature.NFC)
            if (sensorPrivacyController.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA))
                add(AxPlatformFeature.CAMERA_PRIVACY)
            if (sensorPrivacyController.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE))
                add(AxPlatformFeature.MIC_PRIVACY)
            add(AxPlatformFeature.WORK_PROFILE)
            if (tetheringManager?.isTetheringSupported == true)
                add(AxPlatformFeature.USB_TETHER)
            if (dreamManager != null)
                add(AxPlatformFeature.DREAM)

            if (batteryController.isReverseSupported)
                add(AxPlatformFeature.POWER_SHARE)
            add(AxPlatformFeature.CAFFEINE)
            add(AxPlatformFeature.VPN)
            add(AxPlatformFeature.CAST)
            add(AxPlatformFeature.SMART_PIXELS)
            add(AxPlatformFeature.SCREEN_RECORD)
            add(AxPlatformFeature.SCREENSHOT)
        }.toTypedArray()
    }

    fun toggle(feature: String) {
        when (feature) {
            AxPlatformFeature.WIFI -> {
                val current = stateManager.getState(feature).getBoolean("enabled", false)
                networkController.setWifiEnabled(!current)
            }
            AxPlatformFeature.MOBILE_DATA -> {
                val ctrl = networkController.mobileDataController ?: return
                ctrl.isMobileDataEnabled = !ctrl.isMobileDataEnabled
            }
            AxPlatformFeature.BLUETOOTH ->
                bluetoothController.setBluetoothEnabled(!bluetoothController.isBluetoothEnabled)
            AxPlatformFeature.HOTSPOT -> {
                val current = stateManager.getState(feature).getBoolean("enabled", false)
                hotspotController.setHotspotEnabled(!current)
            }
            AxPlatformFeature.FLASHLIGHT -> {
                if (flashlightController.hasFlashlight())
                    flashlightController.setFlashlight(!flashlightController.isEnabled)
            }
            AxPlatformFeature.LOCATION ->
                locationController.setLocationEnabled(!locationController.isLocationEnabled)
            AxPlatformFeature.ROTATION ->
                rotationLockController.setRotationLocked(
                    !rotationLockController.isRotationLocked, TAG
                )
            AxPlatformFeature.BATTERY_SAVER ->
                batteryController.setPowerSaveMode(!batteryController.isPowerSave)
            AxPlatformFeature.ZEN -> {
                val current = zenModeController.zen
                zenModeController.setZen(if (current == 0) 1 else 0, null, TAG)
            }
            AxPlatformFeature.DATA_SAVER ->
                dataSaverController.setDataSaverEnabled(!dataSaverController.isDataSaverEnabled)
            AxPlatformFeature.AOD ->
                stateManager.toggleSecure(Settings.Secure.DOZE_ALWAYS_ON)
            AxPlatformFeature.AIRPLANE_MODE -> {
                val enabled = !stateManager.getGlobalBool(Settings.Global.AIRPLANE_MODE_ON)
                stateManager.setGlobalBool(Settings.Global.AIRPLANE_MODE_ON, enabled)
                context.sendBroadcastAsUser(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled),
                    UserHandle.ALL
                )
            }
            AxPlatformFeature.NFC -> nfcAdapter?.let {
                if (it.isEnabled) it.disable() else it.enable()
            }
            AxPlatformFeature.DARK_MODE ->
                uiModeManager.setNightModeActivated(
                    !isDarkMode(context.resources.configuration)
                )
            AxPlatformFeature.NIGHT_LIGHT ->
                colorDisplayManager.setNightDisplayActivated(
                    !colorDisplayManager.isNightDisplayActivated
                )
            AxPlatformFeature.COLOR_INVERSION ->
                stateManager.toggleSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
            AxPlatformFeature.COLOR_CORRECTION ->
                stateManager.toggleSecure(Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED)
            AxPlatformFeature.REDUCE_BRIGHTNESS ->
                stateManager.toggleSecure(SETTING_REDUCE_BRIGHT)
            AxPlatformFeature.ONE_HANDED_MODE ->
                stateManager.toggleSecure(SETTING_ONE_HANDED)
            AxPlatformFeature.HEADS_UP ->
                stateManager.toggleGlobal(Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED)
            AxPlatformFeature.AUTO_SYNC ->
                ContentResolver.setMasterSyncAutomatically(
                    !ContentResolver.getMasterSyncAutomatically()
                )
            AxPlatformFeature.CAMERA_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.CAMERA,
                    !sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA)
                )
            AxPlatformFeature.MIC_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.MICROPHONE,
                    !sensorPrivacyController.isSensorBlocked(
                        SensorPrivacyManager.Sensors.MICROPHONE
                    )
                )
            AxPlatformFeature.WORK_PROFILE ->
                managedProfileController.setWorkModeEnabled(
                    !managedProfileController.isWorkModeEnabled
                )
            AxPlatformFeature.USB_TETHER -> {
                val current = stateManager.getState(feature).getBoolean("active", false)
                tetheringManager?.setUsbTethering(!current)
            }
            AxPlatformFeature.DREAM -> try {
                dreamManager?.let { if (it.isDreaming) it.awaken() else it.dream() }
            } catch (e: RemoteException) {
                Log.w(TAG, "Dream toggle failed", e)
            }
            AxPlatformFeature.POWER_SHARE ->
                batteryController.setReverseState(!batteryController.isReverseOn)
            AxPlatformFeature.CAFFEINE -> {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                } else {
                    wakeLock.acquire(CAFFEINE_DURATION_MS)
                }
                stateManager.broadcastBool(feature, wakeLock.isHeld)
            }
            AxPlatformFeature.VPN -> {
                if (securityController.isVpnEnabled) {
                    securityController.disconnectPrimaryVpn()
                }
            }
            AxPlatformFeature.CAST -> {
                val active = castController.castDevices.firstOrNull { it.isCasting }
                active?.let { castController.stopCasting(it, StopReason.STOP_QS_TILE) }
            }

            AxPlatformFeature.SMART_PIXELS ->
                stateManager.toggleSecure(SETTING_SMART_PIXELS)
            AxPlatformFeature.SCREEN_RECORD -> {
                if (screenRecordUxController.isStarting) {
                    screenRecordUxController.cancelCountdown()
                } else if (screenRecordUxController.isRecording) {
                    screenRecordUxController.stopRecording(StopReason.STOP_QS_TILE)
                } else {
                    screenRecordUxController.createScreenRecordDialog(null).show()
                }
            }
            AxPlatformFeature.SCREENSHOT -> {
                screenshotHandler.postDelayed({
                    screenshotHelper.takeScreenshot(
                        WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                        WindowManager.ScreenshotSource.SCREENSHOT_OTHER,
                        screenshotHandler,
                        null
                    )
                }, SCREENSHOT_DELAY_MS)
            }
            else -> Log.w(TAG, "Unknown toggle: $feature")
        }
    }

    fun setEnabled(feature: String, enabled: Boolean) {
        when (feature) {
            AxPlatformFeature.WIFI -> networkController.setWifiEnabled(enabled)
            AxPlatformFeature.MOBILE_DATA ->
                networkController.mobileDataController?.let { it.isMobileDataEnabled = enabled }
            AxPlatformFeature.BLUETOOTH -> bluetoothController.setBluetoothEnabled(enabled)
            AxPlatformFeature.HOTSPOT -> hotspotController.setHotspotEnabled(enabled)
            AxPlatformFeature.FLASHLIGHT -> {
                if (flashlightController.hasFlashlight()) flashlightController.setFlashlight(enabled)
            }
            AxPlatformFeature.LOCATION -> locationController.setLocationEnabled(enabled)
            AxPlatformFeature.ROTATION ->
                rotationLockController.setRotationLocked(!enabled, TAG)
            AxPlatformFeature.BATTERY_SAVER -> batteryController.setPowerSaveMode(enabled)
            AxPlatformFeature.ZEN ->
                zenModeController.setZen(if (enabled) 1 else 0, null, TAG)
            AxPlatformFeature.DATA_SAVER -> dataSaverController.setDataSaverEnabled(enabled)
            AxPlatformFeature.AOD ->
                stateManager.setSecureBool(Settings.Secure.DOZE_ALWAYS_ON, enabled)
            AxPlatformFeature.AIRPLANE_MODE -> {
                stateManager.setGlobalBool(Settings.Global.AIRPLANE_MODE_ON, enabled)
                context.sendBroadcastAsUser(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled),
                    UserHandle.ALL
                )
            }
            AxPlatformFeature.NFC -> nfcAdapter?.let {
                if (enabled) it.enable() else it.disable()
            }
            AxPlatformFeature.DARK_MODE -> uiModeManager.setNightModeActivated(enabled)
            AxPlatformFeature.NIGHT_LIGHT ->
                colorDisplayManager.setNightDisplayActivated(enabled)
            AxPlatformFeature.COLOR_INVERSION ->
                stateManager.setSecureBool(
                    Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, enabled
                )
            AxPlatformFeature.COLOR_CORRECTION ->
                stateManager.setSecureBool(
                    Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, enabled
                )
            AxPlatformFeature.REDUCE_BRIGHTNESS ->
                stateManager.setSecureBool(SETTING_REDUCE_BRIGHT, enabled)
            AxPlatformFeature.ONE_HANDED_MODE ->
                stateManager.setSecureBool(SETTING_ONE_HANDED, enabled)
            AxPlatformFeature.HEADS_UP ->
                stateManager.setGlobalBool(
                    Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, enabled
                )
            AxPlatformFeature.AUTO_SYNC ->
                ContentResolver.setMasterSyncAutomatically(enabled)
            AxPlatformFeature.CAMERA_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.CAMERA,
                    enabled
                )
            AxPlatformFeature.MIC_PRIVACY ->
                sensorPrivacyController.setSensorBlocked(
                    SensorPrivacyManager.Sources.QS_TILE,
                    SensorPrivacyManager.Sensors.MICROPHONE,
                    enabled
                )
            AxPlatformFeature.WORK_PROFILE ->
                managedProfileController.setWorkModeEnabled(enabled)
            AxPlatformFeature.USB_TETHER -> tetheringManager?.setUsbTethering(enabled)
            AxPlatformFeature.DREAM -> try {
                dreamManager?.let { if (enabled) it.dream() else it.awaken() }
            } catch (e: RemoteException) {
                Log.w(TAG, "Dream setEnabled failed", e)
            }
            AxPlatformFeature.POWER_SHARE ->
                batteryController.setReverseState(enabled)
            AxPlatformFeature.CAFFEINE -> {
                if (enabled && !wakeLock.isHeld) {
                    wakeLock.acquire(CAFFEINE_DURATION_MS)
                } else if (!enabled && wakeLock.isHeld) {
                    wakeLock.release()
                }
                stateManager.broadcastBool(feature, wakeLock.isHeld)
            }
            AxPlatformFeature.VPN -> {
                if (!enabled && securityController.isVpnEnabled) {
                    securityController.disconnectPrimaryVpn()
                }
            }
            AxPlatformFeature.CAST -> {
                if (!enabled) {
                    castController.castDevices.firstOrNull { it.isCasting }
                        ?.let { castController.stopCasting(it, StopReason.STOP_QS_TILE) }
                }
            }

            AxPlatformFeature.SMART_PIXELS ->
                stateManager.setSecureBool(SETTING_SMART_PIXELS, enabled)
            AxPlatformFeature.SCREEN_RECORD -> {
                if (!enabled && screenRecordUxController.isRecording) {
                    screenRecordUxController.stopRecording(StopReason.STOP_QS_TILE)
                }
            }
            AxPlatformFeature.SCREENSHOT -> {
                if (enabled) toggle(feature)
            }
            else -> Log.w(TAG, "Unknown setEnabled: $feature")
        }
    }

    fun setValue(feature: String, value: Int) {
        when (feature) {
            AxPlatformFeature.ZEN -> {
                if (zenModeController.zen != value)
                    zenModeController.setZen(value, null, TAG)
            }
            else -> Log.w(TAG, "Unknown setValue: $feature")
        }
    }

    fun performAction(feature: String, param: String) {
        when (feature) {
            AxPlatformClient.ACTION_WIFI_CONNECT ->
                latestAccessPoints
                    .find { it.getKey() == param || it.getTitle() == param }
                    ?.let { accessPointController.connect(it) }
            AxPlatformClient.ACTION_BT_CONNECT ->
                getAllBluetoothDevices().find { it.getAddress() == param }?.let {
                    if (it.isConnected()) it.disconnect() else it.connect(true)
                }
            else -> Log.w(TAG, "Unknown performAction: $feature")
        }
    }

    fun getAllBluetoothDevices(): Collection<CachedBluetoothDevice> =
        localBluetoothManager?.cachedDeviceManager?.cachedDevicesCopy
            ?: bluetoothController.connectedDevices



    companion object {
        private const val TAG = "AxPlatformFeatureCtrl"
        private const val CAFFEINE_DURATION_MS = 5L * 60 * 1000
        private const val SCREENSHOT_DELAY_MS = 500L
        const val SETTING_NIGHT_DISPLAY = "night_display_activated"
        const val SETTING_REDUCE_BRIGHT = "reduce_bright_colors_activated"
        const val SETTING_ONE_HANDED = "one_handed_mode_enabled"
        const val SETTING_SMART_PIXELS = "ax_smart_pixel_filter_enabled"

        fun isDarkMode(config: Configuration): Boolean =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        private val BASE_FEATURES = arrayOf(
            AxPlatformFeature.WIFI,
            AxPlatformFeature.MOBILE_DATA,
            AxPlatformFeature.BLUETOOTH,
            AxPlatformFeature.HOTSPOT,
            AxPlatformFeature.FLASHLIGHT,
            AxPlatformFeature.LOCATION,
            AxPlatformFeature.ROTATION,
            AxPlatformFeature.BATTERY_SAVER,
            AxPlatformFeature.ZEN,
            AxPlatformFeature.AOD,
            AxPlatformFeature.DATA_SAVER,
            AxPlatformFeature.AIRPLANE_MODE,
            AxPlatformFeature.DARK_MODE,
            AxPlatformFeature.NIGHT_LIGHT,
            AxPlatformFeature.COLOR_INVERSION,
            AxPlatformFeature.COLOR_CORRECTION,
            AxPlatformFeature.REDUCE_BRIGHTNESS,
            AxPlatformFeature.ONE_HANDED_MODE,
            AxPlatformFeature.HEADS_UP,
            AxPlatformFeature.AUTO_SYNC
        )
    }
}
