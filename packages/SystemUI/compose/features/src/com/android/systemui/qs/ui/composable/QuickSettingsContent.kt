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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
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
import com.android.systemui.qs.composefragment.connectivityFolderSpecs
import com.android.systemui.qs.composefragment.POSITION_ABOVE_GRID
import com.android.systemui.qs.composefragment.POSITION_BELOW_GRID
import com.android.systemui.qs.composefragment.POSITION_HEADER
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_POSITION
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.ui.compose.toolbar.EditModeButton
import com.android.systemui.qs.composefragment.MyUiCardSpecs
import com.android.systemui.qs.composefragment.LocalMyUiInteractive
import com.android.systemui.qs.composefragment.MyUiGridGap
import com.android.systemui.qs.composefragment.MyUiTileAspect
import com.android.systemui.qs.composefragment.MyUiConnectivityCard
import com.android.systemui.qs.composefragment.MyUiTileGrid
import com.android.systemui.qs.composefragment.PenguinMediaCard
import com.android.systemui.qs.panels.ui.compose.FOLDER_SPEC
import com.android.systemui.qs.panels.ui.compose.MEDIA_SPEC
import com.android.systemui.qs.panels.ui.compose.SLIDERS_SPEC
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_STYLE
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_POSITION
import com.android.systemui.qs.composefragment.DEFAULT_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_SPAN
import com.android.systemui.qs.composefragment.qsModuleHeight
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
            QsPanelStyle.MyUi ->
                MyUiQuickSettingsContent(viewModel, modifier, mediaSquishiness)
        }
    }
}

@Composable
private fun ContentScope.MyUiQuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
    mediaSquishiness: () -> Float = { 1f },
) {
    val gap = MyUiGridGap
    val allTiles = viewModel.tileGridViewModel.tileViewModels
    val cardSpecs = MyUiCardSpecs(allTiles)
    val gridTiles = remember(allTiles, cardSpecs) { allTiles.filterNot { it.spec in cardSpecs } }
    var interactable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { Elements.QuickSettingsContent.currentAlpha() }
            .filterNotNull()
            .collect { interactable = it >= .5f }
    }

    Column(
        verticalArrangement = spacedBy(gap),
        modifier =
            modifier
                .element(Elements.QuickSettingsContent)
                .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                .sysuiResTag("quick_settings_panel"),
    ) {
        MyUiHeaderRow(tiles = allTiles, interactable = interactable)
        if (viewModel.showMedia && isAlwaysComposedContentVisible()) {
            QsMedia(viewModel, mediaSquishiness, square = false)
        }
        var listening by remember { mutableStateOf(false) }
        LifecycleStartEffect(Unit) {
            listening = true
            onStopOrDispose { listening = false }
        }
        Box {
            GridAnchor()
            MyUiTileGrid(
                tiles = gridTiles,
                columns = 4,
                gap = gap,
                modifier = Modifier.element(Elements.QuickSettingsTiles),
            )
        }
        val editButtonViewModel =
            rememberViewModel(traceName = "MyUiQuickSettings-editButton") {
                viewModel.editModeButtonViewModelFactory.create()
            }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EditModeButton(viewModel = editButtonViewModel, isVisible = interactable)
        }
    }
}

@Composable
fun ContentScope.MyUiHeaderRow(
    tiles: List<TileViewModel>,
    interactable: Boolean,
    modifier: Modifier = Modifier,
) {
    val gap = MyUiGridGap
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val column = (maxWidth - gap * 3) / 4
        val headerHeight = column / MyUiTileAspect * 2 + gap
        Row(
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            horizontalArrangement = spacedBy(gap),
        ) {
            Box(Modifier.weight(1f)) {
                MyUiConnectivityCard(
                    tiles = tiles,
                    modifier = Modifier.element(Elements.ConnectivityFolder),
                )
            }
            Element(key = Elements.BrightnessSlider, modifier = Modifier.weight(1f)) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().thenIf(!interactable) {
                            Modifier.gesturesDisabled()
                        },
                    horizontalArrangement = spacedBy(gap),
                ) {
                    Box(Modifier.weight(1f)) {
                        VolumeLayout(
                            enable = interactable,
                            verticalCornerRadius = MyUiSliderCorner,
                            verticalWidth = null,
                            sliderHeight = headerHeight,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        BrightnessLayout(
                            enable = interactable,
                            verticalCornerRadius = MyUiSliderCorner,
                            verticalWidth = null,
                            sliderHeight = headerHeight,
                        )
                    }
                }
            }
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
    val mediaWantsHeader = showMedia && isAlwaysComposedContentVisible()
    val folderEnabled = connectivityFolderEnabled()
    val folderMemberSpecs =
        if (folderEnabled) connectivityFolderSpecs() else emptyList()
    val availableTiles =
        remember(viewModel.tileGridViewModel.tileViewModels, folderMemberSpecs) {
            viewModel.tileGridViewModel.tileViewModels.filterNot {
                it.spec.spec in folderMemberSpecs
            }
        }
    val inFolderSpecs =
        remember(viewModel.tileGridViewModel.tileViewModels, folderMemberSpecs) {
            viewModel.tileGridViewModel.tileViewModels
                .map { it.spec }
                .filter { it.spec in folderMemberSpecs }
        }
    val top2Specs = remember(availableTiles) { availableTiles.take(2).map { it.spec } }
    val topHeaderSpec = remember(viewModel.tileGridViewModel.tileViewModels) {
        viewModel.tileGridViewModel.tileViewModels.take(1).map { it.spec }
    }
    var folderExpanded by remember { mutableStateOf(false) }
    val panelVisible = isAlwaysComposedContentVisible()
    LaunchedEffect(panelVisible) { if (!panelVisible) folderExpanded = false }
    val folderSpan = secureIntSetting(SETTING_QS_FOLDER_SPAN, 1)
    val mediaSpan = secureIntSetting(SETTING_QS_MEDIA_SPAN, 1)
    val headerHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    val slidersPosition = secureIntSetting(SETTING_QS_SLIDERS_POSITION, POSITION_HEADER)
    val slidersSpan = secureIntSetting(SETTING_QS_SLIDERS_SPAN, DEFAULT_SLIDERS_SPAN)
    val standingSliderHeight =
        headerHeight * 2 + dimensionResource(id = R.dimen.qs_tile_margin_vertical)
    val slidersInHeader = false
    val folderPosition = secureIntSetting(SETTING_QS_FOLDER_POSITION, POSITION_HEADER)
    val mediaPosition = secureIntSetting(SETTING_QS_MEDIA_POSITION, POSITION_HEADER)
    val headerPairFits = mediaSpan < 2 && folderSpan < 2
    val headerShowsMedia =
        mediaWantsHeader && mediaPosition <= POSITION_ABOVE_GRID && headerPairFits
    val headerShowsFolder =
        folderEnabled && folderPosition <= POSITION_ABOVE_GRID && headerPairFits
    val headerShowsBoth = headerShowsMedia && headerShowsFolder
    val headerHasContent = headerShowsFolder || headerShowsMedia
    val headerLeftIsHalf =
        !headerShowsBoth && (headerShowsFolder || (headerShowsMedia && mediaSpan < 2))

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
                if (headerShowsFolder && !headerShowsMedia) {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        modifier = Modifier.element(Elements.ConnectivityFolder),
                        compactHeight = qsModuleHeight(2),
                        expanded = false,
                        onExpandedChange = { folderExpanded = it },
                    )
                } else if (headerShowsMedia) {
                    QsMedia(viewModel, mediaSquishiness, headerHeight, square = mediaSpan < 2)
                }
            },
        headerPresent = headerHasContent,
        headerRightPresent = headerShowsBoth || headerLeftIsHalf,

        headerRight =
            @Composable {
                if (headerShowsBoth) {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        modifier = Modifier.element(Elements.ConnectivityFolder),
                        compactHeight = qsModuleHeight(2),
                        expanded = false,
                        onExpandedChange = { folderExpanded = it },
                    )
                } else if (headerLeftIsHalf) {
                    var headerListening by remember { mutableStateOf(false) }
                    LifecycleStartEffect(Unit) {
                        headerListening = true
                        onStopOrDispose { headerListening = false }
                    }
                    Element(key = Elements.HeaderTiles, modifier = Modifier) {
                        TileGrid(
                            viewModel = viewModel.tileGridViewModel,
                            includeSpecs = top2Specs,
                            columnsOverride = 1,
                            forceLargeTiles = true,
                            listening = { headerListening },
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

                val headerSpecs = if (headerLeftIsHalf) top2Specs else emptyList()

                Column(
                    verticalArrangement =
                        spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_vertical))
                ) {
                    val mediaOwnRow =
                        showMedia && isAlwaysComposedContentVisible() && !headerShowsMedia
                    val folderInGrid = folderEnabled && !headerShowsFolder
                    val gap = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
                    val folderOrder = secureIntSetting("qs_connectivity_folder_edit_index", 0)
                    val mediaOrder = secureIntSetting("qs_media_edit_index", 1)
                    val slidersOrder = secureIntSetting("qs_sliders_edit_index", 2)
                    fun elementsAt(slot: Int): List<PanelElement> = buildList {
                        val matches = { position: Int ->
                            if (slot <= POSITION_ABOVE_GRID) position <= POSITION_ABOVE_GRID
                            else position >= POSITION_BELOW_GRID
                        }
                        if (folderInGrid && matches(folderPosition)) {
                            add(
                                PanelElement(folderSpan, folderOrder) {
                                    ConnectivityFolder(
                                        tiles = viewModel.tileGridViewModel.tileViewModels,
                                        modifier = Modifier.element(Elements.ConnectivityFolder),
                                        compactHeight =
                                            if (folderSpan < 2) qsModuleHeight(2) else null,
                                        onExpandedChange = { folderExpanded = it },
                                    )
                                }
                            )
                        }
                        if (mediaOwnRow && matches(mediaPosition)) {
                            add(
                                PanelElement(mediaSpan, mediaOrder) {
                                    QsMedia(viewModel, mediaSquishiness, square = mediaSpan < 2)
                                }
                            )
                        }
                        val slidersSlot =
                            if (slidersPosition == POSITION_HEADER) POSITION_ABOVE_GRID
                            else slidersPosition
                        if (matches(slidersSlot)) {
                            add(
                                PanelElement(
                                    slidersSpan,
                                    slidersOrder,
                                    alignEnd = slidersSpan < 2,
                                ) {
                                    if (slidersSpan < 2) {
                                        QsStandingSliders(
                                            sliderHeight = standingSliderHeight,
                                            gap = gap,
                                        )
                                    } else {
                                        QsSliders(
                                            sliderHeight = LyingSliderHeight,
                                            gap = gap,
                                            horizontal = true,
                                        )
                                    }
                                }
                            )
                        }
                    }
                    val aboveElements = elementsAt(POSITION_ABOVE_GRID).sortedBy { it.order }
                    val belowElements = elementsAt(POSITION_BELOW_GRID).sortedBy { it.order }
                    val pool = availableTiles.map { it.spec }.filterNot { it in headerSpecs }
                    val aboveSlots = panelFillerSlots(aboveElements)
                    val belowSlots = panelFillerSlots(belowElements)
                    val aboveFillers = List(aboveSlots) { pool.drop(it * 2).take(2) }
                    val belowFillers =
                        List(belowSlots) { pool.drop((aboveSlots + it) * 2).take(2) }
                    val excludeSpecs =
                        headerSpecs + aboveFillers.flatten() + belowFillers.flatten() + inFolderSpecs

                    PanelElementRows(aboveElements, gap) { slot ->
                        FillerTiles(viewModel, aboveFillers.getOrElse(slot) { emptyList() }, listening)
                    }
                    Box {
                        GridAnchor()
                        TileGrid(
                            viewModel = viewModel.tileGridViewModel,
                            excludeSpecs = excludeSpecs,
                            listening = { listening },
                            modifier = Modifier.element(Elements.QuickSettingsTiles),
                            belowTiles = {
                                val rowGap = dimensionResource(id = R.dimen.qs_tile_margin_vertical)
                                Column(
                                    modifier =
                                        Modifier.thenIf(belowElements.isNotEmpty()) {
                                            Modifier.padding(top = rowGap)
                                        },
                                    verticalArrangement = spacedBy(rowGap),
                                ) {
                                    PanelElementRows(belowElements, gap) { slot ->
                                        FillerTiles(
                                            viewModel,
                                            belowFillers.getOrElse(slot) { emptyList() },
                                            listening,
                                        )
                                    }
                                }
                            },
                        )
                    }
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
    val elementHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    val standingHeight =
        elementHeight * 2 + dimensionResource(id = R.dimen.qs_tile_margin_vertical)
    val gap = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
    val folderSpan = secureIntSetting(SETTING_QS_FOLDER_SPAN, 1)
    val mediaSpan = secureIntSetting(SETTING_QS_MEDIA_SPAN, 1)
    val slidersStanding = secureIntSetting(SETTING_QS_SLIDERS_SPAN, DEFAULT_SLIDERS_SPAN) < 2
    return mapOf(
        FOLDER_SPEC to
            {
                PanelElementPreview(span = folderSpan) {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        interactive = false,
                        compactHeight = if (folderSpan < 2) qsModuleHeight(2) else null,
                    )
                }
            },
        MEDIA_SPEC to
            {
                PanelElementPreview(span = mediaSpan) {
                    PenguinMediaCard(
                        viewModelFactory = viewModel.mediaViewModelFactory,
                        behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                        square = mediaSpan < 2,
                        interactive = false,
                    )
                }
            },
        SLIDERS_SPEC to
            {
                PanelElementPreview(span = if (slidersStanding) 1 else 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            BrightnessLayout(
                                enable = false,
                                horizontal = !slidersStanding,
                                verticalCornerRadius = MyUiSliderCorner,
                                verticalWidth = null,
                                sliderHeight =
                                    if (slidersStanding) standingHeight else LyingSliderHeight,
                                interactive = false,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            VolumeLayout(
                                enable = false,
                                horizontal = !slidersStanding,
                                verticalCornerRadius = MyUiSliderCorner,
                                verticalWidth = null,
                                sliderHeight =
                                    if (slidersStanding) standingHeight else LyingSliderHeight,
                                interactive = false,
                            )
                        }
                    }
                }
            },
    )
}

@Composable
fun qsHeaderPreview(viewModel: QuickSettingsContainerViewModel): (@Composable () -> Unit)? {
    val style =
        QsPanelStyle.fromValue(
            secureIntSetting(QsPanelStyle.SETTING_NAME, QsPanelStyle.Penguin.value)
        )
    if (style != QsPanelStyle.MyUi) return null
    val tiles = viewModel.tileGridViewModel.tileViewModels
    return {
        val gap = MyUiGridGap
        CompositionLocalProvider(LocalMyUiInteractive provides false) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val column = (maxWidth - gap * 3) / 4
                val headerHeight = column / MyUiTileAspect * 2 + gap
                Row(
                    modifier = Modifier.fillMaxWidth().height(headerHeight),
                    horizontalArrangement = spacedBy(gap),
                ) {
                    Box(Modifier.weight(1f)) { MyUiConnectivityCard(tiles = tiles) }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = spacedBy(gap),
                    ) {
                        Box(Modifier.weight(1f)) {
                            VolumeLayout(
                                enable = false,
                                verticalCornerRadius = MyUiSliderCorner,
                                verticalWidth = null,
                                sliderHeight = headerHeight,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            BrightnessLayout(
                                enable = false,
                                verticalCornerRadius = MyUiSliderCorner,
                                verticalWidth = null,
                                sliderHeight = headerHeight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun panelElementPreviewHeights(): Map<TileSpec, Dp> {
    val elementHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    val twoRows = elementHeight * 2 + dimensionResource(id = R.dimen.qs_tile_margin_vertical)
    val folderFull = secureIntSetting(SETTING_QS_FOLDER_SPAN, 1) >= 2
    val mediaFull = secureIntSetting(SETTING_QS_MEDIA_SPAN, 1) >= 2
    val slidersLying = secureIntSetting(SETTING_QS_SLIDERS_SPAN, DEFAULT_SLIDERS_SPAN) >= 2
    return mapOf(
        FOLDER_SPEC to if (folderFull) FolderFlatHeight else twoRows,
        MEDIA_SPEC to if (mediaFull) PenguinMediaHeight else twoRows,
        SLIDERS_SPEC to if (slidersLying) LyingSliderHeight else twoRows,
    )
}

@Composable
private fun PanelElementPreview(span: Int, content: @Composable () -> Unit) {
    val margin = dimensionResource(id = R.dimen.qs_horizontal_margin)
    val gap = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
    val panelWidth = LocalConfiguration.current.screenWidthDp.dp - margin * 2
    val width = if (span >= 2) panelWidth else (panelWidth - gap) / 2
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier =
                Modifier.layout { measurable, constraints ->
                    val target = width.roundToPx()
                    val placeable =
                        measurable.measure(Constraints(minWidth = target, maxWidth = target))
                    val scale =
                        min(
                            1f,
                            min(
                                constraints.maxWidth.toFloat() / placeable.width.coerceAtLeast(1),
                                constraints.maxHeight.toFloat() / placeable.height.coerceAtLeast(1),
                            ),
                        )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeWithLayer(
                            x = (constraints.maxWidth - placeable.width) / 2,
                            y = (constraints.maxHeight - placeable.height) / 2,
                        ) {
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                }
        ) {
            content()
        }
    }
}

private val CompactSliderHeight = 96.dp
internal val MyUiSliderCorner = 22.dp
private val LyingSliderHeight = 56.dp
private val FolderFlatHeight = 92.dp
private val PenguinMediaHeight = 132.dp

internal class PanelElement(
    val span: Int,
    val order: Int,
    val alignEnd: Boolean = false,
    val content: @Composable () -> Unit,
)

internal fun panelFillerSlots(elements: List<PanelElement>): Int {
    var slots = 0
    var index = 0
    while (index < elements.size) {
        if (elements[index].span >= 2) {
            index++
            continue
        }
        val paired = elements.getOrNull(index + 1)?.span?.let { it < 2 } ?: false
        if (!paired) slots++
        index += if (paired) 2 else 1
    }
    return slots
}

@Composable
internal fun PanelElementRows(
    elements: List<PanelElement>,
    gap: Dp,
    filler: @Composable (slot: Int) -> Unit = {},
) {
    var slot = 0
    var index = 0
    while (index < elements.size) {
        val element = elements[index]
        if (element.span >= 2) {
            Box(Modifier.fillMaxWidth()) { element.content() }
            index++
            continue
        }
        val partner = elements.getOrNull(index + 1)?.takeIf { it.span < 2 }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(gap)) {
            val fillerSlot = slot
            if (partner == null && element.alignEnd) {
                Box(Modifier.weight(1f)) { filler(fillerSlot) }
            }
            Box(Modifier.weight(1f)) { element.content() }
            partner?.let { Box(Modifier.weight(1f)) { it.content() } }
            if (partner == null && !element.alignEnd) {
                Box(Modifier.weight(1f)) { filler(fillerSlot) }
            }
        }
        if (partner == null) slot++
        index += if (partner != null) 2 else 1
    }
}

@Composable
private fun ContentScope.FillerTiles(
    viewModel: QuickSettingsContainerViewModel,
    specs: List<TileSpec>,
    listening: Boolean,
) {
    if (specs.isEmpty()) return
    TileGrid(
        viewModel = viewModel.tileGridViewModel,
        includeSpecs = specs,
        columnsOverride = 1,
        forceLargeTiles = true,
        listening = { listening },
    )
}

@Composable
private fun ContentScope.QsSliders(sliderHeight: Dp, gap: Dp, horizontal: Boolean = false) {
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
            Box(Modifier.weight(1f)) {
                BrightnessLayout(
                    enable = interactable,
                    horizontal = true,
                    sliderHeight = sliderHeight,
                )
            }
            Box(Modifier.weight(1f)) {
                VolumeLayout(
                    enable = interactable,
                    horizontal = true,
                    sliderHeight = sliderHeight,
                )
            }
        }
    }
}

@Composable
private fun ContentScope.QsStandingSliders(sliderHeight: Dp, gap: Dp) {
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
            horizontalArrangement = spacedBy(gap),
        ) {
            Box(Modifier.weight(1f)) {
                BrightnessLayout(
                    enable = interactable,
                    verticalCornerRadius = MyUiSliderCorner,
                    verticalWidth = null,
                    sliderHeight = sliderHeight,
                )
            }
            Box(Modifier.weight(1f)) {
                VolumeLayout(
                    enable = interactable,
                    verticalCornerRadius = MyUiSliderCorner,
                    verticalWidth = null,
                    sliderHeight = sliderHeight,
                )
            }
        }
    }
}

@Composable
private fun ContentScope.QsMedia(
    viewModel: QuickSettingsContainerViewModel,
    mediaSquishiness: () -> Float,
    height: Dp = PenguinMediaHeight,
    square: Boolean = false,
) {
    if (secureIntSetting(SETTING_QS_MEDIA_STYLE, 0) != 0) {
        Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
            PenguinMediaCard(
                viewModelFactory = viewModel.mediaViewModelFactory,
                behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                square = square,
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
    headerPresent: Boolean = true,
) {
    Column(
        verticalArrangement = spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_vertical)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (headerPresent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    spacedBy(dimensionResource(id = R.dimen.qs_tile_margin_horizontal)),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) { headerLeft() }
                if (headerRightPresent) Box(modifier = Modifier.weight(1f)) { headerRight() }
            }
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
