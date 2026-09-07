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

import android.content.ContentResolver
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.composefragment.POSITION_ABOVE_GRID
import com.android.systemui.qs.composefragment.POSITION_BELOW_GRID
import com.android.systemui.qs.composefragment.POSITION_HEADER
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_POSITION
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.res.R

val FOLDER_SPEC = TileSpec.create("penguin_connectivity_folder")
val MEDIA_SPEC = TileSpec.create("penguin_media_player")
val SLIDERS_SPEC = TileSpec.create("penguin_sliders")

val PANEL_ELEMENT_SPECS = setOf(FOLDER_SPEC, MEDIA_SPEC, SLIDERS_SPEC)

fun TileSpec.panelSpanSetting(): String? =
    when (this) {
        FOLDER_SPEC -> SETTING_QS_FOLDER_SPAN
        MEDIA_SPEC -> SETTING_QS_MEDIA_SPAN
        SLIDERS_SPEC -> SETTING_QS_SLIDERS_SPAN
        else -> null
    }

val LocalPanelElementPreview =
    compositionLocalOf<Map<TileSpec, @Composable () -> Unit>> { emptyMap() }

@Composable
fun EditMode(
    viewModel: EditModeViewModel,
    modifier: Modifier = Modifier,
    previews: Map<TileSpec, @Composable () -> Unit> = emptyMap(),
) {
    CompositionLocalProvider(LocalPanelElementPreview provides previews) {
        EditModeContent(viewModel, modifier)
    }
}

@Composable
private fun EditModeContent(viewModel: EditModeViewModel, modifier: Modifier = Modifier) {
    val gridLayout by viewModel.gridLayout.collectAsStateWithLifecycle()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())
    val resolver = LocalContext.current.contentResolver

    BackHandler { viewModel.stopEditing() }

    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    Column(modifier) {
        gridLayout.EditTileGrid(
            tiles.withPanelElements(resolver),
            Modifier,
            viewModel::addTile,
            { spec -> if (spec.isPanelElement()) resolver.park(spec) else viewModel.removeTile(spec) },
            { specs -> viewModel.setTiles(specs.stripPanelElements(resolver)) },
            viewModel::stopEditing,
        )
    }
}

private fun TileSpec.isPanelElement() = this in PANEL_ELEMENT_SPECS

private fun TileSpec.positionSetting() =
    when (this) {
        FOLDER_SPEC -> SETTING_QS_FOLDER_POSITION
        SLIDERS_SPEC -> SETTING_QS_SLIDERS_POSITION
        else -> SETTING_QS_MEDIA_POSITION
    }

private fun TileSpec.editIndexSetting() =
    when (this) {
        FOLDER_SPEC -> "qs_connectivity_folder_edit_index"
        SLIDERS_SPEC -> "qs_sliders_edit_index"
        else -> "qs_media_edit_index"
    }

private fun ContentResolver.editIndex(spec: TileSpec) =
    Settings.Secure.getInt(
        this,
        spec.editIndexSetting(),
        when (spec) {
            FOLDER_SPEC -> 0
            MEDIA_SPEC -> 1
            else -> 2
        },
    )

private fun ContentResolver.park(spec: TileSpec) {
    Settings.Secure.putInt(this, spec.positionSetting(), POSITION_HEADER)
}

private fun List<EditTileViewModel>.withPanelElements(
    resolver: ContentResolver
): List<EditTileViewModel> {
    val current = filter { it.isCurrent }.toMutableList()
    val rest = filterNot { it.isCurrent }
    PANEL_ELEMENT_SPECS.sortedBy { resolver.editIndex(it) }
        .forEach { spec ->
            val index = resolver.editIndex(spec).coerceIn(0, current.size)
            current.add(index, spec.toEditTileViewModel())
        }
    return current + rest
}

private fun List<TileSpec>.stripPanelElements(resolver: ContentResolver): List<TileSpec> {
    val firstTile = indexOfFirst { !it.isPanelElement() }
    forEachIndexed { index, spec ->
        if (spec.isPanelElement()) {
            Settings.Secure.putInt(resolver, spec.editIndexSetting(), index)
            val position =
                if (firstTile == -1 || index < firstTile) POSITION_ABOVE_GRID
                else POSITION_BELOW_GRID
            Settings.Secure.putInt(resolver, spec.positionSetting(), position)
        }
    }
    return filterNot { it.isPanelElement() }
}

private fun TileSpec.toEditTileViewModel(): EditTileViewModel {
    val label =
        when (this) {
            FOLDER_SPEC -> "Connectivity folder"
            SLIDERS_SPEC -> "Brightness and volume"
            else -> "Media player"
        }
    val iconRes =
        when (this) {
            FOLDER_SPEC -> R.drawable.ic_apps_expressive
            SLIDERS_SPEC -> R.drawable.ic_brightness_full
            else -> R.drawable.ic_music_note
        }
    return EditTileViewModel(
        tileSpec = this,
        icon = Icon.Resource(iconRes, null),
        label = AnnotatedString(label),
        inlinedLabel = null,
        appName = null,
        appIcon = null,
        isCurrent = true,
        isDualTarget = false,
        availableEditActions = setOf(AvailableEditActions.MOVE, AvailableEditActions.REMOVE),
        category = TileCategory.CONNECTIVITY,
    )
}
