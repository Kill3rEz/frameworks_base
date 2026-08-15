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

import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
import static android.provider.Settings.Global.ZEN_MODE_OFF;

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

    public static class SleepModeController {
        private final Resources mResources;
        private final Context mUiContext;

        private Context mContext;
        private AudioManager mAudioManager;
        private NotificationManager mNotificationManager;
        private WifiManager mWifiManager;
        private SensorPrivacyManager mSensorPrivacyManager;
        private BluetoothAdapter mBluetoothAdapter;
        private int mSubscriptionId;
        private Toast mToast;

        private boolean mSleepModeEnabled;

        private static boolean mWifiState;
        private static boolean mCellularState;
        private static boolean mBluetoothState;
        private static int mRingerState;
        private static int mZenState;

        private static final String TAG = "SleepModeController";
        private static final int SLEEP_NOTIFICATION_ID = 727;
        public static final String SLEEP_MODE_TURN_OFF = "android.intent.action.SLEEP_MODE_TURN_OFF";

        public SleepModeController(Context context) {
            mContext = context;
            mUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            mResources = mContext.getResources();

            mSleepModeEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;

            SettingsObserver observer = new SettingsObserver(new Handler(Looper.getMainLooper()));
            observer.observe();
            observer.update();
        }

        private TelephonyManager getTelephonyManager() {
            int subscriptionId = mSubscriptionId;

            // If mSubscriptionId is invalid, get default data sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
            }

            // If data sub is also invalid, get any active sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                int[] activeSubIds = SubscriptionManager.from(mContext).getActiveSubscriptionIdList();
                if (!ArrayUtils.isEmpty(activeSubIds)) {
                    subscriptionId = activeSubIds[0];
                }
            }

            return mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subscriptionId);
        }

        private boolean isWifiEnabled() {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                return mWifiManager.isWifiEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setWifiEnabled(boolean enable) {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                mWifiManager.setWifiEnabled(enable);
            } catch (Exception e) {
            }
        }

        private boolean isBluetoothEnabled() {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                return mBluetoothAdapter.isEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setBluetoothEnabled(boolean enable) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                if (enable) mBluetoothAdapter.enable();
                else mBluetoothAdapter.disable();
            } catch (Exception e) {
            }
        }

        private boolean isSensorEnabled() {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                return !mSensorPrivacyManager.isAllSensorPrivacyEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setSensorEnabled(boolean enable) {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                mSensorPrivacyManager.setAllSensorPrivacy(!enable);
            } catch (Exception e) {
            }
        }

        private int getZenMode() {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                return mNotificationManager.getZenMode();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setZenMode(int mode) {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                mNotificationManager.setZenMode(mode, null, TAG);
            } catch (Exception e) {
            }
        }

        private int getRingerModeInternal() {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                return mAudioManager.getRingerModeInternal();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setRingerModeInternal(int mode) {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                mAudioManager.setRingerModeInternal(mode);
            } catch (Exception e) {
            }
        }

        private void enable() {
            if (!ActivityManager.isSystemReady()) return;

            // Disable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi) {
                mWifiState = isWifiEnabled();
                setWifiEnabled(false);
            }

            // Disable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth) {
                mBluetoothState = isBluetoothEnabled();
                setBluetoothEnabled(false);
            }

            // Disable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData) {
                mCellularState = getTelephonyManager().isDataEnabled();
                getTelephonyManager().setDataEnabled(false);
            }

            // Disable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors) {
                setSensorEnabled(false);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0) {
                mRingerState = getRingerModeInternal();
                mZenState = getZenMode();
                if (ringerMode == 1) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                    setZenMode(ZEN_MODE_OFF);
                } else if (ringerMode == 2) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                    setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                } else if (ringerMode == 3) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                    setZenMode(ZEN_MODE_OFF);
                }
            }

            showToast(mResources.getString(R.string.sleep_mode_enabled_toast), Toast.LENGTH_LONG);
            addNotification();
        }

        private void disable() {
            if (!ActivityManager.isSystemReady()) return;

            // Enable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi && mWifiState != isWifiEnabled()) {
                setWifiEnabled(mWifiState);
            }

            // Enable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth && mBluetoothState != isBluetoothEnabled()) {
                setBluetoothEnabled(mBluetoothState);
            }

            // Enable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData && mCellularState != getTelephonyManager().isDataEnabled()) {
                getTelephonyManager().setDataEnabled(mCellularState);
            }

            // Enable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors) {
                setSensorEnabled(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                if (!isSensorEnabled()) {
                    setSensorEnabled(true);
                }
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0 && (mRingerState != getRingerModeInternal() ||
                    mZenState != getZenMode())) {
                setRingerModeInternal(mRingerState);
                setZenMode(mZenState);
            }

            showToast(mResources.getString(R.string.sleep_mode_disabled_toast), Toast.LENGTH_LONG);
            mNotificationManager.cancel(SLEEP_NOTIFICATION_ID);
        }

        private void addNotification() {
            Intent intent = new Intent(SLEEP_MODE_TURN_OFF);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Display a notification
            Notification.Builder builder = new Notification.Builder(mContext, SystemNotificationChannels.SLEEP)
                .setTicker(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentTitle(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentText(mResources.getString(R.string.sleep_mode_notification_content))
                .setSmallIcon(R.drawable.ic_sleep)
                .setWhen(java.lang.System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

            Notification notification = builder.build();
            mNotificationManager.notify(SLEEP_NOTIFICATION_ID, notification);
        }

        private void showToast(String msg, int duration) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mToast != null) mToast.cancel();
                        mToast = Toast.makeText(mUiContext, msg, duration);
                        mToast.show();
                    } catch (Exception e) {
                    }
                }
            });
        }

        private void setSleepMode(boolean enabled) {
            if (mSleepModeEnabled == enabled) {
                return;
            }

            mSleepModeEnabled = enabled;

            if (mSleepModeEnabled) {
                enable();
            } else {
                disable();
            }
        }

        class SettingsObserver extends ContentObserver {
            SettingsObserver(Handler handler) {
                super(handler);
            }

            void observe() {
                ContentResolver resolver = mContext.getContentResolver();
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                        Settings.Secure.SLEEP_MODE_ENABLED), false, this,
                        UserHandle.USER_ALL);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                update();
            }

            void update() {
                final boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
                setSleepMode(enabled);
            }
        }
    }
}