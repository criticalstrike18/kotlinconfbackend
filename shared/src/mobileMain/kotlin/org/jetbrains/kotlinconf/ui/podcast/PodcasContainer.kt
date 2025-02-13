package org.jetbrains.kotlinconf.ui.podcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.podcast.PlayerState
import org.jetbrains.kotlinconf.podcast.PodcastEpisode

sealed class PodcastScreenState {
    data object ChannelList : PodcastScreenState()
    data class Episodes(val channelId: Long) : PodcastScreenState()
    data object FullScreenPlayer : PodcastScreenState()
}

@Composable
fun PodcastContainer(
    service: ConferenceService,
    playerState: PlayerState,
    onPlayPause: (PodcastEpisode) -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit
) {
    var screenState by remember { mutableStateOf<PodcastScreenState>(PodcastScreenState.ChannelList) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = screenState) {
            is PodcastScreenState.ChannelList -> {
                ChannelScreen(
                    service = service,
                    playerState = playerState,
                    onPlayPause = onPlayPause,
                    onNavigateToEpisodes = { channelId ->
                        screenState = PodcastScreenState.Episodes(channelId)
                    },
                    onExpandPlayer = {
                        screenState = PodcastScreenState.FullScreenPlayer
                    }
                )
            }

            is PodcastScreenState.Episodes -> {
                PodcastScreen(
                    service = service,
                    playerState = playerState,
                    onPlayPause = onPlayPause,
                    channelId = currentState.channelId,
                    onBackPress = {
                        screenState = PodcastScreenState.ChannelList
                    },
                    onExpandPlayer = {
                        screenState = PodcastScreenState.FullScreenPlayer
                    }
                )
            }

            PodcastScreenState.FullScreenPlayer -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val previousState = remember(currentState) {
                        if (currentState is PodcastScreenState.Episodes) currentState
                        else PodcastScreenState.ChannelList
                    }) {
                        is PodcastScreenState.Episodes -> {
                            PodcastScreen(
                                service = service,
                                playerState = playerState,
                                onPlayPause = onPlayPause,
                                channelId = previousState.channelId,
                                onBackPress = { },
                                onExpandPlayer = { }
                            )
                        }
                        else -> {
                            ChannelScreen(
                                service = service,
                                playerState = playerState,
                                onPlayPause = onPlayPause,
                                onNavigateToEpisodes = { },
                                onExpandPlayer = { }
                            )
                        }
                    }

                    FullScreenPlayer(
                        playerState = playerState,
                        onPlayPause = { playerState.currentEpisode?.let(onPlayPause) },
                        onMinimize = {
                            screenState = if (currentState is PodcastScreenState.Episodes) {
                                currentState
                            } else {
                                PodcastScreenState.ChannelList
                            }
                        },
                        onSeek = onSeek,
                        onSpeedChange = onSpeedChange,
                        onBoostChange = onBoostChange
                    )
                }
            }
        }
    }
}
