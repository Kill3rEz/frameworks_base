/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.obscura;

import static android.app.ObscuraManager.SETTING_OBSCURA_CONFIG;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppControlController {
    private static final String TAG = "Obscura.AppControl";

    private static final String KEY_HIDDEN_PKGS = "hidden_pkgs";
    private static final String KEY_LAUNCHER_HIDDEN_PKGS = "launcher_hidden_pkgs";
    private static final String KEY_DETACHED_PKGS = "detached_pkgs";
    private static final String KEY_ISOLATED_PKGS = "isolated_pkgs";
    private static final String KEY_GID_RESTRICTIONS = "gid_restrictions";
    private static final String KEY_SPOOF_SETTINGS_MAP = "spoof_settings_map";
    private static final String KEY_DATA_ISOLATION = "data_isolation_pkgs";
    private static final String KEY_APP_SCOPE_MODES = "app_scope_modes";
    private static final String KEY_APP_SCOPE_LISTS = "app_scope_lists";

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final LauncherApps mLauncherApps;
    private final Set<String> mBlacklistedPackages;
    private final Handler mHandler;

    private final Set<String> mHiddenPackages = new HashSet<>();
    private final Set<String> mLauncherHiddenPackages = new HashSet<>();
    private final Set<String> mDetachedPackages = new HashSet<>();
    private final Set<String> mIsolatedPackages = new HashSet<>();
    private final Map<String, int[]> mGidRestrictions = new HashMap<>();
    private final Map<String, Set<String>> mSpoofSettingsMap = new HashMap<>();
    private final Set<String> mDataIsolationPackages = new HashSet<>();
    private final Map<String, Integer> mAppScopeModes = new HashMap<>();
    private final Map<String, Set<String>> mAppScopeLists = new HashMap<>();

    private ContentObserver mConfigObserver;

    public AppControlController(Context context, Set<String> blacklistedPackages) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mLauncherApps = context.getSystemService(LauncherApps.class);
        mBlacklistedPackages = blacklistedPackages;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init() {
        registerSettingsObserver();
        loadConfigFromSettings();
    }

    private void registerSettingsObserver() {
        mConfigObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                loadConfigFromSettings();
                broadcastPackageChanges();
            }
        };

        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SETTING_OBSCURA_CONFIG),
                false, mConfigObserver, UserHandle.USER_ALL);
    }

    private volatile boolean mHasActiveHidingRules = false;

    private void updateActiveHidingRules() {
        mHasActiveHidingRules = !mHiddenPackages.isEmpty() || !mAppScopeModes.isEmpty();
    }

    private void loadConfigFromSettings() {
        String jsonStr = Settings.Secure.getString(mContentResolver, SETTING_OBSCURA_CONFIG);

        synchronized (this) {
            mHiddenPackages.clear();
            mLauncherHiddenPackages.clear();
            mDetachedPackages.clear();
            mIsolatedPackages.clear();
            mGidRestrictions.clear();
            mSpoofSettingsMap.clear();
            mDataIsolationPackages.clear();
            mAppScopeModes.clear();
            mAppScopeLists.clear();

            if (!TextUtils.isEmpty(jsonStr)) {
                try {
                    JSONObject config = new JSONObject(jsonStr);
                    loadPackageSet(config, KEY_HIDDEN_PKGS, mHiddenPackages);
                    loadPackageSet(config, KEY_LAUNCHER_HIDDEN_PKGS, mLauncherHiddenPackages);
                    loadPackageSet(config, KEY_DETACHED_PKGS, mDetachedPackages);
                    loadPackageSet(config, KEY_ISOLATED_PKGS, mIsolatedPackages);
                    loadSpoofSettingsMap(config);
                    loadPackageSet(config, KEY_DATA_ISOLATION, mDataIsolationPackages);
                    loadGidRestrictions(config);
                    loadAppScopeModes(config);
                    loadAppScopeLists(config);
                } catch (JSONException e) {
                    Slog.e(TAG, "Failed to parse config JSON", e);
                }
            }
            updateActiveHidingRules();
        }
    }

    private void loadPackageSet(JSONObject config, String key, Set<String> target) {
        JSONArray arr = config.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String pkg = arr.optString(i);
                if (!TextUtils.isEmpty(pkg) && !mBlacklistedPackages.contains(pkg)) {
                    target.add(pkg);
                }
            }
        }
    }

    private void saveConfigToSettings() {
        try {
            JSONObject config = new JSONObject();
            synchronized (this) {
                config.put(KEY_HIDDEN_PKGS, new JSONArray(mHiddenPackages));
                config.put(KEY_LAUNCHER_HIDDEN_PKGS, new JSONArray(mLauncherHiddenPackages));
                config.put(KEY_DETACHED_PKGS, new JSONArray(mDetachedPackages));
                config.put(KEY_ISOLATED_PKGS, new JSONArray(mIsolatedPackages));
                saveSpoofSettingsMap(config);
                config.put(KEY_DATA_ISOLATION, new JSONArray(mDataIsolationPackages));
                saveGidRestrictions(config);
                saveAppScopeModes(config);
                saveAppScopeLists(config);
                updateActiveHidingRules();
            }
            Settings.Secure.putString(mContentResolver, SETTING_OBSCURA_CONFIG, config.toString());
        } catch (JSONException e) {
            Slog.e(TAG, "Failed to save config JSON", e);
        }
    }

    private void broadcastPackageChanges() {
        Set<String> allPackages = new HashSet<>();
        synchronized (this) {
            allPackages.addAll(mHiddenPackages);
        }
        for (String packageName : allPackages) {
            int uid = getPackageUid(packageName);
            if (uid >= 0) {
                broadcastPackageChange(packageName, uid);
            }
        }
    }

    private void broadcastPackageChange(String packageName, int uid) {
        try {
            Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
            intent.setData(Uri.fromParts("package", packageName, null));
            intent.putExtra(Intent.EXTRA_UID, uid);
            intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(uid));
            intent.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, new String[]{packageName});
            intent.putExtra(Intent.EXTRA_DONT_KILL_APP, true);
            mContext.sendBroadcastAsUser(intent, UserHandle.of(UserHandle.getUserId(uid)));
        } catch (Exception e) {
            Slog.w(TAG, "Failed to broadcast package change for " + packageName, e);
        }
    }

    public boolean isPackageHidden(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        synchronized (this) {
            return mHiddenPackages.contains(packageName);
        }
    }

    public boolean isPackageLauncherHidden(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        synchronized (this) {
            return mLauncherHiddenPackages.contains(packageName);
        }
    }

    public boolean isPackageDetached(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        synchronized (this) {
            return mDetachedPackages.contains(packageName);
        }
    }

    public boolean isPackageIsolated(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        synchronized (this) {
            return mIsolatedPackages.contains(packageName);
        }
    }

    public void setPackageHidden(String packageName, boolean hidden) {
        if (TextUtils.isEmpty(packageName)) return;
        if (hidden && !isPackageLockable(packageName)) {
            Slog.w(TAG, "Cannot hide package - not lockable: " + packageName);
            return;
        }
        int uid = getPackageUid(packageName);
        synchronized (this) {
            boolean changed = hidden ? mHiddenPackages.add(packageName)
                                     : mHiddenPackages.remove(packageName);
            if (changed) {
                saveConfigToSettings();
                if (uid >= 0) broadcastPackageChange(packageName, uid);
            }
        }
    }

    public void setPackageLauncherHidden(String packageName, boolean hidden) {
        if (TextUtils.isEmpty(packageName)) return;
        if (hidden && !isPackageLockable(packageName)) {
            Slog.w(TAG, "Cannot hide from launcher - not lockable: " + packageName);
            return;
        }
        int uid = getPackageUid(packageName);
        synchronized (this) {
            boolean changed = hidden ? mLauncherHiddenPackages.add(packageName)
                                     : mLauncherHiddenPackages.remove(packageName);
            if (changed) {
                saveConfigToSettings();
                if (uid >= 0) broadcastPackageChange(packageName, uid);
            }
        }
    }

    public void setPackageDetached(String packageName, boolean detached) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            boolean changed = detached ? mDetachedPackages.add(packageName)
                                       : mDetachedPackages.remove(packageName);
            if (changed) saveConfigToSettings();
        }
    }

    public void setPackageIsolated(String packageName, boolean isolated) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            boolean changed = isolated ? mIsolatedPackages.add(packageName)
                                        : mIsolatedPackages.remove(packageName);
            if (changed) saveConfigToSettings();
        }
    }

    public List<String> getHiddenPackages() {
        synchronized (this) {
            return new ArrayList<>(mHiddenPackages);
        }
    }

    public List<String> getLauncherHiddenPackages() {
        synchronized (this) {
            return new ArrayList<>(mLauncherHiddenPackages);
        }
    }

    public List<String> getDetachedPackages() {
        synchronized (this) {
            return new ArrayList<>(mDetachedPackages);
        }
    }

    public List<String> getIsolatedPackages() {
        synchronized (this) {
            return new ArrayList<>(mIsolatedPackages);
        }
    }

    public boolean isPackageLockable(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        if (mBlacklistedPackages.contains(packageName)) return false;
        if (mLauncherApps == null) return false;
        try {
            List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(
                    packageName, UserHandle.of(UserHandle.USER_SYSTEM));
            return activities != null && !activities.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getLockablePackages() {
        List<String> result = new ArrayList<>();
        if (mLauncherApps != null) {
            try {
                List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(
                        null, UserHandle.of(UserHandle.USER_SYSTEM));
                Set<String> seen = new HashSet<>();
                for (LauncherActivityInfo info : activities) {
                    String pkgName = info.getApplicationInfo().packageName;
                    if (!mBlacklistedPackages.contains(pkgName) && !seen.contains(pkgName)) {
                        result.add(pkgName);
                        seen.add(pkgName);
                    }
                }
            } catch (Exception e) {
                Slog.e(TAG, "Failed to get lockable packages", e);
            }
        }
        if (result.isEmpty()) {
            try {
                PackageManager pm = mContext.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (ApplicationInfo appInfo : apps) {
                    if (mBlacklistedPackages.contains(appInfo.packageName)) continue;
                    if (isSystemUid(appInfo.uid)) continue;
                    if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                        result.add(appInfo.packageName);
                    }
                }
            } catch (Exception e) {
                Slog.e(TAG, "Failed to get lockable packages fallback", e);
            }
        }
        return result;
    }

    private int getPackageUid(String packageName) {
        try {
            return mContext.getPackageManager()
                    .getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static boolean isSystemUid(int uid) {
        int appId = UserHandle.getAppId(uid);
        return appId == android.os.Process.ROOT_UID || appId == android.os.Process.SYSTEM_UID;
    }

    private void loadGidRestrictions(JSONObject config) {
        JSONObject gidObj = config.optJSONObject(KEY_GID_RESTRICTIONS);
        if (gidObj == null) return;
        java.util.Iterator<String> keys = gidObj.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            JSONArray arr = gidObj.optJSONArray(pkg);
            if (arr != null && arr.length() > 0) {
                int[] gids = new int[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    gids[i] = arr.optInt(i);
                }
                mGidRestrictions.put(pkg, gids);
            }
        }
    }

    private void saveGidRestrictions(JSONObject config) throws JSONException {
        JSONObject gidObj = new JSONObject();
        for (Map.Entry<String, int[]> entry : mGidRestrictions.entrySet()) {
            JSONArray arr = new JSONArray();
            for (int gid : entry.getValue()) {
                arr.put(gid);
            }
            gidObj.put(entry.getKey(), arr);
        }
        config.put(KEY_GID_RESTRICTIONS, gidObj);
    }

    public void setRestrictedGids(String packageName, int[] gids) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            if (gids == null || gids.length == 0) {
                mGidRestrictions.remove(packageName);
            } else {
                mGidRestrictions.put(packageName, gids);
            }
            saveConfigToSettings();
        }
    }

    public int[] getRestrictedGids(String packageName) {
        if (TextUtils.isEmpty(packageName)) return null;
        synchronized (this) {
            return mGidRestrictions.get(packageName);
        }
    }

    public boolean isSpoofSettingEnabled(String packageName, String settingKey) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(settingKey)) return false;
        synchronized (this) {
            Set<String> settings = mSpoofSettingsMap.get(packageName);
            return settings != null && settings.contains(settingKey);
        }
    }

    public void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(settingKey)) return;
        synchronized (this) {
            Set<String> settings = mSpoofSettingsMap.get(packageName);
            boolean changed;
            if (enabled) {
                if (settings == null) {
                    settings = new HashSet<>();
                    mSpoofSettingsMap.put(packageName, settings);
                }
                changed = settings.add(settingKey);
            } else {
                if (settings == null) return;
                changed = settings.remove(settingKey);
                if (settings.isEmpty()) {
                    mSpoofSettingsMap.remove(packageName);
                }
            }
            if (changed) saveConfigToSettings();
        }
    }

    public List<String> getEnabledSpoofSettings(String packageName) {
        if (TextUtils.isEmpty(packageName)) return java.util.Collections.emptyList();
        synchronized (this) {
            Set<String> settings = mSpoofSettingsMap.get(packageName);
            if (settings == null || settings.isEmpty()) return java.util.Collections.emptyList();
            return new java.util.ArrayList<>(settings);
        }
    }

    private void loadSpoofSettingsMap(JSONObject config) {
        JSONObject mapObj = config.optJSONObject(KEY_SPOOF_SETTINGS_MAP);
        if (mapObj == null) return;
        java.util.Iterator<String> keys = mapObj.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            JSONArray arr = mapObj.optJSONArray(pkg);
            if (arr != null && arr.length() > 0) {
                Set<String> settings = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i);
                    if (!TextUtils.isEmpty(s)) settings.add(s);
                }
                if (!settings.isEmpty()) {
                    mSpoofSettingsMap.put(pkg, settings);
                }
            }
        }
    }

    private void saveSpoofSettingsMap(JSONObject config) throws JSONException {
        JSONObject mapObj = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : mSpoofSettingsMap.entrySet()) {
            mapObj.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        config.put(KEY_SPOOF_SETTINGS_MAP, mapObj);
    }

    public boolean isDataIsolationEnabled(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        synchronized (this) {
            return mDataIsolationPackages.contains(packageName);
        }
    }

    public void setDataIsolationEnabled(String packageName, boolean enabled) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            boolean changed = enabled ? mDataIsolationPackages.add(packageName)
                                      : mDataIsolationPackages.remove(packageName);
            if (changed) saveConfigToSettings();
        }
    }

    public void launchHiddenApp(String packageName) {
        if (TextUtils.isEmpty(packageName)) return;
        boolean wasHidden = isPackageHidden(packageName);
        boolean wasLauncherHidden = isPackageLauncherHidden(packageName);

        if (wasHidden) {
            setPackageHidden(packageName, false);
        }
        if (wasLauncherHidden) {
            setPackageLauncherHidden(packageName, false);
        }

        // Launch the app
        try {
            Intent intent = mContext.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                mContext.startActivityAsUser(intent, UserHandle.CURRENT);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to launch hidden app: " + packageName, e);
        }

        // Re-hide after a short delay to allow launch to complete
        if (wasHidden || wasLauncherHidden) {
            mHandler.postDelayed(() -> {
                if (wasHidden) setPackageHidden(packageName, true);
                if (wasLauncherHidden) setPackageLauncherHidden(packageName, true);
            }, 2000);
        }
    }

    private void loadAppScopeModes(JSONObject config) {
        JSONObject obj = config.optJSONObject(KEY_APP_SCOPE_MODES);
        if (obj == null) return;
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            int mode = obj.optInt(pkg, android.app.ObscuraManager.SCOPE_MODE_DISABLED);
            if (mode != android.app.ObscuraManager.SCOPE_MODE_DISABLED) {
                mAppScopeModes.put(pkg, mode);
            }
        }
    }

    private void saveAppScopeModes(JSONObject config) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Integer> entry : mAppScopeModes.entrySet()) {
            if (entry.getValue() != android.app.ObscuraManager.SCOPE_MODE_DISABLED) {
                obj.put(entry.getKey(), entry.getValue());
            }
        }
        config.put(KEY_APP_SCOPE_MODES, obj);
    }

    private void loadAppScopeLists(JSONObject config) {
        JSONObject obj = config.optJSONObject(KEY_APP_SCOPE_LISTS);
        if (obj == null) return;
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            JSONArray arr = obj.optJSONArray(pkg);
            if (arr != null && arr.length() > 0) {
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    String target = arr.optString(i);
                    if (!TextUtils.isEmpty(target)) {
                        set.add(target);
                    }
                }
                if (!set.isEmpty()) {
                    mAppScopeLists.put(pkg, set);
                }
            }
        }
    }

    private void saveAppScopeLists(JSONObject config) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : mAppScopeLists.entrySet()) {
            obj.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        config.put(KEY_APP_SCOPE_LISTS, obj);
    }

    public int getAppScopeMode(String packageName) {
        if (TextUtils.isEmpty(packageName)) return android.app.ObscuraManager.SCOPE_MODE_DISABLED;
        synchronized (this) {
            return mAppScopeModes.getOrDefault(packageName, android.app.ObscuraManager.SCOPE_MODE_DISABLED);
        }
    }

    public void setAppScopeMode(String packageName, int mode) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            boolean changed;
            if (mode == android.app.ObscuraManager.SCOPE_MODE_DISABLED) {
                changed = (mAppScopeModes.remove(packageName) != null);
            } else {
                Integer old = mAppScopeModes.put(packageName, mode);
                changed = (old == null || old != mode);
            }
            if (changed) saveConfigToSettings();
        }
    }

    public List<String> getAppScopeList(String packageName) {
        if (TextUtils.isEmpty(packageName)) return java.util.Collections.emptyList();
        synchronized (this) {
            Set<String> set = mAppScopeLists.get(packageName);
            if (set == null || set.isEmpty()) return java.util.Collections.emptyList();
            return new ArrayList<>(set);
        }
    }

    public void setAppScopeList(String packageName, List<String> packages) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            if (packages == null || packages.isEmpty()) {
                mAppScopeLists.remove(packageName);
            } else {
                Set<String> set = new HashSet<>();
                for (String p : packages) {
                    if (!TextUtils.isEmpty(p) && !mBlacklistedPackages.contains(p)) {
                        set.add(p);
                    }
                }
                mAppScopeLists.put(packageName, set);
            }
            saveConfigToSettings();
        }
    }

    public boolean shouldHidePackageFromCaller(String callerPackage, String targetPackage) {
        if (!mHasActiveHidingRules) return false;
        if (TextUtils.isEmpty(targetPackage) || TextUtils.isEmpty(callerPackage)) return false;
        if (callerPackage.equals(targetPackage)) return false;
        if (mBlacklistedPackages.contains(targetPackage)) return false;
        if (mBlacklistedPackages.contains(callerPackage)) return false;

        synchronized (this) {
            // 1. Check Global Hide Mode
            if (mHiddenPackages.contains(targetPackage)) {
                return true;
            }

            // 2. Check Caller's Per-App Scope Mode
            int scopeMode = mAppScopeModes.getOrDefault(callerPackage, android.app.ObscuraManager.SCOPE_MODE_DISABLED);
            if (scopeMode == android.app.ObscuraManager.SCOPE_MODE_BLACKLIST) {
                Set<String> scopeList = mAppScopeLists.get(callerPackage);
                if (scopeList != null && scopeList.contains(targetPackage)) {
                    return true;
                }
            } else if (scopeMode == android.app.ObscuraManager.SCOPE_MODE_WHITELIST) {
                Set<String> scopeList = mAppScopeLists.get(callerPackage);
                if (scopeList == null || !scopeList.contains(targetPackage)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void cleanupPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) return;
        synchronized (this) {
            boolean changed = mHiddenPackages.remove(packageName)
                    | mLauncherHiddenPackages.remove(packageName)
                    | mDetachedPackages.remove(packageName)
                    | mIsolatedPackages.remove(packageName)
                    | (mGidRestrictions.remove(packageName) != null)
                    | (mSpoofSettingsMap.remove(packageName) != null)
                    | mDataIsolationPackages.remove(packageName)
                    | (mAppScopeModes.remove(packageName) != null)
                    | (mAppScopeLists.remove(packageName) != null);

            for (Set<String> list : mAppScopeLists.values()) {
                if (list.remove(packageName)) {
                    changed = true;
                }
            }

            if (changed) {
                saveConfigToSettings();
                Slog.i(TAG, "Cleaned up entries for uninstalled package: " + packageName);
            }
        }
    }
}
