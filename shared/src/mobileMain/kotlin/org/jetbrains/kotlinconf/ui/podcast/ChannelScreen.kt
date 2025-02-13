package org.jetbrains.kotlinconf.ui.podcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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


sealed class PodcastNavigation {
    data object Channels : PodcastNavigation()
    data class Episodes(val channelId: Long) : PodcastNavigation()
}

@Composable
fun ChannelScreen(
    service: ConferenceService,
    playerState: PlayerState,
    onPlayPause: (PodcastEpisode) -> Unit,
    onNavigateToEpisodes: (Long) -> Unit,
    onExpandPlayer: () -> Unit
) {
    val listState = rememberLazyListState()
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playerState.currentEpisode != null) 80.dp else 0.dp)
        ) {
            NavigationBar(
                title = "Podcast Channels",
                isLeftVisible = false,
                isRightVisible = false
            )

            val channels by service.podcastChannels.collectAsState()

            LaunchedEffect(channels) {
                isLoading = false
            }

            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = channels,
                        key = { channel -> channel.id }
                    ) { channel ->
                        AnimatedChannelCard(
                            channel = channel,
                            onClick = { onNavigateToEpisodes(channel.id) }
                        )
                    }
                }
            }
        }

        // Animated Mini Player
        AnimatedVisibility(
            visible = playerState.currentEpisode != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            playerState.currentEpisode?.let { episode ->
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
private fun AnimatedChannelCard(
    channel: PodcastChannels,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animation values
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 8f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colors.grey5Black,
        elevation = elevation.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Animated Image
            if (channel.imageUrl.isNotEmpty()) {
                AsyncImage(
                    imageUrl = channel.imageUrl,
                    contentDescription = "${channel.title} thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop,
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.h3,
                    color = MaterialTheme.colors.blackWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.greyGrey5,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "By ${channel.author}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.greyGrey20,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    channel.language.let { language ->
                        Surface(
                            color = MaterialTheme.colors.menuSelected,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            Text(
                                text = language.uppercase(),
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.blackWhite,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}