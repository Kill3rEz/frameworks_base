/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.media.MediaViewController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.ScrimState;
import com.android.systemui.tuner.TunerService;

import dagger.Lazy;

public class WallpaperDepthUtils {

    private static final String WALLPAPER_DEPTH_KEY = "system:depth_wallpaper_subject_image_uri";
    private static final String WALLPAPER_DEPTH_ENABLED_KEY = "system:depth_wallpaper_enabled";
    private static final String WALLPAPER_DEPTH_OPACITY_KEY = "system:depth_wallpaper_opacity";
    private static final String WALLPAPER_DEPTH_OFFSET_X_KEY = "system:depth_wallpaper_offset_x";
    private static final String WALLPAPER_DEPTH_OFFSET_Y_KEY = "system:depth_wallpaper_offset_y";

    private static WallpaperDepthUtils instance;
    private FrameLayout mLockScreenSubject;

    private final Context mContext;
    private final Lazy<ScrimController> mScrimControllerLazy;
    private final TunerService mTunerService;

    private boolean mDWallpaperEnabled;
    private int mDWallOpacity = 255;
    private String mWallpaperSubjectPath;
    private boolean mDozing;
    private boolean mBouncerShowing;
    private boolean mGlanceableHubShowing;
    private boolean mWallpaperLoaded = false;
    private String mPreviousWallpaperPath;
    private Bitmap mWallpaperBitmap;
    private int mOffsetX;
    private int mOffsetY;
    private boolean mUnlocking;

    private WallpaperDepthUtils(Context context, Lazy<ScrimController> scrimControllerLazy) {
        mContext = context.getApplicationContext();
        mScrimControllerLazy = scrimControllerLazy;
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(mTunable, WALLPAPER_DEPTH_KEY, 
            WALLPAPER_DEPTH_ENABLED_KEY, WALLPAPER_DEPTH_OPACITY_KEY, 
            WALLPAPER_DEPTH_OFFSET_X_KEY, WALLPAPER_DEPTH_OFFSET_Y_KEY);
        mLockScreenSubject = new FrameLayout(mContext) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                WallpaperDepthUtils.this.onDetachedFromWindow();
            }
        };
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        mLockScreenSubject.setLayoutParams(lp);
        // Make view non-interactive so touches pass through to notifications
        mLockScreenSubject.setClickable(false);
        mLockScreenSubject.setFocusable(false);
        mLockScreenSubject.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mLockScreenSubject.setElevation(2.0f);
    }

    public static WallpaperDepthUtils getInstance(Context context, Lazy<ScrimController> scrimControllerLazy) {
        if (instance == null) {
            instance = new WallpaperDepthUtils(context, scrimControllerLazy);
        }
        return instance;
    }
    public static WallpaperDepthUtils get() {
        return instance;
    }

    public void onUnlockStarted() {
        mUnlocking = true;
        hideDepthWallpaperImmediate();
    }

    public void onUnlockCancelled() {
        mUnlocking = false;
        updateDepthWallpaperVisibility();
    }

    public void onUnlockCompleted() {
        mUnlocking = false;
    }
    
    public void onDozingChanged(boolean dozing) {
        mDozing = dozing;
        if (mDozing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaper(true);
            updateDepthWallpaperVisibility();
            if (mLockScreenSubject != null) {
                mLockScreenSubject.postDelayed(this::updateDepthWallpaperVisibility, 200);
                mLockScreenSubject.postDelayed(this::updateDepthWallpaperVisibility, 500);
            }
        }
    }

    public void onBouncerShowingChanged(boolean showing) {
        if (mBouncerShowing == showing) {
            return;
        }
        mBouncerShowing = showing;
        if (mBouncerShowing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    public void onGlanceableHubShowingChanged(boolean showing) {
        if (mGlanceableHubShowing == showing) {
            return;
        }
        mGlanceableHubShowing = showing;
        if (mGlanceableHubShowing) {
            hideDepthWallpaperImmediate();
        } else {
            updateDepthWallpaperVisibility();
        }
    }

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case WALLPAPER_DEPTH_ENABLED_KEY:
                    mDWallpaperEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_KEY:
                    mPreviousWallpaperPath = mWallpaperSubjectPath;
                    mWallpaperSubjectPath = newValue;
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OPACITY_KEY:
                    int opacity = TunerService.parseInteger(newValue, 100);
                    mDWallOpacity = Math.round(opacity * 2.55f);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_X_KEY:
                    mOffsetX = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                case WALLPAPER_DEPTH_OFFSET_Y_KEY:
                    mOffsetY = TunerService.parseInteger(newValue, 0);
                    updateDepthWallpaper(true);
                    break;
                default:
                    break;
            }
        }
    };
    
    public void setSubjectAlpha(float subjectAlpha) {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setAlpha(subjectAlpha));
    }
    
    public void updateDepthWallpaper() {
        updateDepthWallpaper(false);
    }

    public FrameLayout getDepthWallpaperView() {
        return mLockScreenSubject;
    }

    private boolean isDWallpaperEnabled() {
        return mDWallpaperEnabled && mWallpaperSubjectPath != null
                && !mWallpaperSubjectPath.isEmpty();
    }

    private boolean isAlbumArtVisible() {
        try {
            MediaViewController controller = MediaViewController.get(mContext);
            return controller != null && controller.albumArtVisible();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canShowDepthWallpaper() {
        ScrimState currentState = null;
        try {
            if (mScrimControllerLazy != null && mScrimControllerLazy.get() != null) {
                currentState = mScrimControllerLazy.get().getState();
            }
        } catch (Throwable t) {
            // Ignore legacy assertion error if SceneContainer is active
        }
        // Only show on KEYGUARD state when bouncer is NOT showing
        return mLockScreenSubject != null
                && isDWallpaperEnabled()
                && !mDozing
                && !mBouncerShowing
                && !mGlanceableHubShowing
                && !mUnlocking
                && (currentState == null || currentState == ScrimState.KEYGUARD)
                && mContext.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE
                && !isAlbumArtVisible();
    }

    public void updateDepthWallpaperVisibility() {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        final boolean canShow = canShowDepthWallpaper();
        final int targetVisibility = canShow ? View.VISIBLE : View.GONE;
        mLockScreenSubject.post(() -> {
            mLockScreenSubject.setVisibility(targetVisibility);
            if (canShow) {
                mLockScreenSubject.setAlpha(1f);
                mLockScreenSubject.setTranslationZ(0f);
                mLockScreenSubject.setElevation(2.0f);
                mLockScreenSubject.invalidate();
            } else {
                mLockScreenSubject.setTranslationZ(-100f);
            }
        });
    }
    
    public void hideDepthWallpaper() {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> {
            mLockScreenSubject.setVisibility(View.GONE);
            mLockScreenSubject.setTranslationZ(-100f);
        });
    }

    public void hideDepthWallpaperImmediate() {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> {
            mLockScreenSubject.animate().cancel();
            mLockScreenSubject.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        mLockScreenSubject.setVisibility(View.GONE);
                        mLockScreenSubject.setTranslationZ(-100f);
                        mLockScreenSubject.setAlpha(1f);
                    })
                    .start();
        });
    }

    public Bitmap getResizedBitmap(Bitmap wallpaperBitmap, float xOffsetDp, float yOffsetDp) {
        Rect displayBounds = mContext.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        float xOffsetPx = xOffsetDp * displayMetrics.density;
        float yOffsetPx = yOffsetDp * displayMetrics.density;
        float ratioW = displayBounds.width() / (float) wallpaperBitmap.getWidth();
        float ratioH = displayBounds.height() / (float) wallpaperBitmap.getHeight();
        int desiredHeight = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getHeight());
        int desiredWidth = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getWidth());
        desiredHeight = Math.max(desiredHeight, 0);
        desiredWidth = Math.max(desiredWidth, 0);
        Bitmap scaledWallpaperBitmap = Bitmap.createScaledBitmap(wallpaperBitmap, desiredWidth, desiredHeight, true);
        int xPixelShift = Math.max((desiredWidth - displayBounds.width()) / 2, 0) - Math.round(xOffsetPx);
        int yPixelShift = Math.max((desiredHeight - displayBounds.height()) / 2, 0) - Math.round(yOffsetPx);
        int cropWidth = Math.min(displayBounds.width(), scaledWallpaperBitmap.getWidth() - xPixelShift);
        int cropHeight = Math.min(displayBounds.height(), scaledWallpaperBitmap.getHeight() - yPixelShift);
        scaledWallpaperBitmap = Bitmap.createBitmap(scaledWallpaperBitmap, Math.max(xPixelShift, 0), Math.max(yPixelShift, 0), cropWidth, cropHeight);
        return scaledWallpaperBitmap;
    }

    public void updateDepthWallpaper(boolean forced) {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        boolean pathChanged = (mPreviousWallpaperPath != null && !mPreviousWallpaperPath.equals(mWallpaperSubjectPath));
        boolean backgroundMissing = (mLockScreenSubject.getBackground() == null);
        if (!mWallpaperLoaded || pathChanged || backgroundMissing || forced) {
            Log.d("WallpaperDepthUtils", "updateDepthWallpaper: " + (mWallpaperLoaded || forced ? "update required" : "first load"));
            new LoadWallpaperTask().execute();
            mWallpaperLoaded = true;
            mPreviousWallpaperPath = mWallpaperSubjectPath;
        }
        updateDepthWallpaperVisibility();
    }

    private class LoadWallpaperTask extends AsyncTask<Void, Void, Drawable> {
        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                Log.d("LoadWallpaperTask", "Wallpaper path: " + mWallpaperSubjectPath);
                Bitmap bitmap = BitmapFactory.decodeFile(mWallpaperSubjectPath);
                if (bitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode bitmap from file");
                    return null;
                }
                Bitmap resizedBitmap = getResizedBitmap(bitmap, mOffsetX, mOffsetY);
                if (resizedBitmap == null) {
                    Log.d("LoadWallpaperTask", "Failed to decode resized bitmap from file");
                    return null;
                }
                if (mWallpaperBitmap != null) {
                    mWallpaperBitmap = null;
                }
                mWallpaperBitmap = resizedBitmap;
                Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), mWallpaperBitmap);
                bitmapDrawable.setAlpha(255);
                return bitmapDrawable;
            } catch (OutOfMemoryError e) {
                Log.e("LoadWallpaperTask", "Out of memory error", e);
                return null;
            } catch (Exception e) {
                Log.e("LoadWallpaperTask", "Error loading wallpaper", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (drawable == null || mWallpaperBitmap == null) {
                Log.d("LoadWallpaperTask", "decodeFile returned nothing, skipping application of subject as background");
                mWallpaperLoaded = false;
                updateDepthWallpaperVisibility();
                return;
            }
            mLockScreenSubject.setBackground(drawable);
            mLockScreenSubject.getBackground().setAlpha(mDWallOpacity);
            Log.d("LoadWallpaperTask", "Subject Loaded!");
            updateDepthWallpaperVisibility();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            mWallpaperBitmap = null;
        }
    }
    
    public void onDetachedFromWindow() {
        mTunerService.removeTunable(mTunable);
        mWallpaperBitmap = null;
    }
}
