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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.grid.ui.compose.CustomVerticalSpannedGrid
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.shared.model.splitInRowsSequence
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.QuickQuickSettingsViewModel
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.toElementKey
import com.android.systemui.res.R

@Composable
fun ContentScope.QuickQuickSettings(
    viewModel: QuickQuickSettingsViewModel,
    modifier: Modifier = Modifier.fillMaxWidth(),
    columnsOverride: Int? = null,
    showAnchor: Boolean = true,
    listening: () -> Boolean,
) {
    val columns = columnsOverride ?: viewModel.columns
    val sizedTiles =
        remember(columns, viewModel.allSizedTiles) {
            splitInRowsSequence(viewModel.allSizedTiles, columns).take(2).toList().flatten()
        }
    val tiles = sizedTiles.fastMap { it.tile }
    val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val bounceables = remember(sizedTiles) { List(sizedTiles.size) { BounceableTileViewModel() } }
    val spans by remember(sizedTiles) { derivedStateOf { sizedTiles.fastMap { it.width } } }

    Box(modifier = modifier) {
        if (showAnchor) {
            GridAnchor()
        }
        CustomVerticalSpannedGrid(
            columns = columns,
            rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
            spans = spans,
            modifier = Modifier.sysuiResTag("qqs_tile_layout"),
            keys = { sizedTiles[it].tile.spec },
        ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
            val it = sizedTiles[spanIndex]
            val tileContent =
                @Composable {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Tile(
                            tile = it.tile,
                            iconOnly = it.isIcon,
                            squishiness = { squishiness },
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
                            tileHapticsViewModelFactory = viewModel.tileHapticsViewModelFactory,
                            detailsViewModel = null,
                            isVisible = listening,
                        )
                        if (it.isIcon) {
                            Text(
                                text = it.tile.currentState.label?.toString() ?: "",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp).alpha(0f),
                            )
                        }
                    }
                }

            Element(it.tile.spec.toElementKey(), Modifier) { tileContent() }
        }
    }

    TileListener(tiles, listening)
}
