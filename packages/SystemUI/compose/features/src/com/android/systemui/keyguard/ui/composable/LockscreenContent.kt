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

package com.android.systemui.keyguard.ui.composable

import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.internal.jank.Cuj
import com.android.internal.jank.Cuj.CujType
import com.android.internal.jank.InteractionJankMonitor
import com.android.compose.modifiers.thenIf
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.composable.modifier.nonAuthUI
import com.android.systemui.keyguard.ui.viewmodel.LockscreenBehindScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenFrontScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.ViewStateAccessor
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.penguin.LockscreenPlayer
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import kotlin.math.min
import kotlinx.coroutines.flow.first
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

/**
 * Renders the content of the lockscreen.
 *
 * This is separate from the [LockscreenScene] because it's meant to support usage of this UI from
 * outside the scene container framework.
 */
class LockscreenContent(
    private val viewModelFactory: LockscreenContentViewModel.Factory,
    private val lockscreenFrontScrimViewModelFactory: LockscreenFrontScrimViewModel.Factory,
    private val lockscreenBehindScrimViewModelFactory: LockscreenBehindScrimViewModel.Factory,
    private val lockscreenElements: LockscreenElements,
    private val clockInteractor: KeyguardClockInteractor,
    private val interactionJankMonitor: InteractionJankMonitor,
    private val player: LockscreenPlayer? = null,
) {
    @Composable
    fun ContentScope.Content(modifier: Modifier = Modifier) {
        val view = LocalView.current
        var lockscreenAlpha by remember { mutableFloatStateOf(0f) }
        val viewModel =
            rememberViewModel("LockscreenContent-viewModel") {
                viewModelFactory.create(
                    keyguardTransitionAnimationCallback =
                        KeyguardTransitionAnimationCallbackImpl(
                            view,
                            interactionJankMonitor,
                            clockInteractor,
                        ),
                    viewState = ViewStateAccessor(alpha = { lockscreenAlpha }),
                )
            }

        LaunchedEffect(viewModel.alpha) { lockscreenAlpha = viewModel.alpha }
        val lockscreenFrontScrimViewModel =
            rememberViewModel("LockscreenContent-frontScrimViewModel") {
                lockscreenFrontScrimViewModelFactory.create()
            }
        val lockscreenBehindScrimViewModel =
            rememberViewModel("LockscreenContent-behindScrimViewModel") {
                lockscreenBehindScrimViewModelFactory.create()
            }

        // Alpha for the animation when transitioning from Shade scene to Lockscreen Scene and
        // ending user input, at which point the content fades in, visually completing the
        // transition.
        val contentAlphaAnimatable = remember { Animatable(1f) }
        LaunchedEffect(contentAlphaAnimatable) {
            snapshotFlow { contentAlphaAnimatable.value }
                .collect {
                    // Pipe the content alpha animation progress to the view model, so NSSL can
                    // fade-in the stack in tandem.
                    viewModel.setContentAlphaForLockscreenFadeIn(it)
                }
        }

        // The `contentAlphaAnimatable` must animate from 0 -> 1 at the end of each transition / end
        // of user's gestures, iff the transition matched `shouldContentFadeIn`.  Keep track of the
        // last transition that required this fade-in, in order to trigger an animation every time.
        var revealContentAfterTransition by remember {
            mutableStateOf<TransitionState.Transition?>(null, policy = referentialEqualityPolicy())
        }
        layoutState.currentTransition
            ?.takeIf { currentTransition -> viewModel.shouldContentFadeIn(currentTransition) }
            ?.also { revealContentAfterTransition = it }

        LaunchedEffect(contentAlphaAnimatable, revealContentAfterTransition) {
            // When initially composed, either outside a transition or without the
            // shouldContentFadeIn, there's nothing to animate.
            val transitionToAwaitEnd = revealContentAfterTransition ?: return@LaunchedEffect

            // In all other cases, start transparent (the content won't be visible at the beginning
            // of such a transition)
            contentAlphaAnimatable.snapTo(0f)

            // Wait until the transition ended, was interrupted, or the user ended the gesture.
            snapshotFlow {
                    (transitionToAwaitEnd != layoutState.currentTransition) ||
                        !transitionToAwaitEnd.isUserInputOngoing
                }
                .first { it }

            // now fade the content in.
            contentAlphaAnimatable.animateTo(1f, tween())
        }

        // Ensure clock events are connected. This is a no-op if they are already registered.
        clockInteractor.clockEventController.registerListeners()

        DisposableEffect(view) {
            val handle = clockInteractor.clockEventController.bind(view)
            onDispose { handle.dispose() }
        }

        val overlaid = { overlays: (OverlayKey) -> Boolean ->
            when (val transition = layoutState.currentTransition) {
                is TransitionState.Transition.ShowOrHideOverlay ->
                    if (!overlays(transition.overlay)) {
                        if (layoutState.currentOverlays.any(overlays)) 1f else 0f
                    } else if (transition.toContent == transition.overlay) {
                        transition.progress
                    } else {
                        1f - transition.progress
                    }
                else -> if (layoutState.currentOverlays.any(overlays)) 1f else 0f
            }.coerceIn(0f, 1f)
        }
        val covered = { overlaid { true } }
        val shaded = { overlaid { it != Overlays.Bouncer } }
        val glass = liquidGlassEnabled()
        val screenCorner = LocalScreenCornerRadius.current
        val unlocking = {
            val toGone =
                (layoutState.currentTransition as? TransitionState.Transition.ChangeScene)
                    ?.takeIf { it.fromContent == contentKey && it.toContent == Scenes.Gone }
                    ?.progress
            (toGone ?: overlaid { it == Overlays.Bouncer }).coerceIn(0f, 1f)
        }
        player?.ExpandedArt(
            alpha = {
                val transition = layoutState.currentTransition as? TransitionState.Transition.ChangeScene
                val sceneAlpha =
                    when {
                        transition == null -> 1f
                        glass && transition.toContent == Scenes.Gone -> {
                            val p = unlocking()
                            1f - p * p
                        }
                        transition.fromContent == contentKey -> 1f - transition.progress
                        transition.toContent == contentKey -> transition.progress
                        else -> 1f
                    }.coerceIn(0f, 1f)
                min(viewModel.alpha, contentAlphaAnimatable.value) * sceneAlpha
            },
            covered = covered,
            unlocking = { if (glass) unlocking() else 0f },
        )
        Box(Modifier.fillMaxSize().thenIf(glass) { Modifier.glassUnlockSheet(unlocking, screenCorner) }) {
            LockscreenBehindScrim(
                lockscreenBehindScrimViewModel,
                Modifier.element(LockscreenElementKeys.BehindScrim),
            )
            with(lockscreenElements) {
                LockscreenElement(
                    LockscreenElementKeys.Root,
                    modifier
                        .sysuiResTag("keyguard_root_view")
                        .graphicsLayer {
                            alpha =
                                min(viewModel.alpha, contentAlphaAnimatable.value) *
                                    (if (glass) 1f - shaded() else 1f)
                        }
                        .motionTestValues {
                            LockscreenElementKeys.Root.currentAlpha()?.let { alpha ->
                                alpha exportAs LockscreenContentMotionTestKeys.Alpha
                            }
                        }
                        .focusable(),
                    LockscreenElementContext(nonAuthUI = Modifier.nonAuthUI(viewModel)),
                )
            }
        }
        LockscreenFrontScrim(lockscreenFrontScrimViewModel)
    }

    @VisibleForTesting
    object LockscreenContentMotionTestKeys {
        val Alpha = MotionTestValueKey<Float>("lockscreenContentAlpha")
    }
}

private class KeyguardTransitionAnimationCallbackImpl(
    private val view: View,
    private val interactionJankMonitor: InteractionJankMonitor,
    private val clockInteractor: KeyguardClockInteractor,
) : KeyguardTransitionAnimationCallback {

    override fun onAnimationStarted(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj ->
            val builder =
                InteractionJankMonitor.Configuration.Builder.withView(cuj, view)
                    .setTag(clockInteractor.renderedClockId)
            interactionJankMonitor.begin(builder)
        }
    }

    override fun onAnimationEnded(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj -> interactionJankMonitor.end(cuj) }
    }

    override fun onAnimationCanceled(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj -> interactionJankMonitor.cancel(cuj) }
    }

    @CujType
    private fun cujOrNull(from: KeyguardState, to: KeyguardState): Int? {
        return when {
            from == KeyguardState.AOD -> Cuj.CUJ_KEYGUARD_TRANSITION_AOD_TO_LOCKSCREEN
            to == KeyguardState.AOD -> Cuj.CUJ_KEYGUARD_TRANSITION_LOCKSCREEN_TO_AOD
            to == KeyguardState.DOZING -> Cuj.CUJ_KEYGUARD_TRANSITION_LOCKSCREEN_TO_DOZING
            else -> null
        }
    }
}

private fun Modifier.glassUnlockSheet(progress: () -> Float, corner: Dp): Modifier =
    graphicsLayer {
            val p = progress()
            translationY = -p * size.height
            clip = p > 0f
            shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        }
        .drawWithContent {
            val p = progress()
            if (p > 0f) {
                drawRect(Color.White.copy(alpha = 0.06f))
            }
            drawContent()
            if (p <= 0f) return@drawWithContent
            val r = corner.toPx()
            val edge =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            0f,
                            -r,
                            size.width,
                            size.height,
                            bottomLeftCornerRadius = CornerRadius(r),
                            bottomRightCornerRadius = CornerRadius(r),
                        )
                    )
                }
            val fade = (p * 6f).coerceAtMost(1f)
            for ((width, alpha) in listOf(28.dp to 0.05f, 12.dp to 0.08f)) {
                drawPath(edge, Color.White.copy(alpha = alpha * fade), style = Stroke(width.toPx()))
            }
            drawPath(
                edge,
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.8f to Color.White.copy(alpha = 0.15f * fade),
                    1f to Color.White.copy(alpha = 0.75f * fade),
                ),
                style = Stroke(2.5.dp.toPx()),
            )
        }
