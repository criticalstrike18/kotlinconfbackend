package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinApp.storage.ApplicationStorage
import org.jetbrains.kotlinApp.storage.getLong
import org.jetbrains.kotlinApp.storage.putLong

sealed class DataChangeEvent {
    data object SessionsChanged : DataChangeEvent()
    data object SpeakersChanged : DataChangeEvent()
    data object FavoritesChanged : DataChangeEvent()
    data object VotesChanged : DataChangeEvent()
    data object PodcastsChanged : DataChangeEvent()
}

class SyncManager(
    private val dbStorage: DatabaseStorage,
    private val client: APIClient,
    private val scope: CoroutineScope,
    private val storage: ApplicationStorage
) {
    private val pushSyncInterval = 10_000L // 30 seconds for pushing changes
    private val pullSyncInterval = 30_000L // 3 minutes for pulling new data

    private var pushSyncJob: Job? = null
    private var pullSyncJob: Job? = null

    // Last sync timestamps
    private var lastSessionPullTime: Long
        get() = storage.getLong("last_session_pull_time", 0L)
        set(value) = storage.putLong("last_session_pull_time", value)

    private var lastSpeakerPullTime: Long
        get() = storage.getLong("last_speaker_pull_time", 0L)
        set(value) = storage.putLong("last_speaker_pull_time", value)

    private var lastCategoryPullTime: Long
        get() = storage.getLong("last_category_pull_time", 0L)
        set(value) = storage.putLong("last_category_pull_time", value)

    private var lastRoomPullTime: Long
        get() = storage.getLong("last_room_pull_time", 0L)
        set(value) = storage.putLong("last_room_pull_time", value)

    private var lastPodcastPullTime: Long
        get() = storage.getLong("last_podcast_pull_time", 0L)
        set(value) = storage.putLong("last_podcast_pull_time", value)

    // Channels for handling sync requests
    private val voteChannel = Channel<String>(Channel.BUFFERED)
    private val feedbackChannel = Channel<String>(Channel.BUFFERED)
    private val favoriteChannel = Channel<String>(Channel.BUFFERED)
    private val sessionChannel = Channel<String>(Channel.BUFFERED)
    private val speakerChannel = Channel<String>(Channel.BUFFERED)
    private val roomChannel = Channel<String>(Channel.BUFFERED)
    private val categoryChannel = Channel<String>(Channel.BUFFERED)
    private val podcastChannel = Channel<String>(Channel.BUFFERED)

    // Flow to notify about data changes
    private val _dataChangeEvents = MutableSharedFlow<DataChangeEvent>(extraBufferCapacity = 10)
    val dataChangeEvents: SharedFlow<DataChangeEvent> = _dataChangeEvents

    fun startSync() {
        stopSync() // Ensure we don't have multiple jobs running

        // Start push sync (local to server)
        pushSyncJob = scope.launch(Dispatchers.IO) {
            launch { handleVoteSync() }
            launch { handleFeedbackSync() }
            launch { handleFavoriteSync() }
            launch { handleSessionSync() }
            launch { handleSpeakerSync() }
            launch { handleRoomSync() }
            launch { handleCategorySync() }
            launch { handlePodcastSync() }

            while (isActive) {
                try {
                    syncPendingItems()
                } catch (e: Exception) {
                    println("Background push sync failed: ${e.message}")
                }
                delay(pushSyncInterval)
            }
        }

        // Start pull sync (server to local)
        pullSyncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pullNewDataFromServer()
                } catch (e: Exception) {
                    println("Background pull sync failed: ${e.message}")
                }
                delay(pullSyncInterval)
            }
        }
    }

    // Handle pushing local changes to server
    private suspend fun handleSessionSync() {
        for (sessionId in sessionChannel) {
            try {
                val pendingSessions = dbStorage.getPendingSessions()
                val localSession = pendingSessions.firstOrNull { it.id == sessionId } ?: continue
                val sessionRequest = localSession.toConferenceSessionRequest()
                val serverResponse = client.sendSessionData(sessionRequest)
                if (serverResponse.success) {
                    val serverSessionId = serverResponse.sessionId!!

                    // Update local session with server ID
                    dbStorage.updateSessionId(localSession.id, serverSessionId)
                    dbStorage.markSessionSynced(serverSessionId, GMTDate().timestamp)

                    // Update related records with new session ID
                    localSession.speakerIds.forEach { speakerId ->
                        dbStorage.updateSessionSpeakersId(
                            localSession.id,
                            serverSessionId,
                        )
                        dbStorage.markSessionSpeakerSynced(
                            serverSessionId,
                            speakerId,
                            GMTDate().timestamp
                        )
                    }

                    localSession.categoryIds.forEach { categoryId ->
                        dbStorage.updateSessionCategoryId(
                            localSession.id,
                            serverSessionId,
                        )
                        dbStorage.markSessionCategorySynced(
                            serverSessionId,
                            categoryId.toLong(),
                            GMTDate().timestamp
                        )
                    }

                    // Notify that sessions have changed
                    _dataChangeEvents.emit(DataChangeEvent.SessionsChanged)
                }
            } catch (e: Exception) {
                println("Session sync failed for session $sessionId: ${e.message}")
            }
        }
    }

    private suspend fun handleSpeakerSync() {
        for (speakerId in speakerChannel) {
            try {
                val pendingSessionSpeakers = dbStorage.getPendingSessionSpeakers()

                pendingSessionSpeakers.forEach { sessionSpeaker ->
                    try {
                        // Attempt to sync with the server
                        if (client.sendSessionSpeaker(sessionSpeaker.sessionId, sessionSpeaker.speakerId)) {
                            // Mark as synced only if server sync was successful
                            dbStorage.markSessionSpeakerSynced(
                                sessionId = sessionSpeaker.sessionId,
                                speakerId = sessionSpeaker.speakerId,
                                currentTimestamp = GMTDate().timestamp
                            )
                            _dataChangeEvents.emit(DataChangeEvent.SpeakersChanged)
                        }
                    } catch (e: Exception) {
                        println("Failed to sync session-speaker relationship: ${sessionSpeaker.sessionId}-${sessionSpeaker.speakerId}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("Session-speaker sync failed: ${e.message}")
            }
        }
    }

    private suspend fun handleRoomSync() {
        for (roomId in roomChannel) {
            try {
                val pendingRooms = dbStorage.getPendingRooms()
                pendingRooms.forEach { room ->
                    val oldId = room.id!!
                    val roomRequest = RoomTableReq(room.name, room.sort)
                    val serverResponse = client.sendRoomData(roomRequest)

                    if (serverResponse.success) {
                        val serverRoomId = serverResponse.roomId!!

                        // Update local room ID to match server ID
                        dbStorage.updateRoomId(oldId, serverRoomId.toLong())
                        dbStorage.markRoomSynced(serverRoomId.toLong(), GMTDate().timestamp)
                        _dataChangeEvents.emit(DataChangeEvent.SessionsChanged)
                    } else {
                        // Log sync failure for retry
                        println("Failed to sync room ${room.id} - will retry in next sync cycle: ${serverResponse.message}")
                    }
                }
            } catch (e: Exception) {
                println("Room sync failed: ${e.message}")
            }
        }
    }

    private suspend fun handleCategorySync() {
        for (categoryId in categoryChannel) {
            try {
                val pendingCategories = dbStorage.getPendingSessionCategories()
                pendingCategories.forEach { category ->
                    // Implement server sync logic here
                    dbStorage.markSessionCategorySynced(currentTimestamp = GMTDate().timestamp, categoryId = category.categoryId, sessionId = category.sessionId)
                    _dataChangeEvents.emit(DataChangeEvent.SessionsChanged)
                }
            } catch (e: Exception) {
                println("Category sync failed: ${e.message}")
            }
        }
    }

    private suspend fun handleVoteSync() {
        for (sessionId in voteChannel) {
            try {
                val vote = dbStorage.getPendingVotes()
                    .firstOrNull { it.sessionId == sessionId } ?: continue

                if (client.vote(vote.sessionId, vote.score)) {
                    dbStorage.markVoteSynced(vote.sessionId, GMTDate().timestamp)
                    _dataChangeEvents.emit(DataChangeEvent.VotesChanged)
                }
            } catch (e: Exception) {
                println("Vote sync failed for session $sessionId: ${e.message}")
            }
        }
    }

    private suspend fun handleFeedbackSync() {
        for (sessionId in feedbackChannel) {
            try {
                val feedback = dbStorage.getPendingFeedback()
                    .firstOrNull { it.sessionId == sessionId } ?: continue

                if (client.sendFeedback(feedback.sessionId, feedback.value)) {
                    dbStorage.markFeedbackSynced(feedback.sessionId, GMTDate().timestamp)
                    // No need to emit event as feedback doesn't affect UI state
                }
            } catch (e: Exception) {
                println("Feedback sync failed for session $sessionId: ${e.message}")
            }
        }
    }

    private suspend fun handleFavoriteSync() {
        for (sessionId in favoriteChannel) {
            try {
                val favorite = dbStorage.getPendingFavorites()
                    .firstOrNull { it.sessionId == sessionId } ?: continue

                if (syncFavoriteWithServer(favorite)) {
                    dbStorage.markFavoriteSynced(favorite.sessionId, GMTDate().timestamp)
                    _dataChangeEvents.emit(DataChangeEvent.FavoritesChanged)
                }
            } catch (e: Exception) {
                println("Favorite sync failed for session $sessionId: ${e.message}")
            }
        }
    }

    private suspend fun handlePodcastSync() {
        for (podcastId in podcastChannel) {
            // Handle podcast sync if needed
            // Currently just a placeholder
        }
    }

    // New method to pull data from server
    private suspend fun pullNewDataFromServer() {
        withContext(Dispatchers.IO) {
            var dataChanged = false

            // Sync sessions with changes since last sync
            try {
                val lastSyncTime = lastSessionPullTime
                val sessions = client.getSessionDataSince(lastSyncTime)
                if (sessions.isNotEmpty()) {
                    sessions.forEach { session ->
                        dbStorage.insertSession(session)
                    }
                    lastSessionPullTime = System.currentTimeMillis()
                    dataChanged = true
                }
            } catch (e: Exception) {
                println("Failed to pull new sessions: ${e.message}")
            }

            // Sync speakers with changes since last sync
            try {
                val lastSyncTime = lastSpeakerPullTime
                val speakers = client.getSpeakerDataSince(lastSyncTime)
                if (speakers.isNotEmpty()) {
                    speakers.forEach { speaker ->
                        dbStorage.insertSpeaker(speaker)
                    }
                    lastSpeakerPullTime = System.currentTimeMillis()
                    dataChanged = true
                }
            } catch (e: Exception) {
                println("Failed to pull new speakers: ${e.message}")
            }

            // Sync rooms
            try {
                val lastSyncTime = lastRoomPullTime
                val rooms = client.getRoomDataSince(lastSyncTime)
                if (rooms.isNotEmpty()) {
                    rooms.forEach { room ->
                        dbStorage.insertRoom(room)
                    }
                    lastRoomPullTime = System.currentTimeMillis()
                    dataChanged = true
                }
            } catch (e: Exception) {
                println("Failed to pull new rooms: ${e.message}")
            }

            // Sync categories
            try {
                val lastSyncTime = lastCategoryPullTime
                val categories = client.getCategoryDataSince(lastSyncTime)
                if (categories.isNotEmpty()) {
                    categories.forEach { category ->
                        dbStorage.insertCategory(category)
                    }
                    lastCategoryPullTime = System.currentTimeMillis()
                    dataChanged = true
                }
            } catch (e: Exception) {
                println("Failed to pull new categories: ${e.message}")
            }

            // Sync podcast content if needed
            try {
                val lastSyncTime = lastPodcastPullTime
                val podcasts = client.getPodcastDataSince(lastSyncTime)
                if (podcasts.isNotEmpty()) {
                    dbStorage.syncPodcastDataBatch(podcasts)
                    lastPodcastPullTime = System.currentTimeMillis()
                    _dataChangeEvents.emit(DataChangeEvent.PodcastsChanged)
                }
            } catch (e: Exception) {
                println("Failed to pull new podcast data: ${e.message}")
            }

            // Notify data changes if needed
            if (dataChanged) {
                _dataChangeEvents.emit(DataChangeEvent.SessionsChanged)
            }
        }
    }

    // Add any helper methods needed for the implementation
    private fun syncFavoriteWithServer(favoriteInfo: FavoriteInfo): Boolean {
        // Implement server sync logic for favorites
        return true // Placeholder implementation
    }

    private fun SessionInfo.toConferenceSessionRequest(): ConferenceSessionRequest {
        return ConferenceSessionRequest(
            title = this.title,
            description = this.description,
            startsAt = this.startsAt,
            endsAt = this.endsAt,
            roomId = this.roomId,
            isServiceSession = this.isServiceSession,
            isPlenumSession = this.isPlenumSession,
            status = this.status,
            speakerIds = this.speakerIds,
            categoryIds = this.categoryIds
        )
    }

    // Synchronize pending items to the server
    private suspend fun syncPendingItems() {
        withContext(Dispatchers.IO) {
            // Sync all pending votes
            dbStorage.getPendingVotes().forEach { vote ->
                voteChannel.send(vote.sessionId)
            }

            // Sync all pending feedback
            dbStorage.getPendingFeedback().forEach { feedback ->
                feedbackChannel.send(feedback.sessionId)
            }

            // Sync all pending favorites
            dbStorage.getPendingFavorites().forEach { favorite ->
                favoriteChannel.send(favorite.sessionId)
            }

            // Sync all pending sessions
            dbStorage.getPendingSessions().forEach { session ->
                sessionChannel.send(session.id)
            }

            // Sync all pending speakers
            dbStorage.getPendingSpeakers().forEach { speaker ->
                speakerChannel.send(speaker.id)
            }

            // Sync all pending rooms
            dbStorage.getPendingRooms().forEach { room ->
                roomChannel.send(room.id.toString())
            }

            // Sync all pending categories
            dbStorage.getPendingCategories().forEach { category ->
                categoryChannel.send(category.id.toString())
            }
        }
    }

    suspend fun requestSync(type: SyncType, id: String) {
        when (type) {
            SyncType.VOTE -> voteChannel.send(id)
            SyncType.FEEDBACK -> feedbackChannel.send(id)
            SyncType.FAVORITE -> favoriteChannel.send(id)
            SyncType.SESSION -> sessionChannel.send(id)
            SyncType.SPEAKER -> speakerChannel.send(id)
            SyncType.ROOM -> roomChannel.send(id)
            SyncType.CATEGORY -> categoryChannel.send(id)
            SyncType.PODCAST -> podcastChannel.send(id)
        }
    }

    // Trigger immediate pull sync (useful after network reconnection)
    suspend fun triggerImmediatePull() {
        pullNewDataFromServer()
    }

    fun stopSync() {
        pushSyncJob?.cancel()
        pushSyncJob = null

        pullSyncJob?.cancel()
        pullSyncJob = null
    }
}

enum class SyncType {
    VOTE,
    FEEDBACK,
    FAVORITE,
    SESSION,
    SPEAKER,
    ROOM,
    CATEGORY,
    PODCAST
}