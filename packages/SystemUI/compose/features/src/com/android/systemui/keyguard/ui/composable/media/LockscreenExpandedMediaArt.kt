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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.penguin.DepthSubject
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

private val CoveredBlur = 40.dp

private val CoverGap = 10.dp

private val CoverMin = 96.dp

private const val TallArtAspect = 3f / 4f

@Composable
fun LockscreenExpandedMediaArt(
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    covered: () -> Float = { 0f },
    unlocking: () -> Float = { 0f },
) {
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
    LaunchedEffect(Unit) {
        snapshotFlow { state.fraction > 0f || (state.enabled && state.mediaVisible) }
            .distinctUntilChanged()
            .collect { DepthSubject.get()?.setExpandedMediaArtVisible(it) }
    }

    LaunchedEffect(Unit) { snapshotFlow { alpha() }.collect { state.lockscreenAlpha = it } }

    val context = LocalContext.current
    var tallArt by remember { mutableStateOf<File?>(null) }
    var squareArt by remember { mutableStateOf<File?>(null) }
    var tallRendering by remember { mutableStateOf(false) }
    val track = state.track
    LaunchedEffect(state.enabled, state.motionArtEnabled, track) {
        tallArt = null
        squareArt = null
        tallRendering = false
        if (state.enabled && state.motionArtEnabled && track != null) {
            val (artist, title) = track
            tallArt = MotionArt.find(context, artist, title, MotionArt.Kind.TALL)
            if (tallArt == null) {
                squareArt = MotionArt.find(context, artist, title, MotionArt.Kind.SQUARE)
            }
        }
    }
    val coverAlpha by animateFloatAsState(if (tallRendering) 0f else 1f, label = "cover")

    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableStateOf(0f) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                height = it.size.height.toFloat()
            }
            .graphicsLayer {
                this.alpha = alpha()
                val radius = CoveredBlur.toPx() * covered()
                renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
            }
    ) {
        val f = state.fraction
        val artwork = state.artwork
        if (f <= 0f || artwork == null) return@Box
        val density = LocalDensity.current

        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = f }) {
            if (tallArt == null) {
                Artwork(artwork, Modifier.fillMaxSize().blur(60.dp))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
            tallArt?.let {
                MotionArtVideo(
                    it,
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .aspectRatio(TallArtAspect)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0.6f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    crop = true,
                ) {
                    tallRendering = true
                }
            }
        }

        val gap = with(density) { CoverGap.toPx() }
        val rise = Offset(0f, unlocking() * height)
        val slot = state.artSlotBounds.translate(-origin + rise)
        val side = minOf(slot.width, slot.height - gap).coerceAtLeast(0f)
        val to =
            Rect(
                left = slot.left + (slot.width - side) / 2f,
                top = slot.bottom - gap - side,
                right = slot.left + (slot.width + side) / 2f,
                bottom = slot.bottom - gap,
            )
        val from = state.thumbnailBounds.translate(-origin + rise)
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
                    this.alpha = lerp(1f, side.within(minSide * 0.6f, minSide), f) * coverAlpha
                }
                .clip(RoundedCornerShape(lerp(10f, 22f, f).dp))
        ) {
            Artwork(artwork, Modifier.fillMaxSize())
            squareArt?.let { MotionArtVideo(it, Modifier.fillMaxSize()) }
        }
    }
}
