package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import io.ktor.util.date.plus
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.kotlinApp.Database.DatabaseImportService
import org.jetbrains.kotlinApp.Database.DatabaseWrapper
import org.jetbrains.kotlinApp.Database.DriverFactory
import org.jetbrains.kotlinApp.Database.ImportProgress
import org.jetbrains.kotlinApp.Database.ImportResult
import org.jetbrains.kotlinApp.fileImport.DownloadProgress
import org.jetbrains.kotlinApp.fileImport.DownloadStatus
import org.jetbrains.kotlinApp.fileImport.FileDownloadService
import org.jetbrains.kotlinApp.podcast.PaginatedResult
import org.jetbrains.kotlinApp.podcast.PaginationParams
import org.jetbrains.kotlinApp.podcast.PodcastCacheManager
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastRepository
import org.jetbrains.kotlinApp.storage.ApplicationStorage
import org.jetbrains.kotlinApp.storage.bind
import org.jetbrains.kotlinApp.storage.put
import org.jetbrains.kotlinApp.ui.SearchTab
import org.jetbrains.kotlinApp.utils.App
import org.jetbrains.kotlinApp.utils.StateFlowClass
import org.jetbrains.kotlinApp.utils.asStateFlowClass
import org.jetbrains.kotlinconf.GetAllChannelDetails
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

val UNKNOWN_SESSION_CARD: SessionCardView = SessionCardView(
    "unknown", "unknown", "unknown",
    "unknown",
    GMTDate.START,
    GMTDate.START,
    emptyList(),
    isFinished = false,
    isFavorite = false,
    description = "unknown",
    vote = null,
    tags = emptyList()
)

val UNKNOWN_SPEAKER: Speaker = Speaker(
    "unknown", "unknown", "unknown", "unknown", ""
)

enum class AppInitState {
    WELCOME,          // Show welcome screen
    INITIALIZING,     // Combined downloading and importing state
    READY             // App is ready for use
}

// Unified data initialization progress state
data class DataInitProgress(
    val stage: InitStage = InitStage.PREPARING,
    val downloadProgress: Float = 0f,
    val importProgress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val startTime: Long = System.currentTimeMillis(),
    val error: String? = null
) {
    enum class InitStage {
        PREPARING,
        DOWNLOADING,
        IMPORTING,
        COMPLETED,
        FAILED
    }

    // Calculate the overall progress (download is 70% of total progress, import is 30%)
    val overallProgress: Float get() = when(stage) {
        InitStage.PREPARING -> 0f
        InitStage.DOWNLOADING -> downloadProgress * 0.7f
        InitStage.IMPORTING -> 0.7f + (importProgress * 0.3f)
        InitStage.COMPLETED -> 1f
        InitStage.FAILED -> downloadProgress * 0.7f // Show progress up to failure point
    }

    // Calculate estimated time remaining in seconds
    fun estimateTimeRemaining(): Int? {
        if (stage == InitStage.PREPARING || stage == InitStage.COMPLETED || stage == InitStage.FAILED)
            return null

        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000 // in seconds
        if (elapsedTime < 2 || overallProgress < 0.05f) return null // Need some data to make an estimate

        return try {
            val remainingProgress = 1f - overallProgress
            val timePerProgressUnit = elapsedTime / overallProgress
            (remainingProgress * timePerProgressUnit).toInt()
        } catch (e: Exception) {
            null // Return null if calculation fails
        }
    }
}

class ConferenceService(
    val context: ApplicationContext,
    private val endpoint: String,
    private val importEndpoint: String
) : CoroutineScope, Closeable {
    private val storage: ApplicationStorage = ApplicationStorage(context)
    private var userId2024: String? by storage.bind(String.serializer().nullable) { null }
    private var needsOnboarding: Boolean by storage.bind(Boolean.serializer()) { true }
    private var notificationsAllowed: Boolean by storage.bind(Boolean.serializer()) { false }

    private var onboardingCompleted: Boolean by storage.bind(Boolean.serializer()) { false }
    private var databaseImported: Boolean by storage.bind(Boolean.serializer()) { false }

    private val _dataInitProgress = MutableStateFlow(DataInitProgress())
    val dataInitProgress: StateFlow<DataInitProgress> = _dataInitProgress.asStateFlow()


    private val driver = DriverFactory(context).createDriver()
    private val database = SessionDatabase(driver)
    private val databaseWrapper = DatabaseWrapper(database)
    internal val dbStorage: DatabaseStorage = DatabaseStorage(database, databaseWrapper)

    private val client: APIClient by lazy {
        APIClient(endpoint)
    }

    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.App

    private var serverTime = GMTDate()
    private var requestTime = GMTDate()
    private val notificationManager = NotificationManager(context)

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress

    private val syncManager = SyncManager(dbStorage, client, this, storage)

    private val favorites = MutableStateFlow(emptySet<String>())
    private val conference = MutableStateFlow(Conference())

    private val votes = MutableStateFlow(emptyList<VoteInfo>())
    internal val podcastRepository = PodcastRepository(dbStorage)

    private val _podcastChannels = MutableStateFlow<List<GetAllChannelDetails>>(emptyList())
    val podcastChannels: StateFlow<List<GetAllChannelDetails>> = _podcastChannels.asStateFlow()

    private val _currentChannelsCursor = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))
    val currentChannelsCursor: StateFlow<Pair<String?, String?>> = _currentChannelsCursor.asStateFlow()

    private val _currentEpisodesCursor = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))
    val currentEpisodesCursor: StateFlow<Pair<String?, String?>> = _currentEpisodesCursor.asStateFlow()

    private val _currentChannelEpisodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val currentChannelEpisodes: StateFlow<List<PodcastEpisode>> = _currentChannelEpisodes.asStateFlow()

    private val searchCache by lazy { PodcastCacheManager.getInstance(dbStorage) }
    val searchCacheState: StateFlow<PodcastCacheManager.CacheState> = searchCache.cacheState

    // Non-suspending versions for immediate access in composables
    fun getCachedChannelTags(): List<String> = searchCache.cachedChannelTags
    fun getCachedEpisodeTags(): List<String> = searchCache.cachedEpisodeTags
    fun getCachedChannelResults(): List<PodcastChannelSearchItem> = searchCache.cachedChannelResults
    fun getCachedEpisodeResults(): List<EpisodeSearchItem> = searchCache.cachedEpisodeResults

    private val _time = MutableStateFlow(GMTDate())
    val time: StateFlowClass<GMTDate> = _time
        .asStateFlowClass()

    val agenda: StateFlowClass<Agenda> by lazy {
        combine(
            conference,
            favorites,
            time,
            votes,
        ) { conference, favorites, time, votes ->
            conference.buildAgenda(favorites, votes, time)
        }.stateIn(this, SharingStarted.Eagerly, Agenda())
            .asStateFlowClass()
    }

    val sessionCards: StateFlowClass<List<SessionCardView>> by lazy {
        agenda.map {
            it.days
                .flatMap { it.timeSlots }
                .flatMap { it.sessions }
        }.stateIn(this, SharingStarted.Eagerly, emptyList())
            .asStateFlowClass()
    }

    val speakers: StateFlowClass<Speakers> = conference
        .map { it.speakers }
        .map { it.filter { speaker -> speaker.photoUrl.isNotBlank() } }
        .map { Speakers(it) }
        .stateIn(this, SharingStarted.Eagerly, Speakers(emptyList()))
        .asStateFlowClass()

    private fun sign() {
        client.userUuid = userId2024

        launch {
            if (userId2024 != null) {
                client.sign()
            }
        }
    }

    private val _appInitState = MutableStateFlow(
        when {
            databaseImported -> AppInitState.READY
            onboardingCompleted -> AppInitState.INITIALIZING
            else -> AppInitState.WELCOME
        }
    )
    val appInitState: StateFlow<AppInitState> = _appInitState.asStateFlow()

    private fun syncTime() {
        launch {
            synchronizeTime()
            _time.value = now()

            while (true) {
                delay(1000)
                _time.value = now()
            }
        }
    }

    fun updateDownloadProgress(progress: DownloadProgress) {
        if (_dataInitProgress.value.stage == DataInitProgress.InitStage.FAILED) return

        when (progress.status) {
            DownloadStatus.DOWNLOADING -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.DOWNLOADING,
                    downloadProgress = progress.progress,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes
                )
            }
            DownloadStatus.COMPLETED -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.IMPORTING,
                    downloadProgress = 1f,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    importProgress = 0f
                )
            }
            DownloadStatus.FAILED -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.FAILED,
                    error = progress.error ?: "Download failed"
                )
            }
            else -> { /* Do nothing for IDLE state */ }
        }
    }

    // Update import progress
    private fun updateImportProgress(progress: ImportProgress) {
        if (_dataInitProgress.value.stage == DataInitProgress.InitStage.FAILED) return

        when (progress) {
            is ImportProgress.Processing -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.IMPORTING,
                    importProgress = progress.tableProgress
                )
            }
            is ImportProgress.Completed -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.COMPLETED,
                    importProgress = 1f
                )
            }
            is ImportProgress.Error -> {
                _dataInitProgress.value = _dataInitProgress.value.copy(
                    stage = DataInitProgress.InitStage.FAILED,
                    error = progress.message
                )
            }
            else -> { /* Do nothing for other states */ }
        }
    }


    internal fun updateConferenceData() {
        launch {
            try {
                // First, try to load from local database
                val localData = dbStorage.getConferenceData()
                // Check if the localData is “empty”
                if (localData.sessions.isEmpty() || localData.speakers.isEmpty()) {
                    throw Exception("Local conference data is empty")
                }
                println("DB not empty-> $localData")
                conference.value = localData
            } catch (e: Exception) {
                println("Trying the server")
                val serverData = client.downloadConferenceData()
                // Cache the raw server response
                storage.put("conferenceCache", serverData)
                // Update the conference state with new data
                conference.value = serverData
                println("Failed to get conference data from sqlDelight: ${e.message}")
            }
        }
    }

    // Track background jobs
    private var currentEpisodesJob: Job? = null
    private var searchJob: Job? = null

    // Default pagination parameters
    private val defaultChannelParams = PaginationParams(page = 0, pageSize = 10)
    private val defaultEpisodeParams = PaginationParams(page = 0, pageSize = 10)
    private val defaultSearchParams = PaginationParams(page = 0, pageSize = 15)

    private var channelLoadingJob: Job? = null

    // Add this to the ConferenceService class implementation
//    override fun close() {
//        channelLoadingJob?.cancel()
//        syncManager.stopSync()
//        client.close()
//    }

    fun loadChannelsWithCursor(cursor: String? = null, limit: Int = 20, backward: Boolean = false) {
        // Use a more controlled approach to loading
        launch(Dispatchers.Default) {
            try {
                val result = if (backward && cursor != null) {
                    // Load previous page
                    val previousChannels = dbStorage.getChannelsListBackward(cursor.toLong(), limit)

                    // Update state with uniques and maintain cursor state
                    val updatedChannels = previousChannels + _podcastChannels.value
                        .distinctBy { it.id } // Ensure no duplicates

                    // Update cursors
                    val prevCursor = previousChannels.firstOrNull()?.id?.toString()
                    val nextCursor = _currentChannelsCursor.value.second

                    Triple(updatedChannels, prevCursor, nextCursor)
                } else {
                    // Load next page or initial load
                    val nextChannels = dbStorage.getChannelsList(cursor?.toLong(), limit)

                    // For initial load or next page
                    val updatedChannels = if (cursor == null) {
                        nextChannels
                    } else {
                        _podcastChannels.value.distinctBy { it.id } + nextChannels
                    }

                    // Update cursors
                    val prevCursor = _currentChannelsCursor.value.first
                    val nextCursor = nextChannels.lastOrNull()?.id?.toString()

                    Triple(updatedChannels, prevCursor, nextCursor)
                }

                // Update state atomically
                _podcastChannels.value = result.first
                _currentChannelsCursor.value = Pair(result.second, result.third)

            } catch (e: Exception) {
                println("Channel loading error: ${e.message}")
            }
        }
    }

    // Update episode loading to use cursor-based pagination
    fun loadEpisodesForChannel(channelId: Long, cursor: String? = null, limit: Int = 20, backward: Boolean = false) {
        // Cancel any existing job
        currentEpisodesJob?.cancel()

        // Clear current episodes first to avoid UI glitches
        _currentChannelEpisodes.value = emptyList()

        // Start a new job with cursor-based pagination
        currentEpisodesJob = launch {
            try {
                podcastRepository.getEpisodesForChannel(channelId, cursor?.toLongOrNull(), limit, backward)
                    .collect { result ->
                        _currentChannelEpisodes.value = result.items
                        // Store cursors for navigation
                        _currentEpisodesCursor.value = Pair(result.prevCursor, result.nextCursor)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error loading episodes: ${e.message}")
                _currentChannelEpisodes.value = emptyList()
            }
        }
    }

    // Add method for loading more episodes with cursor
    fun loadMoreEpisodesForChannel(channelId: Long, cursor: String?, limit: Int = 20, backward: Boolean = false) {
        launch {
            try {
                val result = podcastRepository.getEpisodesForChannel(
                    channelId,
                    cursor?.toLongOrNull(),
                    limit,
                    backward
                ).first()

                // Append or prepend based on direction
                if (backward) {
                    _currentChannelEpisodes.value = result.items + _currentChannelEpisodes.value
                    _currentEpisodesCursor.value = Pair(result.prevCursor, _currentEpisodesCursor.value.second)
                } else {
                    _currentChannelEpisodes.value += result.items
                    _currentEpisodesCursor.value = Pair(_currentEpisodesCursor.value.first, result.nextCursor)
                }
            } catch (e: Exception) {
                println("Error loading more episodes: ${e.message}")
            }
        }
    }

    // Get all available tags with efficient database queries
    suspend fun getSessionTags(): List<String> = podcastRepository.getAllSessionTags()
    suspend fun getAllChannelTags(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Try to get from cache first
                if (searchCacheState.value == PodcastCacheManager.CacheState.LOADED) {
                    val cachedTags = searchCache.getChannelTags()
                    if (cachedTags.isNotEmpty()) {
                        return@withContext cachedTags
                    }
                }

                // Fall back to database
                dbStorage.getAllUniqueChannelCategories()
            } catch (e: Exception) {
                println("Error getting channel tags: ${e.message}")
                emptyList()
            }
        }
    }
    suspend fun getEpisodeTags(): List<String> = podcastRepository.getAllEpisodeTags()

    /**
     * Get episode tags for a specific channel
     */
    suspend fun getEpisodeTagsForChannel(channelId: Long): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all unique tags from episodes in this channel
                val tags = dbStorage.getEpisodeTagsForChannel(channelId)

                // Cache the results for future use
                searchCache.cacheEpisodeTagsForChannel(channelId, tags)

                tags
            } catch (e: Exception) {
                println("Error getting episode tags for channel: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun getEpisodeTagsForEpisodeBatch(episodeIds: List<String>): Map<String, List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                // Convert string IDs to long IDs, skipping any that aren't valid numbers
                val longIds = episodeIds.mapNotNull { it.toLongOrNull() }
                if (longIds.isEmpty()) return@withContext emptyMap()

                // First check the cache for any existing entries
                val result = mutableMapOf<String, List<String>>()
                val uncachedIds = mutableListOf<Long>()

                longIds.forEach { id ->
                    val stringId = id.toString()
                    searchCache.getCachedEpisodeTags(stringId)?.let {
                        // Found in cache
                        result[stringId] = it
                    } ?: run {
                        // Not in cache, will need to fetch from DB
                        uncachedIds.add(id)
                    }
                }

                // If we have any uncached IDs, fetch them from the database
                if (uncachedIds.isNotEmpty()) {
                    val dbTags = dbStorage.getEpisodeTagsForEpisodes(uncachedIds)

                    // Cache the results and add to our result map
                    dbTags.forEach { (id, tags) ->
                        val stringId = id.toString()
                        result[stringId] = tags
                        searchCache.cacheEpisodeTags(stringId, tags)
                    }
                }

                return@withContext result
            } catch (e: Exception) {
                println("Error getting tags for episode batch: ${e.message}")
                emptyMap()
            }
        }
    }

    /**
     * Get tags for a specific episode - with memory caching
     */
    fun getEpisodeTags(episodeId: String): List<String> {
        // Check cache first for better performance
        searchCache.getCachedEpisodeTags(episodeId)?.let {
            return it
        }

        val tags = try {
            val result = dbStorage.getEpisodeTagsById(episodeId.toLong())
            // Cache the result for future use
            searchCache.cacheEpisodeTags(episodeId, result)
            result
        } catch (e: Exception) {
            println("Error getting tags for episode $episodeId: ${e.message}")
            emptyList()
        }

        return tags
    }

    /**
     * Check if an episode matches the given tags - optimized with caching
     */
    fun doesEpisodeMatchTags(episodeId: String, tags: List<String>): Boolean {
        if (tags.isEmpty()) return true

        val episodeTags = getEpisodeTags(episodeId)
        return episodeTags.any { episodeTag ->
            tags.any { tag -> episodeTag.equals(tag, ignoreCase = true) }
        }
    }

    /**
     * Get episode position flow for UI components with more efficient access
     */
    fun getEpisodePositionFlow(episodeId: Long): Flow<Long?> {
        return dbStorage.getEpisodePositionFlow(episodeId)
    }

    /**
     * Ensure channel is loaded - helpful for direct navigation
     */
    fun ensureChannelLoaded(channelId: Long) {
        // Check if we already have this channel in our list
        val existingChannel = _podcastChannels.value.find { it.id == channelId }

        if (existingChannel != null) {
            // Channel is already loaded, nothing to do
            return
        }

        // Launch a coroutine to load the channel
        launch(Dispatchers.IO) {
            try {
                val channel = dbStorage.getChannelById(channelId)

                if (channel != null) {
                    // Add this channel to our list
                    _podcastChannels.value = _podcastChannels.value + channel

                    // Also preload episodes for this channel in the background
                    loadEpisodesForChannel(channelId)
                } else {
                    println("Could not find channel with ID $channelId")
                }
            } catch (e: Exception) {
                println("Error ensuring channel is loaded: ${e.message}")
            }
        }
    }

    // Main search method that delegates to specific search implementations
    suspend fun searchContent(
        query: String,
        searchTab: SearchTab,
        activeTags: List<String>,
        cursor: String? = null,
        limit: Int = 20,
        backward: Boolean = false
    ): PaginatedResult<*> {
        // Cancel any ongoing search job
        searchJob?.cancel()

        return try {
            withContext(Dispatchers.IO) {
                when (searchTab) {
                    SearchTab.PODCASTS -> {
                        // For podcast channels, use direct DB search for better performance
                        val result = podcastRepository.searchChannelsFTS(
                            query = query,
                            tags = activeTags,
                            cursor = cursor?.toLongOrNull(),
                            limit = limit,
                            backward = backward
                        )

                        // Convert to PaginatedResult
                        PaginatedResult(
                            items = result.items,
                            totalCount = -1,
                            hasMore = result.hasMore,
                            nextPage = null,
                            nextCursor = result.nextCursor,
                            prevCursor = result.prevCursor
                        )
                    }
                    SearchTab.EPISODES -> searchEpisodesForUI(query, activeTags, cursor, limit, backward)
                    SearchTab.TALKS -> {
                        val page = cursor?.toIntOrNull() ?: 0
                        searchSessionsForUI(query, activeTags, page, limit)
                    }
                }
            }
        } catch (e: Exception) {
            println("Search error: ${e.message}")
            e.printStackTrace()
            PaginatedResult(
                items = emptyList<Any>(),
                totalCount = 0,
                hasMore = false,
                nextPage = null,
                nextCursor = null,
                prevCursor = null
            )
        }
    }

    private fun syncVotes() {
        launch {
            // Only need to set up the continuous flow from database
            dbStorage.getVotes()
                .collect { dbVotes ->
                    votes.value = dbVotes
                }
        }
    }

    private fun syncFavorites() {
        launch {
            // Only need database flow observation
            dbStorage.getFavorites()
                .map { favoritesList ->
                    favoritesList
                        .filter { it.isFavorite }
                        .map { it.sessionId }
                        .toSet()
                }
                .collect { dbFavorites ->
                    favorites.value = dbFavorites
                }
        }
    }

    private fun downloadAndSyncSessions() {
        launch {
            try {
                val sessions = client.getSessionData()
                if (sessions.isNotEmpty()) {
                    // Clear old synced data first
                    dbStorage.clearSyncedSessions()

                    // Store new data
                    sessions.forEach { session ->
                        dbStorage.insertSession(session)
                    }
                } else {
                    println("No sessions received from server")
                }
            } catch (e: Exception) {
                println("Error syncing sessions: ${e.message}")
            }
        }

    }

    private fun downloadAndSyncRooms() {
        launch {
            try {
                val rooms = client.getRoomData()
                if (rooms.isNotEmpty()) {
                    databaseWrapper.clearSyncedRooms()
                    rooms.forEach { room ->
                        dbStorage.insertRoom(room)
                    }
                } else {
                    println("No rooms received from server")
                }
            } catch (e: Exception) {
                println("Error syncing rooms: ${e.message}")
            }
        }
    }

    private fun downloadAndSyncSpeakers() {
        launch {
            try {
                val speakers = client.getSpeakerData()
                if (speakers.isNotEmpty()) {
                    databaseWrapper.clearSyncedSpeakers()
                    speakers.forEach { speaker ->
                        dbStorage.insertSpeaker(speaker)
                    }
                } else {
                    println("No speakers received from server")
                }
            } catch (e: Exception) {
                println("Error syncing speakers: ${e.message}")
            }
        }
    }

    private fun downloadAndSyncCategories() {
        launch {
            try {
                val categories = client.getCategoryData()
                if (categories.isNotEmpty()) {
                    databaseWrapper.clearSyncedCategories()
                    categories.forEach { category ->
                        dbStorage.insertCategory(category)
                    }
                } else {
                    println("No categories received from server")
                }
            } catch (e: Exception) {
                println("Error syncing categories: ${e.message}")
            }
        }
    }

    private fun downloadAndSyncSessionRelations() {
        launch {
            try {
                val sessionSpeakers = client.getSessionSpeakersData()
                val sessionCategories = client.getSessionCategoriesData()

                if (sessionSpeakers.isNotEmpty() || sessionCategories.isNotEmpty()) {
                    databaseWrapper.clearSyncedSessionCategories()
                    databaseWrapper.clearSyncedSessionSpeakers()

                    // Insert session-category relations
                    sessionCategories.forEach { (sessionId, categoryIds) ->
                        categoryIds.forEach { categoryId ->
                            dbStorage.insertSessionCategory(sessionId, categoryId.toLong())
                        }
                    }

                    // Insert session-speaker relations
                    sessionSpeakers.forEach { (sessionId, speakerIds) ->
                        speakerIds.forEach { speakerId ->
                            dbStorage.insertSessionSpeaker(sessionId, speakerId)
                        }
                    }

                } else {
                    println("No session relations received from server")
                }
            } catch (e: Exception) {
                println("Error syncing session relations: ${e.message}")
            }
        }
    }

    private fun startPodcastChannelsSync() {
        launch {
//            dbStorage.getAllChannelsFlow()
//                .collect { channels ->
////                    _podcastChannels.value = channels
//                }
        }
    }


    fun loadEpisodesForChannel(channelId: Long) {
        launch {
            dbStorage.getEpisodesForChannelFlow(channelId)
                .map { episodes ->
                    episodes.map { dbEpisode ->
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
                }
                .collect { mappedEpisodes ->
                    _currentChannelEpisodes.value = mappedEpisodes
                }
        }
    }

    suspend fun startDatabaseInitialization() {
        try {
            if (_appInitState.value != AppInitState.INITIALIZING) return

            _dataInitProgress.value = DataInitProgress(stage = DataInitProgress.InitStage.PREPARING)

            val fileDownloadService = FileDownloadService(context)
            val serverUrl = "$importEndpoint/download_latest_file" // Adjust URL as needed
            val destDir = fileDownloadService.getDefaultDownloadDirectory()
            val fileName = "kotlinapp_data.db"
            val filePath = "$destDir/$fileName"

            // Direct GET request implementation - skip HttpDownloader and use a direct approach
            // This creates a simpler flow that doesn't depend on HEAD requests
            withContext(Dispatchers.IO) {
                try {
                    val file = File(destDir, fileName)
                    // Create parent directories if needed
                    file.parentFile?.mkdirs()

                    // Set download stage
                    _dataInitProgress.value = _dataInitProgress.value.copy(
                        stage = DataInitProgress.InitStage.DOWNLOADING,
                        downloadProgress = 0f
                    )

                    // Use HttpURLConnection for direct download
                    val url = URL(serverUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000

                    val totalBytes = connection.contentLength.toLong()
                    var downloadedBytes = 0L
                    val startTime = System.currentTimeMillis()

                    connection.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var lastProgressUpdate = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                // Update progress about 10 times per second at most
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 100) {
                                    val progress = if (totalBytes > 0) {
                                        downloadedBytes.toFloat() / totalBytes
                                    } else {
                                        // If content length unknown, use a conservative progress indicator
                                        min(0.95f, downloadedBytes.toFloat() / (10 * 1024 * 1024))
                                    }

                                    _dataInitProgress.value = _dataInitProgress.value.copy(
                                        stage = DataInitProgress.InitStage.DOWNLOADING,
                                        downloadProgress = progress,
                                        bytesDownloaded = downloadedBytes,
                                        totalBytes = totalBytes,
                                        startTime = startTime
                                    )

                                    lastProgressUpdate = now
                                }
                            }
                        }
                    }

                    // Download complete
                    _dataInitProgress.value = _dataInitProgress.value.copy(
                        stage = DataInitProgress.InitStage.IMPORTING,
                        downloadProgress = 1f,
                        importProgress = 0f
                    )

                    // Continue with import...
                    val importService = DatabaseImportService(database, driver)

                    // Collect import progress
                    val importProgressJob = launch {
                        importService.importProgress.collect { importProgress ->
                            updateImportProgress(importProgress)
                        }
                    }

                    // Start the import
                    val result = importService.importFromSqliteFile(filePath)

                    // Wait for import progress collection to finish
                    importProgressJob.join()

                    if (result is ImportResult.Error) {
                        println("Import failed, falling back to API sync: ${result.message}")
                        synchronizeAllData()
                    }


                    markDatabaseImportComplete()

                } catch (e: Exception) {
                    println("Direct download failed: ${e.message}")
                    throw e
                }finally {
                    syncVotes()
                    syncFavorites()
                    syncManager.startSync()
                    updateConferenceData()
                    startPodcastChannelsSync()
                }
            }

        } catch (e: Exception) {
            println("Error during initialization: ${e.message}")
            _dataInitProgress.value = _dataInitProgress.value.copy(
                stage = DataInitProgress.InitStage.FAILED,
                error = e.message
            )

            // Fallback to standard initialization
            synchronizeAllData()
            syncVotes()
            syncFavorites()
            syncManager.startSync()
            updateConferenceData()
            startPodcastChannelsSync()

            markDatabaseImportComplete()
        }
    }


    private fun synchronizeAllData() {
        // Sync in order of dependencies
        downloadAndSyncCategories()
        downloadAndSyncRooms()
        downloadAndSyncSpeakers()
        downloadAndSyncSessions()
//        downloadAndSyncSessionRelations()
        updateConferenceData()
        startPodcastChannelsSync()
    }


    init {
        sign()
        syncTime()

        // Determine what to do based on app state
        when (_appInitState.value) {
            AppInitState.READY -> {
                // If already initialized, just refresh data
                launch {
                    syncVotes()
                    syncFavorites()
                    updateConferenceData()
                    // Listen for data change events - pass syncManager as parameter
                    listenForDataChanges(syncManager)
                    // Start sync process
                    syncManager.startSync()
                    startPodcastChannelsSync()
                    loadChannelsWithCursor(limit = 20)
                }
            }

            AppInitState.INITIALIZING -> {
                // Start data initialization if we're in this state
                launch {
                    startDatabaseInitialization()
                }
            }

            else -> {
                // For WELCOME state, do nothing here
            }
        }
    }


    /**
     * Returns true if app is launched first time.
     */
    fun needsOnboarding(): Boolean {
        return !onboardingCompleted
    }

    fun completeOnboarding() {
        onboardingCompleted = true
        needsOnboarding = false
        _appInitState.value = AppInitState.INITIALIZING
        _dataInitProgress.value = DataInitProgress(
            stage = DataInitProgress.InitStage.PREPARING
        )
    }

    fun markDatabaseImportComplete() {
        // First update the DataInitProgress to COMPLETED
        _dataInitProgress.value = _dataInitProgress.value.copy(
            stage = DataInitProgress.InitStage.COMPLETED,
            importProgress = 1f
        )

        // Launch a coroutine to give UI time to show the completion state
        launch {
            // Brief delay so user can see completion message
            delay(1000)

            // Now update the app state to READY which triggers transition to main screen
            databaseImported = true
            _appInitState.value = AppInitState.READY

            // Initialize search cache in the background
            // This happens AFTER the app is in the READY state, so it won't block the UI
            launch(Dispatchers.Default) {
                searchCache.initialize()
            }
        }
    }

    suspend fun getChannelTagsFromCache(): List<String> {
        // Try to get from cache first
        if (searchCacheState.value == PodcastCacheManager.CacheState.LOADED) {
            val cachedTags = searchCache.getChannelTags()
            if (cachedTags.isNotEmpty()) {
                return cachedTags
            }
        }

        // Fall back to database
        return dbStorage.getAllUniqueChannelCategories()
    }

    suspend fun getEpisodeTagsFromCache(): List<String> {
        // Try to get from cache first
        if (searchCacheState.value == PodcastCacheManager.CacheState.LOADED) {
            val cachedTags = searchCache.getEpisodeTags()
            if (cachedTags.isNotEmpty()) {
                return cachedTags
            }
        }

        // Fall back to database
        return dbStorage.getAllUniqueEpisodeCategories()
    }



    /**
     * ------------------------------
     * User actions.
     * ------------------------------
     */

    /**
     * Accept privacy policy clicked.
     */
    fun acceptPrivacyPolicy() {
        if (userId2024 != null) return
        userId2024 = generateUserId()
        client.userUuid = userId2024
        launch {
            client.sign()
        }
    }

    /**
     * Request permissions to send notifications.
     */
    fun requestNotificationPermissions() {
        notificationsAllowed = true
        notificationManager.requestPermission()
    }

    private fun listenForDataChanges(syncManager: SyncManager) {
        launch {
            syncManager.dataChangeEvents.collect { event ->
                when (event) {
                    is DataChangeEvent.SessionsChanged -> {
                        println("Sessions changed, updating conference data")
                        updateConferenceData()
                    }
                    is DataChangeEvent.SpeakersChanged -> {
                        println("Speakers changed, updating conference data")
                        updateConferenceData()
                    }
                    is DataChangeEvent.FavoritesChanged -> {
                        // No specific action needed as favorites are handled through flow collection
                        println("Favorites changed (handled by flow)")
                    }
                    is DataChangeEvent.VotesChanged -> {
                        // No specific action needed as votes are handled through flow collection
                        println("Votes changed (handled by flow)")
                    }
                    is DataChangeEvent.PodcastsChanged -> {
                        println("Podcasts changed, updating podcast data")
                    }
                }
            }
        }
    }



    /**
     * Vote for session.
     */
    suspend fun vote(sessionId: String, rating: Score?): Boolean {
        // Store vote locally first
        rating?.let { score ->
            dbStorage.insertVote(sessionId, score)
        }
        // Try immediate sync
        try {
            syncManager.requestSync(SyncType.VOTE, sessionId)
        } catch (e: Exception) {
            // If sync fails, that's okay - it will be retried by background sync
            println("Initial vote sync failed, will retry later: ${e.message}")
        }

        return true
    }

    suspend fun sendFeedback(sessionId: String, feedbackValue: String): Boolean {
        // Store feedback locally first
        dbStorage.insertFeedback(sessionId, feedbackValue)

        // Try immediate sync
        try {
            syncManager.requestSync(SyncType.FEEDBACK, sessionId)
        } catch (e: Exception) {
            println("Initial feedback sync failed, will retry later: ${e.message}")
        }

        return true
    }

    suspend fun sendPodcastQueryRequest(title: String, author: String, rssLink: String){
        try {
            client.sendPodcastRequest(title,author,rssLink)
        }catch (e:Exception){
            println("Failure To send Request: ${e.message}")
        }
    }

    fun speakerById(id: String): Speaker = speakers.value[id] ?: UNKNOWN_SPEAKER

    private fun sessionById(id: String): SessionCardView =
        sessionCards.value.find { it.id == id } ?: UNKNOWN_SESSION_CARD

    fun sessionsForSpeaker(id: String): List<SessionCardView> =
        sessionCards.value.filter { it.speakerIds.contains(id) }

    /**
     * Mark session as favorite.
     */
    fun toggleFavorite(sessionId: String) {
        launch {
            val currentFavorites = favorites.value
            val isFavorite = !currentFavorites.contains(sessionId)

            // Update local database
            dbStorage.insertFavorite(sessionId, isFavorite)

            // Try immediate sync
            try {
                syncManager.requestSync(SyncType.FAVORITE, sessionId)
            } catch (e: Exception) {
                println("Initial favorite sync failed, will retry later: ${e.message}")
            }

            // Handle notifications
            if (isFavorite) {
                scheduleNotification(sessionById(sessionId))
            } else {
                cancelNotification(sessionById(sessionId))
            }
        }
    }

    fun partnerDescription(name: String): String {
        return PARTNER_DESCRIPTIONS[name] ?: ""
    }

    private fun scheduleNotification(session: SessionCardView) {
        if (!notificationsAllowed) return

        val startTimestamp = session.startsAt.timestamp
        val reminderTimestamp = startTimestamp - 5 * 60 * 1000
        val nowTimestamp = now().timestamp
        val delay = reminderTimestamp - nowTimestamp
        val voteTimeStamp = session.endsAt.timestamp

        when {
            delay >= 0 -> {
                notificationManager.schedule(delay, session.title, "Starts in 5 minutes.")
            }
            nowTimestamp in reminderTimestamp..<startTimestamp -> {
                notificationManager.schedule(0, session.title, "The session is about to start.")
            }
            nowTimestamp in startTimestamp..<voteTimeStamp -> {
                notificationManager.schedule(0, session.title, "Hurry up! The session has already started!")
            }
        }

        if (nowTimestamp > voteTimeStamp) return
        
        val voteDelay = voteTimeStamp - nowTimestamp
        notificationManager.schedule(
            voteDelay,
            "${session.title} finished",
            "How was the talk?"
        )
    }

    private fun cancelNotification(session: SessionCardView) {
        if (!notificationsAllowed) {
            return
        }

        notificationManager.cancel(session.title)
        notificationManager.cancel("${session.title} finished")
    }

    private suspend fun synchronizeTime() {
        kotlin.runCatching {
            serverTime = client.getServerTime()
            requestTime = GMTDate()
        }
    }

    private fun isDatabaseEmpty(): Boolean {
        // Check for podcast channels as an indicator
        val channelCount = database.sessionDatabaseQueries
            .selectAllChannels()
            .executeAsList()
            .size

        return channelCount == 0
    }

    /**
     * Get current time synchronized with server.
     */
    private fun now(): GMTDate {
        return GMTDate() + (serverTime.timestamp - requestTime.timestamp)
    }

    override fun close() {
        syncManager.stopSync()
        client.close()
    }


}
