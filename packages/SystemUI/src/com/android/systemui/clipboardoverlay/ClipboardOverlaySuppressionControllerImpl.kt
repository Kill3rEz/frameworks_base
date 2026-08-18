/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.clipboardoverlay

import android.content.ClipData
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

@SysUISingleton
open class ClipboardOverlaySuppressionControllerImpl @Inject constructor() :
    ClipboardOverlaySuppressionController {

    override fun shouldSuppressOverlay(
        clipData: ClipData?,
        clipSource: String?,
        isEmulator: Boolean,
    ): Boolean {
        if (clipData == null || clipData.description.extras == null) {
            return false
        }
        return clipData.description.extras.getBoolean(EXTRA_SUPPRESS_OVERLAY, false)
    }

    companion object {
        @VisibleForTesting const val SHELL_PACKAGE = "com.android.shell"
        @VisibleForTesting
        const val EXTRA_SUPPRESS_OVERLAY = "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY"
    }
}
