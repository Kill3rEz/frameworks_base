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

package com.android.systemui.keyguard.ui.composable.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon as CommonIcon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaNavigationViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.res.R

private val CardCorner = 28.dp
private val CardPadding = 18.dp
private val ThumbnailSize = 64.dp
private val ThumbnailCorner = 10.dp
private val ControlSize = 44.dp
private val TrackHeight = 6.dp
private val CompactGlass = Color(0xFF3A3A3C).copy(alpha = 0.45f)
private val ExpandedGlass = Color.White.copy(alpha = 0.14f)
private val CardBorder = Color.White.copy(alpha = 0.18f)
private val Secondary = Color.White.copy(alpha = 0.6f)

/**
 * The lock screen player. Compact, the artwork is a thumbnail beside the track. Tapping it opens
 * the expanded player, where the artwork becomes the lock screen and the card keeps only the track
 * and the controls; tapping the artwork again closes it.
 */
@Composable
fun LockscreenMediaCard(
    viewModelFactory: MediaViewModel.Factory,
    behavior: MediaUiBehavior,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel =
        rememberViewModel("LockscreenMediaCard") {
            viewModelFactory.create(
                context = context,
                carouselVisibility = behavior.carouselVisibility,
            )
        }
    val cards = viewModel.cards
    if (cards.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { cards.size })
    LaunchedEffect(pagerState, cards.size) {
        snapshotFlow { pagerState.settledPage }
            .collect { if (it < cards.size) viewModel.onCardSelected(it) }
    }
    val selected = cards.getOrNull(pagerState.currentPage) ?: cards.first()
    LaunchedEffect(selected.background) { LockscreenMediaExpansion.artwork = selected.background }
    HorizontalPager(
        state = pagerState,
        pageSpacing = 12.dp,
        // A swipe between sessions would fight the expansion, which follows one artwork.
        userScrollEnabled = !LockscreenMediaExpansion.expanded,
        modifier = modifier,
    ) { page ->
        MediaCard(cards[page], isSelected = page == pagerState.currentPage)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCard(card: MediaCardViewModel, isSelected: Boolean) {
    val density = LocalDensity.current
    val fraction = { LockscreenMediaExpansion.fraction }
    val f = fraction()
    Expandable(
        controller = rememberExpandableController(color = Color.Transparent, shape = RoundedCornerShape(CardCorner)),
        useModifierBasedImplementation = true,
        defaultMinSize = false,
    ) { expandable ->
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                .border(1.dp, CardBorder, RoundedCornerShape(CardCorner))
        ) {
        // Frosted glass, as on iOS: compact it blurs the wallpaper; expanded the blurred cover is
        // already behind it, so a light glass is enough. The compositor's blur trails the card by
        // a frame as it moves, so it goes as soon as the card does, rather than fading with it.
        WallpaperBlur(
            alpha = {
                if (LockscreenMediaExpansion.fraction > 0f) 0f
                else LockscreenMediaExpansion.lockscreenAlpha
            },
            corner = CardCorner,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(lerpColor(CompactGlass, ExpandedGlass, f)))
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (!isSelected) return@onGloballyPositioned
                        val pad = with(density) { CardPadding.toPx() }
                        val size = with(density) { ThumbnailSize.toPx() }
                        val origin = coordinates.positionInWindow() + Offset(pad, pad)
                        LockscreenMediaExpansion.thumbnailBounds = Rect(origin, Size(size, size))
                    }
                    .combinedClickable(
                        onClick = { card.onClick(expandable) },
                        onLongClick = card.onLongClick,
                    )
                    .semantics { contentDescription = card.contentDescription }
                    .padding(CardPadding),
        ) {
            Header(card, f)
            Spacer(Modifier.height(lerp(16f, 12f, f).dp))
            SeekBar(card.navigation)
            Spacer(Modifier.height(10.dp))
            Controls(card)
        }
        }
    }
}

@Composable
private fun Header(card: MediaCardViewModel, f: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val thumbnail = lerp(ThumbnailSize.value, 0f, f).dp
        Box(
            modifier =
                Modifier.size(thumbnail)
                    // The background draws the moving artwork from here, so this copy steps aside
                    // as soon as the expansion starts.
                    .graphicsLayer { alpha = if (LockscreenMediaExpansion.fraction > 0f) 0f else 1f }
                    .clip(RoundedCornerShape(ThumbnailCorner))
                    .clickable { LockscreenMediaExpansion.toggle() }
                    .semantics { contentDescription = "Expand media" }
        ) {
            Artwork(card.background, Modifier.fillMaxWidth().fillMaxHeight())
        }
        Spacer(Modifier.width(lerp(14f, 0f, f).dp))
        Column(
            modifier =
                Modifier.weight(1f)
                    .then(
                        if (LockscreenMediaExpansion.expanded) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures { LockscreenMediaExpansion.collapse() }
                            }
                        } else {
                            Modifier
                        }
                    ),
            horizontalAlignment = BiasAlignment.Horizontal(lerp(-1f, 0f, f)),
        ) {
            Text(
                text = card.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.subtitle,
                color = Secondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            icon = card.icon,
            tint = Secondary,
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
        )
    }
}

@Composable
private fun SeekBar(navigation: MediaNavigationViewModel) {
    val showing = navigation as? MediaNavigationViewModel.Showing ?: return
    val current by rememberUpdatedState(showing)
    val progress = showing.progress.coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(showing.progressText)
        Box(
            modifier =
                Modifier.weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(24.dp)
                    .semantics { contentDescription = showing.contentDescription }
                    .then(
                        if (showing.onScrubChange != null) {
                            Modifier.pointerInput(Unit) {
                                var total = Offset.Zero
                                detectHorizontalDragGestures(
                                    onDragStart = { total = Offset.Zero },
                                    onDragEnd = { current.onScrubFinished?.invoke(total, true) },
                                    onDragCancel = {
                                        current.onScrubFinished?.invoke(Offset.Zero, false)
                                    },
                                ) { change, dragAmount ->
                                    total += Offset(dragAmount, 0f)
                                    current.onScrubChange?.invoke(
                                        (change.position.x / size.width).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val grow = if (showing.isScrubbing) 1.6f else 1f
            Box(
                Modifier.fillMaxWidth()
                    .height(TrackHeight * grow)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Box(
                Modifier.fillMaxWidth(progress)
                    .height(TrackHeight * grow)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.72f))
            )
        }
        TimeText(showing.durationText)
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, color = Secondary, fontSize = 13.sp, maxLines = 1)
}

@Composable
private fun Controls(card: MediaCardViewModel) {
    val extras = card.additionalActions.filterIsInstance<MediaSecondaryActionViewModel.Action>()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlotOrSpace(extras.getOrNull(0), ControlSize - 8.dp, Secondary)
        SlotOrSpace(
            card.navigation.left as? MediaSecondaryActionViewModel.Action,
            ControlSize,
            Color.White,
            R.drawable.ic_penguin_media_previous,
        )
        val play =
            card.playPauseAction?.let { action ->
                action.icon?.let { MediaSecondaryActionViewModel.Action(it, action.onClick) to action.state }
            }
        SlotOrSpace(
            play?.first,
            ControlSize + 12.dp,
            Color.White,
            play?.second?.let(::playPauseIcon),
        )
        SlotOrSpace(
            card.navigation.right as? MediaSecondaryActionViewModel.Action,
            ControlSize,
            Color.White,
            R.drawable.ic_penguin_media_next,
        )
        SlotOrSpace(card.outputSwitcherChipButton, ControlSize - 8.dp, Secondary)
    }
}

/** A control, or the room it would take so the others keep their places. */
@Composable
private fun SlotOrSpace(
    action: MediaSecondaryActionViewModel.Action?,
    size: Dp,
    tint: Color,
    iconRes: Int? = null,
) {
    if (action == null) {
        Spacer(Modifier.size(size))
        return
    }
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .then(action.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes == null) {
            Icon(icon = action.icon, tint = tint, modifier = Modifier.size(size * 0.6f))
        } else {
            M3Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.66f),
            )
        }
    }
}

@Composable
internal fun Artwork(artwork: CommonIcon?, modifier: Modifier) {
    when (artwork) {
        is CommonIcon.Loaded ->
            Image(
                painter = rememberDrawablePainter(artwork.drawable),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        is CommonIcon.Resource ->
            Image(
                painter = painterResource(artwork.resId),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        null -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private fun playPauseIcon(state: MediaSessionState) =
    if (state == MediaSessionState.Playing) {
        R.drawable.ic_penguin_media_pause
    } else {
        R.drawable.ic_penguin_media_play
    }
