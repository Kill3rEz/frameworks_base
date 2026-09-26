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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.android.systemui.util.WallpaperDepthUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/** Where the square artwork sits when expanded, as a share of the screen height. */
private const val ArtTop = 0.1f

/**
 * The artwork behind the expanded player, laid out as iOS does: the whole square cover across the
 * top of the lock screen, melting at its lower edge into a blurred copy that fills the rest. The
 * cover grows out of the compact player's thumbnail and shrinks back into it, so the two read as
 * one image.
 *
 * It also drives [LockscreenMediaExpansion.progress], since it is composed for as long as the lock
 * screen is.
 */
@Composable
fun LockscreenExpandedMediaArt(alpha: () -> Float, modifier: Modifier = Modifier) {
    val state = LockscreenMediaExpansion
    LaunchedEffect(Unit) {
        snapshotFlow { state.target }
            .distinctUntilChanged()
            .collect { target ->
                val value = if (target) 1f else 0f
                if (state.animateNextChange) {
                    state.animateNextChange = false
                    state.progress.animateTo(value, state.Spec)
                } else {
                    state.progress.snapTo(value)
                }
            }
    }
    // The depth wallpaper's subject would stand in front of someone else's artwork.
    LaunchedEffect(Unit) {
        snapshotFlow { state.fraction > 0f }
            .distinctUntilChanged()
            .collect { WallpaperDepthUtils.setExpandedMediaArtVisible(it) }
    }

    // With notifications under the player the cover would fight them for attention, so it
    // gives way to the blur, as on iOS.
    val coverAlpha by
        animateFloatAsState(if (state.notificationsShowing) 0f else 1f, label = "cover")
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { this.alpha = alpha() }
    ) {
        val f = state.fraction
        val artwork = state.artwork
        if (f <= 0f || artwork == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // The blurred fill takes over from the wallpaper as the cover grows.
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = f }) {
            Artwork(artwork, Modifier.fillMaxSize().blur(60.dp))
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = lerp(0.42f, 0.28f, coverAlpha)))
            )
        }

        val from = state.thumbnailBounds.translate(-origin)
        val to = Rect(0f, height * ArtTop, width, height * ArtTop + width)
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
        val size = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
        Box(
            Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(size)
                .clip(RoundedCornerShape(lerp(10f, 0f, f).dp))
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    // Only once it has grown; the thumbnail it starts from stays solid.
                    this.alpha = lerp(1f, coverAlpha, f)
                }
                .drawWithContent {
                    drawContent()
                    // Fade the cover's edges into the blur, top a little and bottom a lot; none of
                    // this while it is still the thumbnail.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 1f - 0.7f * f),
                            0.1f to Color.Black,
                            0.7f to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - f),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            Artwork(artwork, Modifier.fillMaxSize())
        }

        // Keep the status bar, the clock and the controls readable on any artwork.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = f.within(0.3f, 1f) }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        0.15f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.5f),
                    )
                )
        )
    }
}
