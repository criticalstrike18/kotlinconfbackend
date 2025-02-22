package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jetbrains.kotlinApp.ui.theme.grey5Black
import org.jetbrains.kotlinApp.ui.theme.greyGrey20
import org.jetbrains.kotlinApp.ui.theme.title

@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    playerState: PlayerState,
    playbackState: PodcastPlaybackState, // Add playback state for proper state management
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onExpand: () -> Unit
) {
    // Use playback state values instead of local state to maintain consistency
    val availableSpeeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    Surface(
        color = MaterialTheme.colors.grey5Black,
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title and channel section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Episode title with null safety
                playerState.currentEpisode?.let { episode ->
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.h4.copy(
                            color = MaterialTheme.colors.title
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Channel title with null safety
                playerState.currentChannel?.let { channel ->
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.caption.copy(
                            color = MaterialTheme.colors.greyGrey20
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Controls section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Boost control
                IconButton(
                    onClick = { onBoostChange(!playbackState.isBoostEnabled) }
                ) {
                    Icon(
                        painter = if (playbackState.isBoostEnabled)
                            painterResource(Res.drawable.volume_high)
                        else painterResource(Res.drawable.volume_low),
                        contentDescription = "Volume Boost",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Rewind button
                IconButton(
                    onClick = { onSeek(playerState.currentPosition - 10000) }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.rewind_10),
                        contentDescription = "Rewind 10 seconds",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play/Pause button
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colors.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = if (isPlaying)
                            painterResource(Res.drawable.pause)
                        else painterResource(Res.drawable.play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Forward button
                IconButton(
                    onClick = { onSeek(playerState.currentPosition + 30000) }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.fast_forward_30),
                        contentDescription = "Forward 30 seconds",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Speed control
                Text(
                    text = "${playbackState.speed}x",
                    modifier = Modifier
                        .clickable {
                            val currentIndex =
                                availableSpeeds.indexOf(playbackState.speed.toFloat())
                            val nextIndex = (currentIndex + 1) % availableSpeeds.size
                            onSpeedChange(availableSpeeds[nextIndex])
                        }
                        .padding(8.dp),
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
