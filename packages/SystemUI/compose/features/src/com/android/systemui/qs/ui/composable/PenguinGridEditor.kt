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

package com.android.systemui.qs.ui.composable

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.qs.panels.ui.compose.FOLDER_SPEC
import com.android.systemui.qs.panels.ui.compose.MEDIA_SPEC
import com.android.systemui.qs.panels.ui.compose.PenguinGrid
import com.android.systemui.qs.panels.ui.compose.PenguinGrid.Item
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BadgeSize = 24.dp

private val BadgeZone = 26.dp

private val EdgeZone = 36.dp
private const val EdgeDwellMillis = 550L

private class Drag(val item: Item, val fromPage: Int, val grab: Offset) {
    var pointer by mutableStateOf(Offset.Zero)
}

@Composable
fun ContentScope.PenguinGridEditor(viewModel: QuickSettingsContainerViewModel, modifier: Modifier = Modifier) {
    PenguinGridEditorHost(viewModel, modifier)
}

@Composable
fun ContentScope.OneUiGridEditor(viewModel: QuickSettingsContainerViewModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalPenguinGridFlavor provides PenguinGrid.Flavor.OneUi) {
        PenguinGridEditorHost(viewModel, modifier)
    }
}

@Composable
private fun ContentScope.PenguinGridEditorHost(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier,
) {
    val editViewModel = viewModel.editModeViewModel
    val gridLayout by editViewModel.gridLayout.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { closeExpansions() }
    BackHandler { editViewModel.stopEditing() }
    DisposableEffect(Unit) { onDispose { editViewModel.stopEditing() } }
    gridLayout.TileSizing { _, resize ->
        PenguinGridTheme { PenguinGridEditorContent(viewModel, resize, modifier) }
    }
}

@Composable
private fun ContentScope.PenguinGridEditorContent(
    viewModel: QuickSettingsContainerViewModel,
    resizeTile: (TileSpec, Boolean) -> Unit,
    modifier: Modifier,
) {
    val editViewModel = viewModel.editModeViewModel
    val resolver = LocalContext.current.contentResolver
    val flavor = LocalPenguinGridFlavor.current
    val oneUi = flavor == PenguinGrid.Flavor.OneUi
    val model = rememberPenguinGridModel(viewModel)
    val latestModel by rememberUpdatedState(model)
    val pages = model.pages
    CompositionLocalProvider(LocalPenguinGridLoose provides model.loose) {
    val mediaShown = viewModel.showMedia && viewModel.hasMediaCards
    val editTiles by editViewModel.tiles.collectAsStateWithLifecycle(emptyList())
    val tilesBySpec = remember(editTiles) { editTiles.associateBy { it.tileSpec } }

    fun commit(next: List<List<Item>>) {
        val shown = next.flatten().map { it.spec }.toSet()
        val pending =
            latestModel.saved.orEmpty().mapIndexed { index, page ->
                index to
                    page.filter {
                        !PenguinGrid.isModule(it.spec) &&
                            it.spec !in latestModel.tiles &&
                            it.spec !in shown
                    }
            }
        val merged = next.map { it.toMutableList() }.toMutableList()
        for ((index, items) in pending) {
            if (items.isEmpty()) continue
            while (merged.size <= index) merged.add(mutableListOf())
            for (item in items) {
                merged[index].add(
                    if (merged[index].none { it.overlaps(item) }) item
                    else PenguinGrid.firstFree(merged[index], item.spec, item.w, item.h)
                )
            }
        }
        val cleaned = merged.filterIndexed { index, page -> index == 0 || page.isNotEmpty() }
        Settings.Secure.putString(resolver, flavor.setting, PenguinGrid.serialize(cleaned))
    }

    var drag by remember { mutableStateOf<Drag?>(null) }
    var resizing by remember { mutableStateOf<Pair<Int, Item>?>(null) }
    var adding by remember { mutableStateOf(false) }
    val pageCount = pages.size + if (drag != null) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val pageHeight = penguinPageHeight(pages, extraRows = 1)
    val horizontalMargin = PenguinGridMargin
    val rowHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    val gap = PenguinGridGap
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Rounded.Add, stringResource(R.string.penguin_cc_add_control)) {
                adding = true
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.penguin_cc_reset),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable {
                            Settings.Secure.putString(resolver, flavor.setting, null)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.quick_settings_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { editViewModel.stopEditing() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = maxWidth - horizontalMargin * 2
            val columnWidth = (gridWidth - gap * (PenguinGrid.COLUMNS - 1)) / PenguinGrid.COLUMNS
            val metrics =
                remember(columnWidth, rowHeight, gap, horizontalMargin, density) {
                    with(density) {
                        CellMetrics(
                        column = columnWidth.toPx(),
                        row = rowHeight.toPx(),
                        gap = gap.toPx(),
                        left = horizontalMargin.toPx(),
                        badge = BadgeZone.toPx(),
                        )
                    }
                }
            val currentMetrics by rememberUpdatedState(metrics)
            val pageHeightPx = with(density) { pageHeight.toPx() }
            val edgePx = with(density) { EdgeZone.toPx() }

            val currentPages by rememberUpdatedState(pages)
            val activeDrag = drag
            val targetPage = pagerState.currentPage
            val preview: (Int) -> List<Item> = { index ->
                val page = pages.getOrNull(index).orEmpty()
                val sized = resizing?.takeIf { it.first == index }?.second
                val original = sized?.let { target -> page.firstOrNull { it.spec == target.spec } }
                if (activeDrag == null && sized != null && original != null) {
                    PenguinGrid.place(page, original, original.x, original.y, sized.w, sized.h)
                } else if (activeDrag == null) {
                    page
                } else {
                    val without =
                        if (index == activeDrag.fromPage) page.filter { it != activeDrag.item }
                        else page
                    if (index == targetPage) {
                        val (x, y) = metrics.cellFor(activeDrag.pointer - activeDrag.grab)
                        dropOnto(without, activeDrag.item, x, y)
                    } else {
                        without
                    }
                }
            }

            LaunchedEffect(activeDrag) {
                val held = activeDrag ?: return@LaunchedEffect
                var dwell = 0L
                while (true) {
                    delay(50)
                    val y = held.pointer.y
                    val direction =
                        when {
                            y < edgePx -> -1
                            y > pageHeightPx - edgePx -> 1
                            else -> 0
                        }
                    val next = pagerState.currentPage + direction
                    if (direction != 0 && next in 0 until pagerState.pageCount) {
                        dwell += 50
                        if (dwell >= EdgeDwellMillis) {
                            dwell = 0
                            pagerState.animateScrollToPage(next)
                        }
                    } else {
                        dwell = 0
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    .height(pageHeight)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down =
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                            val page = pagerState.currentPage
                            val hit =
                                currentMetrics.hit(
                                    currentPages.getOrNull(page).orEmpty(),
                                    down.position,
                                ) ?: return@awaitEachGesture
                            try {
                                var started: Drag? = null
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (
                                        started == null &&
                                            (change.position - down.position).getDistance() >
                                                viewConfiguration.touchSlop
                                    ) {
                                        started =
                                            Drag(hit, page, down.position - currentMetrics.origin(hit)).also {
                                                it.pointer = change.position
                                                drag = it
                                            }
                                    }
                                    if (started != null) {
                                        change.consume()
                                        started.pointer = change.position
                                    }
                                }
                                val held = started ?: return@awaitEachGesture
                                val target = pagerState.currentPage
                                val (x, y) = currentMetrics.cellFor(held.pointer - held.grab)
                                val next = currentPages.map { it.toMutableList() }.toMutableList()
                                while (next.size <= target) next.add(mutableListOf())
                                next[held.fromPage].remove(held.item)
                                next[target] =
                                    dropOnto(next[target], held.item, x, y).toMutableList()
                                commit(next)
                            } finally {
                                drag = null
                            }
                        }
                    }
            ) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = activeDrag == null,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    EditPage(
                        items = preview(index),
                        rows = PenguinGrid.rows(pages.getOrNull(index).orEmpty()) + 1,
                        metrics = metrics,
                        horizontalMargin = horizontalMargin,
                        columnWidth = columnWidth,
                        rowHeight = rowHeight,
                        gap = gap,
                        dragged =
                            activeDrag?.item?.spec?.takeIf {
                                index == activeDrag.fromPage || index == targetPage
                            },
                        onRemove = { item ->
                            val next = pages.map { it.toMutableList() }
                            next.getOrNull(index)?.remove(item)
                            commit(next)
                            if (!PenguinGrid.isModule(item.spec) && !oneUi) {
                                editViewModel.removeTile(item.spec)
                            }
                        },
                        onResizePreview = { spec, w, h ->
                            resizing = index to Item(spec, 0, 0, w, h)
                        },
                        onResize = { spec, w, h ->
                            resizing = null
                            val latest = currentPages
                            val item = latest.getOrNull(index)?.firstOrNull { it.spec == spec }
                            if (item != null && (item.w != w || item.h != h)) {
                                val next = latest.map { it.toMutableList() }.toMutableList()
                                next[index] =
                                    PenguinGrid.place(next[index], item, item.x, item.y, w, h)
                                        .toMutableList()
                                commit(next)
                                if (!PenguinGrid.isModule(spec)) resizeTile(spec, w > 1)
                            }
                        },
                    ) { item ->
                        EditItem(viewModel, item, mediaShown, tilesBySpec)
                    }
                }

                PageIcons(
                    pages = List(pageCount) { pages.getOrNull(it).orEmpty() },
                    current = pagerState.currentPage,
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .offset(x = -(horizontalMargin - PageIconSize) / 2)
                            .padding(top = pageHeight / 2),
                )

                activeDrag?.let { held ->
                    val topLeft = held.pointer - held.grab
                    Box(
                        Modifier.offset {
                                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                            }
                            .size(
                                columnWidth * held.item.w + gap * (held.item.w - 1),
                                rowHeight * held.item.h + gap * (held.item.h - 1),
                            )
                            .graphicsLayer {
                                scaleX = 1.06f
                                scaleY = 1.06f
                                alpha = 0.92f
                            }
                            .gesturesDisabled()
                    ) {
                        EditItem(viewModel, held.item, mediaShown, tilesBySpec)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.penguin_cc_add_control),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(editorGlass())
                    .clickable { adding = true }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        if (adding) {
            AddControlSheet(
                viewModel = viewModel,
                modules = model.modules,
                currentPage = pages.getOrNull(pagerState.currentPage).orEmpty(),
                loose = if (oneUi) model.loose.toSet() else emptySet(),
                onAddModule = { spec ->
                    val index = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                    val (w, h) = PenguinGrid.sizes(spec, flavor).first()
                    val next = pages.map { it.toMutableList() }
                    next[index] += PenguinGrid.firstFree(next[index], spec, w, h)
                    commit(next)
                    adding = false
                },
                onAddTile = { spec ->
                    val index = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                    val next = pages.map { it.toMutableList() }
                    next[index] += PenguinGrid.firstFree(next[index], spec, 1, 1)
                    Settings.Secure.putString(
                        resolver,
                        flavor.setting,
                        PenguinGrid.serialize(next),
                    )
                    if (spec !in model.loose) editViewModel.addTile(spec)
                    adding = false
                },
                onDismiss = { adding = false },
            )
        }
    }
    }
}

@Composable
private fun ContentScope.EditItem(
    viewModel: QuickSettingsContainerViewModel,
    item: Item,
    mediaShown: Boolean,
    tiles: Map<TileSpec, EditTileViewModel>,
) {
    if (
        PenguinGrid.isModule(item.spec) ||
            LocalPenguinGridFlavor.current == PenguinGrid.Flavor.OneUi
    ) {
        GridItemContent(
            viewModel = viewModel,
            item = item,
            mediaShown = mediaShown,
            interactable = false,
            listening = { false },
            preview = true,
        )
        return
    }
    val tile = tiles[item.spec]
    Row(
        modifier =
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(editorGlass())
                .padding(horizontal = if (item.w > 1) 16.dp else 0.dp),
        horizontalArrangement = if (item.w > 1) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile?.let {
            SmallTileContent(
                iconProvider = { it.icon },
                color = MaterialTheme.colorScheme.onSurface,
                size = { 24.dp },
            )
            if (item.w > 1) {
                Text(
                    text = it.label.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

private fun dropOnto(page: List<Item>, item: Item, x: Int, y: Int): List<Item> {
    val others = page.filter { it.spec != item.spec }
    return PenguinGrid.place(others + item, item, x, y)
}

private class CellMetrics(
    val column: Float,
    val row: Float,
    val gap: Float,
    val left: Float,
    val badge: Float,
) {
    fun origin(item: Item) = Offset(left + item.x * (column + gap), item.y * (row + gap))

    fun cellFor(topLeft: Offset): Pair<Int, Int> =
        ((topLeft.x - left) / (column + gap)).roundToInt().coerceIn(0, PenguinGrid.COLUMNS - 1) to
            (topLeft.y / (row + gap)).roundToInt().coerceAtLeast(0)

    fun hit(page: List<Item>, position: Offset): Item? {
        val onControl =
            page.any {
                val origin = origin(it)
                val end =
                    origin +
                        Offset(it.w * column + (it.w - 1) * gap, it.h * row + (it.h - 1) * gap)
                (position - origin).getDistance() < badge || (position - end).getDistance() < badge
            }
        if (onControl) return null
        return page.firstOrNull {
            val origin = origin(it)
            val right = origin.x + it.w * column + (it.w - 1) * gap
            val bottom = origin.y + it.h * row + (it.h - 1) * gap
            position.x in origin.x..right && position.y in origin.y..bottom
        }
    }
}

@Composable
private fun EditPage(
    items: List<Item>,
    rows: Int,
    metrics: CellMetrics,
    horizontalMargin: Dp,
    columnWidth: Dp,
    rowHeight: Dp,
    gap: Dp,
    dragged: TileSpec?,
    onRemove: (Item) -> Unit,
    onResizePreview: (TileSpec, Int, Int) -> Unit,
    onResize: (TileSpec, Int, Int) -> Unit,
    content: @Composable (Item) -> Unit,
) {
    val cellColor = editorGlass(0.4f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(metrics.column, metrics.row) * 0.36f
            for (y in 0 until rows) {
                for (x in 0 until PenguinGrid.COLUMNS) {
                    val center =
                        Offset(
                            metrics.left + x * (metrics.column + metrics.gap) + metrics.column / 2,
                            y * (metrics.row + metrics.gap) + metrics.row / 2,
                        )
                    drawRoundRect(
                        color = cellColor,
                        topLeft = center - Offset(radius, radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        cornerRadius = CornerRadius(radius),
                    )
                }
            }
        }
        items.forEach { item ->
            if (item.spec == dragged) return@forEach
            key(item.spec) {
                val target =
                    IntOffset(
                        metrics.origin(item).x.roundToInt(),
                        metrics.origin(item).y.roundToInt(),
                    )
                val offset by animateIntOffsetAsState(target, label = "PenguinGridEdit")
                Box(
                    Modifier.offset { offset }
                        .size(
                            columnWidth * item.w + gap * (item.w - 1),
                            rowHeight * item.h + gap * (item.h - 1),
                        )
                ) {
                    Box(Modifier.fillMaxSize().gesturesDisabled()) { content(item) }
                    Badge(
                        Icons.Rounded.Remove,
                        stringResource(R.string.penguin_cc_remove),
                        Modifier.align(Alignment.TopStart).offset(x = -6.dp, y = -6.dp),
                    ) {
                        onRemove(item)
                    }
                    if (PenguinGrid.sizes(item.spec, LocalPenguinGridFlavor.current).size > 1) {
                        ResizeHandle(
                            item = item,
                            metrics = metrics,
                            onPreview = onResizePreview,
                            onResize = onResize,
                            modifier =
                                Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    item: Item,
    metrics: CellMetrics,
    onPreview: (TileSpec, Int, Int) -> Unit,
    onResize: (TileSpec, Int, Int) -> Unit,
    modifier: Modifier,
) {
    val sizes = PenguinGrid.sizes(item.spec, LocalPenguinGridFlavor.current)
    val current by rememberUpdatedState(item)
    val currentMetrics by rememberUpdatedState(metrics)
    val currentOnPreview by rememberUpdatedState(onPreview)
    val currentOnResize by rememberUpdatedState(onResize)
    Box(
        modifier =
            modifier
                .size(BadgeSize + 12.dp)
                .pointerInput(item.spec) {
                    detectTapGestures {
                        val next = sizes[(sizes.indexOf(current.w to current.h) + 1) % sizes.size]
                        currentOnResize(current.spec, next.first, next.second)
                    }
                }
                .pointerInput(item.spec) {
                    var start = current
                    var total = Offset.Zero
                    var target = start.w to start.h
                    detectDragGestures(
                        onDragStart = {
                            start = current
                            total = Offset.Zero
                            target = start.w to start.h
                        },
                        onDragEnd = { currentOnResize(start.spec, target.first, target.second) },
                        onDragCancel = { currentOnResize(start.spec, start.w, start.h) },
                    ) { change, amount ->
                        change.consume()
                        total += amount
                        val m = currentMetrics
                        val w = start.w + total.x / (m.column + m.gap)
                        val h = start.h + total.y / (m.row + m.gap)
                        val nearest =
                            sizes.minBy { (sw, sh) -> (sw - w) * (sw - w) + (sh - h) * (sh - h) }
                        if (nearest != target) {
                            target = nearest
                            currentOnPreview(start.spec, nearest.first, nearest.second)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(BadgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInFull,
                contentDescription = stringResource(R.string.penguin_cc_resize),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp).rotate(90f),
            )
        }
    }
}

@Composable
private fun editorGlass(alpha: Float = 0.7f): Color =
    LocalAndroidColorScheme.current.surfaceEffect1.copy(alpha = alpha)

@Composable
private fun Badge(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    rotation: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(BadgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(editorGlass())
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AddControlSheet(
    viewModel: QuickSettingsContainerViewModel,
    modules: Set<TileSpec>,
    currentPage: List<Item>,
    loose: Set<TileSpec>,
    onAddModule: (TileSpec) -> Unit,
    onAddTile: (TileSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }
    val tiles by viewModel.editModeViewModel.tiles.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    val moduleNames =
        mapOf(
            MEDIA_SPEC to stringResource(R.string.penguin_cc_media),
            FOLDER_SPEC to stringResource(R.string.quick_settings_connectivity_folder_title),
            PenguinGrid.BRIGHTNESS_SPEC to stringResource(R.string.penguin_cc_brightness),
            PenguinGrid.VOLUME_SPEC to stringResource(R.string.penguin_cc_volume),
            PenguinGrid.ONEUI_TOGGLES_SPEC to stringResource(R.string.oneui_toggles),
            PenguinGrid.ONEUI_SOUND_SPEC to stringResource(R.string.oneui_sound_mode),
        )
    val moduleIcons =
        mapOf(
            MEDIA_SPEC to Icons.Rounded.MusicNote,
            FOLDER_SPEC to Icons.Rounded.Wifi,
            PenguinGrid.BRIGHTNESS_SPEC to Icons.Rounded.BrightnessHigh,
            PenguinGrid.VOLUME_SPEC to Icons.AutoMirrored.Rounded.VolumeUp,
            PenguinGrid.ONEUI_TOGGLES_SPEC to Icons.Rounded.Apps,
            PenguinGrid.ONEUI_SOUND_SPEC to Icons.Rounded.Vibration,
        )
    val onPage = currentPage.map { it.spec }.toSet()
    val availableModules =
        LocalPenguinGridFlavor.current.moduleSpecs.filter { it in modules && it !in onPage }
            .filter { moduleNames[it].orEmpty().contains(query, ignoreCase = true) }
    val availableTiles =
        tiles
            .filter { !it.isCurrent || it.tileSpec in loose }
            .filter { it.label.text.contains(query, ignoreCase = true) }
            .sortedBy { it.label.text.lowercase() }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 16.dp)
                .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier.weight(1f)
                        .clip(CircleShape)
                        .background(editorGlass(0.4f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.penguin_cc_search),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.quick_settings_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (availableModules.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.penguin_cc_modules)) }
                items(availableModules, key = { it.spec }) { spec ->
                    ControlRow(
                        icon = { tint ->
                            Icon(
                                imageVector = moduleIcons.getValue(spec),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        label = moduleNames.getValue(spec),
                        onClick = { onAddModule(spec) },
                    )
                }
            }
            if (availableTiles.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.penguin_cc_controls)) }
                items(availableTiles, key = { it.tileSpec.spec }) { tile ->
                    ControlRow(
                        icon = { tint ->
                            SmallTileContent(
                                iconProvider = { tile.icon },
                                color = tint,
                                size = { 20.dp },
                            )
                        },
                        label = tile.label.text,
                        onClick = { onAddTile(tile.tileSpec) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun ControlRow(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(CircleShape)
                .background(editorGlass()),
            contentAlignment = Alignment.Center,
        ) {
            icon(MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
