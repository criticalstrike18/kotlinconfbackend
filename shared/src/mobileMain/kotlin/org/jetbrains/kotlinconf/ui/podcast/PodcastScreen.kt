package org.jetbrains.kotlinconf.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.pause
import kotlinconfapp.shared.generated.resources.play
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.PodcastChannels
import org.jetbrains.kotlinconf.podcast.PlayerState
import org.jetbrains.kotlinconf.podcast.PodcastEpisode
import org.jetbrains.kotlinconf.ui.components.AsyncImage
import org.jetbrains.kotlinconf.ui.components.NavigationBar
import org.jetbrains.kotlinconf.ui.theme.blackWhite
import org.jetbrains.kotlinconf.ui.theme.grey5Black
import org.jetbrains.kotlinconf.ui.theme.greyGrey20
import org.jetbrains.kotlinconf.ui.theme.greyGrey5
import org.jetbrains.kotlinconf.ui.theme.menuSelected



@Composable
fun PodcastScreen(
    playerState: PlayerState,
    onPlayPause: (PodcastEpisode) -> Unit,
    service: ConferenceService,
    channelId: Long,
    onBackPress: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    // Handle back press using BackHandler
    BackHandler(enabled = true, onBack = onBackPress)

    val channels by service.podcastChannels.collectAsState()
    val currentChannel = channels.find { it.id == channelId }
    val currentChannelEpisodes by service.currentChannelEpisodes.collectAsState()

    LaunchedEffect(channelId) {
        service.loadEpisodesForChannel(channelId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playerState.currentEpisode != null) 80.dp else 0.dp)
        ) {
            // Navigation Bar with back button
            NavigationBar(
                title = currentChannel?.title ?: "Episodes",
                isLeftVisible = true,
                onLeftClick = onBackPress,
                isRightVisible = false
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Channel Header
                item {
                    currentChannel?.let { channel ->
                        ChannelHeader(channel = channel)
                    }
                }

                // Episodes Section Title
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.h3,
                            color = MaterialTheme.colors.blackWhite
                        )

                        Text(
                            text = "${currentChannelEpisodes.size} episodes",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.greyGrey20
                        )
                    }
                }

                // Episodes List
                itemsIndexed(
                    items = currentChannelEpisodes.sortedByDescending { it.pubDate },
                    key = { _, episode -> episode.id }
                ) { index, episode ->
                    EnhancedEpisodeItem(
                        episode = PodcastEpisode(
                            id = episode.id,
                            title = episode.title,
                            audioUrl = episode.audioUrl,
                            duration = episode.duration,
                            imageUrl = episode.imageUrl,
                            description = episode.description,
                            pubDate = episode.pubDate
                        ),
                        episodeNumber = currentChannelEpisodes.size - index,
                        isPlaying = playerState.isPlaying &&
                                playerState.currentEpisode?.id == episode.id.toString(),
                        onClick = {
                            onPlayPause(
                                PodcastEpisode(
                                    id = episode.id.toString(),
                                    title = episode.title,
                                    audioUrl = episode.audioUrl,
                                    duration = episode.duration,
                                    imageUrl = episode.imageUrl,
                                    description = episode.description,
                                    pubDate = episode.pubDate
                                )
                            )
                        }
                    )
                }
            }
        }

        // Mini Player
        playerState.currentEpisode?.let { episode ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                MiniPlayer(
                    episode = episode,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = { onPlayPause(episode) },
                    onExpand = onExpandPlayer
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(channel: PodcastChannels) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.grey5Black)
            .padding(16.dp)
    ) {
        // Channel Image and Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel Image
            AsyncImage(
                imageUrl = channel.imageUrl,
                contentDescription = "Channel Image",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Channel Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.h3,
                    color = MaterialTheme.colors.blackWhite
                )

                Text(
                    text = channel.author,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.greyGrey20
                )
            }
        }

        // Channel Description
        if (channel.description.isNotEmpty()) {
            Text(
                text = channel.description.trim(),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.greyGrey5,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EnhancedEpisodeItem(
    episode: PodcastEpisode,
    episodeNumber: Int,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colors.grey5Black,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Number
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colors.menuSelected,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episodeNumber.toString(),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.blackWhite
                )
            }

            // Episode Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.blackWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Duration
                    Text(
                        text = formatDuration(episode.duration),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.greyGrey20
                    )

                    // Publication Date
                    episode.pubDate?.let { timestamp ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.greyGrey20
                        )
                        Text(
                            text = formatDate(timestamp),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.greyGrey20
                        )
                    }
                }
            }

            // Play/Pause Icon
            Icon(
                painter = if (isPlaying)
                    painterResource(Res.drawable.pause)
                else
                    painterResource(Res.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.monthNumber}/${dateTime.dayOfMonth}/${dateTime.year}"
}

fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}


