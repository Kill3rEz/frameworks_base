/*
 * Copyright (C) 2025 AxionOS
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
package com.android.server;

import android.content.Context;

import com.android.server.am.*;
import com.android.server.pm.*;
import com.android.server.wm.GameSpaceService;
import com.android.server.wm.WindowManagerService;

public class AxExtServiceFactory {
    private static AxExtServiceFactory sInstance = null;


    private AxExtServiceFactory(Context context) {
        NtServiceInjector.get().setCtx(context);
    }

    public static synchronized AxExtServiceFactory init(Context context) {
        if (sInstance == null) {
            sInstance = new AxExtServiceFactory(context);
        }
        return sInstance;
    }

    public static AxExtServiceFactory get() {
        if (sInstance == null) {
            throw new IllegalStateException("AxExtServiceFactory not initialized");
        }
        return sInstance;
    }

    public static void injectActivityManagerService(ActivityManagerService ams) {
        NtServiceInjector.get().setActivityManagerService(ams);
    }

    public static void injectWindowManagerService(WindowManagerService wms) {
        NtServiceInjector.get().setWindowManagerService(wms);
    }
    
    public static void injectPackageManagerservice(PackageManagerService pm) {
        NtServiceInjector.get().setPackageManagerService(pm);
    }


    // GameSpaceService must exist before AMS runs goingCallback (starts SystemUI):
    // the keyguard/focus/task hooks call GameSpaceService.get() unconditionally.
    public static void earlySystemReady() {
        GameSpaceService.systemReady();
    }

    public static void systemReady() {
        IAxExtServiceFactory.initObscuraService();
    }
    
    public static void onLateSystemReady() {
    }
}
