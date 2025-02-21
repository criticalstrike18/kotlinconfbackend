package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import io.ktor.util.date.plus
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.kotlinApp.Database.DatabaseWrapper
import org.jetbrains.kotlinApp.Database.DriverFactory
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.storage.ApplicationStorage
import org.jetbrains.kotlinApp.storage.bind
import org.jetbrains.kotlinApp.storage.put
import org.jetbrains.kotlinApp.utils.App
import org.jetbrains.kotlinApp.utils.StateFlowClass
import org.jetbrains.kotlinApp.utils.asStateFlowClass
import org.jetbrains.kotlinconf.GetAllChannelDetails
import kotlin.coroutines.CoroutineContext

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

class ConferenceService(
    val context: ApplicationContext,
    val endpoint: String,
) : CoroutineScope, Closeable {
    private val storage: ApplicationStorage = ApplicationStorage(context)
    private var userId2024: String? by storage.bind(String.serializer().nullable) { null }
    private var needsOnboarding: Boolean by storage.bind(Boolean.serializer()) { true }
    private var notificationsAllowed: Boolean by storage.bind(Boolean.serializer()) { false }

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

    private val syncManager = SyncManager(dbStorage, client, this)

    private val favorites = MutableStateFlow(emptySet<String>())
    private val conference = MutableStateFlow(Conference())

    private val votes = MutableStateFlow(emptyList<VoteInfo>())

    private val _podcastChannels = MutableStateFlow<List<GetAllChannelDetails>>(emptyList())
    val podcastChannels: StateFlow<List<GetAllChannelDetails>> = _podcastChannels.asStateFlow()

    private val _currentChannelEpisodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val currentChannelEpisodes: StateFlow<List<PodcastEpisode>> = _currentChannelEpisodes.asStateFlow()

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
            dbStorage.getAllChannelsFlow()
                .collect { channels ->
                    _podcastChannels.value = channels
                }
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
        synchronizeAllData()
        syncVotes()
        syncFavorites()
        syncManager.startSync()
        updateConferenceData()
        startPodcastChannelsSync()
    }

    /**
     * Returns true if app is launched first time.
     */
    fun needsOnboarding(): Boolean {
        return needsOnboarding
    }

    fun completeOnboarding() {
        needsOnboarding = false
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
            synchronizeAllData()
            syncManager.startSync()

        }
    }

    /**
     * Request permissions to send notifications.
     */
    fun requestNotificationPermissions() {
        notificationsAllowed = true
        notificationManager.requestPermission()
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

    fun speakerById(id: String): Speaker = speakers.value[id] ?: UNKNOWN_SPEAKER

    fun sessionById(id: String): SessionCardView =
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
