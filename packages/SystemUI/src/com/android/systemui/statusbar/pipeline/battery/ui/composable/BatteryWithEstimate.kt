/*
 * Copyright (C) 2025 The Android Open Source Project
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.data.repository.BatteryRepository
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel

@Composable
fun BatteryWithEstimate(
    viewModelFactory: BatteryViewModel.Factory,
    isDarkProvider: () -> IsAreaDark,
    textColor: Color,
    showEstimate: Boolean,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    BatteryViewModel.FontResolverWrapper {
        val boundsState = remember { mutableStateOf(Rect()) }
        val viewModel =
            rememberViewModel(traceName = "BatteryWithEstimate") { viewModelFactory.create() }

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
        val dynamicTextColor = colorProvider().fill

        Row(
            modifier =
                modifier.onLayoutRectChanged {
                    boundsState.value = with(it.boundsInScreen) { Rect(left, top, right, bottom) }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.batteryIconStyle == BatteryRepository.ICON_STYLE_TEXT) {
                Text(
                    text = percentText,
                    color = dynamicTextColor,
                    style = textStyle,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            } else {
                if (showIcon) {
                    UnifiedBattery(
                        viewModel = viewModel,
                        isDarkProvider = isDarkProvider,
                        modifier = Modifier.height(batteryHeight).align(Alignment.CenterVertically),
                    )
                }
                if (showEstimate && viewModel.batteryTimeRemainingEstimate != null) {
                    Text(
                        text = viewModel.batteryTimeRemainingEstimate!!,
                        color = dynamicTextColor,
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = 1),
                    )
                } else if (showPercentNextToIcon) {
                    Text(
                        text = percentText,
                        color = dynamicTextColor,
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
