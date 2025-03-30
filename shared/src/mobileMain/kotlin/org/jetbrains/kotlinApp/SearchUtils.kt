package org.jetbrains.kotlinApp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.podcast.PaginatedResult

suspend fun ConferenceService.searchChannelsForUI(
    query: String,
    activeTags: List<String>,
    cursor: String? = null,
    limit: Int = 20,
    backward: Boolean = false
): PaginatedResult<PodcastChannelSearchItem> {
    return withContext(Dispatchers.IO) {
        try {
            // Use the tag-based channel search from PodcastRepository
            val result = podcastRepository.searchChannelsFTS(
                query = query,
                tags = activeTags,  // Tags are now the primary filter
                cursor = cursor?.toLongOrNull(),
                limit = limit,
                backward = backward
            )

            // Convert to PaginatedResult format
            PaginatedResult(
                items = result.items,
                totalCount = -1, // Not needed with cursor-based pagination
                hasMore = result.hasMore,
                nextPage = null,
                nextCursor = result.nextCursor,
                prevCursor = result.prevCursor
            )
        } catch (e: Exception) {
            println("Channel search error: ${e.message}")
            PaginatedResult(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                nextPage = null,
                nextCursor = null,
                prevCursor = null
            )
        }
    }
}

// In SearchUtils.kt - update searchEpisodesForUI method
suspend fun ConferenceService.searchEpisodesForUI(
    query: String,
    activeTags: List<String>,
    cursor: String? = null,
    limit: Int = 20,
    backward: Boolean = false
): PaginatedResult<EpisodeSearchItem> {
    return withContext(Dispatchers.IO) {
        try {
            // Use the optimized category-based search instead of direct episode search
            val result = podcastRepository.searchEpisodesByCategory(
                query = query,
                tags = activeTags,
                cursor = cursor?.toLongOrNull()?.toString(),
                limit = limit,
                backward = backward
            )

            // Convert to PaginatedResult format for compatibility
            PaginatedResult(
                items = result.items,
                totalCount = -1, // Not needed with cursor-based pagination
                hasMore = result.hasMore,
                nextPage = null, // No longer using page numbers
                nextCursor = result.nextCursor,
                prevCursor = result.prevCursor
            )
        } catch (e: Exception) {
            println("Error searching episodes: ${e.message}")
            e.printStackTrace()
            PaginatedResult(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                nextPage = null,
                nextCursor = null,
                prevCursor = null
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