/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.app;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;

public class HiddenNotificationInfo implements Parcelable {
    public final String key;
    public final String packageName;
    public final Icon appIcon;
    public final CharSequence title;
    public final CharSequence text;
    public final PendingIntent contentIntent;
    public final long postTime;
    public final int userId;

    public HiddenNotificationInfo(
            String key,
            String packageName,
            Icon appIcon,
            CharSequence title,
            CharSequence text,
            PendingIntent contentIntent,
            long postTime,
            int userId) {
        this.key = key;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.title = title;
        this.text = text;
        this.contentIntent = contentIntent;
        this.postTime = postTime;
        this.userId = userId;
    }

    protected HiddenNotificationInfo(Parcel in) {
        key = in.readString();
        packageName = in.readString();
        appIcon = in.readParcelable(Icon.class.getClassLoader(), Icon.class);
        title = in.readCharSequence();
        text = in.readCharSequence();
        contentIntent = in.readParcelable(PendingIntent.class.getClassLoader(), PendingIntent.class);
        postTime = in.readLong();
        userId = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(key);
        dest.writeString(packageName);
        dest.writeParcelable(appIcon, flags);
        dest.writeCharSequence(title);
        dest.writeCharSequence(text);
        dest.writeParcelable(contentIntent, flags);
        dest.writeLong(postTime);
        dest.writeInt(userId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<HiddenNotificationInfo> CREATOR = new Creator<HiddenNotificationInfo>() {
        @Override
        public HiddenNotificationInfo createFromParcel(Parcel in) {
            return new HiddenNotificationInfo(in);
        }

        @Override
        public HiddenNotificationInfo[] newArray(int size) {
            return new HiddenNotificationInfo[size];
        }
    };
}
