/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.edgelighting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private static final String AUTHORITY = "com.penguin.edgelighting.provider";
    private static final Uri STATE_URI = Uri.parse("content://" + AUTHORITY + "/state");
    private static final Uri IMAGE_URI = Uri.parse("content://" + AUTHORITY + "/image");
    private static final String PACKAGE = "com.penguin.edgelighting";
    private static final String ACTION_PREVIEW = PACKAGE + ".action.PREVIEW";
    private static final long PREVIEW_MS = 4500;

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
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (PACKAGE.equals(getSentFromPackage())) onPreview();
            }
        }, new IntentFilter(ACTION_PREVIEW), Context.RECEIVER_EXPORTED);
    }

    private void onPreview() {
        mBgExecutor.execute(() -> {
            Bitmap cutout = loadCutout(true);
            mMainExecutor.execute(() -> {
                if (cutout == null || mView != null) return;
                show(cutout);
                mView.postDelayed(() -> {
                    if (mView != null && !mPulsing) mView.finish();
                }, PREVIEW_MS);
            });
        });
    }

    public void onNotificationPulseStarted() {
        mPulsing = true;
        mBgExecutor.execute(() -> {
            Bitmap cutout = loadCutout(false);
            mMainExecutor.execute(() -> {
                if (mPulsing && cutout != null) show(cutout);
            });
        });
    }

    public void onPulseFinished() {
        mPulsing = false;
        if (mView != null) mView.finish();
    }

    private Bitmap loadCutout(boolean force) {
        long version;
        try (Cursor cursor = mContext.getContentResolver().query(
                STATE_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            version = cursor.getLong(1);
            if (version == 0 || (!force && cursor.getInt(0) == 0)) return null;
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
