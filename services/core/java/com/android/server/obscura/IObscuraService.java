/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.obscura;

import java.util.List;

public interface IObscuraService {
    void systemReadyInternal();
    boolean isPackageHidden(String packageName);
    boolean isPackageLauncherHidden(String packageName);
    boolean isPackageIsolated(String packageName);
    boolean isSpoofSettingEnabled(String packageName, String settingKey);
    List<String> getEnabledSpoofSettings(String packageName);
    String getSpoofedSetting(String callingPackage, String settingName);
    int[] getRestrictedGids(String packageName);
    boolean isDataIsolationEnabled(String packageName);
}
