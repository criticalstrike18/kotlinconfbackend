@file:OptIn(ExperimentalLayoutApi::class)

package org.jetbrains.kotlinApp.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.back
import kotlinconfapp.shared.generated.resources.episode
import kotlinconfapp.shared.generated.resources.podcast
import kotlinconfapp.shared.generated.resources.session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.EpisodeSearchItem
import org.jetbrains.kotlinApp.PodcastChannelSearchItem
import org.jetbrains.kotlinApp.SessionSearchItem
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.SearchField
import org.jetbrains.kotlinApp.ui.components.SearchSessionTags
import org.jetbrains.kotlinApp.ui.components.Tab
import org.jetbrains.kotlinApp.ui.components.TabBar
import org.jetbrains.kotlinApp.ui.components.Tag
import org.jetbrains.kotlinApp.ui.theme.blackWhite
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.grey5Black
import org.jetbrains.kotlinApp.ui.theme.grey80Grey20
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.orange
import org.jetbrains.kotlinApp.ui.theme.white
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    back: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchTab.TALKS) }
    val coroutineScope = rememberCoroutineScope()

    // Separate loading states for each tab
    var isLoadingTalks by remember { mutableStateOf(false) }
    var isLoadingPodcasts by remember { mutableStateOf(false) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // Pagination state
    var hasMoreResults by remember { mutableStateOf(true) }
    var currentCursor by remember { mutableStateOf<String?>(null) }
    var prevCursor by remember { mutableStateOf<String?>(null) }

    // Search job for cancellation
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Results state
    var talkResults by remember { mutableStateOf<List<SessionSearchItem>>(emptyList()) }
    var podcastResults by remember { mutableStateOf<List<PodcastChannelSearchItem>>(emptyList()) }
    var episodeResults by remember { mutableStateOf<List<EpisodeSearchItem>>(emptyList()) }

    // Tag management
    val talkTags = remember { mutableStateListOf<String>() }
    val podcastTags = remember { mutableStateListOf<String>() }
    val episodeTags = remember { mutableStateListOf<String>() }
    val activeTalkTags = remember { mutableStateListOf<String>() }
    val activePodcastTags = remember { mutableStateListOf<String>() }
    val activeEpisodeTags = remember { mutableStateListOf<String>() }

    // Scroll state
    val listState = rememberLazyListState()

    // Track if we need to load more on scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = layoutInfo.totalItemsCount

            hasMoreResults && !isLoadingEpisodes && !isLoadingPodcasts && !isLoadingTalks &&
                    currentCursor != null && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    // Load more results function
    val loadMoreResults = { tab: SearchTab, cursor: String? ->
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            when (tab) {
                SearchTab.TALKS -> isLoadingTalks = true
                SearchTab.PODCASTS -> isLoadingPodcasts = true
                SearchTab.EPISODES -> isLoadingEpisodes = true
            }

            try {
                val activeTags = when (tab) {
                    SearchTab.TALKS -> activeTalkTags.toList()
                    SearchTab.PODCASTS -> activePodcastTags.toList()
                    SearchTab.EPISODES -> activeEpisodeTags.toList()
                }

                val result = controller.service.searchContent(
                    query = query,
                    searchTab = tab,
                    activeTags = activeTags,
                    cursor = cursor,
                    limit = 20,
                    backward = false
                )

                hasMoreResults = result.hasMore
                currentCursor = result.nextCursor

                when (tab) {
                    SearchTab.TALKS -> {
                        val newItems = result.items as List<SessionSearchItem>
                        talkResults = talkResults + newItems
                    }
                    SearchTab.PODCASTS -> {
                        val newItems = result.items as List<PodcastChannelSearchItem>
                        podcastResults = podcastResults + newItems
                    }
                    SearchTab.EPISODES -> {
                        val newItems = result.items as List<EpisodeSearchItem>
                        episodeResults = episodeResults + newItems
                    }
                }
            } catch (e: Exception) {
                println("Error loading more results: ${e.message}")
            } finally {
                when (tab) {
                    SearchTab.TALKS -> isLoadingTalks = false
                    SearchTab.PODCASTS -> isLoadingPodcasts = false
                    SearchTab.EPISODES -> isLoadingEpisodes = false
                }
            }
        }
    }

    // Load more when scrolling near the end
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadMoreResults(selectedTab, currentCursor)
        }
    }

    // Perform search with debounce
    val performSearch = { tab: SearchTab ->
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            when (tab) {
                SearchTab.TALKS -> isLoadingTalks = true
                SearchTab.PODCASTS -> isLoadingPodcasts = true
                SearchTab.EPISODES -> isLoadingEpisodes = true
            }

            // Reset cursors and clear results when starting a new search
            currentCursor = null
            prevCursor = null

            when (tab) {
                SearchTab.TALKS -> talkResults = emptyList()
                SearchTab.PODCASTS -> podcastResults = emptyList()
                SearchTab.EPISODES -> episodeResults = emptyList()
            }

            delay(300) // Debounce

            try {
                val activeTags = when (tab) {
                    SearchTab.TALKS -> activeTalkTags.toList()
                    SearchTab.PODCASTS -> activePodcastTags.toList()
                    SearchTab.EPISODES -> activeEpisodeTags.toList()
                }

                println("Starting search for $tab with query='$query', tags=$activeTags")

                val result = controller.service.searchContent(
                    query = query,
                    searchTab = tab,
                    activeTags = activeTags,
                    cursor = null,
                    limit = 20
                )

                hasMoreResults = result.hasMore
                currentCursor = result.nextCursor
                prevCursor = result.prevCursor

                when (tab) {
                    SearchTab.TALKS -> talkResults = result.items as List<SessionSearchItem>
                    SearchTab.PODCASTS -> podcastResults = result.items as List<PodcastChannelSearchItem>
                    SearchTab.EPISODES -> episodeResults = result.items as List<EpisodeSearchItem>
                }
            } catch (e: Exception) {
                println("Search error: ${e.message}")
                e.printStackTrace()
            } finally {
                when (tab) {
                    SearchTab.TALKS -> isLoadingTalks = false
                    SearchTab.PODCASTS -> isLoadingPodcasts = false
                    SearchTab.EPISODES -> isLoadingEpisodes = false
                }
            }
        }
    }

    // Load tags and initial data on tab change
    LaunchedEffect(selectedTab) {
        // Load tags for the selected tab
        when (selectedTab) {
            SearchTab.TALKS -> {
                if (talkTags.isEmpty()) {
                    try {
                        val tags = controller.service.getSessionTags()
                        talkTags.clear()
                        talkTags.addAll(tags)
                    } catch (e: Exception) {
                        println("Error loading talk tags: ${e.message}")
                    }
                }
            }
            SearchTab.PODCASTS -> {
                if (podcastTags.isEmpty()) {
                    try {
                        val tags = controller.service.getChannelTags()
                        podcastTags.clear()
                        podcastTags.addAll(tags)
                    } catch (e: Exception) {
                        println("Error loading podcast tags: ${e.message}")
                    }
                }
            }
            SearchTab.EPISODES -> {
                if (episodeTags.isEmpty()) {
                    try {
                        val tags = controller.service.getEpisodeTags()
                        episodeTags.clear()
                        episodeTags.addAll(tags)
                    } catch (e: Exception) {
                        println("Error loading episode tags: ${e.message}")
                    }
                }
            }
        }

        // Perform initial search if needed
        if ((selectedTab == SearchTab.TALKS && talkResults.isEmpty() && !isLoadingTalks) ||
            (selectedTab == SearchTab.PODCASTS && podcastResults.isEmpty() && !isLoadingPodcasts) ||
            (selectedTab == SearchTab.EPISODES && episodeResults.isEmpty() && !isLoadingEpisodes)) {
            performSearch(selectedTab)
        }
    }

    // Perform search when query or active tags change
    LaunchedEffect(query, activeTalkTags.toList(), activePodcastTags.toList(), activeEpisodeTags.toList()) {
        performSearch(selectedTab)
    }

    // Main UI
    Column(
        Modifier
            .background(MaterialTheme.colors.grey5Black)
            .fillMaxHeight()
    ) {
        Box {
            TabBar(SearchTab.entries, selectedTab, onSelect = { selectedTab = it })
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { back() }) {
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

        // Tags section
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

        // Results section with proper loading states
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SearchTab.TALKS -> {
                    if (isLoadingTalks && talkResults.isEmpty()) {
                        LoadingIndicator("Loading talks...")
                    } else if (talkResults.isEmpty()) {
                        EmptyResults("No talks found matching your criteria")
                    } else {
                        TalksResults(
                            talks = talkResults,
                            query = query,
                            activeTags = activeTalkTags,
                            listState = listState,
                            controller = controller
                        )
                    }
                }
                SearchTab.PODCASTS -> {
                    if (isLoadingPodcasts && podcastResults.isEmpty()) {
                        LoadingIndicator("Loading podcasts...")
                    } else if (podcastResults.isEmpty()) {
                        EmptyResults("No podcasts found matching your criteria")
                    } else {
                        PodcastsResults(
                            podcasts = podcastResults,
                            query = query,
                            activeTags = activePodcastTags,
                            listState = listState,
                            controller = controller
                        )
                    }
                }
                SearchTab.EPISODES -> {
                    if (isLoadingEpisodes && episodeResults.isEmpty()) {
                        LoadingIndicator("Loading episodes...")
                    } else if (episodeResults.isEmpty()) {
                        EmptyResults("No episodes found matching your criteria")
                    } else {
                        EpisodesResults(
                            episodes = episodeResults,
                            query = query,
                            activeTags = activeEpisodeTags,
                            listState = listState,
                            controller = controller
                        )
                    }
                }
            }

            // Bottom loading indicator
            this@Column.AnimatedVisibility(
                visible = (selectedTab == SearchTab.TALKS && isLoadingTalks && talkResults.isNotEmpty()) ||
                        (selectedTab == SearchTab.PODCASTS && isLoadingPodcasts && podcastResults.isNotEmpty()) ||
                        (selectedTab == SearchTab.EPISODES && isLoadingEpisodes && episodeResults.isNotEmpty()),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(text = message)
        }
    }
}

@Composable
private fun EmptyResults(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
        )
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
    textColor: Color,
    secondaryColor: Color,
    descriptionColor: Color
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

@Composable
private fun TalksResults(
    talks: List<SessionSearchItem>,
    query: String,
    activeTags: List<String>,
    listState: LazyListState,
    controller: AppController
) {
    val textColor = MaterialTheme.colors.blackWhite
    val secondaryColor = MaterialTheme.colors.grey80Grey20
    val descriptionColor = grey50

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            items = talks,
            key = { it.id }
        ) { talk ->
            val uiModel = talk.toUIModel(query, textColor, secondaryColor, descriptionColor)
            TalkSearchResult(
                text = uiModel.description,
                tags = uiModel.tags,
                activeTags = activeTags
            ) { controller.showSession(uiModel.id) }
        }
    }
}

@Composable
private fun PodcastsResults(
    podcasts: List<PodcastChannelSearchItem>,
    query: String,
    activeTags: List<String>,
    listState: LazyListState,
    controller: AppController
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            items = podcasts,
            key = { it.id }
        ) { podcast ->
            val uiModel = podcast.toUIModel(query)
            PodcastSearchResult(
                imageUrl = uiModel.imageUrl,
                text = uiModel.description,
                episodeCount = uiModel.episodeCount,
                tags = uiModel.categories,
                activeTags = activeTags
            ) { controller.showPodcastScreen(uiModel.id.toLong()) }
        }
    }
}

@Composable
private fun EpisodesResults(
    episodes: List<EpisodeSearchItem>,
    query: String,
    activeTags: List<String>,
    listState: LazyListState,
    controller: AppController
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            items = episodes,
            key = { it.id }
        ) { episode ->
            EpisodeListItem(
                episode = episode,
                query = query,
                activeTags = activeTags,
                controller = controller
            )
        }
    }
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

@Composable
private fun EpisodeListItem(
    episode: EpisodeSearchItem,
    query: String,
    activeTags: List<String>,
    controller: AppController
) {
    Column(
        Modifier
            .background(MaterialTheme.colors.whiteGrey)
            .clickable { controller.showPodcastScreen(episode.channelId.toLong()) }
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image with fixed size to prevent recomposition
            AsyncImage(
                imageUrl = episode.imageUrl ?: "",
                contentDescription = "Episode Cover",
                modifier = Modifier.size(60.dp)
            )

            // Main content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Episode title with minimal highlighting
                Text(
                    text = highlightQuery(episode.title, query),
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Channel name
                Text(
                    text = "From: ${episode.channelTitle}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Date and duration
                Text(
                    text = "${formatDate(episode.pubDate)} • ${formatDuration(episode.duration)}",
                    style = MaterialTheme.typography.caption,
                    color = grey50,
                    maxLines = 1
                )

                // Only show tags that are active, and limit to 3
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val tagsToShow = activeTags.filter { it in episode.categories }.take(3)
                    tagsToShow.forEach { tag ->
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 4.dp)
                                .height(24.dp),
                            isActive = true
                        )
                    }
                }
            }
        }
        HDivider()
    }
}


private fun formatDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

// Helper function for query highlighting
@Composable
private fun highlightQuery(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true))
        return AnnotatedString(text)

    return buildAnnotatedString {
        val startIndex = text.indexOf(query, ignoreCase = true)
        val endIndex = startIndex + query.length

        append(text.substring(0, startIndex))
        withStyle(SpanStyle(background = MaterialTheme.colors.primary.copy(alpha = 0.3f))) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}
