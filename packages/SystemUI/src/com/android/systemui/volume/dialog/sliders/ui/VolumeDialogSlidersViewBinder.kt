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

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.compose.ui.util.fastForEachIndexed
import com.android.app.animation.Interpolators
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

    /**
     * Both PenguinOS styles draw every stream as a bare pill, so neither wants the per-slider cards
     * the stock floating sliders come with.
     */
    private val isExpandableStyle: Boolean
        get() = expansionInteractor.isExpandable

    private val isOneUiStyle: Boolean
        get() = expansionInteractor.style.isCard

    private val isMyUiStyle: Boolean
        get() = expansionInteractor.style == VolumePanelStyle.MYUI

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
            // Unlike the other styles the card is the whole panel here, so a tap anywhere on it has
            // to reach the dialog rather than the app behind it.
            launchTraced("VDSVB#addCardTouchableBounds") { dialogViewModel.addTouchableBounds(view) }
            // MyUI keeps its captions button outside the card, so the card is a view of its own
            // there rather than the root's background.
            val cardView: View =
                if (isMyUiStyle) view.requireViewById(R.id.volume_dialog_myui_card) else view
            // Built once and put back when the panel expands. The One UI design has no card behind
            // the collapsed pill, and taking the drawable off is what stops the blur as well -
            // fading it out would leave the blurred rectangle behind. MyUI keeps its column.
            val card: Drawable? =
                cardView.background?.let {
                    if (viewModel.showBlur) cardView.frostedCard(it) else it
                }
            if (viewModel.showBlur && card != null) {
                launchTraced("VDSVB#cardBlur") {
                    windowRootViewBlurInteractor.isBlurCurrentlySupported.collect { supported ->
                        cardView.applyCardBlurSupport(card, supported)
                    }
                }
            }
            if (isMyUiStyle) {
                cardView.background = card
                // A pill while it is the collapsed column, a rounded card once expanded.
                launchTraced("VDSVB#cardCorners") {
                    expansionInteractor.isExpanded.collect { isExpanded ->
                        card?.setCornerRadius(
                            cardView.context.resources
                                .getDimensionPixelSize(
                                    if (isExpanded) {
                                        R.dimen.volume_panel_myui_background_corner_radius
                                    } else {
                                        R.dimen.volume_panel_myui_collapsed_corner_radius
                                    }
                                )
                                .toFloat()
                        )
                    }
                }
            } else {
                launchTraced("VDSVB#cardVisibility") {
                    expansionInteractor.isExpanded.collect { isExpanded ->
                        cardView.background = if (isExpanded) card else null
                    }
                }
            }
        } else if (isExpandableStyle) {
            // Invisible rather than gone: the top and bottom sections are constrained to this view,
            // so it still has to take up its space.
            background.visibility = View.INVISIBLE
            // Shorter pills than the stock slider, and the floating ones follow because they are
            // constrained to the top and bottom of this container. A fixed height rather than a
            // maximum: with match constraints the container keeps the stock height and the buttons
            // below it end up a chunk of empty space away from the pills.
            mainSliderContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height =
                    view.context.resources.getDimensionPixelSize(
                        R.dimen.volume_panel_expandable_slider_height
                    )
            }
            // The pills carry their own spacing inside the slider containers, so the extra divider
            // between them would double the gap.
            (floatingSlidersContainer as? LinearLayout)?.showDividers =
                LinearLayout.SHOW_DIVIDER_NONE
            // The stock floating sliders sit on a card that pads them, which the negative margins
            // make room for. Bare, they would come out taller than the primary pill.
            (floatingSlidersContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = 0
                it.bottomMargin = 0
                floatingSlidersContainer.layoutParams = it
            }
            (floatingSlidersContainer as? ViewGroup)?.layoutTransition =
                expandTransition(view)
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
                        // No card around it, so the expanded sliders match the primary one.
                        isExpandableStyle -> R.layout.volume_dialog_slider
                        else -> R.layout.volume_dialog_slider_floating
                    }
                floatingSlidersContainer.ensureChildCount(
                    viewLayoutId = floatingSliderViewLayoutId,
                    count = floatingSliderViewBinders.size,
                )
                floatingSliderViewBinders.fastForEachIndexed { index, sliderComponent ->
                    val sliderContainer = floatingSlidersContainer.getChildAt(index)
                    if (isMyUiStyle) {
                        // The stock slider layout is wider than a MyUI track and its icon.
                        sliderContainer.updateLayoutParams<ViewGroup.LayoutParams> {
                            width =
                                view.context.resources.getDimensionPixelSize(
                                    R.dimen.volume_panel_myui_slider_width
                                )
                        }
                    }
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

    /** Wraps the One UI card in a blur layer, so that the panel frosts the wallpaper behind it. */
    private fun View.frostedCard(card: Drawable): Drawable {
        if (card is LayerDrawable) {
            return card
        }
        val blurDrawable = viewRootImpl.createBackgroundBlurDrawable()
        blurDrawable.setCornerRadius(
            context.resources
                .getDimensionPixelSize(
                    if (isMyUiStyle) {
                        R.dimen.volume_panel_myui_background_corner_radius
                    } else {
                        R.dimen.volume_panel_oneui_background_corner_radius
                    }
                )
                .toFloat()
        )
        blurDrawable.setBlurRadius(0)
        return LayerDrawable(arrayOf<Drawable>(blurDrawable, card.mutate())).also {
            applyCardBlurSupport(it, windowRootViewBlurInteractor.isBlurCurrentlySupported.value)
        }
    }

    /**
     * The extra pills slide out from behind the primary one as they fade in, rather than popping
     * in.
     */
    private fun expandTransition(view: View): LayoutTransition {
        val onLeft =
            Settings.Secure.getInt(
                view.context.contentResolver,
                Settings.Secure.VOLUME_PANEL_ON_LEFT,
                0,
            ) == 1
        val shift = view.resources.displayMetrics.density * 32f * (if (onLeft) -1 else 1)
        return LayoutTransition().apply {
            setAnimator(
                LayoutTransition.APPEARING,
                ObjectAnimator.ofPropertyValuesHolder(
                    null as Any?,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, shift, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1f),
                ),
            )
            setInterpolator(LayoutTransition.APPEARING, Interpolators.EMPHASIZED_DECELERATE)
            setDuration(LayoutTransition.APPEARING, 300)
            setStartDelay(LayoutTransition.APPEARING, 0)
            // A leaving pill keeps its space until it is done, but the window shrinks at once and
            // would push the primary pill off the screen for the length of the animation.
            disableTransitionType(LayoutTransition.DISAPPEARING)
            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        }
    }

    private fun Drawable.setCornerRadius(radius: Float) {
        when (this) {
            is LayerDrawable ->
                (0 until numberOfLayers).forEach { getDrawable(it).setCornerRadius(radius) }
            is BackgroundBlurDrawable -> setCornerRadius(radius)
            is GradientDrawable -> cornerRadius = radius
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
                when {
                    isMyUiStyle && supported -> R.color.volume_panel_myui_background_blur
                    isMyUiStyle -> R.color.volume_panel_myui_background_fallback
                    supported -> R.color.volume_panel_oneui_background_blur
                    else -> R.color.volume_panel_oneui_background_fallback
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
