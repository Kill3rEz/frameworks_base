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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon as CommonIcon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaNavigationViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel

private val ArtworkCorner = 28.dp
private val LogoSize = 44.dp
private val RingStroke = 3.dp
private val ControlSize = 40.dp

@Composable
fun PenguinMediaCard(
    viewModelFactory: MediaViewModel.Factory,
    behavior: MediaUiBehavior,
    modifier: Modifier = Modifier,
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
    val progress =
        (navigation as? MediaNavigationViewModel.Showing)?.progress?.coerceIn(0f, 1f) ?: 0f

    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ArtworkCorner)),
        contentAlignment = Alignment.Center,
    ) {
        when (val artwork = card.background) {
            is CommonIcon.Loaded ->
                Image(
                    painter = rememberDrawablePainter(artwork.drawable),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            is CommonIcon.Resource ->
                Image(
                    painter = painterResource(artwork.resId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))
                        )
                    )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaControl(navigation.left)
            AppLogoWithProgress(card.icon, progress)
            MediaControl(navigation.right)
        }
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
private fun MediaControl(action: MediaSecondaryActionViewModel) {
    val model = action as? MediaSecondaryActionViewModel.Action ?: return
    Box(
        modifier =
            Modifier.size(ControlSize)
                .clip(CircleShape)
                .then(
                    model.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = model.icon, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
