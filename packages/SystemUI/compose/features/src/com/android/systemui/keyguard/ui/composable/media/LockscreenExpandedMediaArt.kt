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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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

/** Gap between the cover and the player under it. */
private val CoverGap = 10.dp

/** Below this the cover is too small to be worth showing, and fades out. */
private val CoverMin = 96.dp

/**
 * The artwork behind the expanded player, laid out as iOS does: the cover as a rounded square card
 * standing on the player, over a blurred copy of itself that fills the lock screen. It grows out
 * of the compact player's thumbnail and shrinks back into it, so the two read as one image, and
 * shrinks to fit when notifications take the room above the player.
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
    // The depth wallpaper's subject would show through the player's glass, and stand in front of
    // someone else's artwork.
    LaunchedEffect(Unit) {
        snapshotFlow { state.fraction > 0f || (state.enabled && state.mediaVisible) }
            .distinctUntilChanged()
            .collect { WallpaperDepthUtils.setExpandedMediaArtVisible(it) }
    }

    LaunchedEffect(Unit) { snapshotFlow { alpha() }.collect { state.lockscreenAlpha = it } }

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

        // The blurred fill takes over from the wallpaper as the cover grows.
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = f }) {
            Artwork(artwork, Modifier.fillMaxSize().blur(60.dp))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        val gap = with(density) { CoverGap.toPx() }
        val slot = state.artSlotBounds.translate(-origin)
        val side = minOf(slot.width, slot.height - gap).coerceAtLeast(0f)
        val to =
            Rect(
                left = slot.left + (slot.width - side) / 2f,
                top = slot.bottom - gap - side,
                right = slot.left + (slot.width + side) / 2f,
                bottom = slot.bottom - gap,
            )
        val from = state.thumbnailBounds.translate(-origin)
        if (slot.isEmpty || from.isEmpty) return@Box
        val rect =
            Rect(
                lerp(from.left, to.left, f),
                lerp(from.top, to.top, f),
                lerp(from.right, to.right, f),
                lerp(from.bottom, to.bottom, f),
            )
        val minSide = with(density) { CoverMin.toPx() }
        val size = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
        Box(
            Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(size)
                .graphicsLayer {
                    // Only once it has grown; the thumbnail it starts from stays solid.
                    this.alpha = lerp(1f, side.within(minSide * 0.6f, minSide), f)
                }
                .clip(RoundedCornerShape(lerp(10f, 22f, f).dp))
        ) {
            Artwork(artwork, Modifier.fillMaxSize())
        }
    }
}
