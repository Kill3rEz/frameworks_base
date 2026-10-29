/*
 * Copyright (C) 2020 The LineageOS Project
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

package com.android.systemui.qs.tiles;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.service.quicksettings.Tile;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.BatteryController;

import javax.inject.Inject;

import vendor.aospa.powershare.IPowerShare;

/** Quick settings tile: Battery Share (reverse wireless charging) **/
public class PowerShareTile extends QSTileImpl<BooleanState>
        implements BatteryController.BatteryStateChangeCallback {

    public static final String TILE_SPEC = "powershare";

    private static final String TAG = "PowerShareTile";
    private static final String POWERSHARE_SERVICE_NAME =
            "vendor.aospa.powershare.IPowerShare/default";
    private static final String CHANNEL_ID = "powershare";
    private static final int NOTIFICATION_ID = 273298;

    private final BatteryController mBatteryController;
    private final boolean mIsDeclared;

    @Nullable
    private IPowerShare mPowerShare;
    @Nullable
    private NotificationManager mNotificationManager;
    @Nullable
    private Notification mNotification;
    @Nullable
    private Icon mIcon = null;

    private volatile int mBatteryLevel;
    private int mMinBatteryLevel = -1;

    @Inject
    public PowerShareTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BatteryController batteryController
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mBatteryController = batteryController;
        mIsDeclared = ServiceManager.isDeclared(POWERSHARE_SERVICE_NAME);
        if (!mIsDeclared) {
            return;
        }

        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,
                mContext.getString(R.string.quick_settings_powershare_label),
                NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(notificationChannel);

        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL_ID);
        builder.setContentTitle(
                mContext.getString(R.string.quick_settings_powershare_enabled_label));
        builder.setSmallIcon(R.drawable.ic_qs_powershare);
        builder.setOnlyAlertOnce(true);
        mNotification = builder.build();
        mNotification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        mNotification.visibility = Notification.VISIBILITY_PUBLIC;

        BatteryManager batteryManager = mContext.getSystemService(BatteryManager.class);
        mBatteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        // Not tied to the tile lifecycle: Battery Share has to be turned off on low battery
        // or battery saver even while the QS panel is closed.
        mBatteryController.addCallback(this);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        if (!mIsDeclared) {
            return;
        }
        mBatteryController.removeCallback(this);
        // Nothing watches the battery level once the tile is gone.
        if (isPowerShareEnabled()) {
            setPowerShareEnabled(false);
        }
        mNotificationManager.cancelAsUser(null, NOTIFICATION_ID, UserHandle.ALL);
    }

    @Override
    public boolean isAvailable() {
        return mIsDeclared;
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        mBatteryLevel = level;
        mHandler.post(this::checkAutoOff);
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mHandler.post(this::checkAutoOff);
    }

    private void checkAutoOff() {
        if (isPowerShareEnabled()
                && (mBatteryController.isPowerSave() || isBatteryTooLow())) {
            setPowerShareEnabled(false);
        }
        refreshState();
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        final boolean enabled = isPowerShareEnabled();
        if (!enabled && (mBatteryController.isPowerSave() || isBatteryTooLow())) {
            return;
        }
        setPowerShareEnabled(!enabled);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_powershare_label);
    }

    // The off strings read "Battery Share off\n<reason>" (also in the AOSPA translations);
    // the tile already shows the label, so only the reason goes in the secondary label.
    private CharSequence getOffReason(int resId) {
        final String text = mContext.getString(resId);
        final int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(newline + 1) : text;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (!mIsDeclared) {
            return;
        }

        if (mIcon == null) {
            mIcon = maybeLoadResourceIcon(R.drawable.ic_qs_powershare);
        }
        state.icon = mIcon;
        state.value = isPowerShareEnabled();
        state.label = mContext.getString(R.string.quick_settings_powershare_label);
        state.contentDescription = state.label;

        if (mBatteryController.isPowerSave()) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel =
                    getOffReason(R.string.quick_settings_powershare_off_powersave_label);
        } else if (isBatteryTooLow()) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel =
                    getOffReason(R.string.quick_settings_powershare_off_low_battery_label);
        } else {
            state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            state.secondaryLabel = null;
        }

        if (state.value) {
            mNotificationManager.notifyAsUser(null, NOTIFICATION_ID, mNotification,
                    UserHandle.ALL);
        } else {
            mNotificationManager.cancelAsUser(null, NOTIFICATION_ID, UserHandle.ALL);
        }
    }

    @Nullable
    private synchronized IPowerShare getPowerShare() {
        if (mPowerShare != null && mPowerShare.asBinder().isBinderAlive()) {
            return mPowerShare;
        }
        IBinder binder = ServiceManager.getService(POWERSHARE_SERVICE_NAME);
        mPowerShare = binder != null ? IPowerShare.Stub.asInterface(binder) : null;
        return mPowerShare;
    }

    private boolean isPowerShareEnabled() {
        IPowerShare powerShare = getPowerShare();
        if (powerShare == null) {
            return false;
        }
        try {
            return powerShare.isEnabled();
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to read the Battery Share state", e);
        }
        return false;
    }

    private void setPowerShareEnabled(boolean enabled) {
        IPowerShare powerShare = getPowerShare();
        if (powerShare == null) {
            return;
        }
        try {
            powerShare.setEnabled(enabled);
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to set the Battery Share state", e);
        }
    }

    private boolean isBatteryTooLow() {
        return mBatteryLevel < getMinBatteryLevel();
    }

    private synchronized int getMinBatteryLevel() {
        if (mMinBatteryLevel >= 0) {
            return mMinBatteryLevel;
        }
        IPowerShare powerShare = getPowerShare();
        if (powerShare == null) {
            return 0;
        }
        try {
            mMinBatteryLevel = powerShare.getMinBattery();
            return mMinBatteryLevel;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to read the Battery Share minimum battery level", e);
        }
        return 0;
    }
}
