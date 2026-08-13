/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.policy;

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.BACK_GESTURE_EDGE_WIDTH;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.android.internal.util.custom.CustomUtils;

/**
 * @hide
 */
public class GestureNavigationSettingsObserver extends ContentObserver {
    private Context mContext;
    private Runnable mOnChangeRunnable;
    private Handler mMainHandler;
    private Handler mBgHandler;
    private IntentFilter mIntentFilter;

    public GestureNavigationSettingsObserver(
            Handler mainHandler, Handler bgHandler, Context context, Runnable onChangeRunnable) {
        super(mainHandler);
        mMainHandler = mainHandler;
        mBgHandler = bgHandler;
        mContext = context;
        mOnChangeRunnable = onChangeRunnable;
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mIntentFilter.addDataScheme("package");
    }

    private final DeviceConfig.OnPropertiesChangedListener mOnPropertiesChangedListener =
            new DeviceConfig.OnPropertiesChangedListener() {
        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            if (DeviceConfig.NAMESPACE_SYSTEMUI.equals(properties.getNamespace())
                            && mOnChangeRunnable != null) {
                mOnChangeRunnable.run();
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    || intent.getData() == null) {
                return;
            }
            String packageName = intent.getData().getSchemeSpecificPart();
            if (CustomUtils.isPackageInstalled(context, packageName)) {
                return;
            }
            ContentResolver resolver = context.getContentResolver();
            resetApplicationActionIfNeeded(resolver, packageName,
                    Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTION,
                    Settings.System.LEFT_LONG_BACK_SWIPE_ACTION,
                    Settings.System.LEFT_LONG_BACK_SWIPE_APP_FR_ACTION);
            resetApplicationActionIfNeeded(resolver, packageName,
                    Settings.System.RIGHT_LONG_BACK_SWIPE_APP_ACTION,
                    Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION,
                    Settings.System.RIGHT_LONG_BACK_SWIPE_APP_FR_ACTION);
            resetApplicationActionIfNeeded(resolver, packageName,
                    Settings.System.LEFT_VERTICAL_BACK_SWIPE_APP_ACTION,
                    Settings.System.LEFT_VERTICAL_BACK_SWIPE_ACTION,
                    Settings.System.LEFT_VERTICAL_BACK_SWIPE_APP_FR_ACTION);
            resetApplicationActionIfNeeded(resolver, packageName,
                    Settings.System.RIGHT_VERTICAL_BACK_SWIPE_APP_ACTION,
                    Settings.System.RIGHT_VERTICAL_BACK_SWIPE_ACTION,
                    Settings.System.RIGHT_VERTICAL_BACK_SWIPE_APP_FR_ACTION);
        }
    };

    private void resetApplicationActionIfNeeded(ContentResolver resolver, String removedPackage,
            String packageSetting, String actionSetting, String friendlyNameSetting) {
        String configuredPackage = Settings.System.getStringForUser(resolver, packageSetting,
                UserHandle.USER_CURRENT);
        if (!removedPackage.equals(configuredPackage)) {
            return;
        }
        Settings.System.putIntForUser(resolver, actionSetting, 0, UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(resolver, friendlyNameSetting, "",
                UserHandle.USER_CURRENT);
    }

    /**
     * Registers the observer for all users.
     */
    public void register() {
        mBgHandler.post(() -> {
            ContentResolver r = mContext.getContentResolver();
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT),
                    false, this, UserHandle.USER_ALL);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT),
                    false, this, UserHandle.USER_ALL);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_ARROW),
                    false, this, UserHandle.USER_ALL);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_HAPTIC),
                    false, this, UserHandle.USER_ALL);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE),
                    false, this, UserHandle.USER_ALL);
            registerAdvancedGestureObservers(r, UserHandle.USER_ALL);
            mContext.registerReceiver(mBroadcastReceiver, mIntentFilter);
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_SYSTEMUI,
                    runnable -> mMainHandler.post(runnable),
                    mOnPropertiesChangedListener);
        });
    }

    /**
     * Registers the observer for the calling user.
     */
    public void registerForCallingUser() {
        mBgHandler.post(() -> {
            ContentResolver r = mContext.getContentResolver();
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT),
                    false, this);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT),
                    false, this);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_ARROW),
                    false, this);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BACK_GESTURE_HAPTIC),
                    false, this);
            r.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE),
                    false, this);
            registerAdvancedGestureObservers(r, UserHandle.myUserId());
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_SYSTEMUI,
                    runnable -> mMainHandler.post(runnable),
                    mOnPropertiesChangedListener);
        });
    }

    public void unregister() {
        mBgHandler.post(() -> {
            mContext.getContentResolver().unregisterContentObserver(this);
            try {
                mContext.unregisterReceiver(mBroadcastReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            DeviceConfig.removeOnPropertiesChangedListener(mOnPropertiesChangedListener);
        });
    }

    private void registerAdvancedGestureObservers(ContentResolver resolver, int userHandle) {
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.LEFT_LONG_BACK_SWIPE_ACTION),
                false, this, userHandle);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION),
                false, this, userHandle);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.BACK_SWIPE_EXTENDED),
                false, this, userHandle);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.LEFT_VERTICAL_BACK_SWIPE_ACTION),
                false, this, userHandle);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.RIGHT_VERTICAL_BACK_SWIPE_ACTION),
                false, this, userHandle);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (mOnChangeRunnable != null) {
            mOnChangeRunnable.run();
        }
    }

    /**
     * Returns the left sensitivity for the current user.  To be used in code that runs primarily
     * in one user's process.
     */
    public int getLeftSensitivity(Resources userRes) {
        final float scale = Settings.Secure.getFloatForUser(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT, 1.0f, UserHandle.USER_CURRENT);
        return (int) (getUnscaledInset(userRes) * scale);
    }

    /**
     * Returns the left sensitivity for the calling user.  To be used in code that runs in a
     * per-user process.
     */
    @SuppressWarnings("NonUserGetterCalled")
    public int getLeftSensitivityForCallingUser(Resources userRes) {
        final float scale = Settings.Secure.getFloat(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT, 1.0f);
        return (int) (getUnscaledInset(userRes) * scale);
    }

    /**
     * Returns the right sensitivity for the current user.  To be used in code that runs primarily
     * in one user's process.
     */
    public int getRightSensitivity(Resources userRes) {
        final float scale = Settings.Secure.getFloatForUser(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT, 1.0f, UserHandle.USER_CURRENT);
        return (int) (getUnscaledInset(userRes) * scale);
    }

    /**
     * Returns the right sensitivity for the calling user.  To be used in code that runs in a
     * per-user process.
     */
    @SuppressWarnings("NonUserGetterCalled")
    public int getRightSensitivityForCallingUser(Resources userRes) {
        final float scale = Settings.Secure.getFloat(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT, 1.0f);
        return (int) (getUnscaledInset(userRes) * scale);
    }

    public boolean getBackArrowGesture() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_ARROW, 1, UserHandle.USER_CURRENT) != 0;
    }

    public boolean getEdgeHapticEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.BACK_GESTURE_HAPTIC, 1, UserHandle.USER_CURRENT) != 0;
    }

    public boolean areNavigationButtonForcedVisible() {
        String SUWTheme = SystemProperties.get("setupwizard.theme", "");
        boolean isExpressiveThemeEnabled = SUWTheme.equals("glif_expressive")
                || SUWTheme.equals("glif_expressive_light");
        // The back gesture is enabled if using the expressive theme
        return !isExpressiveThemeEnabled
                && Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) == 0;
    }

    private float getUnscaledInset(Resources userRes) {
        final DisplayMetrics dm = userRes.getDisplayMetrics();
        final float defaultInset = userRes.getDimension(
                com.android.internal.R.dimen.config_backGestureInset) / dm.density;
        // Only apply the back gesture config if there is an existing inset
        final float backGestureInset = defaultInset > 0
                ? DeviceConfig.getFloat(DeviceConfig.NAMESPACE_SYSTEMUI,
                        BACK_GESTURE_EDGE_WIDTH, defaultInset)
                : defaultInset;
        final float inset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, backGestureInset,
                dm);
        return inset;
    }

    public int getLeftLongSwipeAction() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LEFT_LONG_BACK_SWIPE_ACTION, 0, UserHandle.USER_CURRENT);
    }

    public int getRightLongSwipeAction() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.RIGHT_LONG_BACK_SWIPE_ACTION, 0, UserHandle.USER_CURRENT);
    }

    public boolean getIsExtendedSwipe() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.BACK_SWIPE_EXTENDED, 0, UserHandle.USER_CURRENT) != 0;
    }

    public int getLeftLSwipeAction() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LEFT_VERTICAL_BACK_SWIPE_ACTION, 0, UserHandle.USER_CURRENT);
    }

    public int getRightLSwipeAction() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.RIGHT_VERTICAL_BACK_SWIPE_ACTION, 0, UserHandle.USER_CURRENT);
    }
}
