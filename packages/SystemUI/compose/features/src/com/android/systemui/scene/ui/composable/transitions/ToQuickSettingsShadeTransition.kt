/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.shade.ui.composable.OverlayShade

fun TransitionBuilder.toQuickSettingsShadeTransition(
    durationScale: Double = 1.0,
    shadeExpansionMotion: VerticalExpandContainerSpec,
    revealHaptics: ContainerRevealHaptics,
) {
    spec = controlCentreSpring(durationScale)

    controlCentreReveal(QuickSettingsShade.Elements.Panel)

    fractionRange(end = .5f) { fade(OverlayShade.Elements.Scrim) }
    fractionRange(start = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
}

fun TransitionBuilder.fromQuickSettingsShadeTransition(durationScale: Double = 1.0) {
    spec =
        spring(
            dampingRatio = 1f,
            stiffness = (800.0 / (durationScale * durationScale)).toFloat(),
            visibilityThreshold = Spring.DefaultDisplacementThreshold,
        )

    fractionRange(end = .5f) { fade(QuickSettingsShade.Elements.Panel) }
    scaleDraw(QuickSettingsShade.Elements.Panel, scaleX = 0.94f, scaleY = 0.94f)
    translate(QuickSettingsShade.Elements.Panel, y = (-24).dp)

    fractionRange(end = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
    fade(OverlayShade.Elements.Scrim)
}
