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

import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.compose.animation.scene.ContentScope
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.PlatformTheme
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.qs.composefragment.BrightnessLayout
import com.android.systemui.qs.shared.style.LocalGlassBlurAllowed
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import com.android.systemui.qs.composefragment.ConnectivityFolder
import com.android.systemui.qs.composefragment.ConnectivityFolderExpansion
import com.android.systemui.qs.composefragment.OneUiExtraDimButton
import com.android.systemui.qs.composefragment.OneUiSoundModeButton
import com.android.systemui.qs.composefragment.OneUiTileControl
import com.android.systemui.qs.composefragment.OneUiTogglesCard
import com.android.systemui.qs.composefragment.OneUiVolumePanelButton
import com.android.systemui.qs.composefragment.PenguinMediaCard
import com.android.systemui.qs.composefragment.VolumeLayout
import com.android.systemui.qs.composefragment.connectivityFolderEnabled
import com.android.systemui.qs.composefragment.connectivityFolderSpecs
import com.android.systemui.qs.panels.ui.compose.FOLDER_SPEC
import com.android.systemui.qs.panels.ui.compose.MEDIA_SPEC
import com.android.systemui.qs.panels.ui.compose.PenguinGrid
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull

object PenguinMediaExpansion {
    var expanded by mutableStateOf(false)
}

private object PenguinGridOrigin {
    var bounds by mutableStateOf<Rect?>(null)
}

internal val PageIconSize = 18.dp
private val PageIconSpacing = 36.dp

internal val PenguinGridMargin = 24.dp

internal val PenguinGridGap = 16.dp

@Composable
internal fun penguinModuleHeight(rows: Int): Dp {
    val row = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    return row * rows + PenguinGridGap * (rows - 1).coerceAtLeast(0)
}

internal val LocalPenguinGridFlavor = compositionLocalOf { PenguinGrid.Flavor.ControlCentre }

internal val LocalPenguinGridLoose = compositionLocalOf { emptyList<TileSpec>() }

internal class PenguinGridModel(
    val modules: Set<TileSpec>,
    val tiles: List<TileSpec>,
    val largeTiles: Set<TileSpec>,
    val saved: List<List<PenguinGrid.Item>>?,
    val pages: List<List<PenguinGrid.Item>>,
) {
    val loose: List<TileSpec> =
        pages.flatten().map { it.spec }.toSet().let { placed -> tiles.filterNot { it in placed } }
}

@Composable
internal fun rememberPenguinGridModel(viewModel: QuickSettingsContainerViewModel): PenguinGridModel {
    val flavor = LocalPenguinGridFlavor.current
    val oneUi = flavor == PenguinGrid.Flavor.OneUi
    val folderEnabled = !oneUi && connectivityFolderEnabled()
    val folderMemberSpecs = if (folderEnabled) connectivityFolderSpecs() else emptyList()
    val largeTiles = viewModel.tileGridViewModel.largeTiles
    val tileViewModels = viewModel.tileGridViewModel.tileViewModels
    val modules =
        remember(folderEnabled, flavor) {
            flavor.moduleSpecs.filter { folderEnabled || it != FOLDER_SPEC }.toSet()
        }
    val tiles =
        remember(tileViewModels, folderMemberSpecs) {
            tileViewModels.map { it.spec }.filterNot { it.spec in folderMemberSpecs }
        }
    val raw = secureStringSetting(flavor.setting)
    val roundTiles = !oneUi && liquidGlassEnabled()
    return remember(raw, modules, tiles, largeTiles, flavor, roundTiles) {
        val saved = PenguinGrid.parse(raw)
        val sizedAs = if (roundTiles) emptySet() else largeTiles
        val layout = if (roundTiles) saved?.map { page -> page.filter { it.spec in modules } } else saved
        val resolved =
            layout?.let { PenguinGrid.resolve(it, modules, tiles, sizedAs, flavor = flavor) }
                ?: PenguinGrid.defaults(modules, tiles, sizedAs, flavor)
        PenguinGridModel(
            modules = modules,
            tiles = tiles,
            largeTiles = largeTiles,
            saved = saved,
            pages =
                if (!roundTiles) resolved
                else
                    resolved.map { page ->
                        page.map { item ->
                            val grown = item.copy(h = 6)
                            if (
                                item.spec == FOLDER_SPEC && item.w == 4 && item.h == 4 &&
                                    page.none { it !== item && it.overlaps(grown) }
                            ) grown
                            else item
                        }
                    },
        )
    }
}

@Composable
internal fun penguinPageHeight(pages: List<List<PenguinGrid.Item>>, extraRows: Int = 0): Dp =
    penguinModuleHeight(max((pages.maxOfOrNull { PenguinGrid.rows(it) } ?: 0) + extraRows, 4))

@Composable
fun ContentScope.PenguinGridQuickSettings(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    PenguinGridTheme { PenguinGridQuickSettingsContent(viewModel, modifier) }
}

@Composable
fun ContentScope.OneUiGridQuickSettings(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalPenguinGridFlavor provides PenguinGrid.Flavor.OneUi) {
        PenguinGridTheme { PenguinGridQuickSettingsContent(viewModel, modifier) }
    }
}

@Composable
private fun ContentScope.PenguinGridQuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier,
) {
    val model = rememberPenguinGridModel(viewModel)
    val pages = model.pages
    CompositionLocalProvider(LocalPenguinGridLoose provides model.loose) {
        PenguinGridPages(viewModel, model, modifier)
    }
}

@Composable
private fun ContentScope.PenguinGridPages(
    viewModel: QuickSettingsContainerViewModel,
    model: PenguinGridModel,
    modifier: Modifier,
) {
    val pages = model.pages

    val panelVisible = isAlwaysComposedContentVisible()
    val host = LocalQsHostTransition.current ?: qsHostTransition()
    LaunchedEffect(panelVisible, host.leaving) {
        if (!panelVisible || host.leaving) closeExpansions()
    }

    var listening by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        listening = true
        onStopOrDispose { listening = false }
    }
    var interactable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { Elements.QuickSettingsContent.currentAlpha() }
            .filterNotNull()
            .collect { interactable = it >= .5f }
    }

    val pageHeight = penguinPageHeight(pages)
    val mediaShown = viewModel.showMedia && viewModel.hasMediaCards
    val pagerState = rememberPagerState(pageCount = { pages.size })

    val expanded = ConnectivityFolderExpansion.expanded || PenguinMediaExpansion.expanded
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            if (expanded) 1f else 0f,
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    val sheetShowing = expanded || progress.value > 0.01f
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        settled = true
    }
    val horizontalMargin = PenguinGridMargin

    Box(
        modifier =
            modifier
                .element(Elements.QuickSettingsContent)
                .fillMaxWidth()
                .then(if (settled) Modifier.animateContentSize() else Modifier)
                .sysuiResTag("quick_settings_panel")
    ) {
        val panelBlurAllowed = LocalGlassBlurAllowed.current
        val sheetUp by rememberUpdatedState(sheetShowing)
        val gridBlurAllowed = remember(panelBlurAllowed) { { panelBlurAllowed() && !sheetUp } }
        CompositionLocalProvider(LocalGlassBlurAllowed provides gridBlurAllowed) {
        VerticalPager(
            state = pagerState,
            userScrollEnabled = !sheetShowing,
            modifier =
                Modifier.fillMaxWidth()
                    .height(pageHeight)
                    .padding(horizontal = horizontalMargin)
                    .graphicsLayer {
                        val f = progress.value
                        alpha = 1f - f
                        scaleX = 1f - 0.06f * f
                        scaleY = scaleX
                    }
                    .thenIf(!interactable || sheetShowing) { Modifier.gesturesDisabled() },
        ) { page ->
            ControlGrid(
                pages[page],
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                closeExpansions()
                                viewModel.editModeViewModel.startEditing()
                            }
                        )
                    },
                minHeight = pageHeight,
            ) { item ->
                GridItemContent(
                    viewModel = viewModel,
                    item = item,
                    mediaShown = mediaShown,
                    interactable = interactable,
                    listening = { listening },
                    modifier =
                        if (page == 0 && item.spec == FOLDER_SPEC) {
                            Modifier.element(Elements.ConnectivityFolder)
                        } else {
                            Modifier
                        },
                )
            }
        }
        }

        if (pages.size > 1) {
            PageIcons(
                pages = pages,
                current = pagerState.currentPage,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = -(horizontalMargin - PageIconSize) / 2)
                        .padding(top = pageHeight / 2)
                        .graphicsLayer { alpha = 1f - progress.value },
            )
        }

        if (sheetShowing) {
            Box(
                Modifier.matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        closeExpansions()
                    }
            )
            ExpandedSheet(
                viewModel = viewModel,
                mediaShown = mediaShown,
                progress = { progress.value },
                horizontalMargin = horizontalMargin,
            )
        }
    }
}

@Composable
private fun ExpandedSheet(
    viewModel: QuickSettingsContainerViewModel,
    mediaShown: Boolean,
    progress: () -> Float,
    horizontalMargin: Dp,
) {
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }
    var showingMedia by remember { mutableStateOf(PenguinMediaExpansion.expanded) }
    if (PenguinMediaExpansion.expanded) showingMedia = true
    else if (ConnectivityFolderExpansion.expanded) showingMedia = false

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = horizontalMargin)
                .onGloballyPositioned { sheetBounds = it.boundsInRoot() }
                .graphicsLayer {
                    val f = progress()
                    val from = PenguinGridOrigin.bounds
                    val to = sheetBounds
                    alpha = (f * 1.6f).coerceIn(0f, 1f)
                    if (from != null && to != null && to.width > 0f && to.height > 0f) {
                        transformOrigin =
                            TransformOrigin(
                                ((from.center.x - to.left) / to.width).coerceIn(0f, 1f),
                                ((from.center.y - to.top) / to.height).coerceIn(0f, 1f),
                            )
                        val start = (from.width / to.width).coerceIn(0.2f, 1f)
                        scaleX = start + (1f - start) * f
                        scaleY = scaleX
                    } else {
                        scaleX = 0.9f + 0.1f * f
                        scaleY = scaleX
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            contentAlignment = Alignment.TopCenter,
        ) {
          val panelBlurAllowed = LocalGlassBlurAllowed.current
          val settled = remember { derivedStateOf { progress() >= 0.999f } }
          val sheetBlurAllowed =
              remember(panelBlurAllowed) { { panelBlurAllowed() && settled.value } }
          CompositionLocalProvider(LocalGlassBlurAllowed provides sheetBlurAllowed) {
            if (showingMedia) {
                if (mediaShown) {
                    PenguinMediaCard(
                        viewModelFactory = viewModel.mediaViewModelFactory,
                        behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                        expanded = true,
                    )
                }
            } else {
                ConnectivityFolder(
                    tiles = viewModel.tileGridViewModel.tileViewModels,
                    expanded = true,
                    showHeader = false,
                    carded = false,
                )
            }
          }
        }
    }
}

@Composable
internal fun PenguinGridTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val darkContext =
        remember(context, configuration) {
            val night =
                Configuration(configuration).apply {
                    uiMode =
                        (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            Configuration.UI_MODE_NIGHT_YES
                }
            ContextThemeWrapper(context.createConfigurationContext(night), R.style.Theme_SystemUI)
        }
    CompositionLocalProvider(LocalContext provides darkContext) {
        PlatformTheme(isDarkTheme = true, content = content)
    }
}

internal fun closeExpansions() {
    ConnectivityFolderExpansion.expanded = false
    PenguinMediaExpansion.expanded = false
}

@Composable
internal fun ContentScope.GridItemContent(
    viewModel: QuickSettingsContainerViewModel,
    item: PenguinGrid.Item,
    mediaShown: Boolean,
    interactable: Boolean,
    listening: () -> Boolean,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    height: Dp = penguinModuleHeight(item.h),
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val open = { PenguinGridOrigin.bounds = bounds }
    val oneUi = LocalPenguinGridFlavor.current == PenguinGrid.Flavor.OneUi
    val loose = LocalPenguinGridLoose.current
    Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        when (item.spec) {
            PenguinGrid.ONEUI_TOGGLES_SPEC -> {
                val tiles = viewModel.tileGridViewModel.tileViewModels
                val shown =
                    remember(tiles, loose) {
                        val bySpec = tiles.associateBy { it.spec }
                        loose.mapNotNull { bySpec[it] }
                    }
                OneUiTogglesCard(
                    tiles = shown,
                    modifier = Modifier.height(height),
                    rows = item.h,
                    toggleSize = OneUiGridToggleSize,
                    interactive = !preview && interactable,
                    onEdit = if (preview) ({ OneUiToggleEditing.active = true }) else null,
                )
            }
            PenguinGrid.ONEUI_SOUND_SPEC ->
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OneUiSoundModeButton(height)
                }
            FOLDER_SPEC ->
                if (item.h >= 4) {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        interactive = !preview,
                        expanded = true,
                        showHeader = false,
                        modifier = Modifier.height(height).clipToBounds(),
                    )
                } else {
                    ConnectivityFolder(
                        tiles = viewModel.tileGridViewModel.tileViewModels,
                        interactive = !preview,
                        compactHeight = height,
                        onExpandedChange = {
                            open()
                            ConnectivityFolderExpansion.expanded = it
                        },
                    )
                }
            MEDIA_SPEC ->
                if (mediaShown) {
                    PenguinMediaCard(
                        viewModelFactory = viewModel.mediaViewModelFactory,
                        behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                        interactive = !preview,
                        square = item.w < 4,
                        large = item.h >= 4,
                        tall = item.h >= 6,
                        height = height,
                        controlCentre = true,
                        onExpand = {
                            open()
                            PenguinMediaExpansion.expanded = true
                        },
                    )
                } else {
                    NotPlaying(height)
                }
            PenguinGrid.BRIGHTNESS_SPEC ->
                OneUiSliderWithButton(
                    item = item,
                    height = height,
                    enabled = oneUi,
                    button = { OneUiExtraDimButton(it) },
                ) { sliderHeight ->
                    BrightnessLayout(
                        enable = interactable && !preview,
                        horizontal = item.w > item.h,
                        verticalWidth = null,
                        sliderHeight = sliderHeight,
                        capsule = true,
                    )
                }
            PenguinGrid.VOLUME_SPEC ->
                OneUiSliderWithButton(
                    item = item,
                    height = height,
                    enabled = oneUi,
                    button = { OneUiVolumePanelButton(it) },
                ) { sliderHeight ->
                    VolumeLayout(
                        enable = interactable && !preview,
                        horizontal = item.w > item.h,
                        verticalWidth = null,
                        sliderHeight = sliderHeight,
                        capsule = true,
                    )
                }
            else ->
                if (oneUi) {
                    viewModel.tileGridViewModel.tileViewModels
                        .firstOrNull { it.spec == item.spec }
                        ?.let { tile ->
                            OneUiTileControl(
                                tile = tile,
                                wide = item.w > 1,
                                modifier = Modifier.height(height),
                                interactive = !preview,
                            )
                        }
                } else {
                    TileGrid(
                        viewModel = viewModel.tileGridViewModel,
                        includeSpecs = listOf(item.spec),
                        columnsOverride = item.w,
                        listening = listening,
                    )
                }
        }
    }
}

private val OneUiGridToggleSize = 52.dp

@Composable
private fun OneUiSliderWithButton(
    item: PenguinGrid.Item,
    height: Dp,
    enabled: Boolean,
    button: @Composable (Dp) -> Unit,
    slider: @Composable (Dp) -> Unit,
) {
    val row = penguinModuleHeight(1)
    when {
        enabled && item.w == 1 && item.h >= 3 ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                slider((height - row - PenguinGridGap).coerceAtLeast(row))
                Spacer(Modifier.height(PenguinGridGap))
                button(row)
            }
        else -> slider(height)
    }
}

object OneUiToggleEditing {
    var active by mutableStateOf(false)
}

@Composable
private fun NotPlaying(height: Dp) {
    PanelElementPlaceholder(
        height = height,
        iconRes = R.drawable.ic_music_note,
        label = stringResource(R.string.penguin_media_not_playing),
    )
}

@Composable
internal fun ControlGrid(
    items: List<PenguinGrid.Item>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
    content: @Composable (PenguinGrid.Item) -> Unit,
) {
    val rowHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
    val gap = PenguinGridGap
    Layout(
        modifier = modifier,
        content = { items.forEach { item -> key(item.spec) { Box { content(item) } } } },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val rowPx = rowHeight.roundToPx()
        val columnPx =
            (constraints.maxWidth - gapPx * (PenguinGrid.COLUMNS - 1)) / PenguinGrid.COLUMNS
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val item = items[index]
                measurable.measure(
                    Constraints.fixed(
                        width = columnPx * item.w + gapPx * (item.w - 1),
                        height = rowPx * item.h + gapPx * (item.h - 1),
                    )
                )
            }
        val rows = PenguinGrid.rows(items)
        val height = (rowPx * rows + gapPx * (rows - 1)).coerceAtLeast(0)
        layout(constraints.maxWidth, max(height, minHeight.roundToPx())) {
            placeables.forEachIndexed { index, placeable ->
                val item = items[index]
                placeable.place(item.x * (columnPx + gapPx), item.y * (rowPx + gapPx))
            }
        }
    }
}

internal fun pageIcon(page: List<PenguinGrid.Item>): ImageVector {
    val only = page.singleOrNull()
    return when (only?.spec) {
        MEDIA_SPEC -> Icons.Rounded.MusicNote
        FOLDER_SPEC -> Icons.Rounded.SettingsInputAntenna
        else -> Icons.Rounded.Favorite
    }
}

@Composable
internal fun PageIcons(
    pages: List<List<PenguinGrid.Item>>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = PageIconSpacing
    Column(
        modifier = modifier.offset(y = -((PageIconSize + spacing) * pages.size - spacing) / 2),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        pages.forEachIndexed { index, page ->
            Icon(
                imageVector = pageIcon(page),
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (index == current) 0.9f else 0.3f
                    ),
                modifier = Modifier.size(PageIconSize),
            )
        }
    }
}

@Composable
internal fun secureStringSetting(key: String): String? {
    val resolver = LocalContext.current.contentResolver
    var value by remember(key) { mutableStateOf(Settings.Secure.getString(resolver, key)) }
    DisposableEffect(resolver, key) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value = Settings.Secure.getString(resolver, key)
                }
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return value
}
