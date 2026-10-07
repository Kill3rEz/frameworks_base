/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.TransitionBuilder

interface ShadeMotion {
    @Composable fun key(): Any?

    fun TransitionBuilder.shadeToQuickSettings(animateQsTilesAsShared: () -> Boolean)

    fun TransitionBuilder.quickSettingsToShade(animateQsTilesAsShared: () -> Boolean)

    fun TransitionBuilder.toQuickSettingsShade(durationScale: Double)

    fun TransitionBuilder.fromQuickSettingsShade()

    fun TransitionBuilder.toNotificationsShade(durationScale: Double, enableSharedElements: Boolean)

    fun SceneTransitionsBuilder.overlaySwitchTransitions()

    @Composable fun quickSettingsShadeOverscroll(): OverscrollFactory?
}
