/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.obscura;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.android.internal.os.BackgroundThread;
import android.os.Binder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.app.HiddenNotificationInfo;
import com.android.internal.app.IHiddenNotificationListener;
import com.android.internal.app.IObscuraManager;
import com.android.server.NtServiceInjector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ObscuraService extends IObscuraManager.Stub implements IObscuraService {
    private static final String TAG = "Obscura";

    public static final String OBSCURA_PACKAGE = "com.android.settings";
    public static final Set<String> BLACKLISTED_PACKAGES = Set.of(
            "android",
            "android.media",
            "android.uid.system",
            "android.uid.shell",
            "android.uid.systemui",
            "com.android.permissioncontroller",
            "com.android.providers.downloads",
            "com.android.providers.downloads.ui",
            "com.android.providers.media",
            "com.android.providers.media.module",
            "com.android.providers.settings",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.webview",
            "com.google.android.providers.media.module"
    );

    private Context mContext;
    private AppControlController mAppControlController;
    private HiddenNotificationController mHiddenNotificationController;

    private static final class Holder {
        private static final ObscuraService INSTANCE = new ObscuraService();
    }

    public static ObscuraService get() {
        return Holder.INSTANCE;
    }

    private ObscuraService() {
    }

    private final BroadcastReceiver mPackageRemovedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getData() == null) return;
            String packageName = intent.getData().getSchemeSpecificPart();
            if (TextUtils.isEmpty(packageName)) return;
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;

            Slog.i(TAG, "Package removed: " + packageName + ", cleaning up");
            cleanupPackage(packageName);
        }
    };

    private void cleanupPackage(String packageName) {
        if (mAppControlController == null) return;
        boolean changed = false;

        if (mAppControlController.isPackageHidden(packageName)) {
            mAppControlController.setPackageHidden(packageName, false);
            changed = true;
        }
        if (mAppControlController.isPackageLauncherHidden(packageName)) {
            mAppControlController.setPackageLauncherHidden(packageName, false);
            changed = true;
        }
        if (mAppControlController.isPackageIsolated(packageName)) {
            mAppControlController.setPackageIsolated(packageName, false);
            changed = true;
        }
        if (mHiddenNotificationController != null) {
            mHiddenNotificationController.clearNotificationsForPackage(packageName);
        }
        if (changed) {
            Slog.i(TAG, "Cleaned up entries for uninstalled package: " + packageName);
        }
    }

    @Override
    public void systemReadyInternal() {
        Slog.d(TAG, "systemReady");
        mContext = NtServiceInjector.get().getContext();

        mAppControlController = new AppControlController(mContext, BLACKLISTED_PACKAGES);
        mAppControlController.init();

        mHiddenNotificationController = new HiddenNotificationController();
        mHiddenNotificationController.setPackageHiddenChecker(this::isPackageHidden);

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiverAsUser(mPackageRemovedReceiver, UserHandle.ALL, filter, null, BackgroundThread.getHandler());
    }

    public static void systemReady() {
        ObscuraService instance = get();
        instance.systemReadyInternal();
        ServiceManager.addService(Context.OBSCURA_SERVICE, instance);
        Slog.i(TAG, "ObscuraService ready");
    }

    @Override
    public boolean isPackageHidden(String packageName) {
        if (mAppControlController == null) return false;
        return mAppControlController.isPackageHidden(packageName);
    }

    @Override
    public void setPackageHidden(String packageName, boolean hidden) {
        mAppControlController.setPackageHidden(packageName, hidden);
    }

    @Override
    public boolean isPackageLauncherHidden(String packageName) {
        if (mAppControlController == null) return false;
        return mAppControlController.isPackageLauncherHidden(packageName);
    }

    @Override
    public void setPackageLauncherHidden(String packageName, boolean hidden) {
        mAppControlController.setPackageLauncherHidden(packageName, hidden);
    }

    @Override
    public List<String> getHiddenPackages() {
        if (mAppControlController == null) return java.util.Collections.emptyList();
        return mAppControlController.getHiddenPackages();
    }

    @Override
    public List<String> getLauncherHiddenPackages() {
        if (mAppControlController == null) return java.util.Collections.emptyList();
        return mAppControlController.getLauncherHiddenPackages();
    }

    @Override
    public List<String> getLockablePackages() {
        if (mAppControlController == null) return java.util.Collections.emptyList();
        return mAppControlController.getLockablePackages();
    }

    @Override
    public void registerHiddenNotificationListener(IHiddenNotificationListener listener) {
        mHiddenNotificationController.registerListener(listener);
    }

    @Override
    public void unregisterHiddenNotificationListener(IHiddenNotificationListener listener) {
        mHiddenNotificationController.unregisterListener(listener);
    }

    @Override
    public List<HiddenNotificationInfo> getHiddenNotifications() {
        return mHiddenNotificationController.getHiddenNotifications();
    }

    @Override
    public void onHiddenNotificationPosted(HiddenNotificationInfo info) {
        mHiddenNotificationController.onHiddenNotificationPosted(info);
    }

    @Override
    public void onHiddenNotificationRemoved(String key) {
        mHiddenNotificationController.onHiddenNotificationRemoved(key);
    }

    @Override
    public boolean isPackageIsolated(String packageName) {
        if (mAppControlController == null) return false;
        return mAppControlController.isPackageIsolated(packageName);
    }

    @Override
    public void isolatePackage(String packageName) {
        mAppControlController.setPackageIsolated(packageName, true);
    }

    @Override
    public void unisolatePackage(String packageName) {
        mAppControlController.setPackageIsolated(packageName, false);
    }

    @Override
    public List<String> getIsolatedPackages() {
        if (mAppControlController == null) return java.util.Collections.emptyList();
        return mAppControlController.getIsolatedPackages();
    }

    @Override
    public void setRestrictedGids(String packageName, int[] gids) {
        mAppControlController.setRestrictedGids(packageName, gids);
    }

    @Override
    public int[] getRestrictedGids(String packageName) {
        if (mAppControlController == null) return null;
        return mAppControlController.getRestrictedGids(packageName);
    }

    @Override
    public boolean isDataIsolationEnabled(String packageName) {
        if (mAppControlController == null) return false;
        return mAppControlController.isDataIsolationEnabled(packageName);
    }

    @Override
    public void setDataIsolationEnabled(String packageName, boolean enabled) {
        mAppControlController.setDataIsolationEnabled(packageName, enabled);
    }

    @Override
    public boolean isSpoofSettingEnabled(String packageName, String settingKey) {
        if (mAppControlController == null) return false;
        return mAppControlController.isSpoofSettingEnabled(packageName, settingKey);
    }

    @Override
    public void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled) {
        mAppControlController.setSpoofSettingEnabled(packageName, settingKey, enabled);
    }

    @Override
    public List<String> getEnabledSpoofSettings(String packageName) {
        if (mAppControlController == null) return java.util.Collections.emptyList();
        return mAppControlController.getEnabledSpoofSettings(packageName);
    }

    @Override
    public String getSpoofedSetting(String callingPackage, String settingName) {
        if (mAppControlController == null) return null;
        if (!mAppControlController.isSpoofSettingEnabled(callingPackage, settingName)) {
            return null;
        }
        return SettingsSpoofController.getSpoofedValue(settingName);
    }


}
