package org.jetbrains.kotlinApp.ui.podcast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinconfapp.shared.generated.resources.Res
import kotlinconfapp.shared.generated.resources.back
import kotlinconfapp.shared.generated.resources.pause
import kotlinconfapp.shared.generated.resources.play
import kotlinconfapp.shared.generated.resources.search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinApp.ConferenceService
import org.jetbrains.kotlinApp.podcast.PlayerState
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.ui.HDivider
import org.jetbrains.kotlinApp.ui.components.AsyncImage
import org.jetbrains.kotlinApp.ui.components.NavigationBar
import org.jetbrains.kotlinApp.ui.components.SearchField
import org.jetbrains.kotlinApp.ui.components.SearchSessionTags
import org.jetbrains.kotlinApp.ui.components.Tag
import org.jetbrains.kotlinApp.ui.theme.grey50
import org.jetbrains.kotlinApp.ui.theme.grey5Black
import org.jetbrains.kotlinApp.ui.theme.greyGrey20
import org.jetbrains.kotlinApp.ui.theme.greyGrey5
import org.jetbrains.kotlinApp.ui.theme.greyWhite
import org.jetbrains.kotlinApp.ui.theme.orange
import org.jetbrains.kotlinApp.ui.theme.whiteGrey
import org.jetbrains.kotlinApp.utils.Screen
import org.jetbrains.kotlinApp.utils.isTooWide
import org.jetbrains.kotlinconf.GetAllChannelDetails

private fun formatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.monthNumber}/${dateTime.dayOfMonth}/${dateTime.year}"
}

fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@OptIn(FlowPreview::class)
@Composable
fun PodcastScreen(
    service: ConferenceService,
    playerState: PlayerState,
    playbackState: PodcastPlaybackState,
    onPlayPause: (PodcastEpisode) -> Unit,
    channelId: Long,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onBackPress: () -> Unit,
    onExpandPlayer: () -> Unit
) {
    // Back press handler
    BackHandler(onBack = onBackPress)

    // State collection with loading state
    var isInitialLoading by remember { mutableStateOf(true) }
    var isContentReady by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Channel data with caching
    val channels by service.podcastChannels.collectAsState()
    val currentChannel = remember(channels, channelId) {
        channels.find { it.id == channelId }
    }

    // Episode data with search state
    val allEpisodes by service.currentChannelEpisodes.collectAsState()

    // Cached episode tags and mapping to improve performance
    val episodeTagsMap = remember { mutableMapOf<String, List<String>>() }
    val taggedEpisodesMap = remember { mutableMapOf<String, Set<String>>() }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val activeTags = remember { mutableStateListOf<String>() }
    var filteredEpisodes by remember(allEpisodes, query, activeTags.toList()) {
        mutableStateOf(allEpisodes)
    }

    // Pagination state
    val episodesCursor by service.currentEpisodesCursor.collectAsState()

    // Available tags for this channel
    val availableTags = remember { mutableStateListOf<String>() }

    // Clean up resources when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            episodeTagsMap.clear()
            taggedEpisodesMap.clear()
        }
    }

    // Initial data loading
    LaunchedEffect(channelId) {
        // Set initial loading state
        isInitialLoading = true
        isContentReady = false

        try {
            // Pre-load channel data (should be fast if already cached)
            service.ensureChannelLoaded(channelId)

            // Start loading episodes
            service.loadEpisodesForChannel(channelId)

            // Wait for initial data to be ready
            withContext(Dispatchers.Default) {
                var dataReady = false
                var timeoutCounter = 0

                while (!dataReady && timeoutCounter < 30) { // 3 second timeout max
                    delay(100)
                    timeoutCounter++

                    val episodes = service.currentChannelEpisodes.value
                    if (episodes.isNotEmpty() && currentChannel != null) {
                        // Both channel and episodes are loaded
                        dataReady = true
                    }
                }

                // Load tags in parallel once we have episodes
                if (dataReady) {
                    try {
                        // Get tags for this channel
                        val tags = service.getEpisodeTagsForChannel(channelId)
                        availableTags.clear()
                        availableTags.addAll(tags)

                        // Get all episode IDs and load their tags
                        val episodeIds = service.currentChannelEpisodes.value.map { it.id }
                        val batchTags = service.getEpisodeTagsForEpisodeBatch(episodeIds)

                        // Update tag maps
                        episodeTagsMap.clear()
                        taggedEpisodesMap.clear()

                        batchTags.forEach { (episodeId, tags) ->
                            episodeTagsMap[episodeId] = tags
                            tags.forEach { tag ->
                                taggedEpisodesMap[tag] = taggedEpisodesMap.getOrDefault(tag, emptySet()) + episodeId
                            }
                        }
                    } catch (e: Exception) {
                        println("Error loading tags: ${e.message}")
                    }
                }
            }

            // Mark content as ready after a short delay to ensure smooth transition
            delay(50)  // Small delay to improve transition smoothness
            isContentReady = true

            // After content is marked as ready, wait a moment to ensure transitions
            // don't overlap before hiding the loading indicator
            delay(100)
            isInitialLoading = false

        } catch (e: Exception) {
            println("Error loading initial data: ${e.message}")
            // Ensure we eventually hide the loading screen even if there's an error
            isContentReady = true
            delay(100)
            isInitialLoading = false
        }
    }

    // Efficient filtering with debounce
    LaunchedEffect(allEpisodes, query, activeTags.toList(), isSearchActive) {
        // Only perform filtering if search is active
        if (isSearchActive && (query.isNotBlank() || activeTags.isNotEmpty())) {
            isSearching = true

            // Use snapshotFlow with debounce to avoid excessive filtering
            snapshotFlow { Triple(allEpisodes, query, activeTags.toList()) }
                .debounce(300)
                .collect { (episodes, currentQuery, currentTags) ->
                    // More efficient filtering algorithm
                    val filtered = if (currentTags.isEmpty()) {
                        // If no tags, just filter by query
                        episodes.filter { episode ->
                            val episodeNumber = episodes.size - episodes.indexOf(episode)
                            currentQuery.isBlank() ||
                                    episode.title.contains(currentQuery, ignoreCase = true) ||
                                    (episode.description?.contains(currentQuery, ignoreCase = true) == true) ||
                                    "Episode $episodeNumber".contains(currentQuery, ignoreCase = true)
                        }
                    } else if (currentQuery.isBlank()) {
                        // If only filtering by tags, use the pre-built tag index
                        val matchingEpisodeIds = currentTags.flatMap { tag ->
                            taggedEpisodesMap[tag] ?: emptySet()
                        }.toSet()

                        episodes.filter { episode -> episode.id in matchingEpisodeIds }
                    } else {
                        // If filtering by both query and tags
                        episodes.filter { episode ->
                            // Check query match
                            val episodeNumber = episodes.size - episodes.indexOf(episode)
                            val matchesQuery = currentQuery.isBlank() ||
                                    episode.title.contains(currentQuery, ignoreCase = true) ||
                                    (episode.description?.contains(currentQuery, ignoreCase = true) == true) ||
                                    "Episode $episodeNumber".contains(currentQuery, ignoreCase = true)

                            // Check tag match using cached tags
                            val episodeTags = episodeTagsMap[episode.id] ?: emptyList()
                            val matchesTags = currentTags.isEmpty() ||
                                    currentTags.any { tag -> episodeTags.contains(tag) }

                            matchesQuery && matchesTags
                        }
                    }

                    filteredEpisodes = filtered
                    isSearching = false
                }
        } else {
            // If search is not active, show all episodes
            filteredEpisodes = allEpisodes
            isSearching = false
        }
    }

    // Detect when to load more episodes
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount

            lastVisibleItem >= totalItems - 3 &&
                    !isLoadingMore &&
                    episodesCursor.second != null &&
                    !isSearchActive // Don't load more during search
        }
    }

    // Load more episodes when needed
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            try {
                service.loadMoreEpisodesForChannel(
                    channelId = channelId,
                    cursor = episodesCursor.second,
                    limit = 10
                )
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Main UI with improved transitions
    Box(modifier = Modifier.fillMaxSize()) {
        // Only show content when it's ready (separate from loading state)
        AnimatedVisibility(
            visible = isContentReady,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (playerState.currentEpisode != null) 140.dp else 0.dp)
            ) {
                NavigationBar(
                    title = currentChannel?.title ?: "Episodes",
                    isLeftVisible = true,
                    onLeftClick = onBackPress,
                    isRightVisible = true,
                    rightIcon = if (isSearchActive) Res.drawable.back else Res.drawable.search,
                    onRightClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            query = ""
                            activeTags.clear()
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

                // Main content container - this ensures stable layout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // No search results message
                    this@Column.AnimatedVisibility(
                        visible = isSearchActive && filteredEpisodes.isEmpty() && !isSearching,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No episodes found",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Search progress indicator
                    this@Column.AnimatedVisibility(
                        visible = isSearching,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // Main episode list - only show when not searching and we have results (or not in search mode)
                    this@Column.AnimatedVisibility(
                        visible = !isSearching && !(isSearchActive && filteredEpisodes.isEmpty()),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.background(MaterialTheme.colors.whiteGrey)
                        ) {
                            // Display channel header if not in search mode
                            if (!isSearchActive && currentChannel != null) {
                                item(key = "header-${currentChannel.id}") {
                                    ChannelHeader(channel = currentChannel)
                                }
                            }

                            // Episodes header
                            item(key = "episodes-header") {
                                EpisodesHeader(
                                    episodeCount = if (isSearchActive) filteredEpisodes.size else currentChannel?.episodeCount?.toInt() ?: 0,
                                    isSearchActive = isSearchActive
                                )
                            }

                            // Episodes list with proper sorting and key handling
                            itemsIndexed(
                                items = filteredEpisodes.sortedByDescending { it.pubDate },
                                key = { _, episode -> "episode-${episode.id}" }
                            ) { _, episode ->
                                val isCurrentEpisode = playerState.currentEpisode?.id == episode.id
                                val episodeNumber = allEpisodes.size - allEpisodes.indexOf(episode)

                                // Pass the cached tags for this episode for better performance
                                val episodeTags = episodeTagsMap[episode.id] ?: emptyList()

                                EpisodeListItem(
                                    episode = episode,
                                    episodeNumber = episodeNumber,
                                    isPlaying = isCurrentEpisode && playerState.isPlaying,
                                    positionFlow = service.getEpisodePositionFlow(episode.id.toLong()),
                                    query = query,
                                    tags = episodeTags,
                                    activeTags = activeTags,
                                    onClick = { onPlayPause(episode) }
                                )
                            }

                            // Bottom loading indicator
                            item(key = "bottom-loader") {
                                if (isLoadingMore && !isSearchActive) {
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
            }
        }

        // Full-screen loading indicator - shows on top of everything during initial loading
        AnimatedVisibility(
            visible = isInitialLoading,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Mini player - always rendered last to stay on top
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

@Composable
private fun ChannelHeader(channel: GetAllChannelDetails) {
    val screenSizeIsTooWide = Screen.isTooWide()
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.grey5Black)
    ) {
        HDivider()
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.h2.copy(
                    color = MaterialTheme.colors.greyWhite,
                    fontWeight = FontWeight.Bold
                ),
            )

            Text(
                text = channel.author,
                style = MaterialTheme.typography.body2.copy(
                    color = MaterialTheme.colors.greyGrey20
                )
            )
            Text(
                text = "Last Updated: ${formatDate(channel.latestEpisodePubDate!!)}",
                style = MaterialTheme.typography.caption.copy(color = grey50),
            )
            // Channel Image
            AsyncImage(
                imageUrl = channel.imageUrl,
                contentDescription = "Channel Image",
                modifier = Modifier
                    .size(400.dp)
                    .run {
                        if (screenSizeIsTooWide) {
                            width(400.dp)
                        } else {
                            fillMaxWidth()
                        }
                    }
                    .aspectRatio(1f)
                    .padding(start = 16.dp, end = 16.dp),
                contentScale = ContentScale.FillWidth,
            )
        }
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable { isExpanded = !isExpanded }
        ) {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                }
            ) { expanded ->
                if (expanded) {
                    // Full description
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.body2.copy(
                            color = MaterialTheme.colors.greyGrey20
                        ),
                        modifier = Modifier.animateContentSize()
                    )
                } else {
                    // Collapsed description with ellipsis
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = channel.description,
                            style = MaterialTheme.typography.body2.copy(
                                color = MaterialTheme.colors.greyGrey20
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodesHeader(
    episodeCount: Int,
    isSearchActive: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.grey5Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (isSearchActive) "Search Results" else "Episodes",
            style = MaterialTheme.typography.h2.copy(
                color = MaterialTheme.colors.greyGrey5
            )
        )
        Text(
            text = if (isSearchActive)
                "$episodeCount ${if (episodeCount == 1) "episode" else "episodes"} found"
            else "$episodeCount ${if (episodeCount == 1) "episode" else "episodes"}",
            style = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.colors.greyGrey20
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeListItem(
    episode: PodcastEpisode,
    episodeNumber: Int,
    isPlaying: Boolean,
    positionFlow: Flow<Long?>,
    query: String = "",
    tags: List<String> = emptyList(),
    activeTags: List<String> = emptyList(),
    onClick: () -> Unit
) {
    val screenSizeIsTooWide = Screen.isTooWide()
    val positionMs by positionFlow.collectAsState(initial = 0L)

    // Calculate progress fraction efficiently
    val fractionPlayed = remember(positionMs, episode.duration) {
        if (episode.duration > 0) {
            (positionMs?.toFloat() ?: 0f) / (episode.duration * 1000).toFloat()
        } else 0f
    }

    HDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            // Episode Image with optimized loading
        Box(
            modifier = Modifier
                .size(85.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(if (screenSizeIsTooWide) 170.dp else 85.dp)
                    .padding(0.dp),
                imageUrl = episode.imageUrl!!,
                contentDescription = episodeNumber.toString(),
                contentScale = ContentScale.Crop
            )
        }

            // Episode Info
        Column(
            modifier = Modifier
                .padding(4.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                // Highlighted episode number text
                Text(
                    text = highlightText("Episode: $episodeNumber", query),
                    style = MaterialTheme.typography.h4.copy(
                        color = MaterialTheme.colors.greyWhite, fontWeight = FontWeight.Bold
                    ),
                )

                // Highlighted episode title
                Text(
                    text = highlightText(episode.title.trim(), query),
                    style = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.greyGrey5),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Date info
                episode.pubDate?.let { timestamp ->
                    Text(
                        text = formatDate(timestamp),
                        style = MaterialTheme.typography.caption.copy(color = grey50),
                    )
                }

                // Show active tags that match this episode
                if (tags.isNotEmpty() && activeTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val tagsToShow = activeTags.filter { it in tags }.take(3)
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

                // Add description with highlighting if there's a query match
                if (!episode.description.isNullOrBlank() && query.isNotBlank() &&
                    episode.description.contains(query, ignoreCase = true)) {

                    // Get context around the match for better search results
                    val index = episode.description.indexOf(query, ignoreCase = true)
                    val start = maxOf(0, index - 30)
                    val end = minOf(episode.description.length, index + query.length + 30)
                    val excerpt = (if (start > 0) "..." else "") +
                            episode.description.substring(start, end) +
                            (if (end < episode.description.length) "..." else "")

                    Text(
                        text = highlightText(excerpt, query),
                        style = MaterialTheme.typography.caption.copy(color = grey50),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Play/Pause Icon
            Column(
                modifier = Modifier
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = if (isPlaying)
                        painterResource(Res.drawable.pause)
                    else
                        painterResource(Res.drawable.play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = formatDuration(episode.duration),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.greyGrey20
                )
                if (fractionPlayed > 0f && fractionPlayed < 1f) {
                    LinearProgressIndicator(
                        progress = fractionPlayed,
                        modifier = Modifier
                            .width(30.dp)
                            .padding(top = 4.dp),
                        color = orange, // ignore the theme color for now
                        backgroundColor = LightGray,
                    )
                }
            }
        }
}

@Composable
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        val lowercaseText = text.lowercase()
        val lowercaseQuery = query.lowercase()
        var startIndex = 0

        while (true) {
            val matchIndex = lowercaseText.indexOf(lowercaseQuery, startIndex)
            if (matchIndex < 0) {
                // No more matches, append the rest of the text
                append(text.substring(startIndex))
                break
            }

            // Append text before match
            append(text.substring(startIndex, matchIndex))

            // Append highlighted match
            val endIndex = matchIndex + query.length
            withStyle(SpanStyle(
                background = orange,
                color = MaterialTheme.colors.onPrimary
            )) {
                append(text.substring(matchIndex, endIndex))
            }

            // Continue from end of match
            startIndex = endIndex
        }
    }
}



