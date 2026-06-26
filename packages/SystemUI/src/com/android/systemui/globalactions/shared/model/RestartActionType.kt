/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.globalactions.shared.model

/**
 * Typed enum representing each entry in the advanced restart submenu.
 * Config keys must match entries in config_restartActionsList.
 */
enum class RestartActionType(val configKey: String) {
    RESTART("restart"),
    RESTART_RECOVERY("restart_recovery"),
    RESTART_BOOTLOADER("restart_bootloader"),
    RESTART_DOWNLOAD("restart_download"),
    RESTART_SYSTEMUI("restart_systemui");

    companion object {
        private val KEY_MAP = entries.associateBy { it.configKey }

        fun fromConfigKey(configKey: String): RestartActionType? = KEY_MAP[configKey]
    }
}
