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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun LockscreenExpandedMediaArt(alpha: () -> Float, modifier: Modifier = Modifier) {
    val state = LockscreenMediaExpansion
    LaunchedEffect(Unit) {
        state.progress.snapTo(if (state.expanded) 1f else 0f)
        snapshotFlow { state.expanded && state.supported && state.artwork != null }
            .distinctUntilChanged()
            .collect { target -> state.progress.animateTo(if (target) 1f else 0f, state.Spec) }
    }
    DisposableEffect(Unit) { onDispose { state.expanded = false } }

    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { this.alpha = alpha() }
    ) {
        val f = state.fraction
        val artwork = state.artwork
        if (f <= 0f || artwork == null) return@Box
        val density = LocalDensity.current
        BoxWithFullBounds { width, height ->
            val from = state.thumbnailBounds.translate(-origin)
            val to = Rect(0f, 0f, width, height)
            val rect =
                if (from.isEmpty) {
                    to
                } else {
                    Rect(
                        lerp(from.left, to.left, f),
                        lerp(from.top, to.top, f),
                        lerp(from.right, to.right, f),
                        lerp(from.bottom, to.bottom, f),
                    )
                }
            val shape = RoundedCornerShape(lerp(10f, 0f, f).dp)
            val sizeDp = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
            Box(
                Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(sizeDp)
                    .clip(shape)
            ) {
                Artwork(artwork, Modifier.fillMaxSize())
                Artwork(
                    artwork,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            this.alpha = f.within(0.2f, 1f)
                        }
                        .blur(36.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0.45f to Color.Transparent,
                                    0.72f to Color.Black,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { this.alpha = f.within(0.3f, 1f) }
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.2f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun BoxWithFullBounds(content: @Composable (width: Float, height: Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    }
}
