package org.jetbrains.kotlinconf.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import org.jetbrains.kotlinconf.podcast.PlayerState
import org.jetbrains.kotlinconf.ui.components.AsyncImage
import org.jetbrains.kotlinconf.ui.theme.blackWhite

@Composable
fun FullScreenPlayer(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onMinimize: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableSpeeds = listOf(0.5f, 1.0f, 1.2f, 1.5f, 2.0f)
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var boostEnabled by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(playerState.currentPosition.toFloat()) }

    // Keep slider in sync with player position
    LaunchedEffect(playerState.currentPosition) {
        sliderPosition = playerState.currentPosition.toFloat()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Top Bar
        TopAppBar{
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize"
                )
            }

        }

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Artwork
            playerState.currentEpisode?.let { episode ->
                AsyncImage(
                    modifier = Modifier
                        .padding(vertical = 32.dp)
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    imageUrl = episode.imageUrl ?: "",
                    contentDescription = "Episode Artwork"
                )

                Text(
                    text = episode.title.trim(),
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.blackWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Progress Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Slider
                val duration = if (playerState.duration > 0) playerState.duration else 1L

                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp), // Adds left and right padding
                )

                // Time Display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderPosition.toLong()),
                        style = MaterialTheme.typography.caption,
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.caption,
                    )
                }
            }

            // Controls Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Speed and Volume Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            boostEnabled = !boostEnabled
                            onBoostChange(boostEnabled)
                        }
                    ) {
                        Icon(
                            painter = if (boostEnabled) painterResource(Res.drawable.volume_high) else painterResource(
                                Res.drawable.volume_low),
                            contentDescription = "Volume Boost",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Rewind 10 seconds
                    IconButton(
                        onClick = { onSeek(playerState.currentPosition - 10000) }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.rewind_10),
                            contentDescription = "Rewind 10 seconds",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause Button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colors.primary,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = if (playerState.isPlaying)
                                painterResource(Res.drawable.pause) else painterResource(Res.drawable.play),
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Forward 30 seconds
                    IconButton(
                        onClick = { onSeek(playerState.currentPosition + 30000) }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.fast_forward_30),
                            contentDescription = "Forward 30 seconds",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "${playbackSpeed}x",
                        modifier = Modifier
                            .clickable {
                                val currentIndex = availableSpeeds.indexOf(playbackSpeed)
                                val nextIndex = (currentIndex + 1) % availableSpeeds.size
                                playbackSpeed = availableSpeeds[nextIndex]
                                onSpeedChange(playbackSpeed)
                            }
                            .padding(8.dp),
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}