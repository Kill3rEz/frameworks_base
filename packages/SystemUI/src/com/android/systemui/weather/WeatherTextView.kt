/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.weather

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import com.android.internal.util.custom.OmniJawsClient
import com.android.systemui.Dependency
import com.android.systemui.plugins.ActivityStarter

class WeatherTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    isCustomClock: Boolean = true,
) : TextView(context, attrs, defStyle) {
    private val controller = WeatherViewController(context, ImageView(context), this, this, isCustomClock)

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
}
