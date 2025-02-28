package org.jetbrains.kotlinApp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.whiteGrey

@Composable
fun PodcastRequestScreen(
    service: ConferenceService,
    back: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.whiteGrey)
    ) {
        NavigationBar(
            title = "Request New Podcast",
            isLeftVisible = true,
            onLeftClick = back,
            isRightVisible = false
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PodcastRequestForm(
                service = service,
                requestSent = {
                    back()
                }
            )
        }
    }
}

@Composable
fun PodcastRequestForm(service: ConferenceService, requestSent: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var rssLink by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(8.dp)) {
        // --- Title input ---
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- Author input ---
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text("Author") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // --- RSS Link input ---
        OutlinedTextField(
            value = rssLink,
            onValueChange = { rssLink = it },
            label = { Text("RSS Feed Link") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = MaterialTheme.colors.onSurface)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- Submit Button ---
        Button(
            onClick = {
                if (title.isNotBlank() && author.isNotBlank() && rssLink.isNotBlank()) {
                    // Launch the request in the background
                    service.launch {
                        try {
                            service.sendPodcastQueryRequest(title, author, rssLink)
                            println("Request sent successfully") // Only print after success
                        } catch (e: Exception) {
                            println("Error sending request: ${e.message}")
                        }
                    }
                    requestSent()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Request")
        }
    }
}