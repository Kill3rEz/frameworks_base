/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package android.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import com.android.internal.app.IHiddenNotificationListener;
import com.android.internal.app.IObscuraManager;
import com.android.internal.app.HiddenNotificationInfo;

import java.util.Collections;
import java.util.List;

/** @hide */
@SystemService(Context.OBSCURA_SERVICE)
@SuppressWarnings("ReferencesHidden")
public class ObscuraManager {

    public static final String SETTING_OBSCURA_CONFIG = "obscura_config";

    public static final int GID_INET = 3003;
    public static final int GID_SDCARD_RW = 1015;
    public static final int GID_MEDIA_RW = 1023;
    public static final int GID_EXTERNAL_STORAGE = 1077;
    public static final int GID_EXT_DATA_RW = 1078;
    public static final int GID_EXT_OBB_RW = 1079;

    public static final int[] STORAGE_GIDS = {
            GID_SDCARD_RW, GID_MEDIA_RW, GID_EXTERNAL_STORAGE,
            GID_EXT_DATA_RW, GID_EXT_OBB_RW
    };

    private final Context mContext;
    private final IObscuraManager mService;

    public ObscuraManager(@NonNull Context context, @NonNull IObscuraManager service) {
        mContext = context;
        mService = service;
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        try {
            return mService.isPackageHidden(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setPackageHidden(@NonNull String packageName, boolean hidden) {
        try {
            mService.setPackageHidden(packageName, hidden);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isPackageLauncherHidden(@NonNull String packageName) {
        try {
            return mService.isPackageLauncherHidden(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setPackageLauncherHidden(@NonNull String packageName, boolean hidden) {
        try {
            mService.setPackageLauncherHidden(packageName, hidden);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public List<String> getHiddenPackages() {
        try {
            List<String> result = mService.getHiddenPackages();
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public List<String> getLauncherHiddenPackages() {
        try {
            List<String> result = mService.getLauncherHiddenPackages();
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public List<String> getLockablePackages() {
        try {
            List<String> result = mService.getLockablePackages();
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void registerHiddenNotificationListener(IHiddenNotificationListener listener) {
        try {
            mService.registerHiddenNotificationListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unregisterHiddenNotificationListener(IHiddenNotificationListener listener) {
        try {
            mService.unregisterHiddenNotificationListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<HiddenNotificationInfo> getHiddenNotifications() {
        try {
            return mService.getHiddenNotifications();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void onHiddenNotificationPosted(HiddenNotificationInfo info) {
        try {
            mService.onHiddenNotificationPosted(info);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void onHiddenNotificationRemoved(String key) {
        try {
            mService.onHiddenNotificationRemoved(key);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isPackageIsolated(@NonNull String packageName) {
        try {
            return mService.isPackageIsolated(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void isolatePackage(@NonNull String packageName) {
        try {
            mService.isolatePackage(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unisolatePackage(@NonNull String packageName) {
        try {
            mService.unisolatePackage(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public List<String> getIsolatedPackages() {
        try {
            List<String> result = mService.getIsolatedPackages();
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setRestrictedGids(@NonNull String packageName, int[] gids) {
        try {
            mService.setRestrictedGids(packageName, gids);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int[] getRestrictedGids(@NonNull String packageName) {
        try {
            return mService.getRestrictedGids(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isDataIsolationEnabled(@NonNull String packageName) {
        try {
            return mService.isDataIsolationEnabled(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setDataIsolationEnabled(@NonNull String packageName, boolean enabled) {
        try {
            mService.setDataIsolationEnabled(packageName, enabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isSpoofSettingEnabled(@NonNull String packageName, @NonNull String settingKey) {
        try {
            return mService.isSpoofSettingEnabled(packageName, settingKey);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setSpoofSettingEnabled(@NonNull String packageName, @NonNull String settingKey, boolean enabled) {
        try {
            mService.setSpoofSettingEnabled(packageName, settingKey, enabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public List<String> getEnabledSpoofSettings(@NonNull String packageName) {
        try {
            return mService.getEnabledSpoofSettings(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Nullable
    public String getSpoofedSetting(@NonNull String callingPackage, @NonNull String settingName) {
        try {
            return mService.getSpoofedSetting(callingPackage, settingName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
