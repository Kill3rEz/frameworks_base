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

package com.android.systemui.volume.dialog.expansion.ui.binder

import android.content.res.ColorStateList
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.res.R
import com.android.systemui.volume.VolumePanelStyle
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogExpansionInteractor
import com.android.systemui.volume.dialog.ui.binder.ViewBinder
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onEach

/**
 * Binds the button that expands the volume dialog into a slider per stream. Only the expandable
 * panel style has one: for the default style the button stays hidden.
 */
@VolumeDialogScope
class VolumeDialogExpandButtonViewBinder
@Inject
constructor(
    private val expansionInteractor: VolumeDialogExpansionInteractor,
    private val dialogViewModel: VolumeDialogViewModel,
) : ViewBinder {

    override fun CoroutineScope.bind(view: View) {
        // The horizontal dialog has no bottom section, so there is nothing to bind there.
        val button = view.findViewById<ImageButton>(R.id.volume_dialog_expand) ?: return
        if (!expansionInteractor.isExpandable) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        if (expansionInteractor.style == VolumePanelStyle.EXPANDABLE) {
            // The pills have no card behind them, so the buttons carry their own so that the panel
            // reads as one piece instead of loose icons on the wallpaper.
            button.setBackgroundResource(R.drawable.volume_panel_expandable_button_background)
            button.imageTintList =
                ColorStateList.valueOf(
                    view.context.getColor(R.color.volume_panel_expandable_icon_on_inactive)
                )
            // The sliders grow towards the middle of the screen, so the chevron has to point the
            // other way when the panel sits on the left edge.
            if (isPanelOnLeft(view)) {
                button.rotation = 180f
            }
        }
        launchTraced("VDEBVB#addTouchableBounds") { dialogViewModel.addTouchableBounds(button) }

        val toggle = View.OnClickListener {
            expansionInteractor.toggle()
            dialogViewModel.resetDialogTimeout()
        }
        button.setOnClickListener(toggle)

        // Collapsed, the card panels are only their pill: the dots on it take over from the
        // header chevron.
        val collapsedButton: ImageButton? =
            view.findViewById<ImageButton>(R.id.volume_dialog_expand_collapsed)?.also {
                it.setOnClickListener(toggle)
                launchTraced("VDEBVB#addCollapsedTouchableBounds") {
                    dialogViewModel.addTouchableBounds(it)
                }
            }

        val isMyUi = expansionInteractor.style == VolumePanelStyle.MYUI
        if (isMyUi) {
            // MyUI collapses from the Done button at the foot of the card instead of a chevron.
            button.visibility = View.GONE
            view.findViewById<View>(R.id.volume_dialog_myui_done)?.setOnClickListener(toggle)
        }

        expansionInteractor.isExpanded
            .onEach { isExpanded ->
                if (isMyUi) {
                    view.applyMyUiChrome(isExpanded)
                    collapsedButton?.visibility = if (isExpanded) View.GONE else View.VISIBLE
                    return@onEach
                }
                if (expansionInteractor.style == VolumePanelStyle.ONE_UI) {
                    view.applyOneUiChrome(isExpanded)
                    collapsedButton?.visibility =
                        if (isExpanded) View.GONE else View.VISIBLE
                }
                button.setImageResource(
                    when {
                        // The One UI card grows downwards from its header, so up means collapse.
                        expansionInteractor.style == VolumePanelStyle.ONE_UI ->
                            R.drawable.ic_expand_less_rounded
                        isExpanded -> R.drawable.ic_chevron_right
                        else -> R.drawable.ic_chevron_left
                    }
                )
                button.contentDescription =
                    view.context.getString(
                        if (isExpanded) {
                            R.string.volume_panel_collapse
                        } else {
                            R.string.volume_panel_expand
                        }
                    )
            }
            .launchInTraced("VDEBVB#isExpanded", this)
    }

    /**
     * Hides the header of the One UI panel while it is collapsed, so that the card shrinks to the
     * single pill instead of staying a wide sheet with one slider rattling around in it.
     */
    private fun View.applyOneUiChrome(isExpanded: Boolean) {
        findViewById<View>(R.id.volume_dialog_oneui_header)?.visibility =
            if (isExpanded) View.VISIBLE else View.GONE
        minimumWidth =
            if (isExpanded) {
                context.resources.getDimensionPixelSize(R.dimen.volume_panel_oneui_min_width)
            } else {
                0
            }
    }

    /**
     * Collapsed, the MyUI column shows the sound icon above its slider and the dots under it;
     * expanded, the card takes the title on top and Done and Sound settings along the bottom.
     */
    private fun View.applyMyUiChrome(isExpanded: Boolean) {
        val expandedOnly = if (isExpanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.volume_dialog_myui_title)?.visibility = expandedOnly
        findViewById<View>(R.id.volume_dialog_myui_footer)?.visibility = expandedOnly
        findViewById<View>(R.id.volume_dialog_myui_sound_icon)?.visibility =
            if (isExpanded) View.GONE else View.VISIBLE
        // The captions circle belongs to the collapsed column. Its own binder owns visibility, so
        // it only fades out here and stops taking taps.
        findViewById<View>(R.id.odi_captions_icon)?.apply {
            alpha = if (isExpanded) 0f else 1f
            isEnabled = !isExpanded
        }
        // ConstraintLayout measures against its own minimum, not the View one.
        findViewById<ConstraintLayout>(R.id.volume_dialog_myui_card)?.minWidth =
            if (isExpanded) {
                context.resources.getDimensionPixelSize(R.dimen.volume_panel_myui_expanded_width)
            } else {
                0
            }
        // The gap before the primary slider only makes sense with others beside it; collapsed it
        // would push the lone slider off the middle of the column.
        findViewById<View>(R.id.volume_dialog_main_slider_container)
            ?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart =
                    if (isExpanded) {
                        context.resources.getDimensionPixelSize(
                            R.dimen.volume_panel_myui_slider_spacing
                        )
                    } else {
                        0
                    }
            }
    }

    private fun isPanelOnLeft(view: View): Boolean =
        Settings.Secure.getInt(
            view.context.contentResolver,
            Settings.Secure.VOLUME_PANEL_ON_LEFT,
            0,
        ) == 1
}
