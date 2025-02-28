package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.pause
import kotlinconfapp.shared.generated.resources.play
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.DatabaseStorage
import org.jetbrains.kotlinApp.podcast.PlayerState
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.ui.HDivider
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.grey5Black
import org.jetbrains.kotlinApp.ui.theme.greyGrey20
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.greyWhite
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinApp.utils.Screen
import org.jetbrains.kotlinApp.utils.isTooWide
import org.jetbrains.kotlinconf.GetAllChannelDetails

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

@Composable
fun PodcastScreen(
    service: ConferenceService,
    playerState: PlayerState,
    playbackState: PodcastPlaybackState,
    onPlayPause: (PodcastEpisode) -> Unit,
    channelId: Long,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onBackPress: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    // Back press handler
    BackHandler(onBack = onBackPress)

    // State collection with loading state
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val channels by service.podcastChannels.collectAsState()
    val currentChannel = remember(channels, channelId) {
        channels.find { it.id == channelId }
    }
    val currentChannelEpisodes by service.currentChannelEpisodes.collectAsState()

    // Load episodes and handle loading state
    LaunchedEffect(channelId) {
        isLoading = true
        service.loadEpisodesForChannel(channelId)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playerState.currentEpisode != null) 140.dp else 0.dp)
        ) {
            NavigationBar(
                title = currentChannel?.title ?: "Episodes",
                isLeftVisible = true,
                onLeftClick = onBackPress,
                isRightVisible = false
            )

            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.whiteGrey)
                ) {
                    // Channel header
                    currentChannel?.let { channel ->
                        item(key = "header-${channel.id}") {
                            ChannelHeader(channel = channel)
                        }
                    }

                    // Episodes header
                    item(key = "episodes-header") {
                        EpisodesHeader(episodeCount = currentChannel?.episodeCount ?: 0)
                    }

                    // Episodes list with proper sorting and key handling
                    items(
                        items = currentChannelEpisodes
                            .sortedByDescending { it.pubDate },
                        key = { it.id }
                    ) { episode ->
                        val isCurrentEpisode = playerState.currentEpisode?.id == episode.id
                        EpisodeListItem(
                            episode = episode,
                            episodeNumber = currentChannelEpisodes.size - currentChannelEpisodes.indexOf(episode),
                            isPlaying = isCurrentEpisode && playerState.isPlaying,
                            database = service.dbStorage,
                            onClick = { onPlayPause(episode) }
                        )
                    }
                }
            }
        }

        // Show loading indicator when loading
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Mini player with proper state handling
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

@Composable
private fun ChannelHeader(channel: GetAllChannelDetails) {
    val screenSizeIsTooWide = Screen.isTooWide()
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.grey5Black)
    ) {
        HDivider()
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.h2.copy(
                    color = MaterialTheme.colors.greyWhite,
                    fontWeight = FontWeight.Bold
                ),
            )

            Text(
                text = channel.author,
                style = MaterialTheme.typography.body2.copy(
                    color = MaterialTheme.colors.greyGrey20
                )
            )
            Text(
                text = "Last Updated: ${formatDate(channel.latestEpisodePubDate!!)}",
                style = MaterialTheme.typography.caption.copy(color = grey50),
            )
            // Channel Image
            AsyncImage(
                imageUrl = channel.imageUrl,
                contentDescription = "Channel Image",
                modifier = Modifier
                    .size(400.dp)
                    .run {
                        if (screenSizeIsTooWide) {
                            width(400.dp)
                        } else {
                            fillMaxWidth()
                        }
                    }
                    .aspectRatio(1f)
                    .padding(start = 16.dp, end = 16.dp),
                contentScale = ContentScale.FillWidth,
            )
        }
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable { isExpanded = !isExpanded }
        ) {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                }
            ) { expanded ->
                if (expanded) {
                    // Full description
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.body2.copy(
                            color = MaterialTheme.colors.greyGrey20
                        ),
                        modifier = Modifier.animateContentSize()
                    )
                } else {
                    // Collapsed description with ellipsis
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = channel.description,
                            style = MaterialTheme.typography.body2.copy(
                                color = MaterialTheme.colors.greyGrey20
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesHeader(episodeCount: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.grey5Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.h2.copy(
                color = MaterialTheme.colors.greyGrey5
            )
        )
        Text(
            text = "$episodeCount episodes",
            style = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.colors.greyGrey20
            )
        )
    }
}

@Composable
private fun EpisodeListItem(
    episode: PodcastEpisode,
    episodeNumber: Int,
    isPlaying: Boolean,
    database: DatabaseStorage,
    onClick: () -> Unit
) {
    val screenSizeIsTooWide = Screen.isTooWide()
    val episodePosFlow = remember(episode.id) {
        database.getEpisodePositionFlow(episode.id.toLong())
    }
    val positionMs by episodePosFlow.collectAsState(initial = 0L)

    // 2) Compute fraction for the progress indicator:
    val fractionPlayed = if (episode.duration > 0) {
        (positionMs?.toFloat() ?: 0f) / (episode.duration * 1000).toFloat()
    } else 0f
    HDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Number
        Box(
            modifier = Modifier
                .size(85.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(if (screenSizeIsTooWide) 170.dp else 85.dp)
                    .padding(0.dp),
                imageUrl = episode.imageUrl!!,
                contentDescription = episodeNumber.toString(),
                contentScale = ContentScale.Crop
            )
        }

        // Episode Info
        Column(
            modifier = Modifier
                .padding(4.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
//                modifier = Modifier.padding(top = 4.dp),
                text = "Episode: $episodeNumber",
                style = MaterialTheme.typography.h4.copy(
                    color = MaterialTheme.colors.greyWhite, fontWeight = FontWeight.Bold
                ),
            )

            Text(
                text = episode.title.trim(),
                style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.greyGrey5),
                maxLines = 2
            )
            episode.pubDate?.let { timestamp ->
                Text(
                    text = formatDate(timestamp),
                    style = MaterialTheme.typography.caption.copy(color = grey50),
                )
            }
        }

        // Play/Pause Icon
        Column(
            modifier = Modifier
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = if (isPlaying)
                    painterResource(Res.drawable.pause)
                else
                    painterResource(Res.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier
                    .size(24.dp)
            )

            Text(
                text = formatDuration(episode.duration),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.greyGrey20
            )
            if (fractionPlayed > 0f && fractionPlayed < 1f) {
                LinearProgressIndicator(
                    progress = fractionPlayed,
                    modifier = Modifier
                        .width(30.dp)
                        .padding(top = 4.dp),
                    color = Color.Red, // ignore the theme color for now
                    backgroundColor = Color.LightGray,
                )
            }
        }
    }
}



