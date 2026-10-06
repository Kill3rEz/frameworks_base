/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.shared.style

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object LiquidGlass {
    private const val SETTING = "penguin_liquid_glass"

    private var state: State<Boolean>? = null

    fun enabled(context: Context): State<Boolean> =
        state
            ?: synchronized(this) {
                state
                    ?: run {
                        val resolver = context.applicationContext.contentResolver
                        val read = {
                            Settings.Secure.getIntForUser(resolver, SETTING, 0, UserHandle.USER_CURRENT) !=
                                0
                        }
                        val value = mutableStateOf(read())
                        resolver.registerContentObserver(
                            Settings.Secure.getUriFor(SETTING),
                            false,
                            object : ContentObserver(Handler(Looper.getMainLooper())) {
                                override fun onChange(selfChange: Boolean) {
                                    value.value = read()
                                }
                            },
                            UserHandle.USER_ALL,
                        )
                        value.also { state = it }
                    }
            }
}

val liquidGlassOn: Boolean
    @Composable @ReadOnlyComposable get() = LiquidGlass.enabled(LocalContext.current).value

val LiquidGlassSurface = Color(0xFFB4B4BE).copy(alpha = 0.18f)

val LiquidGlassControl = Color(0xFFC8C8D2).copy(alpha = 0.24f)

val LiquidGlassVolume = Color(0xFF32ADE6)
val LiquidGlassBrightness = Color(0xFFFFCC00)

@Composable
fun liquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { LiquidGlass.enabled(context) }.value
}

fun Modifier.liquidGlassRim(shape: Shape, strength: Float = 1f): Modifier =
    drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path =
            when (outline) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            }
        val rim = 0.7.dp.toPx()
        val diagonal =
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f * strength),
                0.22f to Color.White.copy(alpha = 0.08f * strength),
                0.5f to Color.Transparent,
                0.78f to Color.White.copy(alpha = 0.05f * strength),
                1f to Color.White.copy(alpha = 0.30f * strength),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        clipPath(path) {
            drawPath(path, diagonal, style = Stroke(width = rim * 2))
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.05f * strength),
                    0.3f to Color.Transparent,
                    endY = size.height,
                )
            )
        }
    }

fun Modifier.liquidGlassPress(enabled: Boolean = true): Modifier =
    if (!enabled) this
    else
        composed {
            val lift = remember { Animatable(0f) }
            val lean = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
            this.pointerInput(Unit) {
                    coroutineScope {
                        awaitPointerEventScope {
                            while (true) {
                                val down =
                                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                                        it.pressed && !it.previousPressed
                                    } ?: continue
                                launch { lift.animateTo(1f, PressSpring) }
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val d = change.position - down.position
                                    val reach = 10.dp.toPx()
                                    val dist = hypot(d.x, d.y)
                                    val pull =
                                        if (dist == 0f) 0f
                                        else reach * (1f - 1f / (1f + dist / (reach * 3f))) / dist
                                    launch { lean.snapTo(Offset(d.x * pull, d.y * pull)) }
                                }
                                launch { lift.animateTo(0f, releaseSpring()) }
                                launch { lean.animateTo(Offset.Zero, releaseSpring()) }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val l = lift.value
                    val off = lean.value
                    val stretchX = abs(off.x) / 10.dp.toPx() * 0.035f
                    val stretchY = abs(off.y) / 10.dp.toPx() * 0.035f
                    scaleX = 1f + 0.06f * l + stretchX - stretchY * 0.5f
                    scaleY = 1f + 0.06f * l + stretchY - stretchX * 0.5f
                    translationX = off.x
                    translationY = off.y
                }
        }

private val PressSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
private fun <T> releaseSpring() = spring<T>(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun Modifier.glassPress(): Modifier = liquidGlassPress(liquidGlassEnabled())

@Composable
fun Modifier.glassRim(shape: Shape, strength: Float = 1f): Modifier =
    if (liquidGlassEnabled()) liquidGlassRim(shape, strength) else this
