/*
 * Copyright (C) 2025 crDroid Android Project
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

package com.android.systemui.statusbar.pipeline.battery.ui.composable

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.data.repository.BatteryRepository
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel

@Composable
fun BatteryWithPercent(
    viewModel: BatteryViewModel,
    isDarkProvider: () -> IsAreaDark,
    modifier: Modifier,
) {
    BatteryViewModel.FontResolverWrapper {
        val boundsState = remember { mutableStateOf(Rect()) }
        val showPercentNextToIcon by
            viewModel.interactor.isShowPercentNextToIconEnabled.collectAsState(false)

        val colorProvider = {
            if (isDarkProvider().isDarkTheme(boundsState.value)) {
                viewModel.colorProfile.dark
            } else {
                viewModel.colorProfile.light
            }
        }

        val batteryHeight =
            with(LocalDensity.current) {
                BatteryViewModel.getStatusBarBatteryHeight(LocalContext.current).toDp()
            }

        val percentText = "${viewModel.level}%"
        val textStyle = BatteryViewModel.getStatusBarBatteryTextStyle(LocalContext.current)
        val textColor = colorProvider().fill

        if (viewModel.batteryIconStyle == BatteryRepository.ICON_STYLE_TEXT) {
            Box(
                modifier =
                    modifier.onLayoutRectChanged {
                        boundsState.value = with(it.boundsInScreen) { Rect(left, top, right, bottom) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = percentText,
                    color = textColor,
                    style = textStyle,
                    maxLines = 1,
                )
            }
        } else {
            Row(
                modifier =
                    modifier.onLayoutRectChanged {
                        boundsState.value = with(it.boundsInScreen) { Rect(left, top, right, bottom) }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UnifiedBattery(
                    viewModel = viewModel,
                    isDarkProvider = isDarkProvider,
                    modifier = Modifier.height(batteryHeight).align(Alignment.CenterVertically),
                )

                if (showPercentNextToIcon) {
                    Text(
                        text = percentText,
                        color = textColor,
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
