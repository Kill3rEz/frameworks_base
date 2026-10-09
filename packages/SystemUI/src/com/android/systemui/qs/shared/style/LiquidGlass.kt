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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
        val w = kotlin.math.ceil(bounds.width()).toInt()
        val h = kotlin.math.ceil(bounds.height()).toInt()
        if (w <= 0 || h <= 0) return
        drawGlow(canvas, shapeHash(path, bounds), { path }, bounds, density, strength)
    }

    @JvmStatic
    fun drawGlow(
        canvas: Canvas,
        shape: Int,
        path: () -> android.graphics.Path,
        bounds: RectF,
        density: Float,
        strength: Float,
    ) {
        val w = kotlin.math.ceil(bounds.width()).toInt()
        val h = kotlin.math.ceil(bounds.height()).toInt()
        if (w <= 0 || h <= 0) return
        val key = GlowKey(w, h, shape, strength, density, LiquidGlassTune.tag)
        val glow =
            glowCache.get(key)
                ?: renderGlow(path(), bounds, w, h, density, strength).also { glowCache.put(key, it) }
        canvas.drawBitmap(glow, bounds.left, bounds.top, null)
    }

    private data class GlowKey(
        val width: Int,
        val height: Int,
        val shape: Int,
        val strength: Float,
        val density: Float,
        val tune: String,
    )

    private val glowCache =
        object : android.util.LruCache<GlowKey, android.graphics.Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: GlowKey, value: android.graphics.Bitmap) = value.allocationByteCount
        }

    @JvmStatic
    fun shapeHashOf(path: android.graphics.Path, bounds: RectF): Int = shapeHash(path, bounds)

    private fun shapeHash(path: android.graphics.Path, bounds: RectF): Int {
        val points = path.approximate(0.5f)
        var hash = 1
        for (i in points.indices step 3) {
            hash = 31 * hash + (points[i + 1] - bounds.left).toBits()
            hash = 31 * hash + (points[i + 2] - bounds.top).toBits()
        }
        return hash
    }

    private fun renderGlow(
        path: android.graphics.Path,
        bounds: RectF,
        width: Int,
        height: Int,
        density: Float,
        strength: Float,
    ): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val glowRadius = LiquidGlassTune.f("glow_radius", 6.0f)
        if (glowRadius != glowRadiusDp || glowDensity != density) {
            glowRadiusDp = glowRadius
            glowDensity = density
            glowPaint.maskFilter =
                if (glowRadius > 0f) BlurMaskFilter(glowRadius * density, BlurMaskFilter.Blur.INNER)
                else null
        }
        fun white(alpha: Float) = android.graphics.Color.argb(alpha * strength, 1f, 1f, 1f)
        val (x0, x1) = lightX(0f, width.toFloat())
        glowPaint.shader =
            LinearGradient(
                x0,
                0f,
                x1,
                height.toFloat(),
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
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawPath(path, glowPaint)
        return bitmap
    }

    private fun drawRoundRectGlow(
        canvas: Canvas,
        rect: RectF,
        radii: FloatArray,
        density: Float,
        strength: Float,
    ) {
        val glowRadius = LiquidGlassTune.f("glow_radius", 6.0f) * density
        val reach = kotlin.math.ceil(3f * (glowRadius * 0.57735f + 0.5f)).toInt() + 1
        val tl = radii[0].toInt()
        val tr = radii[2].toInt()
        val br = radii[4].toInt()
        val bl = radii[6].toInt()
        val key = RoundGlowKey(tl, tr, br, bl, glowRadius, LiquidGlassTune.tag)
        val mask = roundGlowCache.get(key) ?: renderRoundGlowMask(key, reach).also { roundGlowCache.put(key, it) }
        val left = maxOf(tl, bl) + reach
        val top = maxOf(tl, tr) + reach
        val right = maxOf(tr, br) + reach
        val bottom = maxOf(bl, br) + reach
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        val l = if (w < left + right) w * left / (left + right) else left.toFloat()
        val r = if (w < left + right) w - l else right.toFloat()
        val t = if (h < top + bottom) h * top / (top + bottom) else top.toFloat()
        val b = if (h < top + bottom) h - t else bottom.toFloat()
        fun white(alpha: Float) = android.graphics.Color.argb(alpha * strength, 1f, 1f, 1f)
        val (x0, x1) = lightX(rect.left, rect.right)
        roundGlowPaint.shader =
            LinearGradient(
                x0,
                rect.top,
                x1,
                rect.bottom,
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
        val mw = mask.width
        val mh = mask.height
        val xs = floatArrayOf(0f, l, w - r, w)
        val ys = floatArrayOf(0f, t, h - b, h)
        val sxs = intArrayOf(0, kotlin.math.ceil(l).toInt(), mw - kotlin.math.ceil(r).toInt(), mw)
        val sys = intArrayOf(0, kotlin.math.ceil(t).toInt(), mh - kotlin.math.ceil(b).toInt(), mh)
        sxs[1] = minOf(sxs[1], left)
        sxs[2] = maxOf(sxs[2], mw - right)
        sys[1] = minOf(sys[1], top)
        sys[2] = maxOf(sys[2], mh - bottom)
        for (row in 0..2) {
            if (ys[row + 1] <= ys[row]) continue
            for (col in 0..2) {
                if (xs[col + 1] <= xs[col]) continue
                glowSrc.set(sxs[col], sys[row], sxs[col + 1], sys[row + 1])
                if (glowSrc.isEmpty) continue
                glowDst.set(
                    rect.left + xs[col],
                    rect.top + ys[row],
                    rect.left + xs[col + 1],
                    rect.top + ys[row + 1],
                )
                canvas.drawBitmap(mask, glowSrc, glowDst, roundGlowPaint)
            }
        }
    }

    private data class RoundGlowKey(
        val tl: Int,
        val tr: Int,
        val br: Int,
        val bl: Int,
        val glowRadius: Float,
        val tune: String,
    )

    private fun renderRoundGlowMask(key: RoundGlowKey, reach: Int): android.graphics.Bitmap {
        val middle = 4
        val width = maxOf(key.tl, key.bl) + maxOf(key.tr, key.br) + 2 * reach + middle
        val height = maxOf(key.tl, key.tr) + maxOf(key.bl, key.br) + 2 * reach + middle
        val bitmap =
            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ALPHA_8)
        val path = android.graphics.Path()
        path.addRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            floatArrayOf(
                key.tl.toFloat(), key.tl.toFloat(), key.tr.toFloat(), key.tr.toFloat(),
                key.br.toFloat(), key.br.toFloat(), key.bl.toFloat(), key.bl.toFloat(),
            ),
            android.graphics.Path.Direction.CW,
        )
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (key.glowRadius > 0f) {
                    maskFilter = BlurMaskFilter(key.glowRadius, BlurMaskFilter.Blur.INNER)
                }
            }
        Canvas(bitmap).drawPath(path, paint)
        return bitmap
    }

    private val roundGlowCache = android.util.LruCache<RoundGlowKey, android.graphics.Bitmap>(64)
    private val roundGlowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowSrc = android.graphics.Rect()
    private val glowDst = RectF()

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
        drawRoundRectGlow(canvas, rect, radii, density, LiquidGlassTune.f("view_glow", 0.5f))
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
                    values.clear()
                    generation.value++
                }
                handler.postDelayed(this, 500)
            }
        }

    internal fun start() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    val tag: String
        get() = lastGen

    private val values = java.util.concurrent.ConcurrentHashMap<String, Float>()

    @JvmStatic
    fun f(name: String, default: Float): Float =
        values.getOrPut(name) { SystemProperties.get("debug.lg.$name").toFloatOrNull() ?: Float.NaN }
            .let { if (it.isNaN()) default else it }

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
    drawWithCache {
        LiquidGlassTune.generation.value
        val bounds = RectF(0f, 0f, size.width, size.height)
        val edge = shape.toPath(size, this).asAndroidPath()
        val glowShape = LiquidGlass.shapeHashOf(edge, bounds)
        val sheen =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = LiquidGlassTune.f("sheen", 0.0f) * strength),
                LiquidGlassTune.f("sheen_end", 0.45f) to Color.Transparent,
                endY = size.height,
            )
        val sheenOn = LiquidGlassTune.f("sheen", 0.0f) > 0f
        val edgePath = edge.asComposePath()
        val dark = LiquidGlassTune.f("edge_dark", 0.35f)
        val hair = LiquidGlassTune.f("edge_dark_width", 0.8f).dp.toPx()
        val outer =
            if (dark > 0f) {
                shape.toPath(Size(size.width - hair, size.height - hair), this).apply {
                    translate(Offset(hair / 2f, hair / 2f))
                }
            } else null
        val width = LiquidGlass.rimWidthDp.dp.toPx()
        val inset = if (dark > 0f) hair else 0f
        val rim =
            shape.toPath(Size(size.width - width - inset * 2, size.height - width - inset * 2), this)
                .apply { translate(Offset(width / 2f + inset, width / 2f + inset)) }
                .asAndroidPath()
        val rimPaint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = width
                shader = LiquidGlass.rimShader(0f, 0f, size.width, size.height, strength)
            }
        onDrawWithContent {
            drawContent()
            drawIntoCanvas {
                LiquidGlass.drawGlow(it.nativeCanvas, glowShape, { edge }, bounds, density, strength)
            }
            if (sheenOn) drawPath(edgePath, sheen)
            if (outer != null) drawPath(outer, Color.Black.copy(alpha = dark * strength), style = Stroke(hair))
            drawIntoCanvas { it.nativeCanvas.drawPath(rim, rimPaint) }
        }
    }

private fun Shape.toPath(size: Size, scope: androidx.compose.ui.draw.CacheDrawScope): Path =
    when (val outline = createOutline(size, scope.layoutDirection, scope)) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }

fun Modifier.liquidGlassPress(enabled: Boolean = true): Modifier =
    if (!enabled) this
    else
        composed {
            val press = remember { GlassPress() }
            this.pointerInput(Unit) { trackGlassPress(press) { true } }.glassPressEffect(press)
        }

class GlassPress {
    internal val lift = Animatable(0f)
    internal val lean = Animatable(Offset.Zero, Offset.VectorConverter)
    internal var bounds = androidx.compose.ui.geometry.Rect.Zero
}

val LockscreenClockPress = GlassPress()

fun Modifier.glassPressTracker(press: GlassPress): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    this.onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(press) { trackGlassPress(press) { press.bounds.contains(it + origin) } }
}

fun Modifier.glassPressTarget(press: GlassPress): Modifier =
    onGloballyPositioned { press.bounds = it.boundsInWindow() }.glassPressEffect(press)

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackGlassPress(
    press: GlassPress,
    accept: (Offset) -> Boolean,
) {
    val lift = press.lift
    val lean = press.lean
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.pressed && !it.previousPressed
                    } ?: continue
                if (!accept(down.position)) continue
                launch { lift.animateTo(1f, PressSpring) }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
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

private fun Modifier.glassPressEffect(press: GlassPress): Modifier = graphicsLayer {
    val l = press.lift.value
    val off = press.lean.value
    val stretchX = abs(off.x) / 10.dp.toPx() * 0.035f
    val stretchY = abs(off.y) / 10.dp.toPx() * 0.035f
    scaleX = 1f + 0.06f * l + stretchX - stretchY * 0.5f
    scaleY = 1f + 0.06f * l + stretchY - stretchX * 0.5f
    translationX = off.x
    translationY = off.y
}

private val PressSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
private fun <T> releaseSpring() = spring<T>(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun Modifier.glassPress(): Modifier = liquidGlassPress(liquidGlassEnabled())

@Composable
fun Modifier.glassRim(shape: Shape, strength: Float = 1f): Modifier =
    if (liquidGlassEnabled()) glassBlurRegion(shape).liquidGlassRim(shape, strength) else this

val LocalGlassBlurAllowed = compositionLocalOf<() -> Boolean> { { true } }

val LocalGlassBlurAlpha = compositionLocalOf<() -> Float> { { 1f } }

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
    val visible = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val fade = LocalGlassBlurAlpha.current
    return onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val topLeft = coordinates.windowToLocal(bounds.topLeft)
            val bottomRight = coordinates.windowToLocal(bounds.bottomRight)
            visible.value =
                androidx.compose.ui.geometry.Rect(topLeft, bottomRight)
                    .intersect(androidx.compose.ui.geometry.Rect(Offset.Zero, coordinates.size.toSize()))
        }
        .drawBehind {
            val shown = visible.value ?: androidx.compose.ui.geometry.Rect(Offset.Zero, size)
            val alpha = fade().coerceIn(0f, 1f)
            if (!allowed() || alpha < 0.01f || shown.width < 1f || shown.height < 1f) {
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
            blur.setAlpha((alpha * 255).toInt())
            blur.setBounds(
                shown.left.toInt(),
                shown.top.toInt(),
                shown.right.toInt(),
                shown.bottom.toInt(),
            )
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
