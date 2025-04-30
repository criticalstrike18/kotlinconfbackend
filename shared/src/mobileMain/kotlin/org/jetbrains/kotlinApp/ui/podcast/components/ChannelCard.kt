package org.jetbrains.kotlinApp.ui.podcast.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.kotlinApp.ui.HDivider
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.Tag
import org.jetbrains.kotlinApp.ui.podcast.formatDateForChannelScreen
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.greyWhite
import org.jetbrains.kotlinApp.ui.theme.orange
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinconf.GetAllChannelDetails

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
fun ChannelCard(
    channel: GetAllChannelDetails,
    query: String = "",
    activeTags: List<String> = emptyList(),
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.whiteGrey)
            .padding(0.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.whiteGrey)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    // Channel image
                    AsyncImage(
                        imageUrl = channel.imageUrl,
                        contentDescription = "Podcast Cover",
                        modifier = Modifier.size(60.dp),
                    )

                    // Title, author, description
                    Column(
                        Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    ) {
                        // Title with highlighting
                        Text(
                            text = highlightText(channel.title, query),
                            style = MaterialTheme.typography.h6.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.greyWhite
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Author with highlighting
                        Text(
                            text = highlightText("by ${channel.author ?: "Unknown"}", query),
                            style = MaterialTheme.typography.body2.copy(
                                color = MaterialTheme.colors.greyGrey5
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Episode count and date range
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${channel.episodeCount ?: 0} episodes",
                                style = MaterialTheme.typography.caption.copy(color = grey50),
                            )
                            if (channel.earliestEpisodePubDate != null && channel.latestEpisodePubDate != null) {
                                Text(
                                    text = "${formatDateForChannelScreen(channel.earliestEpisodePubDate)} - ${formatDateForChannelScreen(channel.latestEpisodePubDate)}",
                                    style = MaterialTheme.typography.caption.copy(color = grey50),
                                )
                            }
                        }

                        // Description with highlighting (truncated)
                        if (channel.description.isNotBlank()) {
                            if (query.isNotBlank() && channel.description.contains(query, ignoreCase = true)) {
                                // Get context around the match
                                val index = channel.description.indexOf(query, ignoreCase = true)
                                val start = maxOf(0, index - 40)
                                val end = minOf(channel.description.length, index + query.length + 40)
                                val excerpt = if (start > 0) "..." else "" +
                                        channel.description.substring(start, end) +
                                        if (end < channel.description.length) "..." else ""

                                Text(
                                    text = highlightText(excerpt, query),
                                    style = MaterialTheme.typography.caption.copy(
                                        color = MaterialTheme.colors.greyGrey5.copy(alpha = 0.7f)
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                // Just show first part of description
                                Text(
                                    text = channel.description.take(80) + if (channel.description.length > 80) "..." else "",
                                    style = MaterialTheme.typography.caption.copy(
                                        color = MaterialTheme.colors.greyGrey5.copy(alpha = 0.7f)
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Categories (tags)
                val categories = channel.categories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (categories.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        // Show active tags first
                        val activeCategories = categories.filter { it in activeTags }
                        val inactiveCategories = categories.filter { it !in activeTags }.take(3)

                        activeCategories.forEach { tag ->
                            Tag(
                                icon = null,
                                text = tag,
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                                isActive = true
                            )
                        }

                        inactiveCategories.forEach { tag ->
                            Tag(
                                icon = null,
                                text = tag,
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                                isActive = false
                            )
                        }

                        if (categories.size > activeCategories.size + inactiveCategories.size) {
                            Text(
                                text = "+${categories.size - activeCategories.size - inactiveCategories.size} more",
                                style = MaterialTheme.typography.caption.copy(color = grey50),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            HDivider()
        }
    }
}

@Composable
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        val startIndex = text.indexOf(query, ignoreCase = true)
        val endIndex = startIndex + query.length

        append(text.substring(0, startIndex))
        withStyle(SpanStyle(background = orange)) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}