/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.pulse

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

class PulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var renderer: PulseRenderer? = null
    private var isAttached = false
    private var isVisible = false
    private var settingsRepo: PulseSettingsRepository? = null

    private var fadeAnimator: ValueAnimator? = null
    private val fadeInterpolator = DecelerateInterpolator()

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        alpha = 0f
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun initialize(settingsRepo: PulseSettingsRepository) {
        this.settingsRepo = settingsRepo
        renderer = PulseRenderer(context, settingsRepo)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false
        fadeAnimator?.cancel()
        fadeAnimator = null
        renderer?.cleanup()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAttached && isVisible && alpha > 0f) {
            renderer?.onDraw(canvas, width, height)
            postInvalidateOnAnimation()
        }
    }

    fun updateHeights(heights: FloatArray) {
        if (isAttached && isVisible) {
            renderer?.updateHeights(heights)
            postInvalidateOnAnimation()
        }
    }

    fun onMediaColorsChanged(color: Int) {
        post { renderer?.onMediaColorsChanged(color) }
    }

    @JvmOverloads
    fun setVisibility(visible: Boolean, animate: Boolean = true, durationMs: Long = 300L) {
        if (isVisible == visible && ((visible && visibility == VISIBLE) || (!visible && visibility == GONE))) {
            return
        }
        isVisible = visible
        fadeAnimator?.cancel()
        fadeAnimator = null

        if (!animate) {
            visibility = if (visible) VISIBLE else GONE
            alpha = if (visible) 1f else 0f
            if (visible) {
                postInvalidateOnAnimation()
            }
            return
        }

        if (visible) {
            visibility = VISIBLE
            fadeAnimator = ValueAnimator.ofFloat(alpha, 1f).apply {
                duration = durationMs
                interpolator = fadeInterpolator
                addUpdateListener { animation ->
                    alpha = animation.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        alpha = 1f
                        fadeAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        fadeAnimator = null
                    }
                })
                start()
            }
        } else {
            fadeAnimator = ValueAnimator.ofFloat(alpha, 0f).apply {
                duration = durationMs
                interpolator = fadeInterpolator
                addUpdateListener { animation ->
                    alpha = animation.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        alpha = 0f
                        visibility = GONE
                        fadeAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        alpha = 0f
                        visibility = GONE
                        fadeAnimator = null
                    }
                })
                start()
            }
        }
    }

    fun fadeIn(durationMs: Long) {
        setVisibility(true, animate = true, durationMs = durationMs)
    }

    fun fadeOut(durationMs: Long, onComplete: (() -> Unit)? = null) {
        fadeAnimator?.cancel()
        isVisible = false

        fadeAnimator = ValueAnimator.ofFloat(alpha, 0f).apply {
            duration = durationMs
            interpolator = fadeInterpolator
            addUpdateListener { animation ->
                alpha = animation.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    alpha = 0f
                    visibility = GONE
                    fadeAnimator = null
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    alpha = 0f
                    visibility = GONE
                    fadeAnimator = null
                    onComplete?.invoke()
                }
            })
            start()
        }
    }
}

