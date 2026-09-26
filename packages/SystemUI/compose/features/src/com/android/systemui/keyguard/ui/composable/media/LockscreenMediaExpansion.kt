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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
    /** Where the player is headed. */
    var expanded by mutableStateOf(false)

    /** 0 is the compact player, 1 the expanded one. Driven by [LockscreenExpandedMediaArt]. */
    val progress = Animatable(0f)

    /** Only the phone layout knows how to lay out around the expanded player. */
    var supported by mutableStateOf(false)

    /** The selected session's artwork, which becomes the background. */
    var artwork by mutableStateOf<Icon?>(null)

    /** The compact player's thumbnail in window coordinates, where the background grows from. */
    var thumbnailBounds by mutableStateOf(Rect.Zero)

    val fraction: Float
        get() = progress.value.coerceIn(0f, 1f)

    fun toggle() {
        if (supported && artwork != null) expanded = !expanded
    }

    fun collapse() {
        expanded = false
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
