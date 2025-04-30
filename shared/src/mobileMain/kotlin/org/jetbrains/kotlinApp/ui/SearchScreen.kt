@file:OptIn(ExperimentalLayoutApi::class)

package org.jetbrains.kotlinApp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.kotlinApp.AppController
import org.jetbrains.kotlinApp.EpisodeSearchItem
import org.jetbrains.kotlinApp.PodcastChannelSearchItem
import org.jetbrains.kotlinApp.SessionSearchItem
import org.jetbrains.kotlinApp.podcast.PodcastCacheManager
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

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    controller: AppController,
    back: () -> Unit
) {
    // OPTIMIZATION: Store queries per tab to avoid rerunning searches during tab switches
    var queries by remember { mutableStateOf(mapOf<SearchTab, String>()) }
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchTab.TALKS) }
    val coroutineScope = rememberCoroutineScope()
    val searchCacheState by controller.service.searchCacheState.collectAsState()

    // Separate loading states for each tab
    var isLoadingTalks by remember { mutableStateOf(false) }
    var isLoadingPodcasts by remember { mutableStateOf(false) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // OPTIMIZATION: Track pagination state separately for each tab
    val cursors = remember { mutableStateMapOf<SearchTab, String?>() }
    val hasMoreMap = remember { mutableStateMapOf<SearchTab, Boolean>() }

    // OPTIMIZATION: Track if initial load has been done for each tab
    val initialLoadDone = remember { mutableStateMapOf<SearchTab, Boolean>() }

    // Search job for cancellation (per tab)
    val searchJobs = remember { mutableStateMapOf<SearchTab, Job?>() }

    // OPTIMIZATION: Preserve results state across tab switches
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

    // OPTIMIZATION: Maintain separate scroll states for each tab to preserve position
    val talksListState = rememberLazyListState()
    val podcastsListState = rememberLazyListState()
    val episodesListState = rememberLazyListState()

// Get the current list state based on selected tab
    val listState = when (selectedTab) {
        SearchTab.TALKS -> talksListState
        SearchTab.PODCASTS -> podcastsListState
        SearchTab.EPISODES -> episodesListState
    }

    // Track if we need to load more on scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = layoutInfo.totalItemsCount
            val hasMore = hasMoreMap[selectedTab] ?: false
            val isLoading = when(selectedTab) {
                SearchTab.TALKS -> isLoadingTalks
                SearchTab.PODCASTS -> isLoadingPodcasts
                SearchTab.EPISODES -> isLoadingEpisodes
            }

            hasMore && !isLoading &&
                    cursors[selectedTab] != null &&
                    lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    // OPTIMIZATION: Initialize tag loading in parallel
    LaunchedEffect(Unit) {
        // Initial load of tags from cache (non-blocking)
        val initialPodcastTags = controller.service.getCachedChannelTags()
        val initialEpisodeTags = controller.service.getCachedEpisodeTags()

        if (initialPodcastTags.isNotEmpty()) {
            podcastTags.addAll(initialPodcastTags)
        }

        if (initialEpisodeTags.isNotEmpty()) {
            episodeTags.addAll(initialEpisodeTags)
        }

        // Load tags in the background if needed
        launch {
            if (podcastTags.isEmpty()) {
                val tags = controller.service.getChannelTagsFromCache()
                withContext(Dispatchers.Main) {
                    podcastTags.clear()
                    podcastTags.addAll(tags)
                }
            }
        }

        launch {
            if (episodeTags.isEmpty()) {
                val tags = controller.service.getEpisodeTagsFromCache()
                withContext(Dispatchers.Main) {
                    episodeTags.clear()
                    episodeTags.addAll(tags)
                }
            }
        }

        // Original talk tags loading
        launch {
            if (talkTags.isEmpty()) {
                val tags = controller.service.getSessionTags()
                withContext(Dispatchers.Main) {
                    talkTags.clear()
                    talkTags.addAll(tags)
                }
            }
        }
    }

    // OPTIMIZATION: Detect and load more content when scrolling near the end
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .debounce(200) // Debounce to prevent multiple rapid loads
            .collect { shouldLoad ->
                if (shouldLoad) {
                    loadMoreResults(
                        selectedTab = selectedTab,
                        cursor = cursors[selectedTab],
                        query = query,
                        activeTags = getActiveTags(selectedTab, activeTalkTags, activePodcastTags, activeEpisodeTags),
                        controller = controller,
                        onLoadStarted = {
                            when (selectedTab) {
                                SearchTab.TALKS -> isLoadingTalks = true
                                SearchTab.PODCASTS -> isLoadingPodcasts = true
                                SearchTab.EPISODES -> isLoadingEpisodes = true
                            }
                        },
                        onLoadCompleted = { result, nextCursor, hasMore ->
                            when (selectedTab) {
                                SearchTab.TALKS -> isLoadingTalks = false
                                SearchTab.PODCASTS -> isLoadingPodcasts = false
                                SearchTab.EPISODES -> isLoadingEpisodes = false
                            }
                            cursors[selectedTab] = nextCursor
                            hasMoreMap[selectedTab] = hasMore

                            // Append new results
                            when (selectedTab) {
                                SearchTab.TALKS -> {
                                    @Suppress("UNCHECKED_CAST")
                                    talkResults = talkResults + (result as List<SessionSearchItem>)
                                }
                                SearchTab.PODCASTS -> {
                                    @Suppress("UNCHECKED_CAST")
                                    podcastResults = podcastResults + (result as List<PodcastChannelSearchItem>)
                                }
                                SearchTab.EPISODES -> {
                                    @Suppress("UNCHECKED_CAST")
                                    episodeResults = episodeResults + (result as List<EpisodeSearchItem>)
                                }
                            }
                        }
                    )
                }
            }
    }

    // OPTIMIZATION: Handle tab change with proper state restoration
    LaunchedEffect(selectedTab) {
        // Restore query if we have one saved for this tab
        query = queries[selectedTab] ?: ""

        when (selectedTab) {
            SearchTab.PODCASTS -> {
                // Show loading state
                isLoadingPodcasts = true

                if (initialLoadDone[selectedTab] != true) {
                    // Try to use cached results first (non-blocking)
                    if (searchCacheState == PodcastCacheManager.CacheState.LOADED) {
                        val cachedResults = controller.service.getCachedChannelResults()
                        if (cachedResults.isNotEmpty()) {
                            podcastResults = cachedResults
                            cursors[selectedTab] = cachedResults.lastOrNull()?.id
                            hasMoreMap[selectedTab] = true
                            initialLoadDone[selectedTab] = true
                            isLoadingPodcasts = false
                        }
                    }

                    // If still not loaded or cache wasn't available, perform search
                    if (initialLoadDone[selectedTab] != true) {
                        performSearch(
                            tab = selectedTab,
                            query = query,
                            activeTags = activePodcastTags,
                            controller = controller,
                            searchJobs = searchJobs,
                            onLoadStarted = { /* already set loading state */ },
                            onLoadCompleted = { result, nextCursor, hasMore ->
                                isLoadingPodcasts = false
                                cursors[selectedTab] = nextCursor
                                hasMoreMap[selectedTab] = hasMore
                                initialLoadDone[selectedTab] = true
                                @Suppress("UNCHECKED_CAST")
                                podcastResults = result as List<PodcastChannelSearchItem>
                            }
                        )
                    }
                } else {
                    // Already loaded, just update state
                    isLoadingPodcasts = false
                }
            }

            SearchTab.EPISODES -> {
                // Show loading state
                isLoadingEpisodes = true

                if (initialLoadDone[selectedTab] != true) {
                    // Try to use cached results first (non-blocking)
                    if (searchCacheState == PodcastCacheManager.CacheState.LOADED) {
                        val cachedResults = controller.service.getCachedEpisodeResults()
                        if (cachedResults.isNotEmpty()) {
                            episodeResults = cachedResults
                            cursors[selectedTab] = cachedResults.lastOrNull()?.pubDate?.toString()
                            hasMoreMap[selectedTab] = true
                            initialLoadDone[selectedTab] = true
                            isLoadingEpisodes = false
                        }
                    }

                    // If still not loaded or cache wasn't available, perform search
                    if (initialLoadDone[selectedTab] != true) {
                        performSearch(
                            tab = selectedTab,
                            query = query,
                            activeTags = activeEpisodeTags,
                            controller = controller,
                            searchJobs = searchJobs,
                            onLoadStarted = { /* already set loading state */ },
                            onLoadCompleted = { result, nextCursor, hasMore ->
                                isLoadingEpisodes = false
                                cursors[selectedTab] = nextCursor
                                hasMoreMap[selectedTab] = hasMore
                                initialLoadDone[selectedTab] = true
                                @Suppress("UNCHECKED_CAST")
                                episodeResults = result as List<EpisodeSearchItem>
                            }
                        )
                    }
                } else {
                    // Already loaded, just update state
                    isLoadingEpisodes = false
                }
            }

            SearchTab.TALKS -> {
                // Keep original behavior for talks
                if (initialLoadDone[selectedTab] != true) {
                    performSearch(
                        tab = selectedTab,
                        query = query,
                        activeTags = activeTalkTags,
                        controller = controller,
                        searchJobs = searchJobs,
                        onLoadStarted = { isLoadingTalks = true },
                        onLoadCompleted = { result, nextCursor, hasMore ->
                            isLoadingTalks = false
                            cursors[selectedTab] = nextCursor
                            hasMoreMap[selectedTab] = hasMore
                            initialLoadDone[selectedTab] = true
                            @Suppress("UNCHECKED_CAST")
                            talkResults = result as List<SessionSearchItem>
                        }
                    )
                }
            }
        }
    }

    // OPTIMIZATION: Debounced query and tag change handling
    val performDebouncedSearch = debounce<Unit>(300, coroutineScope) {
        // Save query for this tab
        queries = queries + (selectedTab to query)

        performSearch(
            tab = selectedTab,
            query = query,
            activeTags = getActiveTags(selectedTab, activeTalkTags, activePodcastTags, activeEpisodeTags),
            controller = controller,
            searchJobs = searchJobs,
            onLoadStarted = {
                when (selectedTab) {
                    SearchTab.TALKS -> isLoadingTalks = true
                    SearchTab.PODCASTS -> isLoadingPodcasts = true
                    SearchTab.EPISODES -> isLoadingEpisodes = true
                }
            },
            onLoadCompleted = { result, nextCursor, hasMore ->
                when (selectedTab) {
                    SearchTab.TALKS -> isLoadingTalks = false
                    SearchTab.PODCASTS -> isLoadingPodcasts = false
                    SearchTab.EPISODES -> isLoadingEpisodes = false
                }
                cursors[selectedTab] = nextCursor
                hasMoreMap[selectedTab] = hasMore

                // Replace results on new search
                when (selectedTab) {
                    SearchTab.TALKS -> {
                        @Suppress("UNCHECKED_CAST")
                        talkResults = result as List<SessionSearchItem>
                    }
                    SearchTab.PODCASTS -> {
                        @Suppress("UNCHECKED_CAST")
                        podcastResults = result as List<PodcastChannelSearchItem>
                    }
                    SearchTab.EPISODES -> {
                        @Suppress("UNCHECKED_CAST")
                        episodeResults = result as List<EpisodeSearchItem>
                    }
                }
            }
        )
    }

    // OPTIMIZATION: Trigger search after query or tag changes
    LaunchedEffect(query, activeTalkTags.toList(), activePodcastTags.toList(), activeEpisodeTags.toList()) {
        if (initialLoadDone[selectedTab] == true) { // Only trigger if initial load is done
            performDebouncedSearch(Unit)
        }
    }

    // Cancel all search jobs when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            searchJobs.values.forEach { it?.cancel() }
        }
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
                        // OPTIMIZATION: Show skeleton loader instead of spinner
                        SkeletonLoader()
                    } else if (talkResults.isEmpty() && initialLoadDone[selectedTab] == true) {
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
                        // OPTIMIZATION: Show skeleton loader instead of spinner
                        SkeletonLoader()
                    } else if (podcastResults.isEmpty() && initialLoadDone[selectedTab] == true) {
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
                        // OPTIMIZATION: Show skeleton loader instead of spinner
                        SkeletonLoader()
                    } else if (episodeResults.isEmpty() && initialLoadDone[selectedTab] == true) {
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

// OPTIMIZATION: Get active tags for the selected tab
private fun getActiveTags(
    tab: SearchTab,
    activeTalkTags: List<String>,
    activePodcastTags: List<String>,
    activeEpisodeTags: List<String>
): List<String> {
    return when (tab) {
        SearchTab.TALKS -> activeTalkTags
        SearchTab.PODCASTS -> activePodcastTags
        SearchTab.EPISODES -> activeEpisodeTags
    }
}

// OPTIMIZATION: Function to perform search with proper error handling
private fun performSearch(
    tab: SearchTab,
    query: String,
    activeTags: List<String>,
    controller: AppController,
    searchJobs: MutableMap<SearchTab, Job?>,
    onLoadStarted: () -> Unit,
    onLoadCompleted: (List<Any>, String?, Boolean) -> Unit
) {
    // Cancel any existing search for this tab
    searchJobs[tab]?.cancel()

    // Start new search
    searchJobs[tab] = CoroutineScope(Dispatchers.IO).launch {
        try {
            withContext(Dispatchers.Main) { onLoadStarted() }

            // Clear results for tab
            val result = controller.service.searchContent(
                query = query,
                searchTab = tab,
                activeTags = activeTags,
                cursor = null, // Start a new search
                limit = 20
            )

            // Update with new results
            withContext(Dispatchers.Main) {
                @Suppress("UNCHECKED_CAST")
                onLoadCompleted(
                    result.items as List<Any>,
                    result.nextCursor,
                    result.hasMore
                )
            }
        } catch (e: Exception) {
            println("Search error for $tab: ${e.message}")
            withContext(Dispatchers.Main) {
                onLoadCompleted(emptyList(), null, false)
            }
        }
    }
}

// OPTIMIZATION: Function to load more results with pagination
private suspend fun loadMoreResults(
    selectedTab: SearchTab,
    cursor: String?,
    query: String,
    activeTags: List<String>,
    controller: AppController,
    onLoadStarted: () -> Unit,
    onLoadCompleted: (List<Any>, String?, Boolean) -> Unit
) {
    try {
        onLoadStarted()

        val result = controller.service.searchContent(
            query = query,
            searchTab = selectedTab,
            activeTags = activeTags,
            cursor = cursor,
            limit = 20,
            backward = false
        )

        // Update with new results
        @Suppress("UNCHECKED_CAST")
        onLoadCompleted(
            result.items as List<Any>,
            result.nextCursor,
            result.hasMore
        )
    } catch (e: Exception) {
        println("Error loading more results: ${e.message}")
        onLoadCompleted(emptyList(), null, false)
    }
}


// OPTIMIZATION: Improved skeleton loader with animation
@Composable
private fun SkeletonLoader() {
    // Create shimmer animation
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton"
    )

    // Create shimmer brush
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colors.onSurface.copy(alpha = alpha),
            MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
            MaterialTheme.colors.onSurface.copy(alpha = alpha)
        )
    )

    LazyColumn {
        repeat(8) {
            item(key = "skeleton-$it") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Image skeleton
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )

                    // Content skeleton
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title skeleton
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )

                        // Subtitle skeleton
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )

                        // Content skeleton
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
                HDivider()
            }
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

// Helper functions for query highlighting
internal fun AnnotatedString.Builder.appendWithQuery(value: String, query: String) {
    if (query.isBlank() || !value.contains(query, ignoreCase = true)) {
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
    if (query.isBlank()) {
        // For blank queries, just show a short preview
        val length = minOf(75, value.length)  // Reduced from 150 to 75 for shorter preview
        append(value.substring(0, length))
        if (value.length > length) append("...")
        return
    }

    val processedValue = value.replace('\n', ' ')
    if (!processedValue.contains(query, ignoreCase = true)) {
        // For text without the query, just show a short preview
        val length = minOf(75, processedValue.length)  // Reduced from 150 to 75
        append(processedValue.substring(0, length))
        if (processedValue.length > length) append("...")
        return
    }

    val startIndex = processedValue.indexOf(query, ignoreCase = true)
    val endIndex = startIndex + query.length

    // Use shorter context around the match - reduce from 75 to 40 characters
    val start = maxOf(0, startIndex - 40)
    val end = minOf(processedValue.length, endIndex + 40)

    // Add ellipses at start if needed
    if (start > 0) append("...")

    append(processedValue.substring(start, startIndex))
    pushStyle(SpanStyle(color = white, background = orange))
    append(processedValue.substring(startIndex, endIndex))
    pop()

    // Limit the amount of text after the match
    val visibleAfterMatch = minOf(40, processedValue.length - endIndex)
    append(processedValue.substring(endIndex, endIndex + visibleAfterMatch))

    // Add ellipses at end if needed
    if (endIndex + visibleAfterMatch < processedValue.length) append("...")
}

// OPTIMIZATION: Efficient debounce function
fun <T> debounce(
    waitMs: Long = 300L,
    scope: CoroutineScope,
    destinationFunction: suspend (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
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

// OPTIMIZATION: More efficient item rendering
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
                // OPTIMIZATION: Fixed-size image with placeholder
                AsyncImage(
                    imageUrl = imageUrl ?: "",
                    contentDescription = "Podcast Cover",
                    modifier = Modifier.size(60.dp),
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
                    // OPTIMIZATION: Show only active tags first, then up to 3 inactive tags
                    val activeTagsToShow = activeTags.filter { it in tags }
                    activeTagsToShow.forEach { tag ->
                        Tag(
                            icon = null,
                            text = tag,
                            modifier = Modifier.padding(end = 4.dp),
                            isActive = true
                        )
                    }

                    val inactiveTags = tags.filter { it !in activeTags }.take(3)
                    inactiveTags.forEach { tag ->
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
                modifier = Modifier.size(60.dp),
            )

            // Main content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // OPTIMIZATION: More efficient highlighting with caching
                val highlightedTitle = remember(episode.title, query) {
                    highlightQueryText(episode.title, query)
                }

                Text(
                    text = highlightedTitle,
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

// OPTIMIZATION: More efficient query highlighting that doesn't rely on Composable context
private fun highlightQueryText(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true))
        return AnnotatedString(text)

    return buildAnnotatedString {
        val startIndex = text.indexOf(query, ignoreCase = true)
        val endIndex = startIndex + query.length

        append(text.substring(0, startIndex))
        withStyle(SpanStyle(background = orange.copy(alpha = 0.3f))) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}
