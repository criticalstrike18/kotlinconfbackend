package org.jetbrains.kotlinApp.ui
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.fillMaxHeight
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.material.Icon
//import androidx.compose.material.IconButton
//import androidx.compose.material.MaterialTheme
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.AnnotatedString
//import androidx.compose.ui.text.SpanStyle
//import androidx.compose.ui.text.buildAnnotatedString
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.withStyle
//import kotlinconfapp.shared.generated.resources.Res
//import kotlinconfapp.shared.generated.resources.back
//import kotlinx.coroutines.FlowPreview
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.flow.debounce
//import org.jetbrains.kotlinApp.AppController
//import org.jetbrains.kotlinApp.SessionCardView
//import org.jetbrains.kotlinApp.ui.components.SearchField
//import org.jetbrains.kotlinApp.ui.components.SearchSessionTags
//import org.jetbrains.kotlinApp.ui.components.TabBar
//import org.jetbrains.kotlinApp.ui.theme.grey5Black
//import org.jetbrains.kotlinApp.ui.theme.greyGrey5
//import org.jetbrains.kotlinApp.ui.theme.orange
//import org.jetbrains.kotlinApp.ui.theme.white
//import org.jetbrains.kotlinconf.EpisodeSearch
//import org.jetbrains.kotlinconf.GetAllChannelDetails
//import org.jetbrains.kotlinconf.GetAllEpisodesDetails
//import org.jetbrains.kotlinconf.PodcastSearch
//
//@OptIn(FlowPreview::class)
//@Composable
//fun OptimizedSearchScreen(
//    controller: AppController,
//    sessions: List<SessionCardView>
//) {
//    var query by remember { mutableStateOf("") }
//    var selectedTab by remember { mutableStateOf(SearchTab.TALKS) }
//
//    // Load session tags
//    val talkTags = sessions.flatMap { it.tags }.distinct()
//
//    // Load podcast and episode tags efficiently from the database
//    val podcastTags = remember { mutableStateOf(emptyList<String>()) }
//    val episodeTags = remember { mutableStateOf(emptyList<String>()) }
//
//    LaunchedEffect(Unit) {
//        controller.service.getPodcastTags().collect {
//            podcastTags.value = it
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        controller.service.getEpisodeTags().collect {
//            episodeTags.value = it
//        }
//    }
//
//    // Active tags for filtering
//    val activeTalkTags = remember { mutableStateListOf<String>() }
//    val activePodcastTags = remember { mutableStateListOf<String>() }
//    val activeEpisodeTags = remember { mutableStateListOf<String>() }
//
//    // Session results (standard approach since sessions are already in memory)
//    val sessionResults = sessions.searchSessions(query, activeTalkTags)
//
//    // Podcast and episode results (optimized with debouncing)
//    val podcastResults = remember { mutableStateOf(emptyList<PodcastChannelSearchData>()) }
//    val episodeResults = remember { mutableStateOf(emptyList<EpisodeSearchData>()) }
//
//    // Debounce search
//    val queryFlow = remember { MutableStateFlow("") }
//    LaunchedEffect(query) {
//        queryFlow.value = query
//    }
//
//    // Load podcast results with debouncing
//    LaunchedEffect(queryFlow, activePodcastTags) {
//        queryFlow.debounce(300).collectLatest { q ->
//            controller.service.searchPodcasts(q, activePodcastTags.toList()).collect { results ->
//                // Convert from PodcastSearch to PodcastChannelSearchData
//                podcastResults.value = results.map { podcast ->
//                    PodcastChannelSearchData(
//                        id = podcast.id.toString(),
//                        description = buildAnnotatedString {
//                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                                appendWithQuery(podcast.title, q)
//                            }
//                            append(" by ")
//                            appendWithQuery(podcast.author, q)
//                            if (podcast.description.contains(q, ignoreCase = true)) {
//                                append(" / ")
//                                appendPartWithQuery(podcast.description, q)
//                            }
//                        },
//                        imageUrl = podcast.imageUrl,
//                        episodeCount = podcast.episodeCount,
//                        categories = podcast.categories?.split(",")
//                            ?.map { it.trim() }
//                            ?.filterNot { it.isBlank() }
//                            ?: emptyList()
//                    )
//                }
//            }
//        }
//    }
//
//    // Load episode results with debouncing
//    LaunchedEffect(queryFlow, activeEpisodeTags) {
//        queryFlow.debounce(300).collectLatest { q ->
//            controller.service.searchEpisodes(q, activeEpisodeTags.toList()).collect { results ->
//                // Convert from EpisodeSearch to EpisodeSearchData
//                episodeResults.value = results.map { episode ->
//                    EpisodeSearchData(
//                        id = episode.id.toString(),
//                        channelId = episode.channelId.toString(),
//                        description = buildAnnotatedString {
//                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
//                                appendWithQuery(episode.title, q)
//                            }
//                            append(" from ")
//                            append(episode.channelTitle)
//                            if (episode.description.contains(q, ignoreCase = true)) {
//                                append(" / ")
//                                appendPartWithQuery(episode.description, q)
//                            }
//                        },
//                        imageUrl = episode.imageUrl,
//                        pubDate = episode.pubDate,
//                        duration = episode.duration,
//                        channelTitle = episode.channelTitle.toString(),
//                        categories = episode.categories?.split(",")
//                            ?.map { it.trim() }
//                            ?.filterNot { it.isBlank() }
//                            ?: emptyList()
//                    )
//                }
//            }
//        }
//    }
//
//    // UI structure matching the original SearchScreen
//    Column(
//        Modifier
//            .background(MaterialTheme.colors.grey5Black)
//            .fillMaxHeight()
//    ) {
//        // Top bar with back button and tabs
//        Box {
//            TabBar(
//                SearchTab.entries,
//                selectedTab, onSelect = {
//                    selectedTab = it
//                }
//            )
//            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
//                IconButton(onClick = { controller.back() }) {
//                    Icon(
//                        painter = Res.drawable.back.painter(),
//                        "Back",
//                        tint = MaterialTheme.colors.greyGrey5
//                    )
//                }
//            }
//        }
//
//        // Search field
//        SearchField(query, onTextChange = { query = it })
//        HDivider()
//
//        // Tags section based on selected tab
//        when (selectedTab) {
//            SearchTab.TALKS -> {
//                SearchSessionTags(talkTags, activeTalkTags, onClick = {
//                    if (it in activeTalkTags) {
//                        activeTalkTags.remove(it)
//                    } else {
//                        activeTalkTags.add(it)
//                    }
//                })
//                HDivider()
//            }
//            SearchTab.PODCASTS -> {
//                SearchSessionTags(podcastTags.value, activePodcastTags, onClick = {
//                    if (it in activePodcastTags) {
//                        activePodcastTags.remove(it)
//                    } else {
//                        activePodcastTags.add(it)
//                    }
//                })
//                HDivider()
//            }
//            SearchTab.EPISODES -> {
//                SearchSessionTags(episodeTags.value, activeEpisodeTags, onClick = {
//                    if (it in activeEpisodeTags) {
//                        activeEpisodeTags.remove(it)
//                    } else {
//                        activeEpisodeTags.add(it)
//                    }
//                })
//                HDivider()
//            }
//        }
//
//        // Results section
//        Box(modifier = Modifier.weight(1f)) {
//            // Loading indicator for async results
//            val isLoading = when (selectedTab) {
//                SearchTab.PODCASTS -> query.isNotBlank() && podcastResults.value.isEmpty() && !activePodcastTags.isEmpty()
//                SearchTab.EPISODES -> query.isNotBlank() && episodeResults.value.isEmpty() && !activeEpisodeTags.isEmpty()
//                else -> false
//            }
//
//            if (isLoading) {
//                Box(
//                    modifier = Modifier.fillMaxSize(),
//                    contentAlignment = Alignment.Center
//                ) {
//                    CircularProgressIndicator()
//                }
//            } else {
//                SearchResults(
//                    selected = selectedTab,
//                    talks = sessionResults,
//                    podcasts = podcastResults.value,
//                    episodes = episodeResults.value,
//                    activeTalkTags = activeTalkTags,
//                    activePodcastTags = activePodcastTags,
//                    activeEpisodeTags = activeEpisodeTags,
//                    controller = controller
//                )
//            }
//        }
//    }
//}
//
//// Only needed to define these helpers for use within the Composable
//private fun AnnotatedString.Builder.appendWithQuery(value: String, query: String) {
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
//private fun AnnotatedString.Builder.appendPartWithQuery(value: String, query: String) {
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