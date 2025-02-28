package org.jetbrains.kotlinApp.podcast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.kotlinApp.DatabaseStorage
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
    val nextPage: Int?
)

/**
 * Repository that handles data access with pagination and mapping
 */
class PodcastRepository(private val dbStorage: DatabaseStorage) {

    // In-memory cache for visible items
    private val channelCache = object : LinkedHashMap<Long, GetAllChannelDetails>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GetAllChannelDetails>): Boolean {
            return size > 100 // Limit cache to 100 channels
        }
    }

    /**
     * Get channels with pagination and mapping to UI model
     */
    fun getChannels(params: PaginationParams): Flow<PaginatedResult<GetAllChannelDetails>> {
        return dbStorage.getChannelsPaginated(params.page, params.pageSize)
            .map { paginatedChannels ->
                // Map to GetAllChannelDetails
                val channels = paginatedChannels.map { channel ->
                    GetAllChannelDetails(
                        id = channel.id,
                        title = channel.title,
                        link = channel.link,
                        description = channel.description,
                        copyright = channel.copyright,
                        language = channel.language,
                        author = channel.author,
                        ownerEmail = channel.ownerEmail,
                        ownerName = channel.ownerName,
                        imageUrl = channel.imageUrl,
                        lastBuildDate = channel.lastBuildDate,
                        episodeCount = channel.episodeCount ?: 0,
                        earliestEpisodePubDate = channel.earliestEpisodePubDate,
                        latestEpisodePubDate = channel.latestEpisodePubDate,
                        categories = channel.categories
                    )
                }

                // Update cache
                channels.forEach { channelCache[it.id] = it }

                val totalCount = runCatching { dbStorage.getChannelCount().toInt() }.getOrDefault(0)
                val hasMore = (params.page + 1) * params.pageSize < totalCount

                PaginatedResult(
                    items = channels,
                    totalCount = totalCount,
                    hasMore = hasMore,
                    nextPage = if (hasMore) params.page + 1 else null
                )
            }
            .distinctUntilChanged()
    }

    /**
     * Get episodes for a channel with pagination and mapping to domain model
     */
    fun getEpisodesForChannel(
        channelId: Long,
        params: PaginationParams
    ): Flow<PaginatedResult<PodcastEpisode>> {
        return dbStorage.getEpisodesForChannelPaginated(channelId, params.page, params.pageSize)
            .map { episodes ->
                // Map to domain model
                val mappedEpisodes = episodes.map { dbEpisode ->
                    PodcastEpisode(
                        id = dbEpisode.id.toString(),
                        channelId = dbEpisode.channelId.toString(),
                        title = dbEpisode.title,
                        audioUrl = dbEpisode.mediaUrl,
                        duration = dbEpisode.duration,
                        imageUrl = dbEpisode.imageUrl,
                        description = dbEpisode.description,
                        pubDate = dbEpisode.pubDate
                    )
                }

                val totalCount = runCatching {
                    dbStorage.getEpisodeCountForChannel(channelId).toInt()
                }.getOrDefault(0)

                val hasMore = (params.page + 1) * params.pageSize < totalCount

                PaginatedResult(
                    items = mappedEpisodes,
                    totalCount = totalCount,
                    hasMore = hasMore,
                    nextPage = if (hasMore) params.page + 1 else null
                )
            }
            .distinctUntilChanged()
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
     * Clear caches
     */
    fun clearCaches() {
        channelCache.clear()
    }
}