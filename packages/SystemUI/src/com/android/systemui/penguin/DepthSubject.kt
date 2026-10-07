/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.penguin

import android.view.View

/**
 * doing.
 */
interface DepthSubject {
    val view: View

    fun updateDepthWallpaper()

    fun updateDepthWallpaper(forced: Boolean)

    fun updateDepthWallpaperVisibility()

    fun hideDepthWallpaper()

    fun hideDepthWallpaperImmediate()

    fun setSubjectAlpha(alpha: Float)

    fun onDozingChanged(dozing: Boolean)

    fun onBouncerShowingChanged(showing: Boolean)

    fun onGlanceableHubShowingChanged(showing: Boolean)

    fun setLockscreenScene(onLockscreen: Boolean)

    fun setExpandedMediaArtVisible(visible: Boolean)

    companion object {
        @JvmStatic var instance: DepthSubject? = null

        @JvmStatic fun get(): DepthSubject? = instance
    }
}
