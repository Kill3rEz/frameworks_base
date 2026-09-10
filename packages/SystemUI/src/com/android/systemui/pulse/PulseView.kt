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
        alpha = 1f
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
        if (isAttached && isVisible) {
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

    fun setVisibility(visible: Boolean) {
        isVisible = visible
        visibility = if (visible) VISIBLE else GONE
        if (visible) {
            alpha = 1f
            postInvalidateOnAnimation()
        }
    }

    fun fadeIn(durationMs: Long) {
        fadeAnimator?.cancel()
        setVisibility(true)
        fadeAnimator = ValueAnimator.ofFloat(alpha, 1f).apply {
            duration = durationMs
            interpolator = fadeInterpolator
            addUpdateListener { animation ->
                alpha = animation.animatedValue as Float
                invalidate()
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
    }

    fun fadeOut(durationMs: Long, onComplete: (() -> Unit)? = null) {
        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(alpha, 0f).apply {
            duration = durationMs
            interpolator = fadeInterpolator
            addUpdateListener { animation ->
                alpha = animation.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    alpha = 0f
                    setVisibility(false)
                    fadeAnimator = null
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    setVisibility(false)
                    fadeAnimator = null
                    onComplete?.invoke()
                }
            })
            start()
        }
    }
}
