package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.fast_forward_30
import kotlinconfapp.shared.generated.resources.pause
import kotlinconfapp.shared.generated.resources.play
import kotlinconfapp.shared.generated.resources.rewind_10
import kotlinconfapp.shared.generated.resources.volume_high
import kotlinconfapp.shared.generated.resources.volume_low
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinApp.podcast.PlayerState
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.theme.greyGrey20
import org.jetbrains.kotlinApp.ui.theme.subtitle
import org.jetbrains.kotlinApp.ui.theme.title
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinApp.utils.Screen
import org.jetbrains.kotlinApp.utils.isTooWide

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun FullScreenPlayer(
    playerState: PlayerState,
    playbackState: PodcastPlaybackState, // Add playback state to sync speed and boost
    onPlayPause: () -> Unit,
    onMinimize: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Early check for null states to prevent crashes
    if (playerState.currentChannel == null || playerState.currentEpisode == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    // Use playback state for speed and boost instead of local state
    val availableSpeeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    val screenSizeIsTooWide = Screen.isTooWide()
    var isExpanded by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(playerState.currentPosition.toFloat()) }

    // Keep slider in sync with player position
    LaunchedEffect(playerState.currentPosition) {
        sliderPosition = playerState.currentPosition.toFloat()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.whiteGrey)
    ) {
        // Top Bar with safe access to episode title
        TopAppBar(
            title = {
                Text(
                    text = playerState.currentEpisode?.title ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize"
                    )
                }
            }
        )

        // Main content with safe channel access
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colors.whiteGrey)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel info section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(if (screenSizeIsTooWide) 170.dp else 85.dp),
                    imageUrl = playerState.currentEpisode.imageUrl ?: "",
                    contentDescription = "Channel Artwork",
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = playerState.currentChannel.title ?: "",
                        style = MaterialTheme.typography.h4,
                        color = MaterialTheme.colors.title
                    )
                    Text(
                        text = playerState.currentEpisode.title ?: "",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.subtitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(2f)
                    .clickable { isExpanded = !isExpanded }
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                    }
                ) { expanded ->
                    if (expanded) {
                        // Expanded description wrapped in a scrollable LazyColumn.
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier
                                .fillMaxWidth()
//                                .heightIn(max = 400.dp) // adjust max height as needed
                        ) {
                            item {
                                Text(
                                    text = playerState.currentEpisode.description.toString(),
                                    style = MaterialTheme.typography.body2.copy(
                                        color = MaterialTheme.colors.greyGrey20
                                    )
                                )
                            }
                        }
                    } else {
                        // Collapsed description with ellipsis
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = playerState.currentEpisode.description.toString(),
                                style = MaterialTheme.typography.body2.copy(
                                    color = MaterialTheme.colors.greyGrey20
                                ),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Progress section
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                    valueRange = 0f..(playerState.duration.coerceAtLeast(1).toFloat()),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderPosition.toLong()),
                        style = MaterialTheme.typography.caption
                    )
                    Text(
                        text = if (playerState.duration <= 0L) "0:00" else formatTime(playerState.duration),
                        style = MaterialTheme.typography.caption
                    )
                }

                PlayerControls(
                    isPlaying = playerState.isPlaying,
                    currentSpeed = playbackState.speed.toFloat(),
                    isBoostEnabled = playbackState.isBoostEnabled,
                    availableSpeeds = availableSpeeds,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSpeedChange = onSpeedChange,
                    onBoostChange = onBoostChange,
                    currentPosition = playerState.currentPosition
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    currentSpeed: Float,
    isBoostEnabled: Boolean,
    availableSpeeds: List<Float>,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    currentPosition: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Volume boost button
        IconButton(onClick = { onBoostChange(!isBoostEnabled) }) {
            Icon(
                painter = painterResource(
                    if (isBoostEnabled) Res.drawable.volume_high
                    else Res.drawable.volume_low
                ),
                contentDescription = "Volume Boost",
                modifier = Modifier.size(32.dp)
            )
        }

        // Rewind button
        IconButton(onClick = { onSeek(currentPosition - 10000) }) {
            Icon(
                painter = painterResource(Res.drawable.rewind_10),
                contentDescription = "Rewind 10 seconds",
                modifier = Modifier.size(32.dp)
            )
        }

        // Play/Pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colors.primary, CircleShape)
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) Res.drawable.pause else Res.drawable.play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colors.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Forward button
        IconButton(onClick = { onSeek(currentPosition + 30000) }) {
            Icon(
                painter = painterResource(Res.drawable.fast_forward_30),
                contentDescription = "Forward 30 seconds",
                modifier = Modifier.size(32.dp)
            )
        }

        // Speed button
        Text(
            text = "${currentSpeed}x",
            modifier = Modifier
                .clickable {
                    val currentIndex = availableSpeeds.indexOf(currentSpeed)
                    val nextIndex = (currentIndex + 1) % availableSpeeds.size
                    onSpeedChange(availableSpeeds[nextIndex])
                }
                .padding(8.dp),
            style = MaterialTheme.typography.body2
        )
    }
}