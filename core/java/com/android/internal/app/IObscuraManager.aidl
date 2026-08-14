/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.app;

import com.android.internal.app.IHiddenNotificationListener;
import com.android.internal.app.HiddenNotificationInfo;

interface IObscuraManager {
    boolean isPackageHidden(String packageName);
    void setPackageHidden(String packageName, boolean hidden);
    boolean isPackageLauncherHidden(String packageName);
    void setPackageLauncherHidden(String packageName, boolean hidden);

    List<String> getHiddenPackages();
    List<String> getLauncherHiddenPackages();
    List<String> getLockablePackages();

    void registerHiddenNotificationListener(IHiddenNotificationListener listener);
    void unregisterHiddenNotificationListener(IHiddenNotificationListener listener);

    List<HiddenNotificationInfo> getHiddenNotifications();
    void onHiddenNotificationPosted(in HiddenNotificationInfo info);
    void onHiddenNotificationRemoved(String key);

    boolean isPackageIsolated(String packageName);
    void isolatePackage(String packageName);
    void unisolatePackage(String packageName);
    List<String> getIsolatedPackages();

    void setRestrictedGids(String packageName, in int[] gids);
    int[] getRestrictedGids(String packageName);

    boolean isSpoofSettingEnabled(String packageName, String settingKey);
    void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled);
    List<String> getEnabledSpoofSettings(String packageName);

    boolean isDataIsolationEnabled(String packageName);
    void setDataIsolationEnabled(String packageName, boolean enabled);

    String getSpoofedSetting(String callingPackage, String settingName);
}
