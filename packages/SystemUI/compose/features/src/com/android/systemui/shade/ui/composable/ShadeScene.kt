/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.shade.ui.composable

import com.android.systemui.qs.composefragment.BrightnessLayout
import com.android.systemui.qs.ui.composable.qsHostTransition
import com.android.systemui.qs.ui.composable.LocalQsHostTransition
import com.android.systemui.qs.ui.composable.HarmonyHeader
import com.android.systemui.qs.ui.composable.penguinPanel
import com.android.systemui.qs.panels.ui.compose.MEDIA_SPEC
import com.android.systemui.qs.panels.ui.compose.FOLDER_SPEC
import com.android.systemui.qs.panels.ui.compose.PanelBands
import com.android.systemui.qs.panels.ui.compose.PanelBand
import com.android.systemui.qs.composefragment.ConnectivityFolder
import com.android.systemui.qs.composefragment.MyUiGridGap
import com.android.systemui.qs.composefragment.MyUiMediaCard
import com.android.systemui.qs.composefragment.PenguinMediaCard
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_STYLE
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_POSITION
import com.android.systemui.qs.composefragment.connectivityFolderEnabled
import com.android.systemui.qs.composefragment.connectivityFolderSpecs
import com.android.systemui.qs.composefragment.POSITION_HEADER
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_POSITION
import com.android.systemui.qs.composefragment.POSITION_ABOVE_GRID
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_POSITION
import com.android.systemui.qs.composefragment.DEFAULT_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.secureIntSetting
import com.android.systemui.qs.composefragment.VolumeLayout
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.panels.ui.compose.PANEL_FILLER_COLUMNS
import com.android.systemui.qs.panels.ui.compose.panelFillerTiles
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.animateContentFloatAsState
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.animateContentSizeNoClip
import com.android.compose.modifiers.height
import com.android.compose.modifiers.padding
import com.android.compose.modifiers.thenIf
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesParentViewModel
import com.android.systemui.notifications.ui.composable.NestedScrollingNotificationPanel
import com.android.systemui.notifications.ui.composable.ScrollingNotificationPanel
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.footer.ui.compose.FooterActionsWithAnimatedVisibility
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.QuickQuickSettings
import com.android.systemui.qs.ui.composable.MyUiHeaderRow
import com.android.systemui.qs.ui.composable.MyUiSliderCorner
import com.android.systemui.qs.shared.style.LocalQsPanelStyle
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.SplitShadeQuickSettings
import com.android.systemui.qs.ui.composable.QuickSettingsContent
import com.android.systemui.qs.ui.composable.PanelElement
import com.android.systemui.qs.ui.composable.PanelElementRows
import com.android.systemui.qs.ui.composable.panelFillerSlots
import com.android.systemui.qs.ui.composable.panelRowsUsed
import com.android.systemui.qs.ui.composable.panelElementPreviewHeights
import com.android.systemui.qs.ui.composable.panelElementPreviews
import com.android.systemui.qs.ui.composable.qsHeaderPreview
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.res.R
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.Edit
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.QS
import com.android.systemui.shade.ui.composable.ShadeScene.Companion.SplitShadeInternalScenes.transitions
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.shade.ui.viewmodel.ShadeSceneContentViewModel
import com.android.systemui.shade.ui.viewmodel.ShadeUserActionsViewModel
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

object Shade {
    object Elements {
        val ShadeHeader = ElementKey("ShadeHeader")
        val BackgroundScrim =
            ElementKey("ShadeBackgroundScrim", contentPicker = LowestZIndexContentPicker)
    }

    object Dimensions {
        val HorizontalPadding = 16.dp
    }
}

/** The shade scene shows scrolling list of notifications and some of the quick setting tiles. */
@SysUISingleton
class ShadeScene
@Inject
constructor(
    private val shadeSession: SaveableSession,
    private val notificationStackScrollView: Lazy<NotificationScrollView>,
    private val actionsViewModelFactory: ShadeUserActionsViewModel.Factory,
    private val contentViewModelFactory: ShadeSceneContentViewModel.Factory,
    private val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
    private val notificationRulesParentViewModelFactory: NotificationRulesParentViewModel.Factory,
    private val jankMonitor: InteractionJankMonitor,
) : ExclusiveActivatable(), Scene {

    override val key = Scenes.Shade

    private val actionsViewModel: ShadeUserActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override suspend fun onActivated() {
        actionsViewModel.activate()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override val alwaysCompose: Boolean = true

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val viewModel =
            rememberViewModel("ShadeScene-viewModel") { contentViewModelFactory.create() }
        val headerViewModel =
            rememberViewModel("ShadeScene-headerViewModel") {
                viewModel.shadeHeaderViewModelFactory.create()
            }
        val notificationsPlaceholderViewModel =
            rememberViewModel("ShadeScene-notifPlaceholderViewModel") {
                notificationsPlaceholderViewModelFactory.create(Scenes.Shade)
            }
        val notificationRulesParentViewModel =
            if (NmContextualDisplayLaunch.isEnabled) {
                rememberViewModel("ShadeScene-notifRulesParentViewModel") {
                    notificationRulesParentViewModelFactory.create()
                }
            } else {
                null
            }

        val targetBlur by
            remember(layoutState) {
                derivedStateOf { viewModel.calculateBlur(layoutState.transitionState) }
            }
        val animatedBlurRadiusPx: Float by
            animateFloatAsState(targetValue = targetBlur, label = "Shade-blurRadius")
        ShadeScene(
            notificationStackScrollView.get(),
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            notificationRulesParentViewModel = notificationRulesParentViewModel,
            jankMonitor = jankMonitor,
            modifier = modifier.blur(with(LocalDensity.current) { animatedBlurRadiusPx.toDp() }),
            shadeSession = shadeSession,
        )
    }

    companion object {
        object SplitShadeInternalScenes {
            val QS = SceneKey("QuickSettingsMainPanel")
            val Edit = SceneKey("QuickSettingsEditPanel")

            private const val EDIT_MODE_TIME_MILLIS = 500

            val transitions = transitions {
                from(QS, Edit) {
                    spec = tween(durationMillis = EDIT_MODE_TIME_MILLIS)
                    fractionRange(start = 0.5f) { fade(Edit.rootElementKey) }
                    fractionRange(end = 0.5f) { fade(QS.rootElementKey) }
                }
            }
        }
    }
}

@Composable
private fun ContentScope.ShadeScene(
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    notificationRulesParentViewModel: NotificationRulesParentViewModel?,
    jankMonitor: InteractionJankMonitor,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
) {
    val onEmptySpaceClick by
        remember(viewModel) {
            derivedStateOf {
                if (viewModel.isEmptySpaceClickable(layoutState.transitionState)) {
                    { viewModel.onEmptySpaceClicked(layoutState.transitionState) }
                } else {
                    null
                }
            }
        }

    if (viewModel.shadeMode is ShadeMode.Split) {
        SplitShade(
            tag = "ShadeScene",
            notificationStackScrollView = notificationStackScrollView,
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            notificationRulesParentViewModel = notificationRulesParentViewModel,
            modifier = modifier,
            shadeSession = shadeSession,
            jankMonitor = jankMonitor,
            onEmptySpaceClick = onEmptySpaceClick,
        )
    } else {
        // Compose SingleShade even if we're in Dual shade mode; the view-model will take care of
        // switching scenes.
        SingleShade(
            tag = "ShadeScene",
            notificationStackScrollView = notificationStackScrollView,
            viewModel = viewModel,
            headerViewModel = headerViewModel,
            notificationsPlaceholderViewModel = notificationsPlaceholderViewModel,
            notificationRulesParentViewModel = notificationRulesParentViewModel,
            modifier = modifier,
            shadeSession = shadeSession,
            jankMonitor = jankMonitor,
            onEmptySpaceClick = onEmptySpaceClick,
        )
    }
}

@Composable
private fun ContentScope.SingleShade(
    tag: String,
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    notificationRulesParentViewModel: NotificationRulesParentViewModel?,
    jankMonitor: InteractionJankMonitor,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
    onEmptySpaceClick: (() -> Unit)?,
) {

    val cutoutLocation = LocalDisplayCutout.current().location

    val cutoutInsets = WindowInsets.Companion.displayCutout

    val tileSquishiness by
        animateContentFloatAsState(
            value = 1f,
            key = QuickSettings.SharedValues.TilesSquishiness,
            canOverflow = false,
        )
    LaunchedEffectWithLifecycle(Unit) {
        snapshotFlow { tileSquishiness }.collect { viewModel.setTileSquishiness(it) }
    }

    LaunchedEffectWithLifecycle(Unit) { viewModel.detectShadeModeChanges() }

    val onlyPunchHolesInThisScene =
        layoutState.isTransitioningBetween(Scenes.Gone, Scenes.Shade) ||
            layoutState.isTransitioningBetween(Scenes.Lockscreen, Scenes.Shade)
    val mediaInRow = viewModel.showMediaInRow
    val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings_single)

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val navBarHeight = { systemBarsPadding.calculateBottomPadding() }

    val shadeHorizontalPadding =
        dimensionResource(id = R.dimen.notification_panel_margin_horizontal)

    Box(
        modifier =
            modifier.thenIf(onlyPunchHolesInThisScene) {
                // Render the scene to an offscreen buffer so that BlendMode.DstOut only clears this
                // scene (and not the one under it). It saves the LS content (e.g. the clock) from
                // being cut out during the LS -> Shade transition.
                Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            }
    ) {
        val scrollState =
            shadeSession.rememberSaveableSession(
                saver = ScrollState.Saver,
                key = "NestedScrollState",
            ) {
                ScrollState(initial = 0)
            }
        val scrollingContentOverscrollEffect = rememberOffsetOverscrollEffect()
        val shortContentOverscrollEffect = rememberOffsetOverscrollEffect()

        // This lambda is automatically remembered by the compiler, staying stable and preventing
        // unnecessary recompositions while still reacting to overscroll changes.
        val visualOffsetProvider: Density.() -> Int = {
            val totalOverscroll =
                scrollingContentOverscrollEffect.overscrollDistance +
                    shortContentOverscrollEffect.overscrollDistance +
                    (((verticalOverscrollEffect as? OffsetOverscrollEffect)?.overscrollDistance)
                        ?: 0f)

            OffsetOverscrollEffect.computeOffset(density = this, totalOverscroll)
        }

        ShadePanelScrim(viewModel.isTransparencyEnabled)
        SingleShadeNestedScrollLayout(
            modifier =
                Modifier.thenIf(onEmptySpaceClick != null) {
                    Modifier.clickable(interactionSource = null, indication = null) {
                        onEmptySpaceClick?.invoke()
                    }
                },
            shadeSession = shadeSession,
            viewModel = notificationsPlaceholderViewModel,
            contentScrollState = scrollState,
            scrollingContentOverscrollEffect = scrollingContentOverscrollEffect,
            shortContentOverscrollEffect = shortContentOverscrollEffect,
            jankMonitor = jankMonitor,
            statusBarHeader = {
                CollapsedShadeHeader(
                    viewModel = headerViewModel,
                    isSplitShade = false,
                    modifier = Modifier.element(Shade.Elements.ShadeHeader),
                )
            },
            mediaAndQqsHeader = {
                val isDefaultStyle = viewModel.panelStyle == QsPanelStyle.Default
                val isHarmonyStyle = viewModel.panelStyle == QsPanelStyle.Harmony
                val isMyUiStyle = viewModel.panelStyle == QsPanelStyle.MyUi || isHarmonyStyle
                val qqsShowsMedia =
                    !isDefaultStyle &&
                        viewModel.isQsEnabled &&
                        viewModel.hasMediaCards &&
                        isAlwaysComposedContentVisible()
                val qqsLayoutPaddingBottom = 16.dp
                val qsHorizontalMargin =
                    shadeHorizontalPadding + dimensionResource(id = R.dimen.qs_horizontal_margin)
                CompositionLocalProvider(LocalQsPanelStyle provides viewModel.panelStyle) {
                MediaAndQqsLayout(
                    modifier =
                        Modifier.element(QuickSettings.Elements.QuickQuickSettingsAndMedia)
                            .offset {
                                // Centering offset when the shade is being dragged down.
                                val down = visualOffsetProvider().fastCoerceAtLeast(0)
                                IntOffset(x = 0, y = down / 2)
                            }
                            .padding(bottom = qqsLayoutPaddingBottom)
                            .padding(horizontal = qsHorizontalMargin),
                    tiles =
                        @Composable {
                            // Because the ShadeScene is always composed, we need to manually tell
                            // the tiles when they're actually visible and should be listening, just
                            // like in the [QuickSettingsContent] Composable.
                            var listening by remember { mutableStateOf(false) }
                            LifecycleStartEffect(Unit) {
                                listening = true

                                onStopOrDispose { listening = false }
                            }
                            Box {
                                if (viewModel.isQsEnabled) {
                                    if (isDefaultStyle) {
                                        val qqsViewModel =
                                            rememberViewModel(traceName = "shade_scene_qqs") {
                                                viewModel.quickQuickSettingsViewModel.create()
                                            }
                                        QuickQuickSettings(
                                            qqsViewModel,
                                            listening = { listening },
                                            modifier = Modifier.sysuiResTag("quick_qs_panel"),
                                        )
                                    } else {
                                        val folderSpecs =
                                            if (connectivityFolderEnabled()) connectivityFolderSpecs()
                                            else emptyList()
                                        val top2Specs =
                                            remember(
                                                viewModel.qsContainerViewModel.tileGridViewModel
                                                    .tileViewModels,
                                                folderSpecs,
                                            ) {
                                                viewModel.qsContainerViewModel.tileGridViewModel
                                                    .tileViewModels
                                                    .filterNot { it.spec.spec in folderSpecs }
                                                    .take(2)
                                                    .map { it.spec }
                                            }
                                        val folderInHeader =
                                            connectivityFolderEnabled() &&
                                                !qqsShowsMedia &&
                                                secureIntSetting(
                                                    SETTING_QS_FOLDER_POSITION,
                                                    POSITION_HEADER,
                                                ) <= POSITION_ABOVE_GRID &&
                                                secureIntSetting(SETTING_QS_FOLDER_SPAN, 1) < 2
                                        if (folderInHeader) {
                                            val tileHeight =
                                                dimensionResource(
                                                    id = R.dimen.common_tile_default_tile_height
                                                )
                                            val tileSpacing =
                                                dimensionResource(id = R.dimen.qs_tile_margin_vertical)
                                            ConnectivityFolder(
                                                tiles =
                                                    viewModel.qsContainerViewModel
                                                        .tileGridViewModel
                                                        .tileViewModels,
                                                modifier =
                                                    Modifier.element(
                                                            QuickSettings.Elements.ConnectivityFolder
                                                        )
                                                        .sysuiResTag("quick_qs_panel"),
                                                compactHeight = tileHeight * 2 + tileSpacing,
                                            )
                                        } else {
                                        Element(
                                            key = QuickSettings.Elements.HeaderTiles,
                                            modifier = Modifier,
                                        ) {
                                            TileGrid(
                                                viewModel = viewModel.qsContainerViewModel.tileGridViewModel,
                                                includeSpecs = top2Specs,
                                                columnsOverride = 1,
                                                forceLargeTiles = true,
                                                listening = { listening },
                                                modifier = Modifier.sysuiResTag("quick_qs_panel"),
                                            )
                                        }
                                        }
                                    }
                                }
                            }
                        },
                    qqsFolder = {
                        val tileHeight =
                            dimensionResource(id = R.dimen.common_tile_default_tile_height)
                        val tileSpacing = dimensionResource(id = R.dimen.qs_tile_margin_vertical)
                        val folderCompact =
                            if (secureIntSetting(SETTING_QS_FOLDER_SPAN, 1) < 2) {
                                tileHeight * 2 + tileSpacing
                            } else {
                                null
                            }
                        ConnectivityFolder(
                            tiles = viewModel.qsContainerViewModel.tileGridViewModel.tileViewModels,
                            modifier =
                                Modifier.element(QuickSettings.Elements.ConnectivityFolder)
                                    .sysuiResTag("quick_qs_panel"),
                            compactHeight = folderCompact,
                            onExpandedChange = {
                                if (it) viewModel.onConnectivityFolderExpandRequested()
                            },
                        )
                    },
                    qqsShowsFolder =
                        connectivityFolderEnabled() &&
                            secureIntSetting(SETTING_QS_FOLDER_POSITION, POSITION_HEADER) <=
                                POSITION_ABOVE_GRID,
                    fillerTiles = { slot ->
                        var fillerListening by remember { mutableStateOf(false) }
                        LifecycleStartEffect(Unit) {
                            fillerListening = true
                            onStopOrDispose { fillerListening = false }
                        }
                        val fillerFolderSpecs =
                            if (connectivityFolderEnabled()) connectivityFolderSpecs()
                            else emptyList()
                        val grid = viewModel.qsContainerViewModel.tileGridViewModel
                        val fillerSpecs =
                            remember(grid.tileViewModels, grid.largeTiles, fillerFolderSpecs, slot) {
                                qqsFillerSpecs(
                                    grid.tileViewModels.map { it.spec },
                                    grid.largeTiles,
                                    fillerFolderSpecs,
                                )
                                    .getOrElse(slot) { emptyList() }
                            }
                        TileGrid(
                            viewModel = grid,
                            includeSpecs = fillerSpecs,
                            columnsOverride = PANEL_FILLER_COLUMNS,
                            listening = { fillerListening },
                        )
                    },
                    topUpTiles = { rows, fillerSlots ->
                        var topUpListening by remember { mutableStateOf(false) }
                        LifecycleStartEffect(Unit) {
                            topUpListening = true
                            onStopOrDispose { topUpListening = false }
                        }
                        val topUpFolderSpecs =
                            if (connectivityFolderEnabled()) connectivityFolderSpecs()
                            else emptyList()
                        val topUpGrid = viewModel.qsContainerViewModel.tileGridViewModel
                        val topUpSpecs =
                            remember(
                                topUpGrid.tileViewModels,
                                topUpGrid.largeTiles,
                                topUpFolderSpecs,
                                rows,
                                fillerSlots,
                            ) {
                                val all = topUpGrid.tileViewModels.map { it.spec }
                                val skip =
                                    qqsFillerSpecs(all, topUpGrid.largeTiles, topUpFolderSpecs)
                                        .take(fillerSlots)
                                        .sumOf { it.size }
                                tilesForRows(
                                    all.filterNot { it.spec in topUpFolderSpecs }.drop(skip),
                                    topUpGrid.largeTiles,
                                    rows,
                                    QQS_TOP_UP_COLUMNS,
                                )
                            }
                        if (topUpSpecs.isNotEmpty()) {
                            Element(
                                key = QuickSettings.Elements.HeaderTiles,
                                modifier = Modifier,
                            ) {
                                TileGrid(
                                    viewModel =
                                        viewModel.qsContainerViewModel.tileGridViewModel,
                                    includeSpecs = topUpSpecs,
                                    columnsOverride = QQS_TOP_UP_COLUMNS,
                                    listening = { topUpListening },
                                    modifier = Modifier.sysuiResTag("quick_qs_panel"),
                                )
                            }
                        }
                    },
                    secondaryTiles = {
                        if (
                            connectivityFolderEnabled() &&
                                !qqsShowsMedia &&
                                secureIntSetting(SETTING_QS_FOLDER_POSITION, POSITION_HEADER) <=
                                    POSITION_ABOVE_GRID &&
                                secureIntSetting(SETTING_QS_FOLDER_SPAN, 1) < 2
                        ) {
                            var secondaryListening by remember { mutableStateOf(false) }
                            LifecycleStartEffect(Unit) {
                                secondaryListening = true
                                onStopOrDispose { secondaryListening = false }
                            }
                            val secondarySpecs =
                                remember(
                                    viewModel.qsContainerViewModel.tileGridViewModel.tileViewModels
                                ) {
                                    viewModel.qsContainerViewModel.tileGridViewModel.tileViewModels
                                        .take(2)
                                        .map { it.spec }
                                }
                            Element(key = QuickSettings.Elements.HeaderTiles, modifier = Modifier) {
                                TileGrid(
                                    viewModel =
                                        viewModel.qsContainerViewModel.tileGridViewModel,
                                    includeSpecs = secondarySpecs,
                                    columnsOverride = 1,
                                    forceLargeTiles = true,
                                    listening = { secondaryListening },
                                )
                            }
                        }
                    },
                    media = {
                        if (isAlwaysComposedContentVisible()) {
                            if (viewModel.isQsEnabled && (viewModel.showMedia || qqsShowsMedia)) {
                                Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
                                    if (
                                        !isDefaultStyle &&
                                            (secureIntSetting(SETTING_QS_MEDIA_SPAN, 1) < 2 ||
                                                secureIntSetting(SETTING_QS_MEDIA_STYLE, 0) != 0)
                                    ) {
                                        PenguinMediaCard(
                                            viewModelFactory = viewModel.mediaViewModelFactory,
                                            behavior =
                                                ShadeSceneContentViewModel.qqsMediaUiBehavior,
                                            square =
                                                secureIntSetting(SETTING_QS_MEDIA_SPAN, 1) < 2,
                                        )
                                    } else {
                                        Media(
                                            viewModelFactory = viewModel.mediaViewModelFactory,
                                            presentationStyle =
                                                if (mediaInRow) {
                                                    MediaPresentationStyle.Compressed
                                                } else {
                                                    MediaPresentationStyle.Default
                                                },
                                            behavior =
                                                ShadeSceneContentViewModel.qqsMediaUiBehavior,
                                            onDismissed = viewModel::onMediaSwipeToDismiss,
                                            location = Media.Location.SHADE,
                                        )
                                    }
                                }
                            }
                        } else {
                            // Add an empty box when QQS content is not visible to keep the same
                            // number of elements.
                            Box(modifier = Modifier)
                        }
                    },
                    mediaInRow = mediaInRow,
                    isDefaultStyle = isDefaultStyle,
                    isMyUiStyle = isMyUiStyle,
                    myUiMedia = {
                        Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
                            MyUiMediaCard(
                                viewModelFactory = viewModel.mediaViewModelFactory,
                                behavior = ShadeSceneContentViewModel.qqsMediaUiBehavior,
                            )
                        }
                    },
                    myUiHeader = {
                        if (isHarmonyStyle) {
                            HarmonyHeader(viewModel.qsContainerViewModel)
                        } else {
                            MyUiHeaderRow(
                                tiles =
                                    viewModel.qsContainerViewModel.tileGridViewModel.tileViewModels,
                                interactable = true,
                            )
                        }
                    },
                    showMedia = qqsShowsMedia && !isHarmonyStyle,
                    penguinQqs = {
                        var penguinListening by remember { mutableStateOf(false) }
                        LifecycleStartEffect(Unit) {
                            penguinListening = true
                            onStopOrDispose { penguinListening = false }
                        }
                        val grid = viewModel.qsContainerViewModel.tileGridViewModel
                        val folderEnabled = connectivityFolderEnabled()
                        val folderSpecs =
                            if (folderEnabled) connectivityFolderSpecs() else emptyList()
                        val mediaShown =
                            viewModel.isQsEnabled &&
                                viewModel.showMedia &&
                                viewModel.hasMediaCards &&
                                isAlwaysComposedContentVisible()
                        val tileSpecs =
                            remember(grid.tileViewModels, folderSpecs) {
                                grid.tileViewModels.map { it.spec }.filterNot {
                                    it.spec in folderSpecs
                                }
                            }
                        val (head, _) =
                            penguinPanel(tileSpecs, grid.largeTiles, folderEnabled, mediaShown)
                        val gap = dimensionResource(R.dimen.qs_tile_margin_horizontal)
                        val standingHeight =
                            dimensionResource(R.dimen.common_tile_default_tile_height) * 2 +
                                dimensionResource(R.dimen.qs_tile_margin_vertical)
                        val qqsMedia: @Composable (Boolean) -> Unit = { square ->
                            Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
                                if (square || secureIntSetting(SETTING_QS_MEDIA_STYLE, 0) != 0) {
                                    PenguinMediaCard(
                                        viewModelFactory = viewModel.mediaViewModelFactory,
                                        behavior = ShadeSceneContentViewModel.qqsMediaUiBehavior,
                                        square = square,
                                    )
                                } else {
                                    Media(
                                        viewModelFactory = viewModel.mediaViewModelFactory,
                                        presentationStyle = MediaPresentationStyle.Default,
                                        behavior = ShadeSceneContentViewModel.qqsMediaUiBehavior,
                                        onDismissed = viewModel::onMediaSwipeToDismiss,
                                        location = Media.Location.SHADE,
                                    )
                                }
                            }
                        }
                        PanelBands(
                            bands = head,
                            gap = gap,
                            tiles = { specs, columns ->
                                if (specs.isNotEmpty()) {
                                    TileGrid(
                                        viewModel = grid,
                                        includeSpecs = specs,
                                        columnsOverride = columns,
                                        listening = { penguinListening },
                                        modifier = Modifier.sysuiResTag("quick_qs_panel"),
                                    )
                                }
                            },
                            element = { spec, half ->
                                when (spec) {
                                    FOLDER_SPEC ->
                                        ConnectivityFolder(
                                            tiles = grid.tileViewModels,
                                            modifier =
                                                Modifier.element(
                                                    QuickSettings.Elements.ConnectivityFolder
                                                ),
                                            compactHeight = if (half) standingHeight else null,
                                            onExpandedChange = {
                                                if (it) viewModel.onConnectivityFolderExpandRequested()
                                            },
                                        )
                                    MEDIA_SPEC -> qqsMedia(half)
                                    else ->
                                        Element(
                                            key = QuickSettings.Elements.BrightnessSlider,
                                            modifier = Modifier,
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = spacedBy(gap),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(Modifier.weight(1f)) {
                                                    BrightnessLayout(
                                                        enable = true,
                                                        horizontal = !half,
                                                        verticalCornerRadius = MyUiSliderCorner,
                                                        verticalWidth = null,
                                                        sliderHeight =
                                                            if (half) standingHeight
                                                            else QqsLyingSliderHeight,
                                                    )
                                                }
                                                Box(Modifier.weight(1f)) {
                                                    VolumeLayout(
                                                        enable = true,
                                                        horizontal = !half,
                                                        verticalCornerRadius = MyUiSliderCorner,
                                                        verticalWidth = null,
                                                        sliderHeight =
                                                            if (half) standingHeight
                                                            else QqsLyingSliderHeight,
                                                    )
                                                }
                                            }
                                        }
                                }
                            },
                        )
                        val headHasMedia =
                            head.any {
                                (it is PanelBand.Full && it.element == MEDIA_SPEC) ||
                                    (it is PanelBand.Half && it.element == MEDIA_SPEC) ||
                                    (it is PanelBand.Pair &&
                                        (it.first == MEDIA_SPEC || it.second == MEDIA_SPEC))
                            }
                        if (mediaShown && !headHasMedia) qqsMedia(false)
                    },
                )
                }
            },
            scrollableScrim = { onContentHeightChanged, isScrimAtRest ->
                NestedScrollingNotificationPanel(
                    tag = "$tag.Single",
                    shadeSession = shadeSession,
                    stackScrollView = notificationStackScrollView,
                    viewModel = notificationsPlaceholderViewModel,
                    notificationRulesParentViewModel = notificationRulesParentViewModel,
                    shouldPunchHoleBehindScrim = true,
                    shouldContentFillMaxSize = true,
                    shouldScrimBackgroundFillMaxHeight = true,
                    isTransparencyEnabled = viewModel.isTransparencyEnabled,
                    stackTopPadding = notificationStackPadding,
                    stackBottomPadding = navBarHeight,
                    contentScrollState = scrollState,
                    scrollingContentOverscrollEffect = scrollingContentOverscrollEffect,
                    shortContentOverscrollEffect = shortContentOverscrollEffect,
                    onEmptySpaceClick = onEmptySpaceClick,
                    modifier = Modifier.padding(horizontal = shadeHorizontalPadding),
                    onStackHeightChanged = onContentHeightChanged,
                    allowSwipeToExpandChildren = isScrimAtRest,
                )
            },
            cutoutInsetsProvider = {
                if (cutoutLocation == CutoutLocation.CENTER) {
                    null
                } else {
                    cutoutInsets
                }
            },
        )
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .height { navBarHeight().roundToPx() }
                    // Intercepts touches, prevents the scrollable container behind from scrolling.
                    .clickable(interactionSource = null, indication = null) { /* do nothing */ }
                    .semantics { hideFromAccessibility() }
        )
    }
}

private const val QqsRows = 2
private const val QQS_TOP_UP_COLUMNS = 4
private const val QQS_MAX_FILLER_SLOTS = 4

private fun qqsFillerSpecs(
    tiles: List<TileSpec>,
    largeTiles: Set<TileSpec>,
    folderSpecs: List<String>,
): List<List<TileSpec>> {
    var pool = tiles.filterNot { it.spec in folderSpecs }
    return List(QQS_MAX_FILLER_SLOTS) {
        panelFillerTiles(pool, largeTiles).also { taken -> pool = pool.drop(taken.size) }
    }
}

private fun tilesForRows(
    pool: List<TileSpec>,
    largeTiles: Set<TileSpec>,
    rows: Int,
    columns: Int,
): List<TileSpec> {
    val taken = mutableListOf<TileSpec>()
    var row = 0
    var used = 0
    for (spec in pool) {
        val width = if (spec in largeTiles) 2 else 1
        if (used + width > columns) {
            row++
            used = 0
        }
        if (row >= rows) break
        taken += spec
        used += width
    }
    return taken
}

private val QqsHeaderHeight = 160.dp
private val QqsLyingSliderHeight = 56.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContentScope.MediaAndQqsLayout(
    tiles: @Composable () -> Unit,
    secondaryTiles: @Composable () -> Unit,
    fillerTiles: @Composable (Int) -> Unit,
    topUpTiles: @Composable (rows: Int, fillerSlots: Int) -> Unit,
    qqsFolder: @Composable () -> Unit,
    qqsShowsFolder: Boolean,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    isDefaultStyle: Boolean,
    isMyUiStyle: Boolean,
    myUiHeader: @Composable () -> Unit,
    myUiMedia: @Composable () -> Unit,
    showMedia: Boolean,
    penguinQqs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modifierAnimated =
        modifier.animateContentSizeNoClip(MaterialTheme.motionScheme.defaultSpatialSpec())
    if (isMyUiStyle) {
        Column(
            modifier = modifierAnimated.fillMaxWidth(),
            verticalArrangement = spacedBy(MyUiGridGap),
        ) {
            myUiHeader()
            if (showMedia) myUiMedia()
        }
        return
    }
    if (isDefaultStyle) {
        if (mediaInRow) {
            Row(
                modifier = modifierAnimated,
                horizontalArrangement = spacedBy(dimensionResource(R.dimen.qs_tile_margin_vertical)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) { tiles() }
                Box(modifier = Modifier.weight(1f)) { media() }
            }
        } else {
            Column(modifier = modifierAnimated, verticalArrangement = spacedBy(16.dp)) {
                tiles()
                media()
            }
        }
        return
    }
    Column(
        modifier = modifierAnimated.fillMaxWidth(),
        verticalArrangement = spacedBy(dimensionResource(R.dimen.qs_tile_margin_vertical)),
    ) {
        penguinQqs()
        GridAnchor()
    }
}

@Composable
private fun ContentScope.SplitShade(
    tag: String,
    notificationStackScrollView: NotificationScrollView,
    viewModel: ShadeSceneContentViewModel,
    headerViewModel: ShadeHeaderViewModel,
    notificationsPlaceholderViewModel: NotificationsPlaceholderViewModel,
    notificationRulesParentViewModel: NotificationRulesParentViewModel?,
    modifier: Modifier = Modifier,
    shadeSession: SaveableSession,
    jankMonitor: InteractionJankMonitor,
    onEmptySpaceClick: (() -> Unit)?,
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    val footerActionsViewModel =
        remember(lifecycleOwner, viewModel) { viewModel.getFooterActionsViewModel(lifecycleOwner) }

    val qsContainerViewModel =
        rememberViewModel(traceName = "SplitShade.QSContainerViewModel") {
            viewModel.qsContainerViewModelFactory.create(supportsBrightnessMirroring = true)
        }

    val notificationStackPadding = dimensionResource(id = R.dimen.notification_side_paddings_split)
    val navBarBottomHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val brightnessMirrorShowing = qsContainerViewModel.brightnessSliderViewModel.showMirror

    val contentAlpha by
        animateFloatAsState(
            targetValue = if (brightnessMirrorShowing) 0f else 1f,
            label = "alphaAnimationBrightnessMirrorContentHiding",
        )

    LaunchedEffectWithLifecycle(key1 = Unit) {
        try {
            snapshotFlow { contentAlpha }
                .collect { notificationsPlaceholderViewModel.setAlphaForBrightnessMirror(it) }
        } finally {
            notificationsPlaceholderViewModel.setAlphaForBrightnessMirror(1f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .thenIf(brightnessMirrorShowing) { Modifier.gesturesDisabled() }
    ) {
        ShadePanelScrim(viewModel.isTransparencyEnabled)

        Column(modifier = Modifier.fillMaxSize()) {
            CollapsedShadeHeader(
                viewModel = headerViewModel,
                isSplitShade = true,
                modifier =
                    // unfoldTranslationXForStartSide may be updated every frame, so only read value
                    // in the layout phase by using lambda.
                    Modifier.element(Shade.Elements.ShadeHeader)
                        .padding(
                            horizontal = { viewModel.unfoldTranslationXForStartSide.roundToInt() }
                        ),
            )

            Row(
                modifier = Modifier.overscroll(verticalOverscrollEffect).fillMaxWidth().weight(1f)
            ) {
                Box(
                    modifier =
                        Modifier.element(SplitShadeQuickSettings)
                            .sysuiResTag("quick_settings_container")
                            .weight(1f)
                            // unfoldTranslationXForStartSide may be updated every frame, so only
                            // read value in the draw phase.
                            .graphicsLayer {
                                translationX = viewModel.unfoldTranslationXForStartSide
                            }
                            .fillMaxSize()
                            .padding(bottom = navBarBottomHeight)
                ) {
                    if (viewModel.isQsEnabled) {
                        val sceneState =
                            rememberMutableSceneTransitionLayoutState(
                                initialScene =
                                    remember { if (qsContainerViewModel.isEditing) Edit else QS },
                                transitions = transitions,
                            )

                        val coroutineScope = rememberCoroutineScope()

                        DisposableEffectWithLifecycle(
                            key1 = qsContainerViewModel,
                            key2 = sceneState,
                        ) {
                            onDispose {
                                qsContainerViewModel.editModeViewModel.stopEditing()
                                sceneState.snapTo(QS)
                            }
                        }

                        LaunchedEffect(sceneState, qsContainerViewModel.isEditing, coroutineScope) {
                            if (qsContainerViewModel.isEditing) {
                                sceneState.setTargetScene(Edit, coroutineScope)
                            } else {
                                sceneState.setTargetScene(QS, coroutineScope)
                            }
                        }

                        CompositionLocalProvider(LocalQsHostTransition provides qsHostTransition()) {
                            NestedSceneTransitionLayout(
                                state = sceneState,
                                debugName = "SplitShade",
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                scene(QS) {
                                    val tileSquishiness by
                                        with(this@SplitShade) {
                                            animateContentFloatAsState(
                                                value = 1f,
                                                key = QuickSettings.SharedValues.TilesSquishiness,
                                                canOverflow = false,
                                            )
                                        }

                                    LaunchedEffectWithLifecycle(Unit) {
                                        snapshotFlow { tileSquishiness }
                                            .collect { viewModel.setTileSquishiness(it) }
                                    }

                                    Element(QS.rootElementKey, Modifier) {
                                        Column {
                                            Box(
                                                Modifier.weight(1f)
                                                    .sysuiResTag("expanded_qs_scroll_view")
                                                    .verticalScroll(rememberScrollState())
                                                    .wrapContentHeight(
                                                        align = Alignment.Top,
                                                        unbounded = true,
                                                    )
                                            ) {
                                                QuickSettingsContent(
                                                    qsContainerViewModel,
                                                    mediaInRow = false,
                                                    mediaSquishiness = { tileSquishiness },
                                                )
                                            }
                                            FooterActionsWithAnimatedVisibility(
                                                viewModel = footerActionsViewModel,
                                                isCustomizing = false,
                                                customizingAnimationDuration = 0,
                                                modifier =
                                                    Modifier.align(Alignment.CenterHorizontally)
                                                        .sysuiResTag("qs_footer_actions"),
                                            )
                                        }
                                    }
                                }

                                scene(Edit) {
                                    Element(Edit.rootElementKey, Modifier) {
                                        GridAnchor()
                                        EditMode(
                                            qsContainerViewModel.editModeViewModel,
                                            Modifier.testTag("edit_mode_scene")
                                                .padding(
                                                    horizontal =
                                                        QuickSettingsShade.Dimensions.HorizontalPadding
                                                ),
                                            previews =
                                                panelElementPreviews(qsContainerViewModel),
                                            previewHeights =
                                                panelElementPreviewHeights(),
                                            headerPreview =
                                                qsHeaderPreview(qsContainerViewModel),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ScrollingNotificationPanel(
                    tag = "$tag.Split",
                    shadeSession = shadeSession,
                    stackScrollView = notificationStackScrollView,
                    viewModel = notificationsPlaceholderViewModel,
                    notificationRulesParentViewModel = notificationRulesParentViewModel,
                    jankMonitor = jankMonitor,
                    stackTopPadding = notificationStackPadding,
                    stackBottomPadding = { notificationStackPadding },
                    shouldFillMaxHeight = true,
                    shouldPunchHoleBehindScrim = false,
                    useVerticalOverscrollEffect = false,
                    isTransparencyEnabled = viewModel.isTransparencyEnabled,
                    onEmptySpaceClick = onEmptySpaceClick,
                    modifier =
                        Modifier.weight(weight = 1f)
                            .fillMaxHeight()
                            .padding(
                                end =
                                    dimensionResource(R.dimen.notification_panel_margin_horizontal),
                                bottom = navBarBottomHeight,
                            ),
                )
            }
        }
    }
}
