/*
 * Copyright (C) 2026 The DerpFest Project
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
package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * Convenience accessors for the slide-status-bar-to-adjust-brightness gesture.
 *
 * <p>The gesture itself is implemented by {@code CentralSurfacesImpl}; this
 * helper exists so every caller that decides whether to invoke or suppress
 * the gesture reads the same setting keys with the same semantics, instead
 * of duplicating {@link Settings.System#getIntForUser} calls inline.
 *
 * <p>Reads are cheap: {@code Settings.System} caches values in-process, so
 * calling these per-touch is fine.
 */
public final class StatusBarBrightnessGesture {

    /**
     * Sub-toggle gating the gesture on the keyguard. Default off — secure
     * default, since otherwise anyone could dim or brighten a locked screen.
     */
    public static final String SETTING_LOCKSCREEN =
            "status_bar_brightness_control_lockscreen";

    private StatusBarBrightnessGesture() {}

    /** Whether the user has enabled the brightness gesture at all. */
    public static boolean isEnabled(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL,
                0,
                UserHandle.USER_CURRENT) != 0;
    }

    /** Whether the gesture is also allowed while the device is on the keyguard. */
    public static boolean isLockscreenAllowed(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                SETTING_LOCKSCREEN,
                0,
                UserHandle.USER_CURRENT) != 0;
    }
}
