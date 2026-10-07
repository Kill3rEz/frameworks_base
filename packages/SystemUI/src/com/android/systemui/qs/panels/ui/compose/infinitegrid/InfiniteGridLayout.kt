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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.EditTileListState
import android.provider.Settings
import com.android.systemui.qs.panels.ui.compose.LocalIsPaginatedGrid
import com.android.systemui.penguin.LocalQsPanels
import com.android.systemui.qs.panels.ui.compose.PaginatableGridLayout
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.bounceableInfo
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.InfiniteGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TextFeedbackContentViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.toElementKey
import com.android.systemui.res.R
import com.android.systemui.shade.shared.flag.DualShadeFlag
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel

@SysUISingleton
class InfiniteGridLayout
@Inject
constructor(
    private val detailsViewModel: DetailsViewModel,
    private val iconTilesViewModel: IconTilesViewModel,
    override val viewModelFactory: InfiniteGridViewModel.Factory,
    private val textFeedbackContentViewModelFactory: TextFeedbackContentViewModel.Factory,
    private val tileHapticsViewModelFactory: TileHapticsViewModel.Factory,
    private val buildNumberViewModelFactory: BuildNumberViewModel.Factory,
) : PaginatableGridLayout {

    @Composable
    override fun ContentScope.TileGrid(
        tiles: List<TileViewModel>,
        modifier: Modifier,
        columnsOverride: Int?,
        forceLargeTiles: Boolean,
        listening: () -> Boolean,
        enableRevealEffect: Boolean,
        belowTiles: @Composable () -> Unit,
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid") {
                viewModelFactory.create()
            }

        val context = LocalContext.current
        val textFeedbackViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid", key = context) {
                textFeedbackContentViewModelFactory.create(context)
            }

        val columns = columnsOverride ?: viewModel.columnsWithMediaViewModel.columns
        val largeTilesSpan = viewModel.columnsWithMediaViewModel.largeSpan
        val largeTiles by viewModel.iconTilesViewModel.largeTilesState
        // Tiles or largeTiles may be updated while this is composed, so listen to any changes
        val roundGrid =
            LocalQsPanels.current?.panels?.tileStyle()?.roundIconTiles == true &&
                !forceLargeTiles &&
                (columnsOverride == null || columnsOverride == 1)
        val sizedTiles =
            remember(tiles, largeTiles, largeTilesSpan, forceLargeTiles, columns, roundGrid) {
                tiles.map {
                    SizedTileImpl(
                        it,
                        if (forceLargeTiles) largeTilesSpan.coerceAtMost(columns)
                        else if (roundGrid) 1
                        else if (largeTiles.contains(it.spec)) largeTilesSpan
                        else 1
                    )
                }
            }
        val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        val bounceables =
            remember(sizedTiles) { List(sizedTiles.size) { BounceableTileViewModel() } }
        val spans by remember(sizedTiles) { derivedStateOf { sizedTiles.fastMap { it.width } } }
        val isPaginated = LocalIsPaginatedGrid.current
        val isMainGrid = columnsOverride == null && !forceLargeTiles
        val tileSquishiness: () -> Float = if (isMainGrid) ({ squishiness }) else ({ 1f })

        Column(modifier) {
            VerticalSpannedGrid(
                columns = columns,
                columnSpacing = dimensionResource(R.dimen.qs_tile_margin_horizontal),
                rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
                spans = spans,
                keys = { sizedTiles[it].tile.spec },
                modifier = Modifier,
            ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
                val it = sizedTiles[spanIndex]

                Element(
                    it.tile.spec.toElementKey(),
                    if (roundGrid) Modifier.fillMaxWidth().wrapContentWidth() else Modifier,
                ) {
                    Tile(
                        tile = it.tile,
                        iconOnly =
                            if (forceLargeTiles) false
                            else roundGrid || iconTilesViewModel.isIconTile(it.tile.spec),
                        squishiness = tileSquishiness,
                        isHeaderTile = !isMainGrid,
                        tileHapticsViewModelFactory = tileHapticsViewModelFactory,
                        coroutineScope = scope,
                        bounceableInfo =
                            bounceables.bounceableInfo(
                                it,
                                index = spanIndex,
                                column = column,
                                columns = columns,
                                isFirstInRow = isFirstInColumn,
                                isLastInRow = isLastInColumn,
                            ),
                        detailsViewModel = detailsViewModel,
                        isVisible = listening,
                        requestToggleTextFeedback = textFeedbackViewModel::requestShowFeedback,
                        enableRevealEffect = enableRevealEffect,
                    )
                }
            }

            belowTiles()
            if (!isPaginated && isMainGrid) {
                Row(
                    modifier = Modifier.requiredHeight(48.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BuildNumber(viewModelFactory = buildNumberViewModelFactory)
                }
            }
        }

        TileListener(tiles, listening)
    }

    @Composable
    override fun TileSizing(
        content: @Composable (largeTiles: Set<TileSpec>, resize: (TileSpec, Boolean) -> Unit) -> Unit
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileSizing") {
                viewModelFactory.create()
            }
        val largeTiles by viewModel.iconTilesViewModel.largeTilesState
        content(largeTiles) { spec, large ->
            viewModel.iconTilesViewModel.resize(spec, toIcon = !large)
        }
    }

    @Composable
    override fun EditTileGrid(
        tiles: List<EditTileViewModel>,
        modifier: Modifier,
        onAddTile: (TileSpec, Int) -> Unit,
        onRemoveTile: (TileSpec) -> Unit,
        onSetTiles: (List<TileSpec>) -> Unit,
        onStopEditing: () -> Unit,
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModelFactory.create()
            }
        val columnsViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModel.columnsWithMediaViewModelFactory.createWithoutMediaTracking()
            }
        val snapshotViewModel =
            rememberViewModel("InfiniteGridLayout.EditTileGrid") {
                viewModel.snapshotViewModelFactory.create()
            }
        val topBarActionsViewModel =
            rememberViewModel("InfiniteGridLayout.EditTileGrid") {
                viewModel.editTopBarActionsViewModelFactory.create()
            }
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val editContext = LocalContext.current
        val dialogDelegate =
            rememberViewModel("InfiniteGridLayout.EditTileGrid") {
                viewModel.resetDialogDelegateFactory.create {
                    // Clear the stack of snapshots on reset
                    snapshotViewModel.clearStack()

                    // Automatically scroll to the top on reset
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                }
            }
        val showDualShadeSetting =
            DualShadeFlag.isEnabled &&
                LocalResources.current.getBoolean(
                    com.android.settingslib.R.bool.config_useDualShadeSetting
                )
        val actions =
            remember(topBarActionsViewModel, showDualShadeSetting) {
                topBarActionsViewModel.actions(showDualShadeSetting).toMutableStateList()
            }
        val columns = columnsViewModel.columns
        val largeTilesSpan = columnsViewModel.largeSpan
        val largeTiles by viewModel.iconTilesViewModel.largeTilesState

        val edit = LocalQsPanels.current?.panels?.editMode
        val editLargeTiles =
            remember(largeTiles, edit) { largeTiles + (edit?.elementSpecs ?: emptySet()) }
        val fullWidthSpecs = edit?.fullWidthSpecs() ?: emptySet()
        val currentTiles by rememberUpdatedState(tiles.filter { it.isCurrent })
        val listState =
            remember(columns, largeTilesSpan, fullWidthSpecs) {
                EditTileListState(
                    currentTiles,
                    editLargeTiles,
                    columns = columns,
                    largeTilesSpan = largeTilesSpan,
                    fullWidthSpecs = fullWidthSpecs,
                    edit = edit,
                )
            }
        LaunchedEffect(currentTiles, editLargeTiles) {
            listState.updateTiles(currentTiles, editLargeTiles)
        }

        DefaultEditTileGrid(
            listState = listState,
            allTiles = tiles,
            modifier = modifier,
            scrollState = scrollState,
            snapshotViewModel = snapshotViewModel,
            onStopEditing = onStopEditing,
            topBarActions = actions,
        ) { action ->
            // Opening the dialog doesn't require a snapshot
            if (action != EditAction.ResetGrid) {
                snapshotViewModel.takeSnapshot(currentTiles.map { it.tileSpec }, largeTiles)
            }

            when (action) {
                is EditAction.AddTile -> {
                    onAddTile(action.tileSpec, listState.tileSpecs().size)
                }
                is EditAction.InsertTile -> {
                    onAddTile(action.tileSpec, action.position)
                }
                is EditAction.RemoveTile -> {
                    onRemoveTile(action.tileSpec)
                }
                EditAction.ResetGrid -> {
                    dialogDelegate.showDialog()
                }
                is EditAction.ResizeTile -> {
                    if (edit?.resize(editContext, action.tileSpec, action.toIcon) != true) {
                        iconTilesViewModel.resize(action.tileSpec, action.toIcon)
                    }
                }
                is EditAction.SetTiles -> {
                    onSetTiles(action.tileSpecs)
                }
            }
        }
    }
}
