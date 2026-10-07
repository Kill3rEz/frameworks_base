/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel

interface LockscreenPlayer {
    val isEnabled: Boolean

    val expansion: Float

    @Composable fun ObserveSettings()

    fun setMediaVisible(visible: Boolean)

    @Composable
    fun Card(
        viewModelFactory: MediaViewModel.Factory,
        behavior: MediaUiBehavior,
        modifier: Modifier,
    )

    @Composable fun ExpandedArt(alpha: () -> Float, covered: () -> Float, unlocking: () -> Float)

    @Composable
    fun ColumnScope.SmallClockColumn(
        notificationsActive: Boolean,
        clock: @Composable () -> Unit,
        media: @Composable ColumnScope.() -> Unit,
        notifications: @Composable (Modifier) -> Unit,
    )
}
