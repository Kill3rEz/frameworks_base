/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.framework.compose

import android.app.Activity
import android.content.Intent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.framework.util.appendSpaParams

interface NavControllerWrapper {
    fun navigate(route: String, popUpCurrent: Boolean = false)
    fun navigateBack()

    fun isHighLightItem(highlightItemKey: String) = false

    val sessionSourceName: String?
        get() = null
}

@Composable
fun NavHostController.localNavController(): ProvidedValue<NavControllerWrapper> {
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val hostActivity = localActivity()
    return LocalNavController provides remember {
        NavControllerWrapperImpl(
            navController = this,
            onBackPressedDispatcher = onBackPressedDispatcherOwner?.onBackPressedDispatcher,
            hostActivity = hostActivity,
        )
    }
}

val LocalNavController = compositionLocalOf<NavControllerWrapper> {
    object : NavControllerWrapper {
        override fun navigate(route: String, popUpCurrent: Boolean) {}

        override fun navigateBack() {}
    }
}

@Composable
fun navigator(route: String?, popUpCurrent: Boolean = false): () -> Unit {
    if (route == null) return {}
    val navController = LocalNavController.current
    return { navController.navigate(route, popUpCurrent) }
}

internal class NavControllerWrapperImpl(
    val navController: NavHostController,
    private val onBackPressedDispatcher: OnBackPressedDispatcher?,
    private val hostActivity: Activity? = null,
) : NavControllerWrapper {
    var highlightItemKey: String? = null
    var sessionName: String? = null

    /**
     * Opens [route] in its own copy of the hosting activity, rather than pushing it onto the
     * current one's [NavHost][androidx.navigation.compose.NavHost].
     *
     * Predictive back is a cross-activity animation, run by WMShell on the window surfaces. A page
     * popped within a single activity never reaches it and can only approximate it in Compose,
     * which is what made SPA pages look out of place next to the rest of Settings.
     */
    override fun navigate(route: String, popUpCurrent: Boolean) {
        val activity = hostActivity
        if (activity == null || !route.opensInOwnActivity()) {
            navigateInHost(route, popUpCurrent)
            return
        }
        activity.startActivity(
            Intent(activity, activity.javaClass)
                .appendSpaParams(destination = route, sessionName = sessionName)
        )
        // popUpCurrent means the caller does not want to come back here, which for an activity per
        // page is simply finishing the one we are leaving.
        if (popUpCurrent) activity.finish()
    }

    private fun navigateInHost(route: String, popUpCurrent: Boolean) {
        navController.navigate(route) {
            if (popUpCurrent) {
                navController.currentDestination?.let { currentDestination ->
                    popUpTo(currentDestination.id) {
                        inclusive = true
                    }
                }
            }
        }
    }

    /**
     * Whether [route] names a full page, as opposed to a dialog - a dialog has no window of its
     * own, so it has to stay in the current activity - or a route this app does not own.
     */
    private fun String.opensInOwnActivity(): Boolean {
        val repository =
            SpaEnvironmentFactory.optionalInstance?.pageProviderRepository?.value ?: return false
        val provider = repository.getProviderOrNull(substringBefore('/')) ?: return false
        return provider.navType == SettingsPageProvider.NavType.Page
    }

    override fun navigateBack() {
        onBackPressedDispatcher?.onBackPressed()
    }

    override fun isHighLightItem(highlightItemKey: String): Boolean {
        return if (this.highlightItemKey == highlightItemKey) {
            // Also clear the highlight key when get, so we only highlight once
            this.highlightItemKey = null
            true
        } else {
            false
        }
    }

    override val sessionSourceName: String?
        get() = sessionName
}
