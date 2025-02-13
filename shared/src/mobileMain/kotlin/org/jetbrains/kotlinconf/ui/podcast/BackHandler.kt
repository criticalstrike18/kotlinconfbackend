package org.jetbrains.kotlinconf.ui.podcast

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
fun BackHandler(state: PodcastScreenState, onBack: () -> Unit) {
    BackHandler(enabled = true) {
        onBack()
    }
}