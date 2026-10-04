/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.edgelighting;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.util.Log;
import android.view.WindowManager;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;

import java.io.InputStream;
import java.util.concurrent.Executor;

import javax.inject.Inject;

@SysUISingleton
public class EdgeLightingController {

    private static final String TAG = "EdgeLighting";
    private static final String AUTHORITY = "co.aospa.edgelighting.provider";
    private static final Uri STATE_URI = Uri.parse("content://" + AUTHORITY + "/state");
    private static final Uri IMAGE_URI = Uri.parse("content://" + AUTHORITY + "/image");

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Executor mMainExecutor;
    private final Executor mBgExecutor;

    private Bitmap mCutout;
    private long mCutoutVersion;
    private EdgeLightingView mView;
    private boolean mPulsing;

    @Inject
    public EdgeLightingController(Context context, @Main Executor mainExecutor,
            @Background Executor bgExecutor) {
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        mMainExecutor = mainExecutor;
        mBgExecutor = bgExecutor;
    }

    public void onNotificationPulseStarted() {
        mPulsing = true;
        mBgExecutor.execute(() -> {
            Bitmap cutout = loadCutout();
            mMainExecutor.execute(() -> {
                if (mPulsing && cutout != null) show(cutout);
            });
        });
    }

    public void onPulseFinished() {
        mPulsing = false;
        if (mView != null) mView.finish();
    }

    private Bitmap loadCutout() {
        long version;
        try (Cursor cursor = mContext.getContentResolver().query(
                STATE_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() || cursor.getInt(0) == 0) return null;
            version = cursor.getLong(1);
        } catch (RuntimeException e) {
            return null;
        }
        synchronized (this) {
            if (mCutout != null && version == mCutoutVersion) return mCutout;
        }
        try (InputStream is = mContext.getContentResolver().openInputStream(IMAGE_URI)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            synchronized (this) {
                mCutout = bitmap;
                mCutoutVersion = version;
            }
            return bitmap;
        } catch (Exception e) {
            Log.w(TAG, "Couldn't load the cutout", e);
            return null;
        }
    }

    private void show(Bitmap cutout) {
        if (mView != null) return;
        mView = new EdgeLightingView(mContext, cutout, this::remove);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setTitle("EdgeLighting");
        mWindowManager.addView(mView, lp);
    }

    private void remove() {
        if (mView == null) return;
        mWindowManager.removeViewImmediate(mView);
        mView = null;
    }
}
