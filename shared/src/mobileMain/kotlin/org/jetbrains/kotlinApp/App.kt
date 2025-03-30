package org.jetbrains.kotlinApp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import org.jetbrains.kotlinApp.podcast.PodcastViewModel
import org.jetbrains.kotlinApp.ui.MainScreen
import org.jetbrains.kotlinApp.ui.theme.KotlinConfTheme
const val ip = "192.168.43.253"
const val apiEndpoint = "http://$ip:8080"
const val importEndpoint = "http://$ip:8000"

@Composable
fun App(context: ApplicationContext) {
    val viewModel = viewModel { PodcastViewModel(context) }

    KotlinConfTheme {
        val service = remember { ConferenceService(context, apiEndpoint, importEndpoint ) }

        CompositionLocalProvider(
            LocalImageLoader provides remember { createImageLoader(context) }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                MainScreen(
                    service = service,
                    podcastViewModel = viewModel
                )
            }
        }
    }
}

expect fun createImageLoader(context: ApplicationContext): ImageLoader
