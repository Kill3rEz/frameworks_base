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

package com.android.compose.gesture.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun rememberControlCentreOverscrollEffectFactory(): OverscrollFactory {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) { ControlCentreOverscrollEffectFactory(animationScope) }
}

data class ControlCentreOverscrollEffectFactory(private val animationScope: CoroutineScope) :
    OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        ControlCentreOverscrollEffect(animationScope)
}

class ControlCentreOverscrollEffect(animationScope: CoroutineScope) :
    BaseContentOverscrollEffect(animationScope, ReleaseSpring) {
    private val shown = Animatable(0f)

    override val node: DelegatableNode =
        object : Modifier.Node(), LayoutModifierNode {
            override fun onAttach() {
                coroutineScope.launch {
                    snapshotFlow { overscrollDistance }
                        .collectLatest { target ->
                            if (target != 0f) {
                                shown.snapTo(target)
                            } else {
                                shown.animateTo(0f, ReleaseSpring)
                            }
                        }
                }
            }

            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints,
            ): MeasureResult {
                val placeable = measurable.measure(constraints)
                val maxDistancePx = MaxDistance.toPx()
                return layout(placeable.width, placeable.height) {
                    val distance = shown.value
                    if (distance == 0f) {
                        placeable.place(0, 0)
                    } else {
                        val progress = Stretch.convert(distance / maxDistancePx)
                        placeable.placeWithLayer(0, 0) {
                            scaleY = 1f + progress * StretchPerProgress
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    }
                }
            }
        }

    private companion object {
        val MaxDistance = 500.dp
        const val StretchPerProgress = 0.45f
        val Stretch = ProgressConverter.tanh(maxProgress = 0.3f, tilt = 1f)
        val ReleaseSpring =
            spring(
                dampingRatio = 0.7f,
                stiffness = 322f,
                visibilityThreshold = Spring.DefaultDisplacementThreshold,
            )
    }
}
