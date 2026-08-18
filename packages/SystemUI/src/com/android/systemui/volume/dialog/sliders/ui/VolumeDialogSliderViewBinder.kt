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

import android.view.View
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.VolumePanelStyle
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogExpansionInteractor
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderScope
import com.android.systemui.volume.dialog.sliders.ui.compose.SliderIconsState
import com.android.systemui.volume.dialog.sliders.ui.compose.SliderTrack
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogOverscrollViewModel
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import com.android.systemui.volume.ui.compose.slider.AccessibilityParams
import com.android.systemui.volume.ui.compose.slider.Haptics
import com.android.systemui.volume.ui.compose.slider.Slider
import com.android.systemui.volume.ui.compose.slider.SliderIcon
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@VolumeDialogSliderScope
class VolumeDialogSliderViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogSliderViewModel,
    private val overscrollViewModel: VolumeDialogOverscrollViewModel,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val expandedAudioTileDetailsFeatureInteractor: ExpandedAudioTileDetailsFeatureInteractor,
    private val expansionInteractor: VolumeDialogExpansionInteractor,
) {
    fun bind(view: View) {
        // Use horizontal volume dialog if the audio tile details view is enabled
        val isVolumeDialogVertical = !expandedAudioTileDetailsFeatureInteractor.isEnabled()
        val sliderComposeViewId =
            if (isVolumeDialogVertical) {
                R.id.volume_dialog_slider
            } else {
                R.id.volume_dialog_slider_horizontal
            }
        val sliderComposeView: ComposeView = view.requireViewById(sliderComposeViewId)
        sliderComposeView.setContent {
            PlatformTheme {
                val isExpanded by
                    expansionInteractor.isExpanded.collectAsStateWithLifecycle(false)
                VolumeDialogSlider(
                    viewModel = viewModel,
                    overscrollViewModel = overscrollViewModel,
                    hapticsViewModelFactory = hapticsViewModelFactory,
                    isVolumeDialogVertical = isVolumeDialogVertical,
                    panelStyle = expansionInteractor.style,
                    showLabel = isExpanded,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeDialogSlider(
    viewModel: VolumeDialogSliderViewModel,
    overscrollViewModel: VolumeDialogOverscrollViewModel,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    isVolumeDialogVertical: Boolean,
    panelStyle: VolumePanelStyle = VolumePanelStyle.DEFAULT,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier,
    dimensions: VolumeSliderDimensions =
        if (isVolumeDialogVertical) {
            VolumeSliderDimensions.Vertical
        } else {
            VolumeSliderDimensions.Horizontal
        },
) {
    val isPillStyle = panelStyle != VolumePanelStyle.DEFAULT
    val isOneUi = panelStyle == VolumePanelStyle.ONE_UI
    val colors =
        when {
            isOneUi -> {
                val active = colorResource(R.color.volume_panel_oneui_track_active)
                val inactive = colorResource(R.color.volume_panel_oneui_track_inactive)
                SliderDefaults.colors(
                    activeTrackColor = active,
                    inactiveTrackColor = inactive,
                    activeTickColor = active,
                    inactiveTickColor = inactive,
                    disabledActiveTrackColor = active,
                    disabledInactiveTrackColor = inactive,
                    disabledActiveTickColor = active,
                    disabledInactiveTickColor = inactive,
                )
            }
            isPillStyle -> {
                val active = colorResource(R.color.volume_panel_expandable_track_active)
                val inactive = colorResource(R.color.volume_panel_expandable_track_inactive)
                SliderDefaults.colors(
                    activeTrackColor = active,
                    inactiveTrackColor = inactive,
                    activeTickColor = active,
                    inactiveTickColor = inactive,
                    disabledActiveTrackColor = active,
                    disabledInactiveTrackColor = inactive,
                    disabledActiveTickColor = active,
                    disabledInactiveTickColor = inactive,
                )
            }
            isVolumeDialogVertical ->
                SliderDefaults.colors(
                    activeTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledActiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            else ->
                SliderDefaults.colors(
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledActiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
        }
    val collectedSliderStateModel by viewModel.state.collectAsStateWithLifecycle(null)
    val sliderStateModel = collectedSliderStateModel ?: return
    val interactionSource = remember { MutableInteractionSource() }

    val trackSize =
        when {
            isOneUi -> dimensionResource(R.dimen.volume_panel_oneui_track_size)
            isPillStyle -> dimensionResource(R.dimen.volume_panel_expandable_track_size)
            else -> dimensions.trackSize
        }
    val trackCornerSize =
        when {
            isOneUi -> dimensionResource(R.dimen.volume_panel_oneui_track_corner_radius)
            isPillStyle -> dimensionResource(R.dimen.volume_panel_expandable_track_corner_radius)
            else -> 12.dp
        }

    val bottomIcon: (@Composable BoxScope.(iconsState: SliderIconsState) -> Unit)? =
        if (isPillStyle) {
            val iconTint =
                if (isOneUi) {
                    colorResource(R.color.volume_panel_oneui_icon)
                } else {
                    val range = sliderStateModel.valueRange
                    val span = range.endInclusive - range.start
                    val fraction =
                        if (span <= 0f) 0f else (sliderStateModel.value - range.start) / span
                    val iconCoveredFraction =
                        dimensions.iconSize /
                            dimensionResource(R.dimen.volume_panel_expandable_slider_height)
                    if (fraction > iconCoveredFraction) {
                        colorResource(R.color.volume_panel_expandable_icon_on_active)
                    } else {
                        colorResource(R.color.volume_panel_expandable_icon_on_inactive)
                    }
                }
            {
                SliderIcon(
                    icon = {
                        Icon(
                            icon = sliderStateModel.icon,
                            tint = { iconTint },
                            modifier = Modifier.size(dimensions.iconSize),
                        )
                    },
                    isVisible = true,
                )
            }
        } else {
            null
        }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> viewModel.onSliderDragStarted()
                is DragInteraction.Cancel -> viewModel.onSliderDragFinished()
                is DragInteraction.Stop -> viewModel.onSliderDragFinished()
            }
        }
    }

    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        Slider(
            value = sliderStateModel.value,
            valueRange = sliderStateModel.valueRange,
            onValueChanged = { value ->
                overscrollViewModel.setSlider(
                    value = value,
                    min = sliderStateModel.valueRange.start,
                    max = sliderStateModel.valueRange.endInclusive,
                )
                viewModel.setStreamVolume(value, true)
            },
            onValueChangeFinished = { viewModel.onSliderChangeFinished(it) },
            isEnabled = !sliderStateModel.isDisabled,
            isReverseDirection = true,
            isVertical = isVolumeDialogVertical,
            colors = colors,
            interactionSource = interactionSource,
            haptics =
                Haptics.Enabled(
                    hapticsViewModelFactory = hapticsViewModelFactory,
                    hapticConfigs =
                        VolumeHapticsConfigsProvider.continuousConfigs(
                            SliderHapticFeedbackFilter()
                        ),
                    orientation =
                        if (isVolumeDialogVertical) {
                            Orientation.Vertical
                        } else {
                            Orientation.Horizontal
                        },
                ),
            stepDistance = 1f,
            track = { sliderState ->
                SliderTrack(
                    sliderState,
                    colors = colors,
                    isEnabled = !sliderStateModel.isDisabled,
                    isVertical = isVolumeDialogVertical,
                    thumbTrackGapSize = if (isPillStyle) 0.dp else 6.dp,
                    trackCornerSize = trackCornerSize,
                    trackInsideCornerSize = if (isPillStyle) 0.dp else 2.dp,
                    activeTrackStartIcon = bottomIcon,
                    activeTrackEndIcon =
                        if (isPillStyle) {
                            null
                        } else {
                            { iconsState ->
                                SliderIcon(
                                    icon = {
                                        Icon(
                                            icon = sliderStateModel.icon,
                                            tint = null,
                                            modifier = Modifier.size(dimensions.iconSize),
                                        )
                                    },
                                    isVisible = !iconsState.isInactiveTrackEndIconVisible,
                                )
                            }
                        },
                    inactiveTrackEndIcon =
                        if (isPillStyle) {
                            null
                        } else {
                            { iconsState ->
                                SliderIcon(
                                    icon = {
                                        Icon(
                                            icon = sliderStateModel.icon,
                                            tint = null,
                                            modifier = Modifier.size(dimensions.iconSize),
                                        )
                                    },
                                    isVisible = iconsState.isInactiveTrackEndIconVisible,
                                )
                            }
                        },
                    trackSize = trackSize,
                )
            },
            thumb =
                if (isPillStyle) {
                    { _, _ -> }
                } else {
                    { sliderState, interactions ->
                        SliderDefaults.Thumb(
                            sliderState = sliderState,
                            interactionSource = interactions,
                            enabled = !sliderStateModel.isDisabled,
                            colors = colors,
                            thumbSize = DpSize(dimensions.thumbWidth, dimensions.thumbHeight),
                        )
                    }
                },
            accessibilityParams = AccessibilityParams(contentDescription = sliderStateModel.label),
            modifier =
                sliderModifier.pointerInput(Unit) {
                    coroutineScope {
                        val currentContext = currentCoroutineContext()
                        awaitPointerEventScope {
                            while (currentContext.isActive) {
                                viewModel.onTouchEvent(awaitPointerEvent())
                            }
                        }
                    }
                },
        )
    }

    if (isOneUi && showLabel) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            slider(Modifier.weight(1f))
            Text(
                text = sliderStateModel.label,
                color = colorResource(R.color.volume_panel_oneui_label),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    } else {
        slider(modifier)
    }
}

data class VolumeSliderDimensions(
    val iconSize: Dp,
    val thumbHeight: Dp,
    val thumbWidth: Dp,
    val trackSize: Dp,
) {
    companion object {
        val Vertical =
            VolumeSliderDimensions(
                iconSize = 20.dp,
                thumbWidth = 52.dp,
                thumbHeight = 4.dp,
                trackSize = 40.dp,
            )

        val Horizontal =
            VolumeSliderDimensions(
                iconSize = 24.dp,
                thumbHeight = 40.dp,
                thumbWidth = 3.dp,
                trackSize = 32.dp,
            )
    }
}
