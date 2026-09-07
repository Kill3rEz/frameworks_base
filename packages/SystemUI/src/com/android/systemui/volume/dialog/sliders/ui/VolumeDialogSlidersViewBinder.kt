/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.dialog.sliders.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.compose.ui.util.fastForEachIndexed
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.res.R
import com.android.systemui.util.children
import com.android.systemui.volume.VolumePanelStyle
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogExpansionInteractor
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSlidersViewModel
import com.android.systemui.volume.dialog.ui.binder.ViewBinder
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onEach

@VolumeDialogScope
class VolumeDialogSlidersViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogSlidersViewModel,
    private val dialogViewModel: VolumeDialogViewModel,
    private val expansionInteractor: VolumeDialogExpansionInteractor,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
) : ViewBinder {

    private val isExpandableStyle: Boolean
        get() = expansionInteractor.isExpandable

    private val isOneUiStyle: Boolean
        get() = expansionInteractor.style == VolumePanelStyle.ONE_UI

    override fun CoroutineScope.bind(view: View) {

        val floatingSlidersContainer: ViewGroup =
            view.requireViewById(R.id.volume_dialog_floating_sliders_container)
        val mainSliderContainer: View =
            view.requireViewById(R.id.volume_dialog_main_slider_container)
        val background: View = view.requireViewById(R.id.volume_dialog_background)
        val bottomSection: View = view.requireViewById(R.id.volume_dialog_bottom_section_container)
        val topSection: View = view.requireViewById(R.id.volume_dialog_top_section_container)

        launchTraced("VDSVB#addTouchableBounds") {
            dialogViewModel.addTouchableBounds(mainSliderContainer, floatingSlidersContainer)
        }

        if (isOneUiStyle) {
            launchTraced("VDSVB#addCardTouchableBounds") { dialogViewModel.addTouchableBounds(view) }
            val card: Drawable? =
                view.background?.let { if (viewModel.showBlur) view.frostedCard(it) else it }
            if (viewModel.showBlur && card != null) {
                launchTraced("VDSVB#cardBlur") {
                    windowRootViewBlurInteractor.isBlurCurrentlySupported.collect { supported ->
                        view.applyCardBlurSupport(card, supported)
                    }
                }
            }
            launchTraced("VDSVB#cardVisibility") {
                expansionInteractor.isExpanded.collect { isExpanded ->
                    view.background = if (isExpanded) card else null
                }
            }
        } else if (isExpandableStyle) {
            background.visibility = View.INVISIBLE
            mainSliderContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height =
                    view.context.resources.getDimensionPixelSize(
                        R.dimen.volume_panel_expandable_slider_height
                    )
            }
            (floatingSlidersContainer as? LinearLayout)?.showDividers =
                LinearLayout.SHOW_DIVIDER_NONE
        }

        viewModel.sliders
            .onEach { uiModel ->
                bindSlider(
                    uiModel.sliderComponent,
                    mainSliderContainer,
                    arrayOf(mainSliderContainer, background, bottomSection, topSection),
                )

                val floatingSliderViewBinders = uiModel.floatingSliderComponent
                val floatingSliderViewLayoutId =
                    when {
                        !viewModel.isVolumeDialogVertical ->
                            R.layout.volume_dialog_slider_floating_horizontal
                        isExpandableStyle -> R.layout.volume_dialog_slider
                        else -> R.layout.volume_dialog_slider_floating
                    }
                floatingSlidersContainer.ensureChildCount(
                    viewLayoutId = floatingSliderViewLayoutId,
                    count = floatingSliderViewBinders.size,
                )
                floatingSliderViewBinders.fastForEachIndexed { index, sliderComponent ->
                    val sliderContainer = floatingSlidersContainer.getChildAt(index)
                    if (viewModel.showBlur && !isExpandableStyle) {
                        sliderContainer.updateBackground()
                    }
                    bindSlider(sliderComponent, sliderContainer, arrayOf(sliderContainer))
                }
            }
            .launchInTraced("VDSVB#sliders", this)

        if (viewModel.showBlur && !isExpandableStyle) {
            launchTraced("VDSVB#isBlurCurrentlySupported") {
                windowRootViewBlurInteractor.isBlurCurrentlySupported.collect { supported ->
                    for (child in floatingSlidersContainer.children) {
                        child.setIsBlurSupported(supported)
                    }
                }
            }
        }
    }

    private fun View.frostedCard(card: Drawable): Drawable {
        if (card is LayerDrawable) {
            return card
        }
        val blurDrawable = viewRootImpl.createBackgroundBlurDrawable()
        blurDrawable.setCornerRadius(
            context.resources
                .getDimensionPixelSize(R.dimen.volume_panel_oneui_background_corner_radius)
                .toFloat()
        )
        blurDrawable.setBlurRadius(0)
        return LayerDrawable(arrayOf<Drawable>(blurDrawable, card.mutate())).also {
            applyCardBlurSupport(it, windowRootViewBlurInteractor.isBlurCurrentlySupported.value)
        }
    }

    private fun View.applyCardBlurSupport(card: Drawable, supported: Boolean) {
        val layers = card as? LayerDrawable ?: return
        (layers.getDrawable(0) as BackgroundBlurDrawable).setBlurRadius(
            if (supported) {
                context.resources.getDimensionPixelSize(
                    R.dimen.volume_dialog_background_surface_blur_radius
                )
            } else {
                0
            }
        )
        (layers.getDrawable(1) as GradientDrawable).setColor(
            context.getColor(
                if (supported) {
                    R.color.volume_panel_oneui_background_blur
                } else {
                    R.color.volume_panel_oneui_background_fallback
                }
            )
        )
    }

    private fun View.updateBackground() {
        if (background is LayerDrawable) {
            return
        }
        val surfaceEffect = background as GradientDrawable
        val blurDrawable = viewRootImpl.createBackgroundBlurDrawable()
        val dialogCornerRadius: Int =
            context.resources.getDimensionPixelSize(
                R.dimen.volume_dialog_floating_slider_background_corner_radius
            )
        blurDrawable.setCornerRadius(dialogCornerRadius.toFloat())
        blurDrawable.setBlurRadius(0)
        background = LayerDrawable(arrayOf<Drawable>(blurDrawable, surfaceEffect))
        setIsBlurSupported(windowRootViewBlurInteractor.isBlurCurrentlySupported.value)
    }

    private fun View.setIsBlurSupported(supported: Boolean) {
        val layers = (background as LayerDrawable)
        (layers.getDrawable(0) as BackgroundBlurDrawable).setBlurRadius(
            if (supported) {
                context.resources.getDimensionPixelSize(
                    R.dimen.volume_dialog_background_surface_blur_radius
                )
            } else {
                0
            }
        )
        (layers.getDrawable(1) as GradientDrawable).setColor(
            context.getColor(
                if (supported) {
                    R.color.volume_dialog_view_background_blur
                } else {
                    R.color.volume_dialog_view_background_blur_fallback
                }
            )
        )
    }

    private fun CoroutineScope.bindSlider(
        component: VolumeDialogSliderComponent,
        sliderContainer: View,
        viewsToAnimate: Array<View>,
    ) {
        with(component.sliderViewBinder()) { bind(sliderContainer) }
        with(component.overscrollViewBinder()) { bind(sliderContainer, viewsToAnimate) }
    }
}

private fun ViewGroup.ensureChildCount(@LayoutRes viewLayoutId: Int, count: Int) {
    val childCountDelta = childCount - count
    when {
        childCountDelta > 0 -> {
            removeViews(0, childCountDelta)
        }
        childCountDelta < 0 -> {
            val inflater = LayoutInflater.from(context)
            repeat(-childCountDelta) { inflater.inflate(viewLayoutId, this, true) }
        }
    }
}
