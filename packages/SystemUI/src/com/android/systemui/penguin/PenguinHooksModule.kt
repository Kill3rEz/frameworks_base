/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import dagger.BindsOptionalOf
import dagger.Module

@Module
interface PenguinHooksModule {
    @BindsOptionalOf fun depthSubject(): DepthSubject

    @BindsOptionalOf fun lockscreenPlayer(): LockscreenPlayer

    @BindsOptionalOf fun powerMenuLayout(): PowerMenuLayout

    @BindsOptionalOf fun qsPanels(): QsPanels

    @BindsOptionalOf fun shadeMotion(): ShadeMotion

    @BindsOptionalOf fun statusBarIsland(): StatusBarIsland

    @BindsOptionalOf fun volumePanelLooks(): VolumePanelLooks
}
