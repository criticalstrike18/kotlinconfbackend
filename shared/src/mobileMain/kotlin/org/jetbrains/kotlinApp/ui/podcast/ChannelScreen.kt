@file:OptIn(FlowPreview::class)

package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.podcast.PlayerState
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.ui.HDivider
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.greyWhite
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinApp.utils.Screen
import org.jetbrains.kotlinApp.utils.isTooWide
import org.jetbrains.kotlinconf.GetAllChannelDetails


sealed class PodcastNavigation {
    data object Channels : PodcastNavigation()
    data class Episodes(val channelId: Long) : PodcastNavigation()
}

private fun formatDateForChannelScreen(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val twoDigitYear = dateTime.year % 100  // Extract last two digits of the year
    return "${dateTime.monthNumber}/$twoDigitYear"
}

@Composable
fun ChannelScreen(
    service: ConferenceService,
    playerState: PlayerState,
    playbackState: PodcastPlaybackState,
    onPlayPause: (PodcastEpisode) -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onNavigateToEpisodes: (Long) -> Unit,
    onExpandPlayer: () -> Unit
) {
    val listState = rememberLazyListState()
    val channels by service.podcastChannels.collectAsState()
    val channelsCursor by service.currentChannelsCursor.collectAsState()

    // Loading states
    var isLoadingMore by remember { mutableStateOf(false) }
    var isLoadingPrevious by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Initial load
    LaunchedEffect(Unit) {
        if (channels.isEmpty()) {
            service.loadChannelsWithCursor(limit = 50)
        }
    }

    // Scroll detection for infinite scrolling
    LaunchedEffect(listState, channels.size) {
        // Wait for layout to be stable
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.layoutInfo.visibleItemsInfo.size,
                listState.layoutInfo.totalItemsCount
            )
        }
            .debounce(100) // Debounce to prevent rapid firing
            .collect { (firstVisible, visibleCount, totalCount) ->
                // Check if we're near the beginning (load previous)
                if (firstVisible < 2 && !isLoadingPrevious && channelsCursor.first != null) {
                    isLoadingPrevious = true
                    try {
                        service.loadChannelsWithCursor(
                            cursor = channelsCursor.first,
                            limit = 10,
                            backward = true
                        )
                    } finally {
                        isLoadingPrevious = false
                    }
                }

                // Check if we're near the end (load more)
                val lastVisible = firstVisible + visibleCount
                if (lastVisible > totalCount - 3 && !isLoadingMore && channelsCursor.second != null) {
                    isLoadingMore = true
                    try {
                        service.loadChannelsWithCursor(
                            cursor = channelsCursor.second,
                            limit = 10,
                            backward = false
                        )
                    } finally {
                        isLoadingMore = false
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playerState.currentEpisode != null) 140.dp else 0.dp)
        ) {
            NavigationBar(
                title = "Podcast Channels",
                isLeftVisible = false,
                isRightVisible = false
            )

            if (channels.isEmpty()) {
                // Initial loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.background(MaterialTheme.colors.whiteGrey)
                ) {
                    // Top loading indicator
                    item(key = "top-loader") {
                        if (isLoadingPrevious) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Channel items
                    items(
                        items = channels,
                        key = { channel -> "channel-${channel.id}" }
                    ) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onNavigateToEpisodes(channel.id) }
                        )
                    }

                    // Bottom loading indicator
                    item(key = "bottom-loader") {
                        if (isLoadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Mini player
        AnimatedVisibility(
            visible = playerState.currentEpisode != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                isPlaying = playerState.isPlaying,
                playerState = playerState,
                playbackState = playbackState,
                onPlayPause = { playerState.currentEpisode?.let(onPlayPause) },
                onExpand = onExpandPlayer,
                onSeek = onSeek,
                onSpeedChange = onSpeedChange,
                onBoostChange = onBoostChange
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ChannelCard(
    channel: GetAllChannelDetails,
    onClick: () -> Unit = {}
) {
    val screenSizeIsTooWide = Screen.isTooWide()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.whiteGrey)
            .padding(0.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.whiteGrey)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    modifier = Modifier
                        .size(if (screenSizeIsTooWide) 170.dp else 85.dp)
                        .padding(0.dp),
                    imageUrl = channel.imageUrl,
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.h4.copy(
                            color = MaterialTheme.colors.greyWhite, fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "By ${channel.author}",
                        style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.greyGrey5),
                        maxLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${channel.episodeCount} episodes",
                            style = MaterialTheme.typography.caption.copy(color = grey50),
                        )
                        Text(
                            text = "${channel.earliestEpisodePubDate?.let {
                                formatDateForChannelScreen(
                                    it
                                )
                            }} - ${channel.latestEpisodePubDate?.let { formatDateForChannelScreen(it) }}",
                            style = MaterialTheme.typography.caption.copy(color = grey50),
                        )
                    }
                }
            }
            HDivider()
        }
    }
}
