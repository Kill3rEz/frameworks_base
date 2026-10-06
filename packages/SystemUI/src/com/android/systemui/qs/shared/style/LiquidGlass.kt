/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.shared.style

import android.content.Context
import android.content.res.Resources
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
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
                        LiquidGlassTune.start()
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

    @JvmStatic fun isEnabled(context: Context): Boolean = enabled(context).value

    @JvmStatic
    fun notificationGap(res: Resources, default: Int): Int =
        if (state?.value == true) (NOTIFICATION_GAP_DP * res.displayMetrics.density).roundToInt()
        else default

    private const val NOTIFICATION_GAP_DP = 8

    @JvmStatic fun viewSurface(): Int = LiquidGlassTune.color("view_surface", 0x1CFFFFFF)

    @JvmStatic
    fun headsUpSurface(): Int = LiquidGlassTune.color("headsup_surface", 0x33FFFFFF)

    @JvmStatic
    fun lockscreenSurface(): Int = LiquidGlassTune.color("lockscreen_surface", 0x8C1C1C1E.toInt())

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowDensity = 0f
    private var glowRadiusDp = -1f

    @JvmStatic
    fun drawGlow(
        canvas: Canvas,
        path: android.graphics.Path,
        bounds: RectF,
        density: Float,
        strength: Float,
    ) {
        if (glowDensity != density) {
            glowDensity = density
        }
        val glowRadius = LiquidGlassTune.f("glow_radius", 6.0f)
        if (glowRadius != glowRadiusDp) {
            glowRadiusDp = glowRadius
            glowPaint.maskFilter =
                if (glowRadius > 0f) BlurMaskFilter(glowRadius * density, BlurMaskFilter.Blur.INNER)
                else null
        }
        fun white(alpha: Float) = android.graphics.Color.argb(alpha * strength, 1f, 1f, 1f)
        val (x0, x1) = lightX(bounds.left, bounds.right)
        glowPaint.shader =
            LinearGradient(
                x0,
                bounds.top,
                x1,
                bounds.bottom,
                intArrayOf(
                    white(LiquidGlassTune.f("glow_tl", 0.1f)),
                    white(LiquidGlassTune.f("glow_tl_mid", 0.0f)),
                    white(LiquidGlassTune.f("glow_mid", 0.0f)),
                    white(LiquidGlassTune.f("glow_br_mid", 0.0f)),
                    white(LiquidGlassTune.f("glow_br", 0.06f)),
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, glowPaint)
    }

    private fun lightX(left: Float, right: Float): Pair<Float, Float> =
        if (LiquidGlassTune.f("light_dir", 1.0f) >= 0.5f) ((left + right) / 2).let { it to it }
        else left to right

    @JvmStatic
    fun rimShader(left: Float, top: Float, right: Float, bottom: Float, strength: Float): Shader {
        fun white(alpha: Float) = android.graphics.Color.argb(alpha * strength, 1f, 1f, 1f)
        val (x0, x1) = lightX(left, right)
        return LinearGradient(
            x0,
            top,
            x1,
            bottom,
            intArrayOf(
                white(LiquidGlassTune.f("rim_tl", 0.9f)),
                white(LiquidGlassTune.f("rim_tl_mid", 0.06f)),
                white(LiquidGlassTune.f("rim_mid", 0.02f)),
                white(LiquidGlassTune.f("rim_br_mid", 0.04f)),
                white(LiquidGlassTune.f("rim_br", 0.4f)),
            ),
            floatArrayOf(0f, 0.18f, 0.5f, 0.82f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private val rimPath = android.graphics.Path()
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rimRect = RectF()
    private val rimRadii = FloatArray(8)

    @JvmStatic
    fun drawRim(canvas: Canvas, rect: RectF, radii: FloatArray, density: Float) {
        if (rect.isEmpty) return
        rimPath.reset()
        rimPath.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
        drawGlow(canvas, rimPath, rect, density, LiquidGlassTune.f("view_glow", 0.5f))
        val dark = LiquidGlassTune.f("edge_dark", 0.35f)
        val hair = if (dark > 0f) LiquidGlassTune.f("edge_dark_width", 0.8f) * density else 0f
        if (dark > 0f) {
            rimRect.set(rect)
            rimRect.inset(hair / 2f, hair / 2f)
            for (i in radii.indices) rimRadii[i] = (radii[i] - hair / 2f).coerceAtLeast(0f)
            rimPath.reset()
            rimPath.addRoundRect(rimRect, rimRadii, android.graphics.Path.Direction.CW)
            darkPaint.strokeWidth = hair
            darkPaint.color = android.graphics.Color.argb(dark, 0f, 0f, 0f)
            canvas.drawPath(rimPath, darkPaint)
        }
        val width = rimWidthDp * density
        rimRect.set(rect)
        rimRect.inset(width / 2f + hair, width / 2f + hair)
        for (i in radii.indices) rimRadii[i] = (radii[i] - width / 2f - hair).coerceAtLeast(0f)
        rimPath.reset()
        rimPath.addRoundRect(rimRect, rimRadii, android.graphics.Path.Direction.CW)
        rimPaint.strokeWidth = width
        rimPaint.shader =
            rimShader(rect.left, rect.top, rect.right, rect.bottom, LiquidGlassTune.f("view_rim", 0.3f))
        canvas.drawPath(rimPath, rimPaint)
    }

    internal val rimWidthDp: Float
        get() = LiquidGlassTune.f("rim_width", 1.0f)
}

object LiquidGlassTune {
    val generation = mutableStateOf(0)
    private var lastGen = ""
    private val handler = Handler(Looper.getMainLooper())
    private val poll =
        object : Runnable {
            override fun run() {
                val gen = SystemProperties.get("debug.lg.gen")
                if (gen != lastGen) {
                    lastGen = gen
                    generation.value++
                }
                handler.postDelayed(this, 500)
            }
        }

    internal fun start() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    @JvmStatic
    fun f(name: String, default: Float): Float =
        SystemProperties.get("debug.lg.$name").toFloatOrNull() ?: default

    @JvmStatic
    fun color(name: String, default: Int): Int =
        SystemProperties.get("debug.lg.$name").removePrefix("0x").removePrefix("#")
            .toLongOrNull(16)?.toInt() ?: default
}

val liquidGlassOn: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LiquidGlass.enabled(LocalContext.current).value.also { LiquidGlassTune.generation.value }

val LiquidGlassSurface: Color
    get() = Color(LiquidGlassTune.color("surface", 0x3AFFFFFF))

val LiquidGlassControl: Color
    get() = Color(LiquidGlassTune.color("control", 0x3DFFFFFF))

val LiquidGlassBlue = Color(0xFF0A84FF)
val LiquidGlassGreen = Color(0xFF30D158)
val LiquidGlassOrange = Color(0xFFFF9F0A)
val LiquidGlassIndigo = Color(0xFF5E5CE6)

fun liquidGlassActive(spec: String): Color =
    when (spec) {
        "airplane" -> LiquidGlassOrange
        "cell", "hotspot" -> LiquidGlassGreen
        "dnd" -> LiquidGlassIndigo
        else -> LiquidGlassBlue
    }

val LiquidGlassVolume = Color(0xFF32ADE6)
val LiquidGlassBrightness = Color(0xFFFFCC00)

@Composable
fun liquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    LiquidGlassTune.generation.value
    return remember(context) { LiquidGlass.enabled(context) }.value
}

fun Modifier.liquidGlassRim(shape: Shape, strength: Float = 1f): Modifier =
    drawWithContent {
        drawContent()
        LiquidGlassTune.generation.value
        val edge = shape.toPath(size, this)
        drawIntoCanvas {
            LiquidGlass.drawGlow(
                it.nativeCanvas,
                edge.asAndroidPath(),
                RectF(0f, 0f, size.width, size.height),
                density,
                strength,
            )
        }
        drawPath(
            edge,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = LiquidGlassTune.f("sheen", 0.0f) * strength),
                LiquidGlassTune.f("sheen_end", 0.45f) to Color.Transparent,
                endY = size.height,
            ),
        )
        val dark = LiquidGlassTune.f("edge_dark", 0.35f)
        if (dark > 0f) {
            val hair = LiquidGlassTune.f("edge_dark_width", 0.8f).dp.toPx()
            val outer = shape.toPath(Size(size.width - hair, size.height - hair), this)
            outer.translate(Offset(hair / 2f, hair / 2f))
            drawPath(outer, Color.Black.copy(alpha = dark * strength), style = Stroke(hair))
        }
        val width = LiquidGlass.rimWidthDp.dp.toPx()
        val inset = if (dark > 0f) LiquidGlassTune.f("edge_dark_width", 0.8f).dp.toPx() else 0f
        val rim = shape.toPath(Size(size.width - width - inset * 2, size.height - width - inset * 2), this)
        rim.translate(Offset(width / 2f + inset, width / 2f + inset))
        drawIntoCanvas {
            val paint = rimStroke
            paint.strokeWidth = width
            paint.shader = LiquidGlass.rimShader(0f, 0f, size.width, size.height, strength)
            it.nativeCanvas.drawPath(rim.asAndroidPath(), paint)
        }
    }

private val rimStroke =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }

private fun Shape.toPath(size: Size, scope: androidx.compose.ui.graphics.drawscope.DrawScope): Path =
    when (val outline = createOutline(size, scope.layoutDirection, scope)) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
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
    if (liquidGlassEnabled()) glassBlurRegion(shape).liquidGlassRim(shape, strength) else this

val LocalGlassBlurAllowed = compositionLocalOf<() -> Boolean> { { true } }

@Composable
fun Modifier.glassBlurRegion(shape: Shape): Modifier {
    val view = LocalView.current
    val drawable = remember(view) { arrayOfNulls<BackgroundBlurDrawable>(1) }
    LiquidGlassTune.generation.value
    val radius = LiquidGlassTune.f("tile_blur", 90.0f).toInt()
    val allowed = LocalGlassBlurAllowed.current
    SideEffect { if (radius <= 0) drawable[0]?.setVisible(false, false) }
    DisposableEffect(view) { onDispose { drawable[0]?.setVisible(false, false) } }
    if (radius <= 0) return this
    return drawBehind {
        if (!allowed()) {
            drawable[0]?.setVisible(false, false)
            return@drawBehind
        }
        val root = view.viewRootImpl ?: return@drawBehind
        val blur =
            drawable[0]
                ?: root.createBackgroundBlurDrawable().also {
                    it.setXfermode(null)
                    drawable[0] = it
                }
        val corner =
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                else -> 0f
            }
        blur.setVisible(true, false)
        blur.setColor(0)
        blur.setBlurRadius(radius)
        blur.setCornerRadius(corner)
        blur.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { blur.draw(it.nativeCanvas) }
    }
}

object LiquidGlassGlyphs {
    private val bySpec =
        mapOf(
            "wifi" to R.drawable.lg_glyph_wifi,
            "internet" to R.drawable.lg_glyph_wifi,
            "cell" to R.drawable.lg_glyph_antenna_radiowaves_left_right,
            "cast" to R.drawable.lg_glyph_rectangle_on_rectangle,
            "dnd" to R.drawable.lg_glyph_moon_fill,
            "wallet" to R.drawable.lg_glyph_creditcard_fill,
            "airplane" to R.drawable.lg_glyph_airplane,
            "rotation" to R.drawable.lg_glyph_lock_rotation,
            "battery" to R.drawable.lg_glyph_battery_25,
            "alarm" to R.drawable.lg_glyph_alarm_fill,
            "controls" to R.drawable.lg_glyph_house_fill,
            "screenrecord" to R.drawable.lg_glyph_smallcircle_fill_circle,
            "hotspot" to R.drawable.lg_glyph_personalhotspot,
            "location" to R.drawable.lg_glyph_location_fill,
            "mictoggle" to R.drawable.lg_glyph_mic_fill,
            "cameratoggle" to R.drawable.lg_glyph_camera_fill,
            "qr_code_scanner" to R.drawable.lg_glyph_qrcode_viewfinder,
            "dark" to R.drawable.lg_glyph_circle_lefthalf_fill,
        )

    @JvmStatic fun forSpec(spec: String): Int? = bySpec[spec]

    fun swap(spec: String, icon: Icon): Icon =
        forSpec(spec)?.let { Icon.Resource(it, icon.contentDescription) } ?: icon
}
