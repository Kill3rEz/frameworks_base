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

package com.android.systemui.keyguard.ui.composable.media

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import kotlin.math.roundToInt

/**
 * Frosted glass over the wallpaper, blurred by the compositor behind the lock screen's window, the
 * way the lock screen shortcuts are. The blur only knows its own alpha, so [alpha] has to carry
 * every fade the glass is drawn with.
 */
@Composable
internal fun WallpaperBlur(
    alpha: () -> Float,
    corner: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 40.dp,
) {
    val density = LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val radiusPx = with(density) { radius.roundToPx() }
    val host = remember { arrayOfNulls<BlurHost>(1) }
    AndroidView(
        factory = { context -> BlurHost(context).also { host[0] = it } },
        update = { it.configure(cornerPx, radiusPx) },
        modifier = modifier,
    )
    LaunchedEffect(Unit) {
        snapshotFlow { alpha().coerceIn(0f, 1f) }.collect { host[0]?.setBlurAlpha(it) }
    }
}

private class BlurHost(context: Context) : View(context) {
    private var blur: BackgroundBlurDrawable? = null
    private var cornerPx = 0f
    private var radiusPx = 0
    private var blurAlpha = 1f

    fun configure(corner: Float, radius: Int) {
        cornerPx = corner
        radiusPx = radius
        blur?.setCornerRadius(corner)
        blur?.setBlurRadius(radius)
    }

    fun setBlurAlpha(value: Float) {
        blurAlpha = value
        blur?.alpha = (255 * value).roundToInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val root = viewRootImpl ?: return
        blur =
            root.createBackgroundBlurDrawable().apply {
                setCornerRadius(cornerPx)
                setBlurRadius(radiusPx)
                alpha = (255 * blurAlpha).roundToInt()
            }
        background = blur
    }

    override fun onDetachedFromWindow() {
        background = null
        blur = null
        super.onDetachedFromWindow()
    }
}
