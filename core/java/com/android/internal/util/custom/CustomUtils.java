package com.android.internal.util.custom;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.IWindowManager;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.hardware.input.InputManager;
import android.widget.Toast;


import com.android.internal.R;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.ScreenshotHelper;
import com.android.internal.util.ScreenshotRequest;

import java.util.ArrayList;
import java.util.List;

public class CustomUtils {

    private static final String TAG = "Utils";

    public static void launchVoiceSearch(Context context) {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (Exception e) {
            SearchManager searchManager = context.getSystemService(SearchManager.class);
            if (searchManager != null) {
                searchManager.launchAssist(null);
            }
        }
    }

    public static void launchCamera(Context context) {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (Exception e) {
            Log.w(TAG, "Unable to launch camera", e);
        }
    }

    public static void toggleCameraFlash() {
        try {
            getStatusBarService().toggleCameraFlash();
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to toggle flashlight", e);
        }
    }

    public static void toggleVolumePanel(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        if (audioManager != null) {
            audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
        }
    }

    public static void switchScreenOff(Context context) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager != null) {
            powerManager.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static void takeScreenshot(Context context) {
        ScreenshotRequest request = new ScreenshotRequest.Builder(
                WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                WindowManager.ScreenshotSource.SCREENSHOT_OTHER)
                .setDisplayId(context.getDisplayId())
                .build();
        new ScreenshotHelper(context).takeScreenshot(request, new Handler(Looper.getMainLooper()),
                null);
    }

    public static void toggleNotifications(Context context) {
        StatusBarManager statusBarManager = context.getSystemService(StatusBarManager.class);
        if (statusBarManager != null) {
            statusBarManager.expandNotificationsPanel();
        }
    }

    public static void toggleQsPanel(Context context) {
        StatusBarManager statusBarManager = context.getSystemService(StatusBarManager.class);
        if (statusBarManager != null) {
            statusBarManager.expandSettingsPanel();
        }
    }

    public static void clearAllNotifications() {
        try {
            getStatusBarService().onClearAllNotifications(UserHandle.USER_CURRENT);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to clear notifications", e);
        }
    }

    public static void toggleRingerModes(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        if (audioManager == null) {
            return;
        }
        int nextMode = audioManager.getRingerModeInternal() == AudioManager.RINGER_MODE_NORMAL
                ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
        audioManager.setRingerModeInternal(nextMode);
    }

    public static void killForegroundApp(Context context) {
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            if (am == null) return;
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return;
            String packageName = tasks.get(0).topActivity.getPackageName();
            // Don't kill ourselves
            if (context.getPackageName().equals(packageName)) return;
            ActivityManager.getService().forceStopPackage(packageName, UserHandle.USER_CURRENT);
        } catch (Exception e) {
            Log.w(TAG, "Unable to kill foreground app", e);
        }
    }

    public static void switchToLastApp(Context context) {
        try {
            getStatusBarService().toggleRecentApps();
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to switch recent app", e);
        }
    }

    public static void showPowerMenu() {
        try {
            WindowManagerGlobal.getWindowManagerService().showGlobalActions();
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to show power menu", e);
        }
    }

    public static void sendKeycode(Context context, int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final InputManager inputManager = context.getSystemService(InputManager.class);
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                inputManager.injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                inputManager.injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }


    private static IStatusBarService getStatusBarService() {
        return IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    public static void restartApp(String appName, Context context) {
        new RestartAppTask(appName, context).execute();
    }

    private static class RestartAppTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;
        private String mApp;

        public RestartAppTask(String appName, Context context) {
            super();
            mContext = context;
            mApp = appName;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if (mApp.equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static boolean isPackageInstalled(Context context, String packageName, boolean ignoreState) {
        if (packageName != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return isPackageInstalled(context, packageName, true);
    }

    public static List<String> launchablePackages(Context context) {
        List<String> list = new ArrayList<>();

        Intent filter = new Intent(Intent.ACTION_MAIN, null);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(filter,
                PackageManager.GET_META_DATA);

        int numPackages = apps.size();
        for (int i = 0; i < numPackages; i++) {
            ResolveInfo app = apps.get(i);
            list.add(app.activityInfo.packageName);
        }

        return list;
    }

    public static String getDefaultLauncher(Context context) {
        final RoleManager roleManager = context.getSystemService(RoleManager.class);
        if (roleManager == null) {
            return "";
        }
        final List<String> roleHolders = roleManager.getRoleHolders(RoleManager.ROLE_HOME);
        return roleHolders.isEmpty() ? "" : roleHolders.get(0);
    }

    public static void forceStopDefaultLauncher(Context context) {
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return;
        }
        try {
            activityManager.forceStopPackageAsUser(
                    getDefaultLauncher(context), UserHandle.USER_CURRENT);
        } catch (Exception ignored) {
        }
    }
    public static void toggleOverlay(Context context, String overlayName, boolean enable) {
        OverlayManager overlayManager = context.getSystemService(OverlayManager.class);
        if (overlayManager == null) {
            Log.e(TAG, "OverlayManager is not available");
            return;
        }

        OverlayIdentifier overlayId = getOverlayID(overlayManager, overlayName);
        if (overlayId == null) {
            Log.e(TAG, "Overlay ID not found for " + overlayName);
            return;
        }

        OverlayManagerTransaction.Builder transaction = new OverlayManagerTransaction.Builder();
        transaction.setEnabled(overlayId, enable, UserHandle.USER_CURRENT);

        try {
            overlayManager.commit(transaction.build());
        } catch (Exception e) {
            Log.e(TAG, "Error toggling overlay", e);
        }
    }

    private static OverlayIdentifier getOverlayID(OverlayManager overlayManager, String name) {
        try {
            if (name.contains(":")) {
                String[] parts = name.split(":");
                List<OverlayInfo> infos = overlayManager.getOverlayInfosForTarget(parts[0], UserHandle.CURRENT);
                for (OverlayInfo info : infos) {
                    if (parts[1].equals(info.getOverlayName())) return info.getOverlayIdentifier();
                }
            } else {
                OverlayInfo info = overlayManager.getOverlayInfo(name, UserHandle.CURRENT);
                if (info != null) return info.getOverlayIdentifier();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving overlay ID", e);
        }
        return null;
    }

    public static void restartSystemUI() {
        try {
            getStatusBarService().restartSystemUI();
        } catch (RemoteException e) {
        }
    }
}
