/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.compose.animation.scene.ContentScope
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.qs.composefragment.BrightnessLayout
import com.android.systemui.qs.composefragment.ConnectivityFolder
import com.android.systemui.qs.composefragment.connectivityFolderEnabled
import com.android.systemui.qs.composefragment.POSITION_ABOVE_GRID
import com.android.systemui.qs.composefragment.POSITION_BELOW_GRID
import com.android.systemui.qs.composefragment.POSITION_HEADER
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_POSITION
import com.android.systemui.qs.composefragment.PenguinMediaCard
import com.android.systemui.qs.panels.ui.compose.FOLDER_SPEC
import com.android.systemui.qs.panels.ui.compose.MEDIA_SPEC
import com.android.systemui.qs.panels.ui.compose.SLIDERS_SPEC
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_STYLE
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_SPAN
import com.android.systemui.qs.composefragment.secureIntSetting
import com.android.systemui.qs.composefragment.VolumeLayout
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.shared.style.LocalQsPanelStyle
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun ContentScope.QuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
    mediaSquishiness: () -> Float = { 1f },
) {
    CompositionLocalProvider(LocalQsPanelStyle provides viewModel.panelStyle) {
        when (viewModel.panelStyle) {
            QsPanelStyle.Default ->
                DefaultQuickSettingsContent(viewModel, mediaInRow, modifier, mediaSquishiness)
            QsPanelStyle.Penguin ->
                PenguinQuickSettingsContent(viewModel, mediaInRow, modifier, mediaSquishiness)
        }
    }
}

@Composable
private fun ContentScope.PenguinQuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
    mediaSquishiness: () -> Float = { 1f },
) {
    val showMedia = viewModel.showMedia
    val headerShowsMedia = showMedia && isAlwaysComposedContentVisible()
    val top2Specs = remember(viewModel.tileGridViewModel.tileViewModels) {
        viewModel.tileGridViewModel.tileViewModels.take(2).map { it.spec }
    }
    val folderEnabled = connectivityFolderEnabled()
    var folderExpanded by remember { mutableStateOf(false) }
    val folderSpan = secureIntSetting(SETTING_QS_FOLDER_SPAN, 1)
    val mediaSpan = secureIntSetting(SETTING_QS_MEDIA_SPAN, 1)
    val headerHeight =
        dimensionResource(id = R.dimen.common_tile_default_tile_height) * 2 +
            dimensionResource(id = R.dimen.qs_tile_margin_vertical)
    val slidersPosition = secureIntSetting(SETTING_QS_SLIDERS_POSITION, POSITION_HEADER)
    val slidersSpan = secureIntSetting(SETTING_QS_SLIDERS_SPAN, 1)
    val slidersInHeader = slidersPosition == POSITION_HEADER
    val folderPosition = secureIntSetting(SETTING_QS_FOLDER_POSITION, POSITION_HEADER)
    val mediaPosition = secureIntSetting(SETTING_QS_MEDIA_POSITION, POSITION_HEADER)
    val headerShowsFolder =
        folderEnabled && folderSpan < 2 && mediaSpan < 2 && folderPosition == POSITION_HEADER
    val headerShowsTop2Tiles = !headerShowsFolder && !headerShowsMedia

    if (folderEnabled && folderExpanded) {
        Column(
            modifier =
                modifier
                    .element(Elements.QuickSettingsContent)
                    .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                    .sysuiResTag("quick_settings_panel")
        ) {
            ConnectivityFolder(
                tiles = viewModel.tileGridViewModel.tileViewModels,
                modifier = Modifier.element(Elements.ConnectivityFolder),
                expanded = true,
                onExpandedChange = { folderExpanded = it },
            )
        }
        return
    }

    PenguinQuickSettingsPanelLayout(
        headerLeft =
            @Composable {
                if (headerShowsFolder) {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        modifier = Modifier.element(Elements.ConnectivityFolder),
                        compactHeight = headerHeight,
                        expanded = false,
                        onExpandedChange = { folderExpanded = it },
                    )
                } else if (headerShowsMedia) {
                    QsMedia(viewModel, mediaSquishiness, headerHeight)
                } else {
                    var listening by remember { mutableStateOf(false) }
                    LifecycleStartEffect(Unit) {
                        listening = true
                        onStopOrDispose { listening = false }
                    }
                    Element(key = Elements.HeaderTiles, modifier = Modifier) {
                        TileGrid(
                            viewModel = viewModel.tileGridViewModel,
                            includeSpecs = top2Specs,
                            columnsOverride = 1,
                            forceLargeTiles = true,
                            listening = { listening },
                        )
                    }
                }
            },
        headerRightPresent = slidersInHeader,
        headerRight =
            @Composable {
                if (slidersInHeader) {
                    QsSliders(
                        sliderHeight = headerHeight,
                        gap = dimensionResource(id = R.dimen.qs_tile_margin_horizontal),
                    )
                }
            },
        tiles =
            @Composable {
                var listening by remember { mutableStateOf(false) }
                LifecycleStartEffect(Unit) {
                    listening = true
                    onStopOrDispose { listening = false }
                }

                val excludeSpecs = if (headerShowsTop2Tiles) top2Specs else emptyList()

                Column(
                    verticalArrangement =
                        spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_vertical))
                ) {
                    val mediaOwnRow =
                        showMedia && isAlwaysComposedContentVisible() && !headerShowsMedia
                    val folderInGrid = folderEnabled && !headerShowsFolder
                    val gap = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
                    fun elementsAt(slot: Int): List<PanelElement> = buildList {
                        val matches = { position: Int ->
                            if (slot <= POSITION_ABOVE_GRID) position <= POSITION_ABOVE_GRID
                            else position >= POSITION_BELOW_GRID
                        }
                        if (folderInGrid && matches(folderPosition)) {
                            add(
                                PanelElement(folderSpan) {
                                    ConnectivityFolder(
                                        tiles = viewModel.tileGridViewModel.tileViewModels,
                                        modifier = Modifier.element(Elements.ConnectivityFolder),
                                        compactHeight = if (folderSpan < 2) headerHeight else null,
                                        onExpandedChange = { folderExpanded = it },
                                    )
                                }
                            )
                        }
                        if (mediaOwnRow && matches(mediaPosition)) {
                            add(PanelElement(mediaSpan) { QsMedia(viewModel, mediaSquishiness) })
                        }
                        if (!slidersInHeader && matches(slidersPosition)) {
                            add(
                                PanelElement(slidersSpan) {
                                    QsSliders(sliderHeight = CompactSliderHeight, gap = gap)
                                }
                            )
                        }
                    }
                    PanelElementRows(elementsAt(POSITION_ABOVE_GRID), gap)
                    Box {
                        GridAnchor()
                        TileGrid(
                            viewModel = viewModel.tileGridViewModel,
                            excludeSpecs = excludeSpecs,
                            listening = { listening },
                            modifier = Modifier.element(Elements.QuickSettingsTiles),
                        )
                    }
                    PanelElementRows(elementsAt(POSITION_BELOW_GRID), gap)
                }
            },
        modifier =
            modifier
                .element(Elements.QuickSettingsContent)
                .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                .sysuiResTag("quick_settings_panel"),
    )
}

@Composable
fun panelElementPreviews(
    viewModel: QuickSettingsContainerViewModel
): Map<TileSpec, @Composable () -> Unit> {
    val folderHeight = CompactSliderHeight
    return mapOf(
        FOLDER_SPEC to
            {
                ConnectivityFolder(
                    tiles = viewModel.tileGridViewModel.tileViewModels,
                    compactHeight = folderHeight,
                )
            },
        MEDIA_SPEC to
            {
                PenguinMediaCard(
                    viewModelFactory = viewModel.mediaViewModelFactory,
                    behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                    modifier = Modifier.height(folderHeight),
                )
            },
        SLIDERS_SPEC to
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrightnessLayout(enable = false, sliderHeight = folderHeight)
                    VolumeLayout(enable = false, sliderHeight = folderHeight)
                }
            },
    )
}

private val CompactSliderHeight = 96.dp
private val PenguinMediaHeight = 132.dp

private class PanelElement(val span: Int, val content: @Composable () -> Unit)

@Composable
private fun PanelElementRows(elements: List<PanelElement>, gap: Dp) {
    elements.filter { it.span >= 2 }.forEach { Box(Modifier.fillMaxWidth()) { it.content() } }
    elements.filter { it.span < 2 }.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(gap)) {
            pair.forEach { element -> Box(Modifier.weight(1f)) { element.content() } }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContentScope.QsSliders(sliderHeight: Dp, gap: Dp) {
    var interactable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { Elements.QuickSettingsContent.currentAlpha() }
            .filterNotNull()
            .collect { interactable = it >= .5f }
    }
    Element(modifier = Modifier, key = Elements.BrightnessSlider) {
        Row(
            modifier =
                Modifier.fillMaxWidth().thenIf(!interactable) { Modifier.gesturesDisabled() },
            horizontalArrangement = spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrightnessLayout(enable = interactable, sliderHeight = sliderHeight)
            VolumeLayout(enable = interactable, sliderHeight = sliderHeight)
        }
    }
}

@Composable
private fun ContentScope.QsMedia(
    viewModel: QuickSettingsContainerViewModel,
    mediaSquishiness: () -> Float,
    height: Dp = PenguinMediaHeight,
) {
    if (secureIntSetting(SETTING_QS_MEDIA_STYLE, 0) != 0) {
        Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
            PenguinMediaCard(
                viewModelFactory = viewModel.mediaViewModelFactory,
                behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                modifier = Modifier.height(height),
            )
        }
        return
    }
    Element(key = Media.Elements.MediaCarousel, modifier = Modifier.height(height)) {
        Media(
            viewModelFactory = viewModel.mediaViewModelFactory,
            presentationStyle = MediaPresentationStyle.Default,
            behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
            onDismissed = viewModel::onMediaSwipeToDismiss,
            mediaSquishiness = mediaSquishiness,
            location = Media.Location.QS,
        )
    }
}

@Composable
private fun PenguinQuickSettingsPanelLayout(
    headerLeft: @Composable () -> Unit,
    headerRight: @Composable () -> Unit,
    tiles: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headerRightPresent: Boolean = true,
) {
    Column(
        verticalArrangement = spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_vertical)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_horizontal)),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) { headerLeft() }
            if (headerRightPresent) Box(modifier = Modifier.weight(1f)) { headerRight() }
        }
        tiles()
    }
}

@Composable
private fun ContentScope.DefaultQuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
    mediaSquishiness: () -> Float = { 1f },
) {
    DefaultQuickSettingsPanelLayout(
        brightness =
            @Composable {
                if (viewModel.isBrightnessSliderVisible) {
                    var isBrightnessSliderInteractable by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        snapshotFlow { Elements.QuickSettingsContent.currentAlpha() }
                            .filterNotNull()
                            .collect { isBrightnessSliderInteractable = it >= .5f }
                    }
                    Element(modifier = Modifier, key = Elements.BrightnessSlider) {
                        BrightnessSliderContainer(
                            viewModel.brightnessSliderViewModel,
                            containerColors =
                                ContainerColors(
                                    Color.Transparent,
                                    ContainerColors.defaultContainerColor,
                                ),
                            modifier =
                                Modifier.padding(
                                        vertical =
                                            dimensionResource(id = R.dimen.qs_brightness_margin_top)
                                    )
                                    .thenIf(!isBrightnessSliderInteractable) {
                                        Modifier.gesturesDisabled()
                                    },
                        )
                    }
                }
            },
        tiles =
            @Composable {
                var listening by remember { mutableStateOf(false) }
                LifecycleStartEffect(Unit) {
                    listening = true

                    onStopOrDispose { listening = false }
                }

                Box {
                    GridAnchor()
                    TileGrid(
                        viewModel.tileGridViewModel,
                        listening = { listening },
                        modifier = Modifier.element(Elements.QuickSettingsTiles),
                    )
                }
            },
        media =
            @Composable {
                if (isAlwaysComposedContentVisible()) {
                    Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
                        Media(
                            viewModelFactory = viewModel.mediaViewModelFactory,
                            presentationStyle = MediaPresentationStyle.Default,
                            behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                            onDismissed = viewModel::onMediaSwipeToDismiss,
                            mediaSquishiness = mediaSquishiness,
                            location = Media.Location.QS,
                        )
                    }
                } else {
                    // Add an empty box when QS content is not visible to keep the same number of
                    // elements.
                    Box(modifier = Modifier)
                }
            },
        mediaInRow = mediaInRow,
        modifier =
            modifier
                .element(Elements.QuickSettingsContent)
                .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                .sysuiResTag("quick_settings_panel"),
    )
}

@Composable
private fun DefaultQuickSettingsPanelLayout(
    brightness: @Composable () -> Unit,
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
) {
    if (mediaInRow) {
        Column(
            verticalArrangement = spacedBy(QuickSettingsShade.Dimensions.VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            brightness()
            Row(
                horizontalArrangement = spacedBy(QuickSettingsShade.Dimensions.HorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) { tiles() }
                Box(modifier = Modifier.weight(1f)) { media() }
            }
        }
    } else {
        Column(
            verticalArrangement = spacedBy(QuickSettingsShade.Dimensions.VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            brightness()
            tiles()
            media()
        }
    }
}
