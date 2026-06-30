/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.app;

import com.android.internal.app.HiddenNotificationInfo;

oneway interface IHiddenNotificationListener {
    void onHiddenNotificationPosted(in HiddenNotificationInfo info);
    void onHiddenNotificationRemoved(String key);
}
