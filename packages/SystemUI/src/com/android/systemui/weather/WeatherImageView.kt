/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.weather

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.TextView

import com.android.internal.util.custom.OmniJawsClient
import com.android.systemui.Dependency
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R

class WeatherImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    isCustomClock: Boolean = true,
) : ImageView(context, attrs, defStyle) {
    private val maxSizePx = context.resources.getDimension(R.dimen.weather_image_max_size).toInt()
    private val controller = WeatherViewController(context, this, TextView(context), this, isCustomClock)

    init {
        visibility = View.GONE
        setOnClickListener {
            val intent = OmniJawsClient.get().getWeatherActivityIntent(context)
            try {
                Dependency.get(ActivityStarter::class.java).postStartActivityDismissingKeyguard(intent, 0)
            } catch (e: Exception) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.init()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.removeObserver()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (drawable == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> maxSizePx.coerceAtMost(MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: maxSizePx)
        }
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> maxSizePx.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: maxSizePx)
        }
        setMeasuredDimension(width, height)
    }
}
