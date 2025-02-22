package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.kotlinApp.AppController

@Composable
fun PodcastNavigator(controller: AppController) {
    // Ensure this composable is only created once per AppController lifecycle
    // (e.g., by hoisting the controller above your TabsView or using a ViewModel)

    // Only push the initial screen if no view is on the stack.
    LaunchedEffect(controller) {
        if (controller.last.value == null) {
            controller.showPodcastChannels()
        }
    }

    // Observe the current top view from the controller's stack.
    val currentView by controller.last.collectAsState()
    currentView?.invoke(controller)
}
