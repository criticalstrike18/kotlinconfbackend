package org.jetbrains.kotlinApp.ui.welcome

import androidx.compose.runtime.Composable
import org.jetbrains.kotlinApp.ui.AppPrivacyPolicyScreen

@Composable
fun WelcomeFullPrivacyPolicyScreen(
    onAccept: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit
) {
    FormWithButtons(
        onAccept = {
            onAccept()
            onClose()
        }, onReject = onClose
    ) {
        AppPrivacyPolicyScreen(showCloseButton = true, onDismiss)
    }
}