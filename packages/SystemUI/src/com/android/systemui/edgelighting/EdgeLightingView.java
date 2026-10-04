/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.edgelighting;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class EdgeLightingView extends View {

    private static final int COUNT = 14;
    private static final float MIN_SIZE = 0.14f;
    private static final float MAX_SIZE = 0.26f;
    private static final float GRAVITY_DP = 2600f;
    private static final float BOUNCE = 0.55f;
    private static final float FRICTION = 0.985f;
    private static final long SPAWN_SPREAD_MS = 1200;
    private static final long MAX_DURATION_MS = 8000;
    private static final long FADE_MS = 450;

    private static final class Sprite {
        float x, y, vx, vy, radius, angle, spin;
        long spawnAt;
        boolean active;
    }

    private final Bitmap mBitmap;
    private final Runnable mOnFinished;
    private final List<Sprite> mSprites = new ArrayList<>();
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF mDst = new RectF();
    private final Random mRandom = new Random();
    private final float mGravity;

    private long mStart;
    private long mLastFrame;
    private long mFinishAt = -1;

    EdgeLightingView(Context context, Bitmap bitmap, Runnable onFinished) {
        super(context);
        mBitmap = bitmap;
        mOnFinished = onFinished;
        mGravity = GRAVITY_DP * context.getResources().getDisplayMetrics().density;
    }

    void finish() {
        if (mFinishAt < 0) mFinishAt = now();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!mSprites.isEmpty() || w == 0) return;
        mStart = mLastFrame = now();
        float aspect = mBitmap.getHeight() / (float) mBitmap.getWidth();
        for (int i = 0; i < COUNT; i++) {
            Sprite s = new Sprite();
            float size = w * (MIN_SIZE + mRandom.nextFloat() * (MAX_SIZE - MIN_SIZE));
            s.radius = size * Math.max(1f, aspect) / 2f;
            s.x = s.radius + mRandom.nextFloat() * (w - 2 * s.radius);
            s.y = -s.radius;
            s.vx = (mRandom.nextFloat() - 0.5f) * w * 0.6f;
            s.vy = mRandom.nextFloat() * h * 0.3f;
            s.angle = mRandom.nextFloat() * 360f;
            s.spin = (mRandom.nextFloat() - 0.5f) * 240f;
            s.spawnAt = mStart + (long) (SPAWN_SPREAD_MS * i / (float) COUNT);
            mSprites.add(s);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long t = now();
        if (mFinishAt < 0 && t - mStart > MAX_DURATION_MS) mFinishAt = t;
        float alpha = 1f;
        if (mFinishAt >= 0) {
            alpha = 1f - Math.min(1f, (t - mFinishAt) / (float) FADE_MS);
            if (alpha <= 0f) {
                post(mOnFinished);
                return;
            }
        }
        float dt = Math.min(0.033f, (t - mLastFrame) / 1000f);
        mLastFrame = t;
        step(t, dt);

        mPaint.setAlpha(Math.round(alpha * 255));
        float aspect = mBitmap.getHeight() / (float) mBitmap.getWidth();
        for (Sprite s : mSprites) {
            if (!s.active) continue;
            float halfW = s.radius / Math.max(1f, aspect);
            float halfH = halfW * aspect;
            canvas.save();
            canvas.rotate(s.angle, s.x, s.y);
            mDst.set(s.x - halfW, s.y - halfH, s.x + halfW, s.y + halfH);
            canvas.drawBitmap(mBitmap, null, mDst, mPaint);
            canvas.restore();
        }
        postInvalidateOnAnimation();
    }

    private void step(long t, float dt) {
        int w = getWidth(), h = getHeight();
        for (Sprite s : mSprites) {
            if (!s.active) {
                if (t < s.spawnAt) continue;
                s.active = true;
            }
            s.vy += mGravity * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.angle += s.spin * dt;
            if (s.y + s.radius > h) {
                s.y = h - s.radius;
                s.vy = -s.vy * BOUNCE;
                s.vx *= FRICTION;
                s.spin *= 0.8f;
            }
            if (s.x - s.radius < 0) {
                s.x = s.radius;
                s.vx = -s.vx * BOUNCE;
            } else if (s.x + s.radius > w) {
                s.x = w - s.radius;
                s.vx = -s.vx * BOUNCE;
            }
        }
        for (int i = 0; i < mSprites.size(); i++) {
            Sprite a = mSprites.get(i);
            if (!a.active) continue;
            for (int j = i + 1; j < mSprites.size(); j++) {
                Sprite b = mSprites.get(j);
                if (!b.active) continue;
                float dx = b.x - a.x, dy = b.y - a.y;
                float min = (a.radius + b.radius) * 0.85f;
                float dist2 = dx * dx + dy * dy;
                if (dist2 >= min * min || dist2 == 0f) continue;
                float dist = (float) Math.sqrt(dist2);
                float nx = dx / dist, ny = dy / dist;
                float push = (min - dist) / 2f;
                a.x -= nx * push; a.y -= ny * push;
                b.x += nx * push; b.y += ny * push;
                float rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
                if (rel < 0) {
                    float impulse = -(1 + BOUNCE) * rel / 2f;
                    a.vx -= impulse * nx; a.vy -= impulse * ny;
                    b.vx += impulse * nx; b.vy += impulse * ny;
                }
            }
        }
    }
}
