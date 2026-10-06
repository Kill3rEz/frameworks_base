/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.keyguard.ui.composable.media

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.internal.util.custom.OmniJawsClient
import java.util.Date
import java.util.Locale
import com.android.systemui.qs.shared.style.LiquidGlassSurface
import com.android.systemui.qs.shared.style.glassRim
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WidgetColor = Color.White.copy(alpha = 0.14f)
private val WidgetBorder = Color.White.copy(alpha = 0.18f)

@Composable
fun LockscreenExpandedClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberBroadcastValue(
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
    ) { System.currentTimeMillis() }
    val time =
        DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now)
            .toString()
    val date =
        DateFormat.format(
                DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"),
                Date(now),
            )
            .toString()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(time, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 34.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun LockscreenMediaWidgets(modifier: Modifier = Modifier) {
    val battery = rememberBattery()
    val weather = rememberWeather()
    val alarm = rememberNextAlarm()
    Row(
        modifier = modifier.fillMaxWidth().height(76.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        battery?.let { (level, charging) ->
            Widget(Modifier.weight(1.6f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            "$level%",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(level / 100f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
        weather?.let { (temp, icon) ->
            Widget(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Image(
                            rememberDrawablePainter(it),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        temp,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        alarm?.let { text ->
            Widget(Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WidgetIcon(Icons.Filled.Alarm)
                    Text(
                        text,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Widget(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .then(
                    if (liquidGlassEnabled()) {
                        Modifier.background(LiquidGlassSurface)
                            .glassRim(RoundedCornerShape(24.dp))
                    } else {
                        Modifier.background(WidgetColor)
                            .border(1.dp, WidgetBorder, RoundedCornerShape(24.dp))
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun WidgetIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
}

@Composable
private fun <T> rememberBroadcastValue(filter: IntentFilter, read: (Intent?) -> T): T {
    val context = LocalContext.current
    var value by remember { mutableStateOf(read(null)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    value = read(intent)
                }
            }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)?.let {
            value = read(it)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return value
}

@Composable
private fun rememberBattery(): Pair<Int, Boolean>? =
    rememberBroadcastValue(IntentFilter(Intent.ACTION_BATTERY_CHANGED)) { intent ->
        intent ?: return@rememberBroadcastValue null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return@rememberBroadcastValue null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        (level * 100 / scale) to charging
    }

@Composable
private fun rememberNextAlarm(): String? {
    val context = LocalContext.current
    return rememberBroadcastValue(IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
        val next =
            context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
                ?: return@rememberBroadcastValue null
        if (next.triggerTime - System.currentTimeMillis() > 24 * 60 * 60 * 1000L) {
            return@rememberBroadcastValue null
        }
        DateFormat.getTimeFormat(context).format(Date(next.triggerTime))
    }
}

@Composable
private fun rememberWeather(): Pair<String, Drawable?>? {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<Pair<String, Drawable?>?>(null) }
    DisposableEffect(context) {
        val client = OmniJawsClient.get()
        fun apply() {
            val info = client.weatherInfo
            weather =
                info?.temp?.let {
                    "$it${info.tempUnits ?: ""}" to
                        client.getWeatherConditionImage(context, info.conditionCode)
                }
        }
        val observer =
            object : OmniJawsClient.OmniJawsObserver {
                override fun weatherUpdated() = apply()

                override fun weatherError(errorReason: Int) {
                    if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) weather = null
                }
            }
        if (client.isOmniJawsEnabled(context)) {
            client.addObserver(context, observer)
            apply()
        }
        onDispose { client.removeObserver(context, observer) }
    }
    androidx.compose.runtime.LaunchedEffect(context) {
        val client = OmniJawsClient.get()
        if (!client.isOmniJawsEnabled(context)) return@LaunchedEffect
        withContext(Dispatchers.IO) { client.queryWeather(context) }
        val info = client.weatherInfo ?: return@LaunchedEffect
        weather =
            info.temp?.let {
                "$it${info.tempUnits ?: ""}" to
                    client.getWeatherConditionImage(context, info.conditionCode)
            }
    }
    return weather
}
