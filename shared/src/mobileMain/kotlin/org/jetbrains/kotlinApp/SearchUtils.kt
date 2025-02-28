package org.jetbrains.kotlinApp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.podcast.PaginatedResult

suspend fun ConferenceService.searchChannelsForUI(
    query: String,
    activeTags: List<String>,
    page: Int,
    pageSize: Int
): PaginatedResult<PodcastChannelSearchItem> {
    return withContext(Dispatchers.IO) {
        // Get raw database results
        val searchResults = dbStorage.searchChannelsPaginated(
            query = query,
            activeTags = activeTags,
            page = page,
            pageSize = pageSize
        )

        // Get total count
        val totalCount = dbStorage.countSearchChannels(query, activeTags).toInt()

        // Map to raw data model
        val items = searchResults.map { row ->
            PodcastChannelSearchItem(
                id = row.id.toString(),
                title = row.title ?: "",
                author = row.author ?: "",
                description = row.description ?: "",
                imageUrl = row.imageUrl,
                episodeCount = row.episodeCount ?: 0,
                categories = row.categories?.split(",")?.map { it.trim() }
                    ?.filterNot { it.isBlank() } ?: emptyList()
            )
        }

        // Calculate pagination metadata
        val hasMore = (page + 1) * pageSize < totalCount

        PaginatedResult(
            items = items,
            totalCount = totalCount,
            hasMore = hasMore,
            nextPage = if (hasMore) page + 1 else null
        )
    }
}


suspend fun ConferenceService.searchEpisodesForUI(
    query: String,
    activeTags: List<String>,
    page: Int,
    pageSize: Int
): PaginatedResult<EpisodeSearchItem> {
    return withContext(Dispatchers.IO) {
        try {
            // Use the same database pagination functions as before
            val searchResults = dbStorage.searchEpisodesBasicPaginated(
                query = query,
                activeTags = activeTags,
                page = page,
                pageSize = pageSize
            )

            val totalCount = dbStorage.countSearchEpisodes(query, activeTags).toInt()

            // Map database results to our UI model
            val items = searchResults.map { row ->
                EpisodeSearchItem(
                    id = row.id.toString(),
                    channelId = row.channelId.toString(),
                    title = row.title ?: "",
                    description = row.description ?: "",
                    channelTitle = row.channelTitle ?: "",
                    imageUrl = row.imageUrl,
                    pubDate = row.pubDate ?: 0,
                    duration = row.duration ?: 0,
                    categories =  emptyList()
                )
            }

            // Calculate pagination metadata
            val hasMore = (page + 1) * pageSize < totalCount

            PaginatedResult(
                items = items,
                totalCount = totalCount,
                hasMore = hasMore,
                nextPage = if (hasMore) page + 1 else null
            )
        } catch (e: Exception) {
            println("Error searching episodes: ${e.message}")
            e.printStackTrace()
            PaginatedResult(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                nextPage = null
            )
        }
    }
}

suspend fun ConferenceService.searchSessionsForUI(
    query: String,
    activeTags: List<String>,
    page: Int,
    pageSize: Int
): PaginatedResult<SessionSearchItem> {
    return withContext(Dispatchers.IO) {
        // Get sessions from the service
        val sessions = sessionCards.value

        // Filter based on query and tags
        val filteredSessions = sessions
            .filter { session ->
                val matchesQuery = session.title.contains(query, ignoreCase = true) ||
                        session.description.contains(query, ignoreCase = true) ||
                        session.speakerLine.contains(query, ignoreCase = true)

                val matchesTags = activeTags.isEmpty() ||
                        session.tags.any { it in activeTags }

                matchesQuery && matchesTags
            }
            .map { session ->
                SessionSearchItem(
                    id = session.id,
                    title = session.title,
                    speakerLine = session.speakerLine,
                    description = session.description,
                    tags = session.tags,
                    timeLine = session.timeLine
                )
            }

        // Paginate the results
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, filteredSessions.size)
        val paginatedResults = if (startIndex < filteredSessions.size) {
            filteredSessions.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        PaginatedResult(
            items = paginatedResults,
            totalCount = filteredSessions.size,
            hasMore = endIndex < filteredSessions.size,
            nextPage = if (endIndex < filteredSessions.size) page + 1 else null
        )
    }
}