package org.jetbrains.kotlinApp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.back
import kotlinconfapp.shared.generated.resources.podcast
import kotlinconfapp.shared.generated.resources.talks
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.SessionCardView
import org.jetbrains.kotlinApp.Speaker
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.SearchField
import org.jetbrains.kotlinApp.ui.components.SearchSessionTags
import org.jetbrains.kotlinApp.ui.components.Tab
import org.jetbrains.kotlinApp.ui.components.TabBar
import org.jetbrains.kotlinApp.ui.components.TabButton
import org.jetbrains.kotlinApp.ui.components.Tag
import org.jetbrains.kotlinApp.ui.theme.blackWhite
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.grey5Black
import org.jetbrains.kotlinApp.ui.theme.grey80Grey20
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.orange
import org.jetbrains.kotlinApp.ui.theme.white
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinconf.GetAllChannelDetails

data class SessionSearchData(
    val id: String,
    val description: AnnotatedString,
    val tags: List<String>,
    val timeLine: String
)

data class SpeakerSearchData(
    val id: String,
    val description: AnnotatedString,
    val photoUrl: String,
)

data class PodcastChannelSearchData(
    val id: String,
    val description: AnnotatedString,
    val imageUrl: String?,
    val episodeCount: Long
)

@OptIn(ExperimentalResourceApi::class)
enum class SearchTab(override val title: StringResource) : Tab {
    TALKS(Res.string.talks),
    SPEAKERS(Res.string.podcast)
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SearchScreen(
    controller: AppController,
    sessions: List<SessionCardView>,
    podcasts: List<GetAllChannelDetails>
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchTab.TALKS) }
    val podcastsResults = podcasts.searchPodcastChannels(query)
    val tags = sessions.flatMap { it.tags }.distinct()
    val activeTags = remember { mutableStateListOf<String>() }
    val sessionResults = sessions.searchSessions(query, activeTags)

    Column(
        Modifier
            .background(MaterialTheme.colors.grey5Black)
            .fillMaxHeight()
    ) {
        Box {
            TabBar(
                SearchTab.entries,
                selectedTab, onSelect = {
                    selectedTab = it
                }
            )
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { controller.back() }) {
                    Icon(
                        painter = Res.drawable.back.painter(),
                        "Back",
                        tint = MaterialTheme.colors.greyGrey5
                    )
                }
            }
        }
        SearchField(query, onTextChange = { query = it })
        HDivider()
        if (selectedTab == SearchTab.TALKS) {
            SearchSessionTags(tags, activeTags, onClick = {
                if (it in activeTags) {
                    activeTags.remove(it)
                } else {
                    activeTags.add(it)
                }
            })
            HDivider()
        }

        SearchResults(
            selected = selectedTab,
            sessionResults,
            podcastsResults,
            activeTags,
            controller
        )
    }
}

@Composable
private fun List<SessionCardView>.searchSessions(
    query: String,
    activeTags: SnapshotStateList<String>
): List<SessionSearchData> {
    var result = this
    if (activeTags.isNotEmpty()) {
        result = result.filter { session -> session.tags.any { it in activeTags } }
    }
    return result.filterNot { it.isBreak || it.isParty || it.isLunch }
        .filter { session ->
            session.title.contains(query, ignoreCase = true) ||
                    session.description.contains(query, ignoreCase = true) ||
                    session.speakerLine.contains(query, ignoreCase = true)
        }.map { it.toSearchData(query) }
}

private fun List<Speaker>.searchSpeakers(query: String): List<SpeakerSearchData> =
    filter { speaker ->
        speaker.name.contains(query, ignoreCase = true) ||
                speaker.description.contains(query, ignoreCase = true)
    }.map { it.toSearchData(query) }

private fun List<GetAllChannelDetails>.searchPodcastChannels(
    query: String
): List<PodcastChannelSearchData> =
    filter { channel ->
        channel.title.contains(query, ignoreCase = true) ||
                channel.description.contains(query, ignoreCase = true) ||
                channel.author.contains(query, ignoreCase = true)
    }.map { it.toSearchData(query) }

private fun GetAllChannelDetails.toSearchData(
    query: String
): PodcastChannelSearchData = PodcastChannelSearchData(
    id = id.toString(),
    description = buildAnnotatedString {
        // Title with highlighting
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendWithQuery(title, query)
        }

        // Author with highlighting
        append(" by ")
        appendWithQuery(author, query)

        // Description with highlighting if it contains the query
        if (description.contains(query, ignoreCase = true)) {
            append(" / ")
            appendPartWithQuery(description, query)
        }
    },
    imageUrl = imageUrl,
    episodeCount = episodeCount
)

private fun Speaker.toSearchData(query: String): SpeakerSearchData = SpeakerSearchData(
    id = id,
    description = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendWithQuery(name, query)
        }
        append(" / ")
        appendWithQuery(position, query)

        if (description.isNotBlank() && description.contains(query, ignoreCase = true)) {
            append(" / ")
            appendPartWithQuery(description, query)
        }
    },
    photoUrl = photoUrl,
)

@Composable
private fun SessionCardView.toSearchData(query: String): SessionSearchData = SessionSearchData(
    id = id,
    description = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colors.blackWhite)) {
            appendWithQuery(title, query)
        }
        append(" / ")
        withStyle(SpanStyle(color = MaterialTheme.colors.grey80Grey20)) {
            appendWithQuery(speakerLine, query)
        }
        withStyle(SpanStyle(color = grey50)) {
            if (description.isNotBlank()) {
                append(" / ")
                appendPartWithQuery(description, query)
            }
        }
    },
    tags = tags,
    timeLine = timeLine
)

private fun AnnotatedString.Builder.appendWithQuery(value: String, query: String) {
    if (!value.contains(query, ignoreCase = true)) {
        append(value)
        return
    }

    val startIndex = value.indexOf(query, ignoreCase = true)
    val endIndex = startIndex + query.length

    append(value.substring(0, startIndex))
    pushStyle(SpanStyle(color = white, background = orange))
    append(value.substring(startIndex, endIndex))
    pop()
    append(value.substring(endIndex))
}

private fun AnnotatedString.Builder.appendPartWithQuery(value: String, query: String) {
    val value = value.replace('\n', ' ')
    val length: Int = minOf(150, value.length)
    if (!value.contains(query, ignoreCase = true)) {
        append(value.substring(0, length))
        return
    }

    val startIndex = value.indexOf(query, ignoreCase = true)
    val endIndex = startIndex + query.length

    val start = maxOf(0, startIndex - 75)
    val end = minOf(value.length, endIndex + 75)

    append("...")
    append(value.substring(start, startIndex))
    pushStyle(SpanStyle(color = white, background = orange))
    append(value.substring(startIndex, endIndex))
    pop()
    append(value.substring(endIndex, end))
    append("...")
}


@Composable
fun SearchTagSelector(selected: SearchTab, onClick: (SearchTab) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .fillMaxWidth()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
    ) {
        SearchTab.entries.forEach { entry ->
            TabButton(
                tab = entry,
                isSelected = entry == selected,
                onSelect = { onClick(entry) }
            )
        }
    }
}

@Composable
private fun SearchResults(
    selected: SearchTab,
    talks: List<SessionSearchData>,
    podcasts: List<PodcastChannelSearchData>, // Add this parameter
    activeTags: List<String>,
    controller: AppController
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        when (selected) {
            SearchTab.SPEAKERS -> items(podcasts) { podcast ->
                PodcastSearchResult(
                    imageUrl = podcast.imageUrl,
                    text = podcast.description,
                    episodeCount = podcast.episodeCount,
                    onClick = { controller.showPodcastScreen(podcast.id.toLong()) }
                )
            }
            SearchTab.TALKS -> items(talks) { session ->
                TalkSearchResult(
                    session.description,
                    tags = session.tags,
                    activeTags = activeTags,
                ) { controller.showSession(session.id) }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TalkSearchResult(
    text: AnnotatedString,
    tags: List<String>,
    activeTags: List<String>,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .clickable { onClick() }
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = text, style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                activeTags.forEach { tag ->
                    if (tag !in tags) return@forEach
                    Tag(
                        icon = null,
                        tag,
                        modifier = Modifier.padding(end = 4.dp),
                        isActive = true
                    )
                }
                tags.forEach { tag ->
                    if (tag in activeTags) return@forEach
                    Tag(
                        icon = null,
                        tag,
                        modifier = Modifier.padding(end = 4.dp),
                        isActive = false
                    )
                }
            }
        }
        HDivider()
    }
}

@Composable
private fun SpeakerSearchResult(
    photoUrl: String,
    text: AnnotatedString,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .clickable { onClick() }) {
        Row {
            AsyncImage(
                imageUrl = photoUrl,
                contentDescription = "avatar",
                modifier = Modifier.size(60.dp)
            )
            Column(Modifier.padding(16.dp)) {
                Text(text = text, style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(8.dp))
            }
        }
        HDivider()
    }
}

@Composable
private fun PodcastSearchResult(
    imageUrl: String?,
    text: AnnotatedString,
    episodeCount: Long,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .clickable { onClick() }
    ) {
        Row {
            AsyncImage(
                imageUrl = imageUrl ?: "",
                contentDescription = "Podcast Cover",
                modifier = Modifier.size(60.dp)
            )
            Column(
                Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.body2
                )
//                Text(
//                    text = "By ${channel.author}",
//                    style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.greyGrey5),
//                    maxLines = 2
//                )
                Text(
                    text = "$episodeCount episodes",
                    style = MaterialTheme.typography.caption.copy(color = grey50),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        HDivider()
    }
}