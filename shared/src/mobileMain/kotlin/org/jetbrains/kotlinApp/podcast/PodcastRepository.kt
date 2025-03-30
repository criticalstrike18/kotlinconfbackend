package org.jetbrains.kotlinApp.podcast

import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.DatabaseStorage
import org.jetbrains.kotlinApp.EpisodeSearchItem
import org.jetbrains.kotlinApp.PodcastChannelSearchItem
import org.jetbrains.kotlinApp.ui.SearchTab
import org.jetbrains.kotlinconf.GetAllChannelDetails

/**
 * Pagination parameters for data loading
 */
data class PaginationParams(
    val page: Int = 0,
    val pageSize: Int = 20
)

/**
 * Paginated result with data and metadata
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val hasMore: Boolean,
    val nextPage: Int? = null,
    // Add cursor support for new pagination model
    val nextCursor: String? = null,
    val prevCursor: String? = null
)



class PodcastRepository(private val dbStorage: DatabaseStorage) {

    // Result wrapper for cursor-based pagination
    data class CursorResult<T>(
        val items: List<T>,
        val nextCursor: String?,
        val prevCursor: String?,
        val hasMore: Boolean
    )

    // Memory-efficient caching with LRU eviction policies
    private val channelCache = LruCache<String, GetAllChannelDetails>(100)
    private val episodeCache = LruCache<String, PodcastEpisode>(200)

    // Track visible items for sliding window optimization
    private val visibleWindowIds = mutableSetOf<String>()

    // Add these missing cache variables
    private val _channelCategoriesCache = MutableStateFlow<Set<String>>(emptySet())
    val channelCategoriesCache: StateFlow<Set<String>> = _channelCategoriesCache.asStateFlow()

    private val _episodeCategoriesCache = MutableStateFlow<Set<String>>(emptySet())
    val episodeCategoriesCache: StateFlow<Set<String>> = _episodeCategoriesCache.asStateFlow()

    // Add placeholder for talk tags
    private val talkTags: List<String> = emptyList()

    // Loads all categories into memory once (since categories are few compared to content)
    suspend fun preloadChannelCategories() {
        if (_channelCategoriesCache.value.isEmpty()) {
            withContext(Dispatchers.IO) {
                val categories = dbStorage.getAllChannelCategories()
                    .filterNotNull()
                    .flatMap { it.split(",").map { it.trim() } }
                    .filter { it.isNotBlank() }
                    .toSet()
                _channelCategoriesCache.value = categories
            }
        }
    }

    suspend fun preloadEpisodeCategories() {
        if (_episodeCategoriesCache.value.isEmpty()) {
            withContext(Dispatchers.IO) {
                val categories = dbStorage.getAllEpisodeCategories()
                    .filterNotNull()
                    .flatMap { it.split(",").map { it.trim() } }
                    .filter { it.isNotBlank() }
                    .toSet()
                _episodeCategoriesCache.value = categories
            }
        }
    }

    /**
     * Get episodes for a channel with cursor-based pagination
     */
    fun getEpisodesForChannel(
        channelId: Long,
        cursor: Long? = null,
        limit: Int = 20,
        backward: Boolean = false
    ): Flow<CursorResult<PodcastEpisode>> {
        return if (backward && cursor != null) {
            dbStorage.getEpisodesForChannelCursorBackward(channelId, cursor, limit)
                .map { episodes ->
                    // Convert to domain model and update cache
                    val mappedEpisodes = episodes.map { episode ->
                        val domainEpisode = PodcastEpisode(
                            id = episode.id.toString(),
                            channelId = episode.channelId.toString(),
                            title = episode.title,
                            audioUrl = episode.mediaUrl,
                            duration = episode.duration,
                            imageUrl = episode.imageUrl,
                            description = episode.description,
                            pubDate = episode.pubDate
                        )

                        // Cache visible episodes
                        episodeCache.put(domainEpisode.id, domainEpisode)

                        domainEpisode
                    }

                    CursorResult(
                        items = mappedEpisodes.reversed(), // Reverse to maintain proper order
                        nextCursor = episodes.firstOrNull()?.pubDate?.toString(),
                        prevCursor = episodes.lastOrNull()?.pubDate?.toString(),
                        hasMore = episodes.size >= limit
                    )
                }
        } else {
            dbStorage.getEpisodesForChannelCursor(channelId, cursor, limit)
                .map { episodes ->
                    // Convert to domain model and update cache
                    val mappedEpisodes = episodes.map { episode ->
                        val domainEpisode = PodcastEpisode(
                            id = episode.id.toString(),
                            channelId = episode.channelId.toString(),
                            title = episode.title,
                            audioUrl = episode.mediaUrl,
                            duration = episode.duration,
                            imageUrl = episode.imageUrl,
                            description = episode.description,
                            pubDate = episode.pubDate
                        )

                        // Cache visible episodes
                        episodeCache.put(domainEpisode.id, domainEpisode)

                        domainEpisode
                    }

                    CursorResult(
                        items = mappedEpisodes,
                        nextCursor = episodes.lastOrNull()?.pubDate?.toString(),
                        prevCursor = episodes.firstOrNull()?.pubDate?.toString(),
                        hasMore = episodes.size >= limit
                    )
                }
        }
    }

    suspend fun searchChannelsFTS(
        query: String,
        tags: List<String> = emptyList(),
        cursor: Long? = null,
        limit: Int = 20,
        backward: Boolean = false
    ): CursorResult<PodcastChannelSearchItem> {
        return withContext(Dispatchers.IO) {
            try {
                // Use tag filtering as primary search method
                val results = dbStorage.getChannelsWithFilters(
                        tags = tags,
                        query = query.takeIf { it.isNotBlank() },
                        cursor = cursor,
                        limit = limit.toLong(),
                        backward = if (backward) 1L else 0L
                    )

                // Map database results to UI model
                val items = results.map { result ->
                    PodcastChannelSearchItem(
                        id = result.id.toString(),
                        title = result.title,
                        author = result.author ?: "",
                        description = result.description,
                        imageUrl = result.imageUrl,
                        episodeCount = result.episodeCount ?: 0,
                        categories = result.categories?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() } ?: emptyList()
                    )
                }

                CursorResult(
                    items = items,
                    nextCursor = items.lastOrNull()?.id,
                    prevCursor = items.firstOrNull()?.id,
                    hasMore = items.size >= limit
                )
            } catch (e: Exception) {
                println("Channel search error: ${e.message}")
                e.printStackTrace()
                CursorResult(
                    items = emptyList(),
                    nextCursor = null,
                    prevCursor = null,
                    hasMore = false
                )
            }
        }
    }

    // Similarly update the searchEpisodesFTS method
    // In PodcastRepository.kt
    // Optimized episode search with cursor pagination
//    suspend fun searchEpisodesFTS(
//        query: String,
//        tags: List<String> = emptyList(),
//        cursor: Long? = null,
//        limit: Int = 20,
//        backward: Boolean = false
//    ): CursorResult<EpisodeSearchItem> {
//        try {
//            // Use the new function for cursor-based pagination with category filtering
//            val results = dbStorage.searchEpisodesByCategory(
//                searchTerm = query.trim(),
//                tags = tags,
//                cursor = cursor,
//                limit = limit,
//                backward = backward
//            )
//
//            // Determine cursors for pagination
//            val nextCursor = results.lastOrNull()?.pubDate?.toString()
//            val prevCursor = results.firstOrNull()?.pubDate?.toString()
//
//            // Determine hasMore based on whether we got the full limit
//            val hasMore = results.size >= limit
//
//            return CursorResult(
//                items = results,
//                nextCursor = nextCursor,
//                prevCursor = prevCursor,
//                hasMore = hasMore
//            )
//        } catch (e: Exception) {
//            println("Repository error in episode search: ${e.message}")
//            e.printStackTrace()
//            return CursorResult(emptyList(), null, null, false)
//        }
//    }
    suspend fun searchEpisodesByCategory(
        query: String,
        tags: List<String>,
        cursor: String?,
        limit: Int = 20,
        backward: Boolean = false
    ): CursorResult<EpisodeSearchItem> {
        val results = dbStorage.searchEpisodesByCategory(
            query = query,
            tags = tags,
            cursor = cursor?.toLongOrNull(),
            limit = limit.toLong(),
            backward = if (backward) 1L else 0L
        )

        // Map to search item model
        val items = results.map { result ->
            EpisodeSearchItem(
                id = result.id.toString(),
                channelId = result.channelId.toString(),
                title = result.title,
                description = result.description,
                channelTitle = result.channelTitle,
                imageUrl = result.imageUrl,
                pubDate = result.pubDate,
                duration = result.duration,
                categories = result.categories?.split(", ")?.filterNot { it.isBlank() } ?: emptyList()
            )
        }

        // Handle cursor for pagination
        val nextCursor = results.lastOrNull()?.pubDate?.toString()
        val prevCursor = results.firstOrNull()?.pubDate?.toString()

        return CursorResult(
            items = items,
            nextCursor = nextCursor,
            prevCursor = prevCursor,
            hasMore = results.size >= limit
        )
    }


    /**
     * Track visible items to implement sliding window memory optimization
     */
    fun updateVisibleItems(ids: Set<String>) {
        visibleWindowIds.clear()
        visibleWindowIds.addAll(ids)
    }

    fun getTagsForContent(tab: SearchTab): List<String> {
        return when (tab) {
            SearchTab.PODCASTS -> _channelCategoriesCache.value.toList()
            SearchTab.EPISODES -> _episodeCategoriesCache.value.toList()
            SearchTab.TALKS -> talkTags
        }
    }

    // Add methods to load tags into memory for better performance
    suspend fun preloadAllTags() {
        preloadChannelCategories()
        preloadEpisodeCategories()
    }

    /**
     * Get all tags (processed to remove duplicates and empty values)
     */
    suspend fun getAllSessionTags(): List<String> = dbStorage.getAllSessionTags()

    suspend fun getAllChannelTags(): List<String> =
        dbStorage.getAllChannelCategories()
            .filterNotNull()
            .flatMap { it.split(",").map { it.trim() } }
            .distinct()
            .filterNot { it.isBlank() }

    suspend fun getAllEpisodeTags(): List<String> =
        dbStorage.getAllEpisodeCategories()
            .filterNotNull()
            .flatMap { it.split(",").map { it.trim() } }
            .distinct()
            .filterNot { it.isBlank() }

    /**
     * Get item from cache if available
     */
    fun getChannelFromCache(id: String): GetAllChannelDetails? = channelCache.get(id)

    fun getEpisodeFromCache(id: String): PodcastEpisode? = episodeCache.get(id)

    /**
     * Clear caches when needed (e.g., on memory pressure)
     */
    fun clearCaches() {
        channelCache.evictAll()
        episodeCache.evictAll()
        visibleWindowIds.clear()
    }
}