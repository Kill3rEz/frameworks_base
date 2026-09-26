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

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.common.shared.model.Icon
import kotlin.math.roundToInt

/**
 * The lock screen's expanded media state, shared by the player, the upper region laying out around
 * it and the artwork behind everything. There is one lock screen, so one state.
 *
 * Everything reads [progress] rather than [expanded], so the card, the artwork, the clock, the
 * widgets and the notifications all move on the same frame of the same animation.
 */
object LockscreenMediaExpansion {
    /** Whether the feature is on, from Settings > System > Experimental features. */
    var enabled by mutableStateOf(false)
        private set

    /** Where the player is headed. Kept in settings, so the lock screen comes back as left. */
    var expanded by mutableStateOf(false)
        private set

    /** 0 is the compact player, 1 the expanded one. Driven by [LockscreenExpandedMediaArt]. */
    val progress = Animatable(0f)

    /** Only the phone layout knows how to lay out around the expanded player. */
    var supported by mutableStateOf(false)

    /** Whether the player is on screen at all: playing, and not dozing. */
    var mediaVisible by mutableStateOf(false)

    /** The selected session's artwork, which becomes the background. */
    var artwork by mutableStateOf<Icon?>(null)

    /** The compact player's thumbnail in window coordinates, where the background grows from. */
    var thumbnailBounds by mutableStateOf(Rect.Zero)

    /**
     * Set by a tap, so that change animates. Anything else, like waking to a lock screen that was
     * left expanded, lands straight in place.
     */
    internal var animateNextChange = false

    private var resolver: ContentResolver? = null

    val fraction: Float
        get() = progress.value.coerceIn(0f, 1f)

    /** Whether the expanded player should be showing right now. */
    val target: Boolean
        get() = enabled && expanded && supported && mediaVisible && artwork != null

    fun toggle() {
        if (!enabled || !supported || artwork == null) return
        persistExpanded(!expanded)
    }

    fun collapse() {
        if (expanded) persistExpanded(false)
    }

    private fun persistExpanded(value: Boolean) {
        animateNextChange = true
        expanded = value
        resolver?.let {
            Settings.System.putIntForUser(
                it,
                Settings.System.LS_MEDIA_EXPANDED,
                if (value) 1 else 0,
                UserHandle.USER_CURRENT,
            )
        }
    }

    /** Follows the two settings for as long as the lock screen player is composed. */
    @Composable
    fun ObserveSettings() {
        val context = LocalContext.current
        DisposableEffect(context) {
            val contentResolver = context.contentResolver
            resolver = contentResolver
            fun read() {
                enabled =
                    Settings.System.getIntForUser(
                        contentResolver,
                        Settings.System.LS_MEDIA_EXPAND,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
                expanded =
                    Settings.System.getIntForUser(
                        contentResolver,
                        Settings.System.LS_MEDIA_EXPANDED,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
            }
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = read()
                }
            read()
            listOf(Settings.System.LS_MEDIA_EXPAND, Settings.System.LS_MEDIA_EXPANDED).forEach {
                contentResolver.registerContentObserver(
                    Settings.System.getUriFor(it),
                    false,
                    observer,
                    UserHandle.USER_ALL,
                )
            }
            onDispose { contentResolver.unregisterContentObserver(observer) }
        }
    }

    internal val Spec = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

internal fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

/** The part of [fraction] that falls in [start, end], rescaled to 0 to 1. */
internal fun Float.within(start: Float, end: Float) = ((this - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Lays the content out at its full size but reports only [fraction] of its height, fading it the
 * same way, so a block grows into or out of a column instead of popping.
 */
internal fun Modifier.collapsible(fraction: () -> Float): Modifier =
    this.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val f = fraction()
            alpha = f
            clip = f < 1f
        }
