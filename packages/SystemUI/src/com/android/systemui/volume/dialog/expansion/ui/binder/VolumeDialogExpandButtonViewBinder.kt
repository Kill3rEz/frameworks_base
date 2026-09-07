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
import android.widget.ImageButton
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

@VolumeDialogScope
class VolumeDialogExpandButtonViewBinder
@Inject
constructor(
    private val expansionInteractor: VolumeDialogExpansionInteractor,
    private val dialogViewModel: VolumeDialogViewModel,
) : ViewBinder {

    override fun CoroutineScope.bind(view: View) {
        val button = view.findViewById<ImageButton>(R.id.volume_dialog_expand) ?: return
        if (!expansionInteractor.isExpandable) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        if (expansionInteractor.style == VolumePanelStyle.EXPANDABLE) {
            button.setBackgroundResource(R.drawable.volume_panel_expandable_button_background)
            button.imageTintList =
                ColorStateList.valueOf(
                    view.context.getColor(R.color.volume_panel_expandable_icon_on_inactive)
                )
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

        val collapsedButton: ImageButton? =
            view.findViewById<ImageButton>(R.id.volume_dialog_expand_collapsed)?.also {
                it.setOnClickListener(toggle)
                launchTraced("VDEBVB#addCollapsedTouchableBounds") {
                    dialogViewModel.addTouchableBounds(it)
                }
            }

        expansionInteractor.isExpanded
            .onEach { isExpanded ->
                if (expansionInteractor.style == VolumePanelStyle.ONE_UI) {
                    view.applyOneUiChrome(isExpanded)
                    collapsedButton?.visibility =
                        if (isExpanded) View.GONE else View.VISIBLE
                }
                button.setImageResource(
                    when {
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

    private fun isPanelOnLeft(view: View): Boolean =
        Settings.Secure.getInt(
            view.context.contentResolver,
            Settings.Secure.VOLUME_PANEL_ON_LEFT,
            0,
        ) == 1
}
