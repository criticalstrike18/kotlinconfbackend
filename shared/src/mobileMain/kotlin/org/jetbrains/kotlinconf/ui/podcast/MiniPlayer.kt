package org.jetbrains.kotlinconf.ui.podcast

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
import kotlinconfapp.shared.generated.resources.pause
import kotlinconfapp.shared.generated.resources.play
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinconf.podcast.PodcastEpisode
import org.jetbrains.kotlinconf.ui.theme.blackWhite
import org.jetbrains.kotlinconf.ui.theme.grey5Black
import org.jetbrains.kotlinconf.ui.theme.greyGrey20
import org.jetbrains.kotlinconf.ui.theme.menuSelected

@Composable
fun MiniPlayer(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    Surface(
        color = MaterialTheme.colors.grey5Black,
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.blackWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDuration(episode.duration),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.greyGrey20
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colors.menuSelected,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = if (isPlaying)
                        painterResource(Res.drawable.pause)
                    else
                        painterResource(Res.drawable.play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colors.blackWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}