/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.phone

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import android.view.WindowManagerGlobal

object DexStatusBar {
    private const val PACKAGE = "com.penguin.dex"
    private const val SERVICE = "com.penguin.dex.DexService"
    private const val ACTION = "com.penguin.dex.action.CONTROL_CENTRE"
    private const val CONTROL_CENTRE = "penguin_dex_control_centre"

    fun isDex(context: Context, displayId: Int): Boolean =
        displayId != Display.DEFAULT_DISPLAY &&
            Settings.Secure.getInt(context.contentResolver, CONTROL_CENTRE, 1) != 0 &&
            runCatching {
                    WindowManagerGlobal.getWindowManagerService()?.isEligibleForDesktopMode(displayId)
                }
                .getOrNull() == true

    fun toggleControlCentre(context: Context, displayId: Int) {
        context.startServiceAsUser(
            Intent(ACTION).setClassName(PACKAGE, SERVICE).putExtra("display", displayId),
            UserHandle.SYSTEM,
        )
    }
}
