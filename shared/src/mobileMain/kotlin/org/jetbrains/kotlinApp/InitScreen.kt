package org.jetbrains.kotlinApp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.privacy_policy_bird
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ln

@Composable
fun DataInitializationScreen(service: ConferenceService) {
    val initProgress by service.dataInitProgress.collectAsState()
    val appInitState by service.appInitState.collectAsState() // Also collect app state
    val coroutineScope = rememberCoroutineScope()

    // Track if initialization has been started
    val initStarted = remember { mutableStateOf(false) }

    // Start the initialization process when the screen is first shown
    LaunchedEffect(Unit) {
        if (!initStarted.value) {
            initStarted.value = true
            coroutineScope.launch {
                service.startDatabaseInitialization()
            }
        }
    }

    // Debug logging of state changes
    LaunchedEffect(appInitState, initProgress.stage) {
        println("AppInitState: $appInitState, Progress Stage: ${initProgress.stage}")
    }

    // UI to show unified download + import progress
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo/branding
            Image(
                painter = painterResource(Res.drawable.privacy_policy_bird), // Replace with your app logo
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 32.dp)
            )

            // Title that changes based on stage
            Text(
                text = when (initProgress.stage) {
                    DataInitProgress.InitStage.PREPARING -> "Preparing..."
                    DataInitProgress.InitStage.DOWNLOADING -> "Downloading Data"
                    DataInitProgress.InitStage.IMPORTING -> "Setting Up App"
                    DataInitProgress.InitStage.COMPLETED -> "Ready!"
                    DataInitProgress.InitStage.FAILED -> "Setup Issue"
                },
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Progress bar
            if (initProgress.stage != DataInitProgress.InitStage.FAILED) {
                LinearProgressIndicator(
                    progress = initProgress.overallProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colors.primary,
                    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f)
                )
            }

            // Progress details
            Spacer(modifier = Modifier.height(16.dp))

            when (initProgress.stage) {
                DataInitProgress.InitStage.PREPARING -> {
                    Text(
                        "Getting things ready...",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )
                }

                DataInitProgress.InitStage.DOWNLOADING -> {
                    // Show download details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(initProgress.overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                        )

                        initProgress.estimateTimeRemaining()?.let { seconds ->
                            Text(
                                text = formatTimeRemaining(seconds),
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }

                    if (initProgress.totalBytes > 0) {
                        Text(
                            "${formatFileSize(initProgress.bytesDownloaded)} of ${formatFileSize(initProgress.totalBytes)}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Text(
                        "Downloading essential app data...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                DataInitProgress.InitStage.IMPORTING -> {
                    // Show import details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(initProgress.overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                        )

                        initProgress.estimateTimeRemaining()?.let { seconds ->
                            Text(
                                text = formatTimeRemaining(seconds),
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Text(
                        "Setting up app data...",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                DataInitProgress.InitStage.COMPLETED -> {
                    Text(
                        "All set! Launching app...",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.8f)
                    )

                    // Add a manual button to force transition to main screen
                    // This helps with debugging and as a fallback
                    Button(
                        onClick = {
                            service.markDatabaseImportComplete()
                        },
                        modifier = Modifier.padding(top = 16.dp),
                        enabled = appInitState == AppInitState.INITIALIZING
                    ) {
                        Text("Continue to App")
                    }
                }

                DataInitProgress.InitStage.FAILED -> {
                    // Show error details and retry button
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colors.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = initProgress.error ?: "An unknown error occurred",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Skip the data download and just mark as complete
                                service.markDatabaseImportComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

// Helper function to format time remaining
fun formatTimeRemaining(seconds: Int): String {
    return when {
        seconds < 60 -> "< 1 min"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
    }
}

// Helper function to format file size
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(size.toDouble()) / ln(1024.0)).toInt().coerceIn(0, 4)

    val formattedSize = size / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(formattedSize, units[digitGroups])
}