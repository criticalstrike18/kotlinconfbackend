@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.back
import kotlinconfapp.shared.generated.resources.episode
import kotlinconfapp.shared.generated.resources.podcast
import kotlinconfapp.shared.generated.resources.session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.EpisodeSearchItem
import org.jetbrains.kotlinApp.PodcastChannelSearchItem
import org.jetbrains.kotlinApp.SessionCardView
import org.jetbrains.kotlinApp.SessionSearchItem
import org.jetbrains.kotlinApp.Speaker
import org.jetbrains.kotlinApp.podcast.PaginatedResult
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
import org.jetbrains.kotlinApp.ui.theme.greyGrey50
import org.jetbrains.kotlinApp.ui.theme.orange
import org.jetbrains.kotlinApp.ui.theme.white
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinconf.GetAllChannelDetails
import org.jetbrains.kotlinconf.GetAllEpisodesDetails

// Data classes remain unchanged
data class SessionSearchData(
    val id: String,
    val description: AnnotatedString,
    val tags: List<String>,
    val timeLine: String
)

data class PodcastChannelSearchData(
    val id: String,
    val description: AnnotatedString,
    val imageUrl: String?,
    val episodeCount: Long,
    val categories: List<String>
)

data class EpisodeSearchData(
    val id: String,
    val channelId: String,
    val description: AnnotatedString,
    val imageUrl: String?,
    val pubDate: Long,
    val duration: Long,
    val channelTitle: String,
    val categories: List<String>
)

enum class SearchTab(override val title: StringResource) : Tab {
    TALKS(Res.string.session),
    PODCASTS(Res.string.podcast),
    EPISODES(Res.string.episode);
}

@Composable
fun SearchScreen(
    controller: AppController,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchTab.TALKS) }
    val coroutineScope = rememberCoroutineScope()

    // Loading state
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var hasMoreResults by remember { mutableStateOf(true) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Results state using raw data types
    var talkResults by remember { mutableStateOf<List<SessionSearchItem>>(emptyList()) }
    var podcastResults by remember { mutableStateOf<List<PodcastChannelSearchItem>>(emptyList()) }
    var episodeResults by remember { mutableStateOf<List<EpisodeSearchItem>>(emptyList()) }

    // Tags state
    val talkTags = remember { mutableStateListOf<String>() }
    val podcastTags = remember { mutableStateListOf<String>() }
    val episodeTags = remember { mutableStateListOf<String>() }

    // Active tags for each type
    val activeTalkTags = remember { mutableStateListOf<String>() }
    val activePodcastTags = remember { mutableStateListOf<String>() }
    val activeEpisodeTags = remember { mutableStateListOf<String>() }

    // List state for infinite scrolling
    val listState = rememberLazyListState()
    val reachEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex > 0 && lastVisibleItemIndex >= totalItemsNumber - 2
        }
    }

    // Perform search with raw data
    val performSearch = {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            if (!isLoading) {
                isLoading = true
                currentPage = 0

                try {
                    val activeTags = when (selectedTab) {
                        SearchTab.TALKS -> activeTalkTags.toList()
                        SearchTab.PODCASTS -> activePodcastTags.toList()
                        SearchTab.EPISODES -> activeEpisodeTags.toList()
                    }

                    val result = controller.service.searchContent(
                        query = query,
                        searchTab = selectedTab,
                        activeTags = activeTags,
                        page = 0
                    )

                    hasMoreResults = result.hasMore
                    when (selectedTab) {
                        SearchTab.TALKS -> {
                            @Suppress("UNCHECKED_CAST")
                            talkResults = result.items as List<SessionSearchItem>
                        }
                        SearchTab.PODCASTS -> {
                            @Suppress("UNCHECKED_CAST")
                            podcastResults = result.items as List<PodcastChannelSearchItem>
                        }
                        SearchTab.EPISODES -> {
                            @Suppress("UNCHECKED_CAST")
                            episodeResults = result.items as List<EpisodeSearchItem>
                        }
                    }
                } catch (e: Exception) {
                    println("Search error: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Load more data with raw data
    val loadMoreData = {
        coroutineScope.launch {
            if (!isLoading && hasMoreResults) {
                isLoading = true
                try {
                    val activeTags = when (selectedTab) {
                        SearchTab.TALKS -> activeTalkTags.toList()
                        SearchTab.PODCASTS -> activePodcastTags.toList()
                        SearchTab.EPISODES -> activeEpisodeTags.toList()
                    }

                    val result = controller.service.searchContent(
                        query = query,
                        searchTab = selectedTab,
                        activeTags = activeTags,
                        page = currentPage
                    )

                    hasMoreResults = result.hasMore
                    when (selectedTab) {
                        SearchTab.TALKS -> {
                            @Suppress("UNCHECKED_CAST")
                            talkResults = talkResults + (result.items as List<SessionSearchItem>)
                        }
                        SearchTab.PODCASTS -> {
                            @Suppress("UNCHECKED_CAST")
                            podcastResults = podcastResults + (result.items as List<PodcastChannelSearchItem>)
                        }
                        SearchTab.EPISODES -> {
                            @Suppress("UNCHECKED_CAST")
                            episodeResults = episodeResults + (result.items as List<EpisodeSearchItem>)
                        }
                    }
                } catch (e: Exception) {
                    println("Load more error: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Infinite scrolling trigger
    LaunchedEffect(reachEnd) {
        if (reachEnd && hasMoreResults && !isLoading && when (selectedTab) {
                SearchTab.TALKS -> talkResults
                SearchTab.PODCASTS -> podcastResults
                SearchTab.EPISODES -> episodeResults
            }.isNotEmpty()
        ) {
            currentPage++
            loadMoreData()
        }
    }

    // Load tags and reset search on tab change
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            SearchTab.TALKS -> {
                if (talkTags.isEmpty()) {
                    val tags = controller.service.getSessionTags()
                    talkTags.clear()
                    talkTags.addAll(tags)
                }
            }
            SearchTab.PODCASTS -> {
                if (podcastTags.isEmpty()) {
                    val tags = controller.service.getChannelTags()
                    podcastTags.clear()
                    podcastTags.addAll(tags)
                }
            }
            SearchTab.EPISODES -> {
                if (episodeTags.isEmpty()) {
                    val tags = controller.service.getEpisodeTags()
                    episodeTags.clear()
                    episodeTags.addAll(tags)
                }
            }
        }
        performSearch()
    }

    // Debounced search on query or tag change
    LaunchedEffect(query, activeTalkTags.toList(), activePodcastTags.toList(), activeEpisodeTags.toList()) {
        delay(300)
        performSearch()
    }

    Column(
        Modifier
            .background(MaterialTheme.colors.grey5Black)
            .fillMaxHeight()
    ) {
        Box {
            TabBar(SearchTab.entries, selectedTab, onSelect = { selectedTab = it })
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
        when (selectedTab) {
            SearchTab.TALKS -> {
                SearchSessionTags(talkTags, activeTalkTags, onClick = {
                    if (it in activeTalkTags) activeTalkTags.remove(it) else activeTalkTags.add(it)
                })
                HDivider()
            }
            SearchTab.PODCASTS -> {
                SearchSessionTags(podcastTags, activePodcastTags, onClick = {
                    if (it in activePodcastTags) activePodcastTags.remove(it) else activePodcastTags.add(it)
                })
                HDivider()
            }
            SearchTab.EPISODES -> {
                SearchSessionTags(episodeTags, activeEpisodeTags, onClick = {
                    if (it in activeEpisodeTags) activeEpisodeTags.remove(it) else activeEpisodeTags.add(it)
                })
                HDivider()
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (isLoading && when (selectedTab) {
                    SearchTab.TALKS -> talkResults
                    SearchTab.PODCASTS -> podcastResults
                    SearchTab.EPISODES -> episodeResults
                }.isEmpty()
            ) {
                CircularProgressIndicator()
            } else {
                SearchResults(
                    query = query,
                    selected = selectedTab,
                    talks = talkResults,
                    podcasts = podcastResults,
                    episodes = episodeResults,
                    activeTalkTags = activeTalkTags,
                    activePodcastTags = activePodcastTags,
                    activeEpisodeTags = activeEpisodeTags,
                    controller = controller,
                    listState = listState
                )
                if (isLoading && when (selectedTab) {
                        SearchTab.TALKS -> talkResults
                        SearchTab.PODCASTS -> podcastResults
                        SearchTab.EPISODES -> episodeResults
                    }.isNotEmpty()
                ) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchResults(
    query: String,
    selected: SearchTab,
    talks: List<SessionSearchItem>,
    podcasts: List<PodcastChannelSearchItem>,
    episodes: List<EpisodeSearchItem>,
    activeTalkTags: List<String>,
    activePodcastTags: List<String>,
    activeEpisodeTags: List<String>,
    controller: AppController,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val textColor = MaterialTheme.colors.blackWhite
    val secondaryColor = MaterialTheme.colors.grey80Grey20
    val descriptionColor = grey50

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        when (selected) {
            SearchTab.PODCASTS -> items(podcasts) { podcast ->
                val uiModel = podcast.toUIModel(query)
                PodcastSearchResult(
                    imageUrl = uiModel.imageUrl,
                    text = uiModel.description,
                    episodeCount = uiModel.episodeCount,
                    tags = uiModel.categories,
                    activeTags = activePodcastTags
                ) { controller.showPodcastScreen(uiModel.id.toLong()) }
            }
            SearchTab.TALKS -> items(talks) { talk ->
                val uiModel = talk.toUIModel(query, textColor, secondaryColor, descriptionColor)
                TalkSearchResult(
                    text = uiModel.description,
                    tags = uiModel.tags,
                    activeTags = activeTalkTags
                ) { controller.showSession(uiModel.id) }
            }
            SearchTab.EPISODES -> items(episodes) { episode ->
                val uiModel = episode.toUIModel(query)
                EpisodeSearchResult(
                    imageUrl = uiModel.imageUrl,
                    text = uiModel.description,
                    pubDate = uiModel.pubDate,
                    duration = uiModel.duration,
                    tags = uiModel.categories,
                    activeTags = activeEpisodeTags
                ) { controller.showPodcastScreen(uiModel.channelId.toLong()) }
            }
        }
    }
}

// Assuming HDivider is defined elsewhere, if not, here's a simple implementation
@Composable
fun HDivider() {
    Spacer(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colors.greyGrey5)
    )
}

// Helper functions for query highlighting
internal fun AnnotatedString.Builder.appendWithQuery(value: String, query: String) {
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

internal fun AnnotatedString.Builder.appendPartWithQuery(value: String, query: String) {
    val value = value.replace('\n', ' ')
    val length = minOf(150, value.length)
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

// Conversion functions
fun SessionSearchItem.toUIModel(
    query: String,
    textColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    descriptionColor: androidx.compose.ui.graphics.Color
): SessionSearchData {
    return SessionSearchData(
        id = id,
        description = buildAnnotatedString {
            withStyle(SpanStyle(color = textColor)) {
                appendWithQuery(title, query)
            }
            append(" / ")
            withStyle(SpanStyle(color = secondaryColor)) {
                appendWithQuery(speakerLine, query)
            }
            withStyle(SpanStyle(color = descriptionColor)) {
                if (description.isNotBlank()) {
                    append(" / ")
                    appendPartWithQuery(description, query)
                }
            }
        },
        tags = tags,
        timeLine = timeLine
    )
}

fun PodcastChannelSearchItem.toUIModel(query: String): PodcastChannelSearchData {
    return PodcastChannelSearchData(
        id = id,
        description = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendWithQuery(title, query)
            }
            append(" by ")
            appendWithQuery(author, query)
            if (description.contains(query, ignoreCase = true)) {
                append(" / ")
                appendPartWithQuery(description, query)
            }
        },
        imageUrl = imageUrl,
        episodeCount = episodeCount,
        categories = categories
    )
}

fun EpisodeSearchItem.toUIModel(query: String): EpisodeSearchData {
    return EpisodeSearchData(
        id = id,
        channelId = channelId,
        description = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendWithQuery(title, query)
            }
            append(" from ")
            append(channelTitle)
            if (description.contains(query, ignoreCase = true)) {
                append(" / ")
                appendPartWithQuery(description, query)
            }
        },
        imageUrl = imageUrl,
        pubDate = pubDate,
        duration = duration,
        channelTitle = channelTitle,
        categories = categories
    )
}

// Existing TalkSearchResult, PodcastSearchResult, EpisodeSearchResult remain unchanged
@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodcastSearchResult(
    imageUrl: String?,
    text: AnnotatedString,
    episodeCount: Long,
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
            Row {
                AsyncImage(
                    imageUrl = imageUrl ?: "",
                    contentDescription = "Podcast Cover",
                    modifier = Modifier.size(60.dp)
                )
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "$episodeCount episodes",
                        style = MaterialTheme.typography.caption.copy(color = grey50),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow {
                    activeTags.forEach { tag ->
                        if (tag !in tags) return@forEach
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier.padding(end = 4.dp),
                            isActive = true
                        )
                    }
                    tags.forEach { tag ->
                        if (tag in activeTags) return@forEach
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier.padding(end = 4.dp),
                            isActive = false
                        )
                    }
                }
            }
        }
        HDivider()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeSearchResult(
    imageUrl: String?,
    text: AnnotatedString,
    pubDate: Long,
    duration: Long,
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
            Row {
                AsyncImage(
                    imageUrl = imageUrl ?: "",
                    contentDescription = "Episode Cover",
                    modifier = Modifier.size(60.dp)
                )
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "${formatDate(pubDate)} • ${formatDuration(duration)}",
                        style = MaterialTheme.typography.caption.copy(color = grey50),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow {
                    activeTags.forEach { tag ->
                        if (tag !in tags) return@forEach
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier.padding(end = 4.dp),
                            isActive = true
                        )
                    }
                    tags.forEach { tag ->
                        if (tag in activeTags) return@forEach
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier.padding(end = 4.dp),
                            isActive = false
                        )
                    }
                }
            }
        }
        HDivider()
    }
}

private fun formatDate(timestamp: Long): String {
    val date = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

// Helper functions for query highlighting
//internal fun AnnotatedString.Builder.appendWithQuery(value: String, query: String) {
//    if (!value.contains(query, ignoreCase = true)) {
//        append(value)
//        return
//    }
//
//    val startIndex = value.indexOf(query, ignoreCase = true)
//    val endIndex = startIndex + query.length
//
//    append(value.substring(0, startIndex))
//    pushStyle(SpanStyle(color = white, background = orange))
//    append(value.substring(startIndex, endIndex))
//    pop()
//    append(value.substring(endIndex))
//}
//
//internal fun AnnotatedString.Builder.appendPartWithQuery(value: String, query: String) {
//    val value = value.replace('\n', ' ')
//    val length: Int = minOf(150, value.length)
//    if (!value.contains(query, ignoreCase = true)) {
//        append(value.substring(0, length))
//        return
//    }
//
//    val startIndex = value.indexOf(query, ignoreCase = true)
//    val endIndex = startIndex + query.length
//
//    val start = maxOf(0, startIndex - 75)
//    val end = minOf(value.length, endIndex + 75)
//
//    append("...")
//    append(value.substring(start, startIndex))
//    pushStyle(SpanStyle(color = white, background = orange))
//    append(value.substring(startIndex, endIndex))
//    pop()
//    append(value.substring(endIndex, end))
//    append("...")
//}
//
//// UI EXTENSION FUNCTIONS (in SearchScreen.kt)
//
//// Convert raw data to UI representation when needed
//@Composable
//fun SessionSearchItem.toUIModel(query: String): SessionSearchData {
//    return SessionSearchData(
//        id = id,
//        description = buildAnnotatedString {
//            withStyle(SpanStyle(color = MaterialTheme.colors.blackWhite)) {
//                appendWithQuery(title, query)
//            }
//            append(" / ")
//            withStyle(SpanStyle(color = MaterialTheme.colors.grey80Grey20)) {
//                appendWithQuery(speakerLine, query)
//            }
//            withStyle(SpanStyle(color = grey50)) {
//                if (description.isNotBlank()) {
//                    append(" / ")
//                    appendPartWithQuery(description, query)
//                }
//            }
//        },
//        tags = tags,
//        timeLine = timeLine
//    )
//}
//
//@Composable
//fun PodcastChannelSearchItem.toUIModel(query: String): PodcastChannelSearchData {
//    return PodcastChannelSearchData(
//        id = id,
//        description = buildAnnotatedString {
//            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                appendWithQuery(title, query)
//            }
//            append(" by ")
//            appendWithQuery(author, query)
//            if (description.contains(query, ignoreCase = true)) {
//                append(" / ")
//                appendPartWithQuery(description, query)
//            }
//        },
//        imageUrl = imageUrl,
//        episodeCount = episodeCount,
//        categories = categories
//    )
//}
//
//@Composable
//fun EpisodeSearchItem.toUIModel(query: String): EpisodeSearchData {
//    return EpisodeSearchData(
//        id = id,
//        channelId = channelId,
//        description = buildAnnotatedString {
//            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                appendWithQuery(title, query)
//            }
//            append(" from ")
//            append(channelTitle)
//            if (description.contains(query, ignoreCase = true)) {
//                append(" / ")
//                appendPartWithQuery(description, query)
//            }
//        },
//        imageUrl = imageUrl,
//        pubDate = pubDate,
//        duration = duration,
//        channelTitle = channelTitle,
//        categories = categories
//    )
//}