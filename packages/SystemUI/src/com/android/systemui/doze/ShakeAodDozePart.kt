/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.doze

import android.hardware.display.AmbientDisplayConfiguration
import com.android.systemui.doze.DozeMachine
import com.android.systemui.doze.dagger.DozeScope
import com.android.systemui.settings.UserTracker
import java.io.PrintWriter
import javax.inject.Inject

@DozeScope
class ShakeAodDozePart @Inject constructor(
    private val controller: ShakeAodController,
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val userTracker: UserTracker,
) : DozeMachine.Part, ShakeAodController.Callback {

    private var machine: DozeMachine? = null

    override fun setDozeMachine(dozeMachine: DozeMachine) {
        machine = dozeMachine
    }

    override fun transitionTo(oldState: DozeMachine.State, newState: DozeMachine.State) {
        when (newState) {
            DozeMachine.State.INITIALIZED -> {
                controller.addCallback(this)
                controller.onDozeMachineStarted()
            }
            DozeMachine.State.FINISH -> {
                controller.removeCallback(this)
                controller.onDozeMachineFinished()
            }
            else -> Unit
        }
    }

    override fun destroy() {
        controller.removeCallback(this)
    }

    override fun onShakeAodPolicyChanged() {
        val dozeMachine = machine ?: return
        if (dozeMachine.isExecutingTransition()) return
        val state = dozeMachine.getState() ?: return
        val baseAlwaysOn = ambientDisplayConfiguration.alwaysOnEnabled(userTracker.userId)
        val allowed = controller.isAodAllowed(baseAlwaysOn)
        when {
            allowed && state == DozeMachine.State.DOZE ->
                dozeMachine.requestState(DozeMachine.State.DOZE_AOD)
            !allowed && state == DozeMachine.State.DOZE_AOD ->
                dozeMachine.requestState(DozeMachine.State.DOZE)
            !allowed && state == DozeMachine.State.DOZE_AOD_PAUSING ->
                dozeMachine.requestState(DozeMachine.State.DOZE)
            !allowed && state == DozeMachine.State.DOZE_AOD_PAUSED ->
                dozeMachine.requestState(DozeMachine.State.DOZE)
            else -> Unit
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.println("ShakeAodDozePart:")
        pw.println(" machineState=${machine?.getState()}")
        controller.dump(pw)
    }
}
