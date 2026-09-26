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

object LockscreenMediaExpansion {
    var expanded by mutableStateOf(false)

    val progress = Animatable(0f)

    var supported by mutableStateOf(false)

    var artwork by mutableStateOf<Icon?>(null)

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

internal fun Float.within(start: Float, end: Float) = ((this - start) / (end - start)).coerceIn(0f, 1f)

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
