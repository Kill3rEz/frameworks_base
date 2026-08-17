/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject

class PreferredNetworkDialogDelegate @Inject constructor(
    private val context: Context,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    private val activityStarter: ActivityStarter
) : SystemUIDialog.Delegate {

    private val telephonyManager: TelephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    override fun createDialog(): SystemUIDialog = systemUIDialogFactory.create(this)

    override fun beforeCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.preferred_network_dialog, null)
        dialog.setView(view)
    }

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

        val tm = telephonyManager.createForSubscriptionId(subId)
        val currentType = getCurrentType(tm)

        val container = dialog.findViewById<LinearLayout>(R.id.mode_options_container) ?: return
        container.removeAllViews()

        val options = listOf(
            NetworkOption(4, "5G", TelephonyManager.NETWORK_CLASS_BITMASK_5G),
            NetworkOption(3, "4G", TelephonyManager.NETWORK_CLASS_BITMASK_4G),
            NetworkOption(2, "3G", TelephonyManager.NETWORK_CLASS_BITMASK_3G),
            NetworkOption(1, "2G", TelephonyManager.NETWORK_CLASS_BITMASK_2G)
        )

        for (opt in options) {
            val itemView = LayoutInflater.from(dialog.context)
                .inflate(R.layout.preferred_network_option_item, container, false)
            val title = itemView.findViewById<TextView>(R.id.option_title)
            val isSelected = (opt.type == currentType)

            title.text = opt.label

            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.preferred_network_pill_active)
            } else {
                itemView.setBackgroundResource(R.drawable.preferred_network_pill_inactive)
            }

            itemView.setOnClickListener {
                tm.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    opt.mask
                )
                dialog.dismiss()
            }

            container.addView(itemView)
        }

        dialog.findViewById<Button>(R.id.button_settings)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$PreferredNetworkSettingsActivity"
                )
            }
            activityStarter.postStartActivityDismissingKeyguard(intent, 0)
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.button_done)?.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun getCurrentType(tm: TelephonyManager): Int {
        val allowed = tm.getAllowedNetworkTypesForReason(
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
        )
        if ((allowed and TelephonyManager.NETWORK_CLASS_BITMASK_5G) != 0L) return 4
        if ((allowed and TelephonyManager.NETWORK_CLASS_BITMASK_4G) != 0L) return 3
        if ((allowed and TelephonyManager.NETWORK_CLASS_BITMASK_3G) != 0L) return 2
        if ((allowed and TelephonyManager.NETWORK_CLASS_BITMASK_2G) != 0L) return 1
        return 0
    }

    private data class NetworkOption(val type: Int, val label: String, val mask: Long)
}
