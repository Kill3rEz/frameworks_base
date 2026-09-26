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

package com.android.systemui.statusbar.backtoapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter

@Composable
fun BackToAppChip(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tracker = remember(context) { BackToAppTracker.get(context) }
    val target by tracker.target.collectAsState()
    val shown = remember { LastShown() }
    target?.let { shown.value = it }

    AnimatedVisibility(
        visible = target != null,
        enter =
            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.85f),
        exit =
            fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) +
                scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        val app = shown.value ?: return@AnimatedVisibility
        Row(
            modifier =
                Modifier.padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.14f))
                    .clickable { tracker.goBack(app) }
                    .semantics { contentDescription = "Back to ${app.label}" }
                    .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            app.icon?.let {
                Image(
                    rememberDrawablePainter(it),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).clip(CircleShape),
                )
            }
            Text(
                text = app.label.toString(),
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).widthIn(max = 96.dp),
            )
        }
    }
}

private class LastShown {
    var value: BackToAppTracker.Target? = null
}
