/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.volume

import android.content.Context
import android.os.UserHandle
import android.provider.Settings

/** Which volume panel design to render. Backed by [SETTING_NAME]. */
enum class VolumePanelStyle(val value: Int) {
    /** AOSP: one slider for the active stream, plus a slider per extra active stream. */
    DEFAULT(0),

    /**
     * PenguinOS: one slider until the panel is expanded, then a slider for every stream the user can
     * change from here - media, ring, alarm and call. Bare white pills on the wallpaper, no card.
     */
    EXPANDABLE(1),

    /** Same expanding behaviour as [EXPANDABLE], drawn as One UI does it: a dark card of blue
     * pills with a header and a label under every slider. */
    ONE_UI(2),

    /**
     * Drawn as MyUI does it: a narrow glass column by the volume keys, with the sound icon on top
     * and a thin track, that expands into a centred "Sound & vibration" card of thin sliders.
     */
    MYUI(3);

    /** Whether the panel is a card of its own, centred once expanded, rather than a column. */
    val isCard: Boolean
        get() = this == ONE_UI || this == MYUI

    companion object {
        const val SETTING_NAME = "volume_panel_style"

        fun fromValue(value: Int): VolumePanelStyle =
            values().firstOrNull { it.value == value } ?: DEFAULT

        /** Reads the current user's choice. */
        fun current(context: Context): VolumePanelStyle =
            fromValue(
                Settings.Secure.getIntForUser(
                    context.contentResolver,
                    SETTING_NAME,
                    DEFAULT.value,
                    UserHandle.USER_CURRENT,
                )
            )
    }
}
