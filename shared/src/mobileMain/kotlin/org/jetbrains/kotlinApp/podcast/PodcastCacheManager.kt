package org.jetbrains.kotlinApp.podcast

import androidx.collection.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinApp.DatabaseStorage
import org.jetbrains.kotlinApp.EpisodeSearchItem
import org.jetbrains.kotlinApp.PodcastChannelSearchItem

class PodcastCacheManager(
    private val dbStorage: DatabaseStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()

    // Cache state
    private val _cacheState = MutableStateFlow(CacheState.NOT_LOADED)
    val cacheState: StateFlow<CacheState> = _cacheState.asStateFlow()

    // Cached data
    private var channelResults: List<PodcastChannelSearchItem> = emptyList()
    private var episodeResults: List<EpisodeSearchItem> = emptyList()
    private var channelTags: List<String> = emptyList()
    private var episodeTags: List<String> = emptyList()

    // Enhanced caching with LRU strategy
    private val episodeTagsCache = LruCache<String, List<String>>(200) // Cache episode tags by ID
    private val channelEpisodeTagsCache = LruCache<Long, List<String>>(50) // Cache tags by channel ID

    // Non-suspending accessors (for immediate UI access)
    val cachedChannelResults: List<PodcastChannelSearchItem> get() = channelResults
    val cachedEpisodeResults: List<EpisodeSearchItem> get() = episodeResults
    val cachedChannelTags: List<String> get() = channelTags
    val cachedEpisodeTags: List<String> get() = episodeTags

    // Suspending accessors (threadsafe)
    suspend fun getChannelResults(): List<PodcastChannelSearchItem> = mutex.withLock { channelResults }
    suspend fun getEpisodeResults(): List<EpisodeSearchItem> = mutex.withLock { episodeResults }
    suspend fun getChannelTags(): List<String> = mutex.withLock { channelTags }
    suspend fun getEpisodeTags(): List<String> = mutex.withLock { episodeTags }

    // Episode tags cache access - non-suspending for better UI responsiveness
    fun getCachedEpisodeTags(episodeId: String): List<String>? = episodeTagsCache.get(episodeId)

    // Add episode tags to cache
    fun cacheEpisodeTags(episodeId: String, tags: List<String>) {
        episodeTagsCache.put(episodeId, tags)
    }

    // Get channel episode tags from cache
    fun getCachedChannelEpisodeTags(channelId: Long): List<String>? = channelEpisodeTagsCache.get(channelId)

    // Cache tags for a channel
    fun cacheEpisodeTagsForChannel(channelId: Long, tags: List<String>) {
        channelEpisodeTagsCache.put(channelId, tags)
    }

    // Initialize the cache
    fun initialize() {
        if (_cacheState.value != CacheState.NOT_LOADED) {
            return
        }

        _cacheState.value = CacheState.LOADING

        scope.launch {
            try {
                // Load all data in parallel
                launch { loadChannelResults() }
                launch { loadEpisodeResults() }
                launch { loadTags() }

                // Update state when complete
                _cacheState.value = CacheState.LOADED
            } catch (e: Exception) {
                println("Cache initialization error: ${e.message}")
                _cacheState.value = CacheState.ERROR
            }
        }
    }

    private suspend fun loadChannelResults() {
        try {
            // Get channels
            val channels = dbStorage.getChannelsList(limit = 100)

            // Convert to search items
            val searchItems = channels.map { channel ->
                PodcastChannelSearchItem(
                    id = channel.id.toString(),
                    title = channel.title,
                    author = channel.author ?: "",
                    description = channel.description,
                    imageUrl = channel.imageUrl,
                    episodeCount = channel.episodeCount ?: 0,
                    categories = channel.categories?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                )
            }

            mutex.withLock {
                channelResults = searchItems
            }
        } catch (e: Exception) {
            println("Error loading channel results: ${e.message}")
        }
    }

    private suspend fun loadEpisodeResults() {
        try {
            // Get all episodes
            val episodesWithChannels = dbStorage.getEpisodesAcrossChannels(limit = 100)

            // Convert to search items
            val searchItems = episodesWithChannels.map { episodeWithChannel ->
                val episode = episodeWithChannel.episode
                EpisodeSearchItem(
                    id = episode.id.toString(),
                    channelId = episode.channelId.toString(),
                    title = episode.title,
                    description = episode.description,
                    channelTitle = episodeWithChannel.channelTitle,
                    imageUrl = episode.imageUrl,
                    pubDate = episode.pubDate,
                    duration = episode.duration,
                    categories = emptyList() // Will load these separately
                )
            }

            mutex.withLock {
                episodeResults = searchItems
            }

            // Load categories for episodes in the background
            scope.launch {
                try {
                    val episodeIds = searchItems.map { it.id.toLong() }
                    val categoriesMap = dbStorage.getEpisodeCategoriesByIds(episodeIds)

                    // Update the cached items with categories
                    val updatedItems = searchItems.map { item ->
                        val categories = categoriesMap[item.id] ?: emptyList()
                        item.copy(categories = categories)
                    }

                    mutex.withLock {
                        episodeResults = updatedItems
                    }
                } catch (e: Exception) {
                    println("Error loading episode categories: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Error loading episode results: ${e.message}")
        }
    }

    private suspend fun loadTags() {
        try {
            // Load channel categories
            val channelTagList = dbStorage.getAllUniqueChannelCategories()
            mutex.withLock {
                channelTags = channelTagList
            }

            // Load episode categories
            val episodeTagList = dbStorage.getAllUniqueEpisodeCategories()
            mutex.withLock {
                episodeTags = episodeTagList
            }
        } catch (e: Exception) {
            println("Error loading tags: ${e.message}")
        }
    }

    // Clear the cache (e.g., for testing or memory issues)
    suspend fun clearCache() = mutex.withLock {
        channelResults = emptyList()
        episodeResults = emptyList()
        channelTags = emptyList()
        episodeTags = emptyList()
        episodeTagsCache.evictAll()
        channelEpisodeTagsCache.evictAll()
        _cacheState.value = CacheState.NOT_LOADED
    }

    // Preload tags for a batch of episodes for better performance
    suspend fun preloadEpisodeTags(episodeIds: List<String>) {
        try {
            val longIds = episodeIds.mapNotNull { it.toLongOrNull() }
            if (longIds.isEmpty()) return

            val tagsMap = dbStorage.getEpisodeTagsForEpisodes(longIds)

            // Cache results
            tagsMap.forEach { (id, tags) ->
                episodeTagsCache.put(id.toString(), tags)
            }
        } catch (e: Exception) {
            println("Error preloading episode tags: ${e.message}")
        }
    }

    enum class CacheState {
        NOT_LOADED,
        LOADING,
        LOADED,
        ERROR
    }

    companion object {
        @Volatile
        private var INSTANCE: PodcastCacheManager? = null

        fun getInstance(dbStorage: DatabaseStorage): PodcastCacheManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PodcastCacheManager(dbStorage).also { INSTANCE = it }
            }
    }
}