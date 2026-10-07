/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import dagger.BindsOptionalOf
import dagger.Module

@Module
interface PenguinHooksModule {
    @BindsOptionalOf fun powerMenuLayout(): PowerMenuLayout

    @BindsOptionalOf fun volumePanelLooks(): VolumePanelLooks
}
