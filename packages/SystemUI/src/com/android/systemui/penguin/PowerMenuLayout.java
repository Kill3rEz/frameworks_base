/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.statusbar.phone.SystemUIDialog;

public interface PowerMenuLayout {

    boolean isActive(@NonNull Context context);

    void inflate(@NonNull SystemUIDialog dialog, @NonNull Host host);

    @NonNull
    Dialog createSubMenu(@NonNull Context context, @NonNull ListAdapter adapter,
            boolean blurSupported);

    interface Host {
        int POWER_OFF = 0;
        int RESTART = 1;
        int EMERGENCY = 2;

        @Nullable
        Option findOption(int kind);

        void onContainerTouch(@NonNull MotionEvent event);

        boolean isBlurBackdropAllowed();

        void setBlurSupported(boolean supported);

        void close();

        void setAnimatedContent(@NonNull View content, @Nullable Drawable backdrop);

        void onLayoutInflated(@NonNull Context context, @NonNull ViewGroup container);
    }

    interface Option {
        @NonNull
        Drawable getIcon(@NonNull Context context);

        void trigger();
    }
}
