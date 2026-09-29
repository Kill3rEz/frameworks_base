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

import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.notifications.ui.composable.NotificationsShade
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.ui.composable.OverlayShade

fun TransitionBuilder.toNotificationsShadeTransition(
    durationScale: Double = 1.0,
    enableSharedElements: Boolean,
    shadeExpansionMotion: VerticalExpandContainerSpec,
    revealHaptics: ContainerRevealHaptics,
) {
    spec = overlayShadeSpring(durationScale)

    // Ensure the shared elements aren't clipped by the shade outline during the transition from
    // lockscreen.
    sharedElement(
        LockscreenElementKeys.Clock.Small,
        enabled = enableSharedElements,
        elevateInContent = Overlays.NotificationsShade,
    )
    sharedElement(
        LockscreenElementKeys.MediaCarousel,
        enabled = enableSharedElements,
        elevateInContent = Overlays.NotificationsShade,
    )
    sharedElement(Notifications.Elements.StackPlaceholder, enabled = enableSharedElements)
    sharedElement(
        Notifications.Elements.HeadsUpNotificationPlaceholder,
        enabled = enableSharedElements,
    )

    oxygenPanelReveal(NotificationsShade.Elements.Panel)

    fractionRange(start = .16f, end = 0.8f) { fade(NotificationsShade.Elements.StatusBar) }
    fractionRange(start = .33f, end = 0.8f) { fade(Notifications.Elements.StackPlaceholder) }
    fractionRange(end = .5f) { fade(OverlayShade.Elements.Scrim) }
}

