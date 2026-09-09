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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.compose.modifiers.thenIf
import com.android.systemui.common.shared.model.Icon as CommonIcon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaNavigationViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel

private val ArtworkCorner = 28.dp
private val LogoSize = 44.dp
private val ArtSize = 52.dp
private val WideHeight = 116.dp
private val TrackHeight = 4.dp
private val RingStroke = 3.dp
private val ControlSize = 40.dp

private val LocalMediaCardInteractive = compositionLocalOf { true }

@Composable
fun PenguinMediaCard(
    viewModelFactory: MediaViewModel.Factory,
    behavior: MediaUiBehavior,
    modifier: Modifier = Modifier,
    square: Boolean = true,
    interactive: Boolean = true,
) {
    CompositionLocalProvider(LocalMediaCardInteractive provides interactive) {
        PenguinMediaCardContent(viewModelFactory, behavior, modifier, square)
    }
}

@Composable
private fun PenguinMediaCardContent(
    viewModelFactory: MediaViewModel.Factory,
    behavior: MediaUiBehavior,
    modifier: Modifier,
    square: Boolean,
) {
    val context = LocalContext.current
    val viewModel =
        rememberViewModel("PenguinMediaCard") {
            viewModelFactory.create(
                context = context,
                carouselVisibility = behavior.carouselVisibility,
            )
        }
    val card = viewModel.cards.firstOrNull() ?: return
    val navigation = card.navigation

    if (!square) {
        WideMediaCard(card, navigation, modifier)
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .thenIf(square) { Modifier.aspectRatio(1f) }
                .clip(RoundedCornerShape(ArtworkCorner))
                .background(glassSurface())
                .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Artwork(card.background, Modifier.size(ArtSize).clip(RoundedCornerShape(10.dp)))
            (card.outputSwitcherChipButton as? MediaSecondaryActionViewModel.Action)?.let {
                MediaControl(it, ControlSize)
            }
        }
        Column {
            Text(
                text = card.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaControl(navigation.left, ControlSize)
            card.playPauseAction?.let { play ->
                play.icon?.let { icon ->
                    MediaControl(
                        MediaSecondaryActionViewModel.Action(icon, play.onClick),
                        ControlSize + 6.dp,
                    )
                }
            }
            MediaControl(navigation.right, ControlSize)
        }
    }
}

@Composable
private fun WideMediaCard(
    card: MediaCardViewModel,
    navigation: MediaNavigationViewModel,
    modifier: Modifier,
) {
    val progress =
        (navigation as? MediaNavigationViewModel.Showing)?.progress?.coerceIn(0f, 1f) ?: 0f
    Box(modifier = modifier.fillMaxWidth().height(WideHeight).clip(RoundedCornerShape(ArtworkCorner))) {
        Artwork(card.background, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.35f))
                    )
                )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = card.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).height(TrackHeight).clip(CircleShape)) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
                    Box(
                        Modifier.fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                card.playPauseAction?.let { play ->
                    play.icon?.let {
                        MediaControl(
                            MediaSecondaryActionViewModel.Action(it, play.onClick),
                            ControlSize,
                            Color.White,
                        )
                    }
                }
                MediaControl(navigation.right, ControlSize, Color.White)
            }
        }
    }
}

@Composable
private fun Artwork(artwork: CommonIcon?, modifier: Modifier) {
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

@Composable
private fun AppLogoWithProgress(icon: CommonIcon, progress: Float) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(LogoSize + RingStroke * 4)) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        Box(
            modifier =
                Modifier.size(LogoSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, tint = Color.White, modifier = Modifier.size(LogoSize / 2))
        }
    }
}

@Composable
private fun MediaControl(
    action: MediaSecondaryActionViewModel,
    size: Dp = ControlSize,
    tint: Color? = null,
) {
    val model = action as? MediaSecondaryActionViewModel.Action ?: return
    val interactive = LocalMediaCardInteractive.current
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .then(
                    model.onClick
                        ?.takeIf { interactive }
                        ?.let { Modifier.clickable(onClick = it) } ?: Modifier
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = model.icon,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
