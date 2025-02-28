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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

            LazyColumn(
                state = listState,
                modifier = Modifier.background(MaterialTheme.colors.whiteGrey)
            ) {
                items(
                    items = channels,
                    key = { it.id }
                ) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = { onNavigateToEpisodes(channel.id) }
                    )
                }
            }
        }

        // Mini player with synchronized state
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
