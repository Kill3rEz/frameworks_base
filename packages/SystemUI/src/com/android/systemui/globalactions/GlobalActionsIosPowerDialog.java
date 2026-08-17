/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.globalactions;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;

import com.android.systemui.globalactions.GlobalActionsDialogLite.Action;
import com.android.systemui.globalactions.GlobalActionsDialogLite.SinglePressAction;
import com.android.systemui.shared.system.BlurUtils;

public class GlobalActionsIosPowerDialog {

    public static Dialog create(@NonNull Context context, @NonNull ListAdapter adapter,
            boolean blurSupported) {
        final Resources res = context.getResources();
        final LayoutInflater inflater = LayoutInflater.from(context);
        final ViewGroup content = (ViewGroup) inflater.inflate(
                com.android.systemui.res.R.layout.global_actions_ios_submenu, null);

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);

        final LinearLayout rows = content.findViewById(
                com.android.systemui.res.R.id.power_menu_ios_submenu_content);
        final int track = res.getColor(
                com.android.systemui.res.R.color.power_menu_ios_track, null);
        final int thumb = res.getColor(
                com.android.systemui.res.R.color.power_menu_ios_thumb, null);
        final int foreground = res.getColor(
                com.android.systemui.res.R.color.power_menu_ios_foreground, null);
        final int rowGap = res.getDimensionPixelSize(
                com.android.systemui.res.R.dimen.power_menu_ios_row_gap);

        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (!(item instanceof Action)) {
                continue;
            }
            final Action action = (Action) item;
            SlideToActView row = new SlideToActView(context, null /* attrs */);
            row.setColors(track, thumb, foreground);
            row.setLabel(getLabel(context, action));
            row.setThumbIcon(action.getIcon(context));
            row.setOnSlideCompleteListener(v -> {
                dialog.dismiss();
                action.onPress();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rows.getChildCount() > 0) {
                params.topMargin = rowGap;
            }
            rows.addView(row, params);
        }

        content.findViewById(com.android.systemui.res.R.id.power_menu_ios_close)
                .setOnClickListener(v -> dialog.dismiss());

        final Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        window.setTitle(""); // prevent Talkback from speaking the first item name twice
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        final boolean translucentPowerMenu = res.getBoolean(
                com.android.systemui.res.R.bool.config_translucentStandalonePowerMenu);
        if (BlurUtils.isVolumeAndPowerBlurEnabled() && translucentPowerMenu && blurSupported) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.setBlurBehindRadius(res.getDimensionPixelSize(
                    com.android.systemui.res.R.dimen.global_actions_blur_radius));
            window.setAttributes(attrs);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        } else {
            window.setBackgroundDrawableResource(
                    com.android.systemui.res.R.drawable.power_menu_ios_backdrop);
        }

        return dialog;
    }

    private static CharSequence getLabel(@NonNull Context context, @NonNull Action action) {
        if (action instanceof SinglePressAction) {
            SinglePressAction singlePress = (SinglePressAction) action;
            if (singlePress.getMessage() != null) {
                return singlePress.getMessage();
            }
            if (singlePress.getMessageResId() != 0) {
                return context.getString(singlePress.getMessageResId());
            }
        }
        return action.getLabelForAccessibility(context);
    }

    private GlobalActionsIosPowerDialog() {
    }
}
