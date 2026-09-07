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

package com.android.systemui.globalactions;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

public enum PowerMenuStyle {
    PENGUIN(0),

    IOS(1);

    public static final String SETTING_NAME = "power_menu_style";

    public final int value;

    PowerMenuStyle(int value) {
        this.value = value;
    }

    public static PowerMenuStyle fromValue(int value) {
        for (PowerMenuStyle style : values()) {
            if (style.value == value) {
                return style;
            }
        }
        return PENGUIN;
    }

    public static PowerMenuStyle current(Context context) {
        return fromValue(Settings.Secure.getIntForUser(context.getContentResolver(), SETTING_NAME,
                PENGUIN.value, UserHandle.USER_CURRENT));
    }
}
