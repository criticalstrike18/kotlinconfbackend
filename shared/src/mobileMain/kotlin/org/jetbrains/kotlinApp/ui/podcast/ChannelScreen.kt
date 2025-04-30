package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.back
import kotlinconfapp.shared.generated.resources.search
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.PodcastChannelSearchItem
import org.jetbrains.kotlinApp.podcast.PlayerState
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.ui.HDivider
import org.jetbrains.kotlinApp.ui.SearchTab
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.components.SearchField
import org.jetbrains.kotlinApp.ui.components.SearchSessionTags
import org.jetbrains.kotlinApp.ui.podcast.components.ChannelCard
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinconf.GetAllChannelDetails


sealed class PodcastNavigation {
    data object Channels : PodcastNavigation()
    data class Episodes(val channelId: Long) : PodcastNavigation()
}

internal fun formatDateForChannelScreen(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val twoDigitYear = dateTime.year % 100  // Extract last two digits of the year
    return "${dateTime.monthNumber}/$twoDigitYear"
}

@OptIn(FlowPreview::class)
@Composable
fun ChannelScreen(
    service: ConferenceService,
    playerState: PlayerState,
    playbackState: PodcastPlaybackState,
    onPlayPause: (PodcastEpisode) -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onNavigateToEpisodes: (Long) -> Unit,
    onExpandPlayer: () -> Unit
) {
    val listState = rememberLazyListState()
    val channels by service.podcastChannels.collectAsState()
    val channelsCursor by service.currentChannelsCursor.collectAsState()

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GetAllChannelDetails>>(emptyList()) }
    var isLoadingSearchResults by remember { mutableStateOf(false) }
    val activeTags = remember { mutableStateListOf<String>() }

    // Search pagination
    var searchCursor by remember { mutableStateOf<String?>(null) }
    var hasMoreSearchResults by remember { mutableStateOf(false) }

    // Load tags only once
    val availableTags = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        try {
            val tags = service.getAllChannelTags()
            availableTags.clear()
            availableTags.addAll(tags)
        } catch (e: Exception) {
            println("Error loading channel tags: ${e.message}")
        }
    }

    // Determine which channels to display
    val displayedChannels = if (isSearchActive && (query.isNotBlank() || activeTags.isNotEmpty())) {
        searchResults
    } else {
        channels
    }

    // Loading states
    var isLoadingMore by remember { mutableStateOf(false) }

    // Initial load
    LaunchedEffect(Unit) {
        if (channels.isEmpty()) {
            service.loadChannelsWithCursor(limit = 30)
        }
    }

    // Detect when we should load more data
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount

            lastVisibleItem >= totalItems - 3 &&
                    ((!isSearchActive && !isLoadingMore && channelsCursor.second != null) ||
                            (isSearchActive && !isLoadingSearchResults && hasMoreSearchResults))
        }
    }

    // Load more data when needed
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            if (isSearchActive) {
                if (searchCursor != null && !isLoadingSearchResults && hasMoreSearchResults) {
                    isLoadingSearchResults = true
                    try {
                        val result = service.searchContent(
                            query = query,
                            searchTab = SearchTab.PODCASTS,
                            activeTags = activeTags.toList(),
                            cursor = searchCursor,
                            limit = 20
                        )

                        // Convert and append search results
                        @Suppress("UNCHECKED_CAST")
                        val items = result.items as List<PodcastChannelSearchItem>
                        val newChannels = items.map { item ->
                            GetAllChannelDetails(
                                id = item.id.toLong(),
                                title = item.title,
                                link = "",
                                description = item.description,
                                copyright = "",
                                language = "",
                                author = item.author,
                                ownerEmail = "",
                                ownerName = "",
                                imageUrl = item.imageUrl ?: "",
                                lastBuildDate = 0,
                                episodeCount = item.episodeCount,
                                earliestEpisodePubDate = null,
                                latestEpisodePubDate = null,
                                categories = item.categories.joinToString(",")
                            )
                        }

                        searchResults = searchResults + newChannels
                        searchCursor = result.nextCursor
                        hasMoreSearchResults = result.hasMore
                    } catch (e: Exception) {
                        println("Load more search error: ${e.message}")
                    } finally {
                        isLoadingSearchResults = false
                    }
                }
            } else {
                if (!isLoadingMore && channelsCursor.second != null) {
                    isLoadingMore = true
                    try {
                        service.loadChannelsWithCursor(
                            cursor = channelsCursor.second,
                            limit = 10
                        )
                    } finally {
                        isLoadingMore = false
                    }
                }
            }
        }
    }

    // Perform search when query or tags change
    LaunchedEffect(query, activeTags.toList()) {
        if (isSearchActive) {
            snapshotFlow { Pair(query, activeTags.toList()) }
                .debounce(300)
                .collect { (currentQuery, currentTags) ->
                    isLoadingSearchResults = true
                    searchResults = emptyList()
                    searchCursor = null

                    try {
                        val result = service.searchContent(
                            query = currentQuery,
                            searchTab = SearchTab.PODCASTS,
                            activeTags = currentTags,
                            cursor = null,
                            limit = 20
                        )

                        // Convert search results to channel format
                        @Suppress("UNCHECKED_CAST")
                        val items = result.items as List<PodcastChannelSearchItem>
                        searchResults = items.map { item ->
                            GetAllChannelDetails(
                                id = item.id.toLong(),
                                title = item.title,
                                link = "",
                                description = item.description,
                                copyright = "",
                                language = "",
                                author = item.author,
                                ownerEmail = "",
                                ownerName = "",
                                imageUrl = item.imageUrl ?: "",
                                lastBuildDate = 0,
                                episodeCount = item.episodeCount,
                                earliestEpisodePubDate = null,
                                latestEpisodePubDate = null,
                                categories = item.categories.joinToString(",")
                            )
                        }

                        searchCursor = result.nextCursor
                        hasMoreSearchResults = result.hasMore

                        // Scroll to top after search
                        listState.scrollToItem(0)
                    } catch (e: Exception) {
                        println("Search error: ${e.message}")
                    } finally {
                        isLoadingSearchResults = false
                    }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playerState.currentEpisode != null) 140.dp else 0.dp)
        ) {
            NavigationBar(
                title = "Podcast Channels",
                isLeftVisible = false,
                isRightVisible = true,
                rightIcon = if (isSearchActive) Res.drawable.back else Res.drawable.search,
                onRightClick = {
                    if (isSearchActive) {
                        isSearchActive = false
                        query = ""
                        activeTags.clear()
                        searchResults = emptyList()
                    } else {
                        isSearchActive = true
                    }
                }
            )

            // Search UI
            AnimatedVisibility(visible = isSearchActive) {
                Column {
                    SearchField(
                        text = query,
                        onTextChange = { query = it },
                    )

                    if (availableTags.isNotEmpty()) {
                        SearchSessionTags(
                            availableTags,
                            activeTags,
                            onClick = { tag ->
                                if (tag in activeTags) {
                                    activeTags.remove(tag)
                                } else {
                                    activeTags.add(tag)
                                }
                            }
                        )
                    }

                    HDivider()
                }
            }

            if (displayedChannels.isEmpty() && (isLoadingMore || isLoadingSearchResults)) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (displayedChannels.isEmpty() && isSearchActive) {
                // No search results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No channels found",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.background(MaterialTheme.colors.whiteGrey)
                ) {
                    // Channel items
                    items(
                        items = displayedChannels,
                        key = { channel -> "channel-${channel.id}" }
                    ) { channel ->
                        ChannelCard(
                            channel = channel,
                            query = query,
                            activeTags = activeTags,
                            onClick = {
                                // Make sure we load complete channel data before navigating
                                // This ensures proper display of channel details
                                service.ensureChannelLoaded(channel.id)
                                onNavigateToEpisodes(channel.id)
                            }
                        )
                    }

                    // Loading indicator at bottom
                    item(key = "bottom-loader") {
                        if ((isSearchActive && isLoadingSearchResults) ||
                            (!isSearchActive && isLoadingMore)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Mini player
        AnimatedVisibility(
            visible = playerState.currentEpisode != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                isPlaying = playerState.isPlaying,
                playerState = playerState,
                playbackState = playbackState,
                onPlayPause = { playerState.currentEpisode?.let(onPlayPause) },
                onExpand = onExpandPlayer,
                onSeek = onSeek,
                onSpeedChange = onSpeedChange,
                onBoostChange = onBoostChange
            )
        }
    }
}