/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final Boolean sDisableGmsProps = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_props", false);

    private static final Boolean sDisableKeyAttestationBlock = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_key_attestation_block", false);
    private static final String DATA_FILE = "gms_certified_props.json";

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final String FEATURE_NEXUS_PRELOAD =
            "com.google.android.apps.photos.NEXUS_PRELOAD";

    private static final Map<String, String> sPixelXLProps = Map.of(
        "PRODUCT", "marlin",
        "DEVICE", "marlin",
        "MANUFACTURER", "Google",
        "BRAND", "google",
        "HARDWARE", "marlin",
        "MODEL", "Pixel XL",
        "ID", "QP1A.191005.007.A3",
        "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sPixelFeatures = Set.of(
        "PIXEL_2017_EXPERIENCE",
        "PIXEL_2017_PRELOAD",
        "PIXEL_2018_EXPERIENCE",
        "PIXEL_2018_PRELOAD",
        "PIXEL_2019_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_PRELOAD",
        "PIXEL_2019_PRELOAD",
        "PIXEL_2020_EXPERIENCE",
        "PIXEL_2020_MIDYEAR_EXPERIENCE",
        "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
        "PIXEL_2021_EXPERIENCE",
        "PIXEL_2022_EXPERIENCE",
        "PIXEL_2022_MIDYEAR_EXPERIENCE",
        "PIXEL_2023_EXPERIENCE",
        "PIXEL_2023_MIDYEAR_EXPERIENCE",
        "PIXEL_2024_EXPERIENCE",
        "PIXEL_2024_MIDYEAR_EXPERIENCE",
        "PIXEL_2025_EXPERIENCE",
        "PIXEL_2025_MIDYEAR_EXPERIENCE",
        "PIXEL_2026_EXPERIENCE"
    );

    private static final Map<String, String> sRecentPixelProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "DEVICE", "mustang",
        "PRODUCT", "mustang",
        "HARDWARE", "mustang",
        "MODEL", "Pixel 10 Pro XL",
        "ID", "CP1A.260405.005",
        "FINGERPRINT", "google/mustang/mustang:16/CP1A.260405.005/15001963:user/release-keys"
    );

    private static final Set<String> sRecentPixelPackages = Set.of(
        "com.google.android.aicore",
        "com.google.android.apps.aiwallpapers",
        "com.google.android.apps.bard",
        "com.google.android.apps.customization.pixel",
        "com.google.android.apps.emojiwallpaper",
        "com.google.android.apps.pixel.agent",
        "com.google.android.apps.pixel.creativeassistant",
        "com.google.android.apps.pixel.nowplaying",
        "com.google.android.apps.wallpaper",
        "com.google.android.apps.wallpaper.pixel",
        "com.google.android.apps.weather",
        "com.google.android.googlequicksearchbox",
        "com.google.android.wallpaper.effects",
        "com.google.pixel.livewallpaper"
    );

    private static volatile List<String> sCertifiedProps = new ArrayList<>();
    private static volatile String sStockFp, sNetflixModel;

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsFinsky, sIsPhotos, sIsRecentPixel;
    private static volatile boolean sForceTensor;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sStockFp = res.getString(R.string.config_stockFingerprint);
        sNetflixModel = res.getString(R.string.config_netflixSpoofModel);

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);
        sIsRecentPixel = sRecentPixelPackages.contains(packageName);

        try {
            sForceTensor = !Process.isIsolated() && Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.PI_TENSOR_SPOOF, 0) == 1;
        } catch (Exception e) {
            sForceTensor = false;
        }

        /* Set Certified Properties for GMSCore
         * Set Stock Fingerprint for ARCore
         * Set custom model for Netflix
         * Set Pixel XL for Google Photos
         * Set Recent Pixel for Tensor spoofing
         */
        if (sIsGms || sIsFinsky) {
            if (!android.os.Process.isIsolated()) {
                setPlayIntegrityProps(context);
            } else {
                dlog("Not setting Play Integrity props in isolated process");
            }
        } else if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
        } else if (sIsPhotos) {
            dlog("Spoofing Pixel XL for Google Photos");
            sPixelXLProps.forEach((PropImitationHooks::setPropValue));
        } else if (sForceTensor && sIsRecentPixel) {
            dlog("Spoofing Recent Pixel for: " + packageName);
            sRecentPixelProps.forEach((PropImitationHooks::setPropValue));
        } else if (!sNetflixModel.isEmpty() && packageName.equals(PACKAGE_NETFLIX)) {
            dlog("Setting model to " + sNetflixModel + " for Netflix");
            setPropValue("MODEL", sNetflixModel);
        }
    }

    private static void setPropValue(String key, String value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);
            // Cast the value to int if it's an integer field, otherwise string.
            field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setPlayIntegrityProps(Context context) {
        if (sDisableGmsProps) {
            dlog("GMS prop imitation is disabled by user");
            return;
        }

        // Guard: isolated processes cannot access content providers (Settings.*).
        if (android.os.Process.isIsolated()) {
            dlog("Skipping setPlayIntegrityProps in isolated process");
            return;
        }

        String savedProps = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.PIF_DATA);
        if (savedProps == null || TextUtils.isEmpty(savedProps)) {
            savedProps = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.FETCHED_PIF);
        }

        List<String> props = new ArrayList<>();
        if (savedProps == null || TextUtils.isEmpty(savedProps)) {
            dlog("Parsing props locally - fetched pif / user provided pif unavailable");
            props.addAll(Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties)));
        } else {
            dlog("Parsing props fetched / provided by user");
            try {
                JSONObject parsedProps = new JSONObject(savedProps);
                Iterator<String> keys = parsedProps.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = parsedProps.getString(key);
                    props.add(key + ":" + value);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON data", e);
                dlog("Parsing props locally as fallback");
                props.clear();
                props.addAll(Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties)));
            }
        }
        sCertifiedProps = props;

        if (sCertifiedProps.isEmpty()) {
            dlog("Certified props are not set");
            return;
        }

        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };

        if (!was) {
            dlog("Spoofing build for GMS / Finsky");
            setCertifiedProps();
        } else {
            dlog("Skip spoofing build for GMS / Finsky, because GmsAddAccountActivityOnTop");
        }

        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String entry : sCertifiedProps) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in certified props: " + entry);
                continue;
            }
            setPropValue(fieldAndProp[0], fieldAndProp[1]);
        }
    }

    private static String readFromFile(File file) {
        StringBuilder content = new StringBuilder();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from file", e);
            }
        }
        return content.toString();
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        if (sDisableGmsProps) {
            return false;
        }

        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        if (sDisableKeyAttestationBlock) {
            dlog("Key attestation blocking is disabled by user");
            return;
        }

        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && name.equalsIgnoreCase(FEATURE_NEXUS_PRELOAD)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        if (!has && (sIsRecentPixel || sIsGms)) {
            if (sForceTensor && sTensorFeatures.stream().anyMatch(name::contains)) {
                dlog("Enabled Tensor system feature " + name + " for: " + sProcessName);
                has = true;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
