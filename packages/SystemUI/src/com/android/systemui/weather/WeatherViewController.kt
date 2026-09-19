/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.weather

import android.content.Context
import android.database.ContentObserver
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import com.android.internal.util.custom.OmniJawsClient

import com.android.systemui.Dependency
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R

import kotlinx.coroutines.*

class WeatherViewController(
    private val context: Context,
    private val weatherIcon: ImageView,
    private val weatherTemp: TextView,
    private val weatherInfoView: View,
    private val isCustomClock: Boolean = false,
) : OmniJawsClient.OmniJawsObserver {

    private var weatherInfo: OmniJawsClient.WeatherInfo? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var mDozing = false
    private var initialized = false
    private val statusBarStateController: StatusBarStateController = Dependency.get(StatusBarStateController::class.java)

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onStateChanged(newState: Int) {}

        override fun onDozingChanged(dozing: Boolean) {
            mDozing = dozing
            updateIconTint()
        }
    }

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            updateWeather()
        }
    }

    fun init() {
        if (initialized) return
        initialized = true

        val cr = context.contentResolver
        listOf(
            Settings.System.getUriFor(LOCKSCREEN_WEATHER_ENABLED),
            Settings.System.getUriFor(LOCKSCREEN_WEATHER_LOCATION),
            Settings.System.getUriFor(LOCKSCREEN_WEATHER_TEXT),
            Settings.System.getUriFor(LOCKSCREEN_WEATHER_WIND_INFO),
            Settings.System.getUriFor(LOCKSCREEN_WEATHER_HUMIDITY_INFO),
            Settings.Secure.getUriFor(CUSTOM_CLOCK_WEATHER),
            Settings.Secure.getUriFor(LOCKSCREEN_WEATHER_ENABLED),
        ).forEach { uri ->
            cr.registerContentObserver(uri, false, settingsObserver, UserHandle.USER_ALL)
        }

        statusBarStateController.addCallback(statusBarStateListener)
        statusBarStateListener.onDozingChanged(statusBarStateController.isDozing())

        updateWeather()
    }

    private fun getConditionText(condition: String): String {
        if (condition.isBlank()) return ""
        val locale = context.resources.configuration.locales[0]
        val isEnglish = locale.language.startsWith("en", ignoreCase = true)

        if (!isEnglish) {
            for ((key, value) in WEATHER_CONDITIONS) {
                if (condition.contains(key, ignoreCase = true)) {
                    return context.resources.getString(value)
                }
            }
        }
        return condition.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
    }

    private fun getWeatherSettings(): WeatherSettings {
        val lsWeatherEnabled = getSystemSetting(LOCKSCREEN_WEATHER_ENABLED, defaultValue = 0)
            || getSecureSetting(LOCKSCREEN_WEATHER_ENABLED, defaultValue = 0)
        val customWeather = getSecureSetting(CUSTOM_CLOCK_WEATHER, defaultValue = 1)

        val weatherEnabled = if (isCustomClock) {
            lsWeatherEnabled && customWeather
        } else {
            lsWeatherEnabled
        }

        return WeatherSettings(
            weatherEnabled = weatherEnabled,
            showWeatherLocation = getSystemSetting(LOCKSCREEN_WEATHER_LOCATION, defaultValue = 0),
            showWeatherText = getSystemSetting(LOCKSCREEN_WEATHER_TEXT, defaultValue = 1),
            showWindInfo = getSystemSetting(LOCKSCREEN_WEATHER_WIND_INFO, defaultValue = 0),
            showHumidityInfo = getSystemSetting(LOCKSCREEN_WEATHER_HUMIDITY_INFO, defaultValue = 0),
            customClockWeather = customWeather,
        )
    }

    private fun getSystemSetting(setting: String, defaultValue: Int = 0) =
        Settings.System.getIntForUser(context.contentResolver, setting, defaultValue, UserHandle.USER_CURRENT) != 0

    private fun getSecureSetting(setting: String, defaultValue: Int = 0) =
        Settings.Secure.getIntForUser(context.contentResolver, setting, defaultValue, UserHandle.USER_CURRENT) != 0

    override fun weatherUpdated() = updateWeather()

    private fun updateIconTint() {
        if (mDozing) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            weatherIcon.colorFilter = ColorMatrixColorFilter(matrix)
        } else {
            weatherIcon.colorFilter = null
        }
    }

    private fun updateWeather() {
        val settings = getWeatherSettings()

        if (!settings.weatherEnabled) {
            hideAllViews()
            OmniJawsClient.get().removeObserver(context, this@WeatherViewController)
            return
        }

        OmniJawsClient.get().addObserver(context, this@WeatherViewController)

        // Show cached weather immediately if available
        val cached = OmniJawsClient.get().weatherInfo
        if (cached != null) {
            weatherInfo = cached
            applyWeatherInfo(cached, settings)
        }

        scope.launch(Dispatchers.IO) {
            try {
                OmniJawsClient.get().queryWeather(context)
                val info = OmniJawsClient.get().weatherInfo
                withContext(Dispatchers.Main) {
                    val currentSettings = getWeatherSettings()
                    if (info != null) {
                        weatherInfo = info
                        applyWeatherInfo(info, currentSettings)
                    } else if (weatherInfo == null) {
                        hideAllViews()
                    }
                }
            } catch (e: Exception) {
                // Ignore transient errors
            }
        }
    }

    private fun applyWeatherInfo(info: OmniJawsClient.WeatherInfo, settings: WeatherSettings) {
        val d = OmniJawsClient.get().getWeatherConditionImage(context, info.conditionCode)
        weatherIcon.setImageDrawable(d)
        updateIconTint()
        weatherTemp.text = buildWeatherText(info, settings)
        weatherTemp.isSelected = true
        showAllViews()
    }

    private fun hideAllViews() {
        listOf(weatherInfoView, weatherIcon, weatherTemp).forEach {
            it.visibility = View.GONE
        }
    }

    private fun showAllViews() {
        listOf(weatherInfoView, weatherIcon, weatherTemp).forEach {
            it.visibility = View.VISIBLE
        }
    }

    private fun clearWeather() {
        weatherInfo = null
        weatherIcon.setImageDrawable(null)
        weatherTemp.text = ""
    }

    private fun buildWeatherText(info: OmniJawsClient.WeatherInfo, settings: WeatherSettings): String {
        val conditionText = getConditionText(info.condition?.lowercase() ?: "")

        val locationText = if (settings.showWeatherLocation && !info.city.isNullOrBlank()) " • ${info.city}" else ""
        val conditionDisplay = if (settings.showWeatherText && conditionText.isNotBlank()) " • $conditionText" else ""
        val windDisplay = if (settings.showWindInfo && !info.windSpeed.isNullOrBlank()) " • ${info.windSpeed} ${info.windUnits} ${info.pinWheel ?: ""}".trimEnd() else ""
        val humidityDisplay = if (settings.showHumidityInfo && !info.humidity.isNullOrBlank()) " • ${info.humidity}" else ""

        val tempStr = info.temp ?: ""
        val unitStr = info.tempUnits ?: ""

        return "$tempStr$unitStr$locationText$conditionDisplay$windDisplay$humidityDisplay"
    }

    override fun weatherError(errorReason: Int) {
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            weatherInfo = null
            weatherIcon.setImageDrawable(null)
            weatherTemp.text = ""
            hideAllViews()
        }
    }

    fun removeObserver() {
        if (!initialized) return
        initialized = false
        scope.cancel()
        try {
            context.contentResolver.unregisterContentObserver(settingsObserver)
        } catch (ignored: Exception) {}
        OmniJawsClient.get().removeObserver(context, this)
        statusBarStateController.removeCallback(statusBarStateListener)
    }

    data class WeatherSettings(
        val weatherEnabled: Boolean,
        val showWeatherLocation: Boolean,
        val showWeatherText: Boolean,
        val showWindInfo: Boolean,
        val showHumidityInfo: Boolean,
        val customClockWeather: Boolean,
    )

    companion object {
        private const val LOCKSCREEN_WEATHER_ENABLED = "lockscreen_weather_enabled"
        private const val LOCKSCREEN_WEATHER_LOCATION = "lockscreen_weather_location"
        private const val LOCKSCREEN_WEATHER_TEXT = "lockscreen_weather_text"
        private const val LOCKSCREEN_WEATHER_WIND_INFO = "lockscreen_weather_wind_info"
        private const val LOCKSCREEN_WEATHER_HUMIDITY_INFO = "lockscreen_weather_humidity_info"
        const val CUSTOM_CLOCK_WEATHER = "custom_clock_weather"

        private val WEATHER_CONDITIONS = mapOf(
            "clouds" to R.string.weather_condition_clouds,
            "rain" to R.string.weather_condition_rain,
            "clear" to R.string.weather_condition_clear,
            "storm" to R.string.weather_condition_storm,
            "snow" to R.string.weather_condition_snow,
            "wind" to R.string.weather_condition_wind,
            "mist" to R.string.weather_condition_mist
        )
    }
}
