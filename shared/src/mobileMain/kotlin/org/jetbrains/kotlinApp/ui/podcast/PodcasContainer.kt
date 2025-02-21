package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.podcast.PodcastViewModel

sealed class PodcastScreenState {
    data object ChannelList : PodcastScreenState()
    data class Episodes(val channelId: Long) : PodcastScreenState()
    data object FullScreenPlayer : PodcastScreenState()
}

@Composable
fun PodcastContainer(
    service: ConferenceService,
    podcastViewModel: PodcastViewModel,  // Changed to use ViewModel directly
    modifier: Modifier = Modifier
) {
    val playerState by podcastViewModel.playerState.collectAsState()
    val playbackState by podcastViewModel.playbackState.collectAsState()

    // Screen state management
    var screenState by remember { mutableStateOf<PodcastScreenState>(PodcastScreenState.ChannelList) }

    // Handle back press at container level
    BackHandler(enabled = screenState != PodcastScreenState.ChannelList) {
        screenState = when (screenState) {
            is PodcastScreenState.FullScreenPlayer -> {
                if (screenState is PodcastScreenState.Episodes) screenState
                else PodcastScreenState.ChannelList
            }
            is PodcastScreenState.Episodes -> PodcastScreenState.ChannelList
            PodcastScreenState.ChannelList -> PodcastScreenState.ChannelList
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val currentState = screenState) {
            is PodcastScreenState.ChannelList -> {
                ChannelScreen(
                    service = service,
                    playerState = playerState,
                    playbackState = playbackState,  // Add playback state
                    onPlayPause = { episode ->
                        if (playerState.currentEpisode?.id == episode.id) {
                            if (playerState.isPlaying) podcastViewModel.pause()
                            else podcastViewModel.play(episode)
                        } else {
                            podcastViewModel.play(episode)
                        }
                    },
                    onNavigateToEpisodes = { channelId ->
                        screenState = PodcastScreenState.Episodes(channelId)
                    },
                    onExpandPlayer = {
                        screenState = PodcastScreenState.FullScreenPlayer
                    },
                    onSeek = podcastViewModel::seekTo,
                    onSpeedChange = podcastViewModel::setSpeed,
                    onBoostChange = podcastViewModel::enableBoost
                )
            }

            is PodcastScreenState.Episodes -> {
                PodcastScreen(
                    service = service,
                    playerState = playerState,
                    playbackState = playbackState,
                    onPlayPause = { episode ->
                        if (playerState.currentEpisode?.id == episode.id) {
                            if (playerState.isPlaying) podcastViewModel.pause()
                            else podcastViewModel.play(episode)
                        } else {
                            podcastViewModel.play(episode)
                        }
                    },
                    channelId = currentState.channelId,
                    onBackPress = {
                        screenState = PodcastScreenState.ChannelList
                    },
                    onExpandPlayer = {
                        screenState = PodcastScreenState.FullScreenPlayer
                    },
                    onSeek = podcastViewModel::seekTo,
                    onSpeedChange = podcastViewModel::setSpeed,
                    onBoostChange = podcastViewModel::enableBoost
                )
            }

            PodcastScreenState.FullScreenPlayer -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Show background screen
                    when (val previousState = remember(currentState) {
                        if (currentState is PodcastScreenState.Episodes) currentState
                        else PodcastScreenState.ChannelList
                    }) {
                        is PodcastScreenState.Episodes -> {
                            PodcastScreen(
                                service = service,
                                playerState = playerState,
                                playbackState = playbackState,
                                onPlayPause = { /* Disabled in background */ },
                                channelId = previousState.channelId,
                                onBackPress = { },
                                onExpandPlayer = { },
                                onSeek = { /* Disabled in background */ },
                                onSpeedChange = { /* Disabled in background */ },
                                onBoostChange = { /* Disabled in background */ }
                            )
                        }
                        else -> {
                            ChannelScreen(
                                service = service,
                                playerState = playerState,
                                playbackState = playbackState,
                                onPlayPause = { /* Disabled in background */ },
                                onNavigateToEpisodes = { },
                                onExpandPlayer = { },
                                onSeek = { /* Disabled in background */ },
                                onSpeedChange = { /* Disabled in background */ },
                                onBoostChange = { /* Disabled in background */ }
                            )
                        }
                    }

                    FullScreenPlayer(
                        playerState = playerState,
                        playbackState = playbackState,
                        onPlayPause = {
                            playerState.currentEpisode?.let { episode ->
                                if (playerState.isPlaying) podcastViewModel.pause()
                                else podcastViewModel.play(episode)
                            }
                        },
                        onMinimize = {
                            screenState = if (currentState is PodcastScreenState.Episodes) {
                                currentState
                            } else {
                                PodcastScreenState.ChannelList
                            }
                        },
                        onSeek = podcastViewModel::seekTo,
                        onSpeedChange = podcastViewModel::setSpeed,
                        onBoostChange = podcastViewModel::enableBoost
                    )
                }
            }
        }
    }
}
