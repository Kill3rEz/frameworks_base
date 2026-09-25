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

import android.content.Context
import android.os.Bundle
import com.android.axion.platform.AxPlatformClient
import com.android.axion.platform.AxPlatformFeature
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.ConfigurationController
import javax.inject.Inject

@SysUISingleton
class AxPlatformFeatureMapper @Inject constructor(
    private val context: Context,
    private val batteryController: BatteryController,
    configurationController: ConfigurationController
) : AxPlatformStateManager.LabelProvider {

    private val labelCache = mutableMapOf<String, String>()

    init {
        configurationController.addCallback(object : ConfigurationController.ConfigurationListener {
            override fun onLocaleListChanged() {
                labelCache.clear()
            }
        })
    }

    override fun getLabel(feature: String): String? {
        labelCache[feature]?.let { return it }
        val label = when {
            FEATURE_LABEL_RES.containsKey(feature) -> context.getString(FEATURE_LABEL_RES[feature]!!)
            feature == AxPlatformFeature.SMART_PIXELS -> LABEL_SMART_PIXELS
            else -> return null
        }
        labelCache[feature] = label
        return label
    }

    override fun getSecondaryLabel(feature: String, state: Bundle): String? = when (feature) {
        AxPlatformFeature.WIFI -> {
            val ssid = state.getString("ssid")
            when {
                state.getBoolean("connected") && !ssid.isNullOrEmpty() -> ssid
                else -> null
            }
        }
        AxPlatformFeature.BLUETOOTH -> {
            @Suppress("DEPRECATION")
            val devices = state.getParcelableArrayList<Bundle>("devices")
            devices?.firstOrNull { it.getBoolean("isConnected") }?.getString("name")
        }
        AxPlatformFeature.HOTSPOT -> {
            val num = state.getInt("numDevices", 0)
            if (num > 0) "$num ${if (num == 1) "device" else "devices"}" else null
        }
        AxPlatformFeature.MOBILE_DATA -> {
            state.getString("description")?.takeIf { it.isNotEmpty() }
        }
        AxPlatformFeature.ZEN -> {
            val mode = state.getInt("mode", 0)
            if (mode != 0) context.getString(R.string.zen_mode_on) else null
        }
        AxPlatformFeature.VPN -> {
            state.getString("name")?.takeIf { it.isNotEmpty() }
        }
        AxPlatformFeature.CAST -> {
            state.getString("deviceName")?.takeIf { it.isNotEmpty() }
        }
        AxPlatformFeature.SCREEN_RECORD -> {
            when {
                state.getBoolean("active") -> context.getString(R.string.quick_settings_screen_record_stop)
                state.getBoolean("starting") -> context.getString(R.string.quick_settings_screen_record_start)
                else -> null
            }
        }
        else -> null
    }

    companion object {
        private const val LABEL_SMART_PIXELS = "Smart Pixels"

        private val FEATURE_LABEL_RES = mapOf(
            AxPlatformFeature.WIFI to R.string.quick_settings_wifi_label,
            AxPlatformFeature.MOBILE_DATA to R.string.quick_settings_internet_label,
            AxPlatformFeature.BLUETOOTH to R.string.quick_settings_bluetooth_label,
            AxPlatformFeature.HOTSPOT to R.string.quick_settings_hotspot_label,
            AxPlatformFeature.FLASHLIGHT to R.string.quick_settings_flashlight_label,
            AxPlatformFeature.LOCATION to R.string.quick_settings_location_label,
            AxPlatformFeature.ROTATION to R.string.quick_settings_rotation_unlocked_label,
            AxPlatformFeature.BATTERY_SAVER to R.string.battery_detail_switch_title,
            AxPlatformFeature.ZEN to R.string.quick_settings_dnd_label,
            AxPlatformFeature.AOD to R.string.quick_settings_aod_label,
            AxPlatformFeature.DATA_SAVER to R.string.data_saver,
            AxPlatformFeature.AIRPLANE_MODE to R.string.airplane_mode,
            AxPlatformFeature.NFC to R.string.quick_settings_nfc_label,
            AxPlatformFeature.DARK_MODE to R.string.quick_settings_ui_mode_night_label,
            AxPlatformFeature.NIGHT_LIGHT to R.string.quick_settings_night_display_label,
            AxPlatformFeature.COLOR_INVERSION to R.string.quick_settings_inversion_label,
            AxPlatformFeature.COLOR_CORRECTION to R.string.quick_settings_color_correction_label,
            AxPlatformFeature.REDUCE_BRIGHTNESS to com.android.internal.R.string.reduce_bright_colors_feature_name,
            AxPlatformFeature.ONE_HANDED_MODE to R.string.quick_settings_onehanded_label,
            AxPlatformFeature.HEADS_UP to R.string.quick_settings_heads_up_label,
            AxPlatformFeature.AUTO_SYNC to R.string.quick_settings_sync_label,
            AxPlatformFeature.CAMERA_PRIVACY to R.string.quick_settings_camera_label,
            AxPlatformFeature.MIC_PRIVACY to R.string.quick_settings_mic_label,
            AxPlatformFeature.WORK_PROFILE to R.string.quick_settings_work_mode_label,
            AxPlatformFeature.USB_TETHER to R.string.quick_settings_usb_tether_label,
            AxPlatformFeature.DREAM to R.string.quick_settings_screensaver_label,
            AxPlatformFeature.CAFFEINE to R.string.quick_settings_caffeine_label,
            AxPlatformFeature.VPN to R.string.quick_settings_vpn_label,
            AxPlatformFeature.CAST to R.string.quick_settings_cast_title,
            AxPlatformFeature.SCREEN_RECORD to R.string.quick_settings_screen_record_label,
            AxPlatformFeature.SCREENSHOT to R.string.quick_settings_screenshot_label
        )
    }
}
