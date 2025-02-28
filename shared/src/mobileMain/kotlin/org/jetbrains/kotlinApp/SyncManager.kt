package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class SyncManager(
    private val dbStorage: DatabaseStorage,
    private val client: APIClient,
    private val scope: CoroutineScope
) {
    private val syncInterval = 30_000L // 30 seconds
    private var syncJob: Job? = null

    // Channels for handling sync requests
    private val voteChannel = Channel<String>(Channel.BUFFERED)
    private val feedbackChannel = Channel<String>(Channel.BUFFERED)
    private val favoriteChannel = Channel<String>(Channel.BUFFERED)
    private val sessionChannel = Channel<String>(Channel.BUFFERED)
    private val speakerChannel = Channel<String>(Channel.BUFFERED)
    private val roomChannel = Channel<String>(Channel.BUFFERED)
    private val categoryChannel = Channel<String>(Channel.BUFFERED)
    private val podcast = Channel<String>(Channel.BUFFERED)

    fun startSync() {
        syncJob?.cancel()

        // Start periodic background sync
        syncJob = scope.launch(Dispatchers.IO) {
            launch { handlePodcastSync() }
            launch { handleVoteSync() }
            launch { handleFeedbackSync() }
            launch { handleFavoriteSync() }
            launch { handleSessionSync() }
            launch { handleSpeakerSync() }
            launch { handleRoomSync() }
            launch { handleCategorySync() }

            while (isActive) {
                try {
                    syncPendingItems()
                } catch (e: Exception) {
                    println("Background sync failed: ${e.message}")
                }
                delay(syncInterval)
            }
        }
    }

    data class ServerSyncResponse(
        val success: Boolean,
        val sessionId: String? = null,
        val roomId: Long? = null,
        val categoryId: Long? = null,
        val speakerId: String? = null,
        val message: String? = null
    )

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
                    dbStorage.markSessionCategorySynced(currentTimestamp = GMTDate().timestamp, categoryId = category.categoryId, sessionId = category.sessionId, )
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
                }
            } catch (e: Exception) {
                println("Vote sync failed for session $sessionId: ${e.message}")
                // Will be picked up by periodic sync later
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
                }
            } catch (e: Exception) {
                println("Favorite sync failed for session $sessionId: ${e.message}")
            }
        }
    }
    private fun syncFavoriteWithServer(favoriteInfo: FavoriteInfo): Boolean {
        // Implement server sync logic for favorites
        return true // Placeholder
    }

    private suspend fun handlePodcastSync() {
//        try {
//            // Fetch data using ProtoBuf from your API
//            val podcastsData: List<ChannelFullData> = client.getPodcastsData()
//            println("Fetched ${podcastsData.count()} podcasts from server")
//
//            // Use batch insert
//            dbStorage.syncPodcastData(podcastsData)
//
//            println("Podcast sync completed successfully")
//        } catch (e: Exception) {
//            println("Podcast sync failed: ${e.message}")
//            e.printStackTrace()
//        }
    }

    private fun parseDate(dateStr: String?): String {
        return try {
            // For example, using Kotlinx datetime:
            if (dateStr != null) {
                Instant.parse(dateStr).toEpochMilliseconds().toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            "0L"
        }
    }

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
            SyncType.PODCAST -> podcast.send(id)
        }
    }


    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
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