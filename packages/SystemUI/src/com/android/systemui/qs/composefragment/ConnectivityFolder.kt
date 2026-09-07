/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.qs.composefragment

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.style.isStockQsStyle
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R

object ConnectivityFolderSpecs {
    val Large = listOf("airplane", "cast", "wifi")
    val Small = listOf("cell", "bt", "hotspot", "dnd")
    val ExpandedCards = listOf("wifi", "bt", "cell", "cast")
}

const val SETTING_QS_FOLDER_LARGE = "qs_connectivity_folder_large"
const val SETTING_QS_FOLDER_SMALL = "qs_connectivity_folder_small"

const val SETTING_QS_FOLDER_SPAN = "qs_connectivity_folder_span"
const val SETTING_QS_FOLDER_POSITION = "qs_connectivity_folder_position"
const val SETTING_QS_MEDIA_POSITION = "qs_media_position"

const val POSITION_HEADER = -1
const val POSITION_ABOVE_GRID = 0
const val POSITION_BELOW_GRID = 1
const val SETTING_QS_MEDIA_SPAN = "qs_media_span"

@Composable
fun secureIntSetting(key: String, default: Int): Int {
    val resolver = LocalContext.current.contentResolver
    var value by remember(key) { mutableStateOf(Settings.Secure.getInt(resolver, key, default)) }
    DisposableEffect(resolver, key) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value = Settings.Secure.getInt(resolver, key, default)
                }
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return value
}

@Composable
private fun folderSpecs(): Pair<List<String>, List<String>> {
    val resolver = LocalContext.current.contentResolver
    fun read(key: String, fallback: List<String>): List<String> {
        val raw = Settings.Secure.getString(resolver, key) ?: return fallback
        val parsed = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parsed.ifEmpty { fallback }
    }
    var specs by remember {
        mutableStateOf(
            read(SETTING_QS_FOLDER_LARGE, ConnectivityFolderSpecs.Large) to
                read(SETTING_QS_FOLDER_SMALL, ConnectivityFolderSpecs.Small)
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    specs =
                        read(SETTING_QS_FOLDER_LARGE, ConnectivityFolderSpecs.Large) to
                            read(SETTING_QS_FOLDER_SMALL, ConnectivityFolderSpecs.Small)
                }
            }
        listOf(SETTING_QS_FOLDER_LARGE, SETTING_QS_FOLDER_SMALL).forEach {
            resolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return specs
}

private val LargeCircle = 56.dp
private val HeroCircle = 64.dp
private val SmallCircle = 30.dp
private val CellSize = 72.dp
private val BigCardHeight = 148.dp
private const val GlassSurfaceAlpha = 0.45f
private val CardPadding = 10.dp
private val CellSpacing = 6.dp

@Composable
fun ConnectivityFolder(
    tiles: List<TileViewModel>,
    modifier: Modifier = Modifier,
    compactHeight: Dp? = null,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val bySpec = remember(tiles) { tiles.associateBy { it.spec.spec } }
    val (largeSpecs, smallSpecs) = folderSpecs()
    val large = remember(bySpec, largeSpecs) { largeSpecs.mapNotNull { bySpec[it] } }
    val small = remember(bySpec, smallSpecs) { smallSpecs.mapNotNull { bySpec[it] } }

    if (large.isEmpty() && small.isEmpty()) return

    var listening by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        listening = true
        onStopOrDispose { listening = false }
    }
    TileListener(large + small) { listening }

    val gap = dimensionResource(id = R.dimen.qs_tile_margin_vertical)

    if (expanded) {
        ExpandedSheet(
            large = large,
            small = small,
            gap = gap,
            uniformGrid = secureIntSetting(SETTING_QS_FOLDER_SPAN, 1) >= 2,
            onDone = { onExpandedChange(false) },
        )
        return
    }

    val cell = compactHeight?.let { (it - CardPadding * 2 - CellSpacing) / 2 } ?: CellSize

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(glassSurface())
                .padding(CardPadding),
        verticalArrangement = spacedBy(CellSpacing),
    ) {
        if (compactHeight == null) {
            Row(horizontalArrangement = spacedBy(CellSpacing), modifier = Modifier.fillMaxWidth()) {
                large.forEach { tile -> Cell(cell) { FolderCircle(tile, cell * 0.88f) } }
                if (small.isNotEmpty()) {
                    Cell(cell) {
                        SmallCluster(small, cell * 0.34f, onClick = { onExpandedChange(true) })
                    }
                }
            }
        } else {
            Row(horizontalArrangement = spacedBy(CellSpacing), modifier = Modifier.fillMaxWidth()) {
                Cell(cell) { large.getOrNull(0)?.let { FolderCircle(it, cell * 0.88f) } }
                Cell(cell) { large.getOrNull(1)?.let { FolderCircle(it, cell * 0.88f) } }
            }
            Row(horizontalArrangement = spacedBy(CellSpacing), modifier = Modifier.fillMaxWidth()) {
                Cell(cell) { large.getOrNull(2)?.let { FolderCircle(it, cell * 0.96f) } }
                Cell(cell) {
                    if (small.isNotEmpty()) {
                        SmallCluster(small, cell * 0.38f, onClick = { onExpandedChange(true) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedSheet(
    large: List<TileViewModel>,
    small: List<TileViewModel>,
    gap: Dp,
    uniformGrid: Boolean,
    onDone: () -> Unit,
) {
    val all = large + small
    val cards = if (uniformGrid) all else all.filter { it.spec.spec in ConnectivityFolderSpecs.ExpandedCards }
    val rows = all.filterNot { it in cards }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = spacedBy(gap)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_settings_connectivity_folder_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.quick_settings_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDone),
            )
        }
        rows.take(1).forEach { FolderRow(it) }
        cards.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(gap)) {
                pair.forEach { tile -> Box(Modifier.weight(1f)) { FolderBigCard(tile) } }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        rows.drop(1).forEach { FolderRow(it) }
    }
}

@Composable
private fun FolderBigCard(tile: TileViewModel) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .height(BigCardHeight)
                .clip(RoundedCornerShape(26.dp))
                .background(glassSurface())
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(folderBackground(active)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(22.dp))
        }
        Column {
            Text(
                text = uiState.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.secondaryLabel.isNotBlank()) {
                Text(
                    text = uiState.secondaryLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Cell(height: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.weight(1f).height(height),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun FolderCircle(tile: TileViewModel, diameter: Dp) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Box(
        modifier =
            Modifier.size(diameter)
                .clip(CircleShape)
                .background(folderBackground(active))
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(diameter / 2))
    }
}

@Composable
private fun SmallCluster(tiles: List<TileViewModel>, dot: Dp, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(4.dp)
    ) {
        Column(
            verticalArrangement = spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = spacedBy(5.dp)) {
                tiles.getOrNull(0)?.let { SmallDot(it, dot) }
                tiles.getOrNull(1)?.let { SmallDot(it, dot) }
            }
            Row(horizontalArrangement = spacedBy(5.dp)) {
                tiles.getOrNull(2)?.let { SmallDot(it, dot) }
                tiles.getOrNull(3)?.let { SmallDot(it, dot) }
            }
        }
    }
}

@Composable
private fun SmallDot(tile: TileViewModel, diameter: Dp) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Box(
        modifier = Modifier.size(diameter).clip(CircleShape).background(folderBackground(active)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(diameter / 2))
    }
}

@Composable
private fun FolderRow(tile: TileViewModel) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(glassSurface())
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(44.dp).clip(CircleShape).background(folderBackground(active)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = uiState.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.secondaryLabel.isNotBlank()) {
                Text(
                    text = uiState.secondaryLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun glassSurface(): Color =
    LocalAndroidColorScheme.current.surfaceEffect1.copy(
        alpha = if (isStockQsStyle) 1f else GlassSurfaceAlpha
    )

@Composable
private fun folderBackground(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.primary else glassSurface()

@Composable
private fun folderForeground(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun rememberTileState(tile: TileViewModel): Pair<TileUiState, Icon> {
    val context = LocalContext.current
    val resources = context.resources
    val state by
        produceState(
            tile.currentState.let { it.toUiState(resources) to it.toIconProvider() },
            tile,
            resources,
        ) {
            tile.state.collect { value = it.toUiState(resources) to it.toIconProvider() }
        }
    return state.first to context.folderIcon(state.second)
}

private fun Context.folderIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

const val SETTING_QS_CONNECTIVITY_FOLDER = "qs_connectivity_folder"

@Composable
fun connectivityFolderEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember {
        mutableStateOf(
            Settings.Secure.getInt(resolver, SETTING_QS_CONNECTIVITY_FOLDER, 0) != 0
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enabled = Settings.Secure.getInt(resolver, SETTING_QS_CONNECTIVITY_FOLDER, 0) != 0
                }
            }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTING_QS_CONNECTIVITY_FOLDER),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun TileViewModel.primaryAction(uiState: TileUiState) {
    if (uiState.handlesToggleClick) toggleClick() else mainClick(null)
}
