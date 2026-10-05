/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.panels.ui.compose.PenguinGrid
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import kotlin.math.roundToInt

private const val Columns = 4
private val CellHeight = 104.dp
private val ToggleSize = 56.dp

@Composable
fun OneUiTogglesEditor(viewModel: QuickSettingsContainerViewModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalPenguinGridFlavor provides PenguinGrid.Flavor.OneUi) {
        val editViewModel = viewModel.editModeViewModel
        val model = rememberPenguinGridModel(viewModel)
        val tiles by editViewModel.tiles.collectAsStateWithLifecycle(emptyList())
        val bySpec = remember(tiles) { tiles.associateBy { it.tileSpec } }
        val current = remember(tiles) { tiles.filter { it.isCurrent }.map { it.tileSpec } }
        val latestCurrent by rememberUpdatedState(current)

        var order by remember { mutableStateOf(model.loose) }
        var dragged by remember { mutableStateOf<TileSpec?>(null) }
        LaunchedEffect(model.loose) { if (dragged == null) order = model.loose }

        val done = { OneUiToggleEditing.active = false }
        BackHandler(onBack = done)
        DisposableEffect(Unit) {
            onDispose {
                if (OneUiToggleEditing.active) {
                    OneUiToggleEditing.active = false
                    editViewModel.stopEditing()
                }
            }
        }

        fun commit(newOrder: List<TileSpec>) {
            val placed = newOrder.toSet()
            val queue = ArrayDeque(newOrder)
            editViewModel.setTiles(latestCurrent.map { if (it in placed) queue.removeFirst() else it })
        }

        Column(
            modifier =
                modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.oneui_expandable_area),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(vertical = 16.dp)
            ) {
                val density = LocalDensity.current
                val cellWidth = maxWidth / Columns
                val cellPx = with(density) { cellWidth.toPx() }
                val rowPx = with(density) { CellHeight.toPx() }
                val rows = ((order.size + Columns - 1) / Columns).coerceAtLeast(1)
                val haptics = LocalHapticFeedback.current
                var dragOffset by remember { mutableStateOf(Offset.Zero) }
                fun slot(index: Int) =
                    Offset((index % Columns) * cellPx, (index / Columns) * rowPx)

                Box(
                    Modifier.fillMaxWidth().height(CellHeight * rows).pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { down ->
                                val column = (down.x / cellPx).toInt().coerceIn(0, Columns - 1)
                                val row = (down.y / rowPx).toInt()
                                val index = row * Columns + column
                                val spec = order.getOrNull(index) ?: return@detectDragGesturesAfterLongPress
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragged = spec
                                dragOffset = slot(index)
                            },
                            onDragEnd = {
                                if (dragged != null) commit(order)
                                dragged = null
                            },
                            onDragCancel = { dragged = null },
                        ) { change, amount ->
                            val spec = dragged ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragOffset += amount
                            val center = dragOffset + Offset(cellPx / 2, rowPx / 2)
                            val column = (center.x / cellPx).toInt().coerceIn(0, Columns - 1)
                            val row = (center.y / rowPx).toInt().coerceAtLeast(0)
                            val to = (row * Columns + column).coerceIn(0, order.lastIndex)
                            val from = order.indexOf(spec)
                            if (to != from) {
                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                            }
                        }
                    }
                ) {
                    if (order.isEmpty()) {
                        Text(
                            text = stringResource(R.string.oneui_toggles_empty),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                    order.forEachIndexed { index, spec ->
                        val tile = bySpec[spec]
                        if (tile != null) key(spec) {
                            val target = slot(index).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                            val position = remember { Animatable(target, IntOffset.VectorConverter) }
                            LaunchedEffect(target) {
                                position.animateTo(
                                    target,
                                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                            val held = dragged == spec
                            Box(
                                Modifier.zIndex(if (held) 1f else 0f)
                                    .offset {
                                        if (held) {
                                            IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                        } else {
                                            position.value
                                        }
                                    }
                                    .size(cellWidth, CellHeight)
                                    .graphicsLayer {
                                        val scale = if (held) 1.08f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            ) {
                                ToggleCell(
                                    label = tile.label.text,
                                    icon = { SmallTileContent(iconProvider = { tile.icon }, color = Color.White, size = { 24.dp }) },
                                    onRemove = { editViewModel.removeTile(spec) },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.quick_settings_done),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.padding(top = 20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = done)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ToggleCell(label: String, icon: @Composable () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier.padding(top = 4.dp)
                    .size(ToggleSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset(x = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3C))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.penguin_cc_remove),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}
