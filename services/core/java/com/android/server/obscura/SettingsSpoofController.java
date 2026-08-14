/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.obscura;

import java.util.HashMap;
import java.util.Map;

public class SettingsSpoofController {

    private static final Map<String, String> SPOOFED_SETTINGS = new HashMap<>();

    static {
        SPOOFED_SETTINGS.put("adb_enabled", "0");
        SPOOFED_SETTINGS.put("development_settings_enabled", "0");
        SPOOFED_SETTINGS.put("adb_wifi_enabled", "0");
        SPOOFED_SETTINGS.put("package_verifier_user_consent", "0");
        SPOOFED_SETTINGS.put("verify_apps_over_usb", "0");
        SPOOFED_SETTINGS.put("accessibility_enabled", "0");
        SPOOFED_SETTINGS.put("enabled_accessibility_services", "");
        SPOOFED_SETTINGS.put("accessibility_display_inversion_enabled", "0");
    }

    public static String getSpoofedValue(String settingName) {
        if (settingName == null) return null;
        return SPOOFED_SETTINGS.get(settingName);
    }
}
