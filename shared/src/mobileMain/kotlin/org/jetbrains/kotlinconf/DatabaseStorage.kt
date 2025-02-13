package org.jetbrains.kotlinconf

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.kotlinconf.Database.DatabaseWrapper
import org.jetbrains.kotlinconf.storage.ApplicationStorage

class DatabaseStorage(
    private val database: SessionDatabase,
    private val databaseWrapper: DatabaseWrapper
) : ApplicationStorage {
    override fun putBoolean(key: String, value: Boolean) {
//        database.userPreferencesQueries.insertPreference(
//            key = key,
//            booleanValue = if (value) 1L else 0L,
//            stringValue = null
//        )
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun putString(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun getString(key: String): String? {
        TODO("Not yet implemented")
    }

    suspend fun getConferenceData(): Conference = withContext(Dispatchers.IO) {
        val sessions = database.sessionDatabaseQueries.selectAllSessions()
            .executeAsList()
            .map { sessionRow ->
                // Get speaker IDs for the session
                val speakerIds = database.sessionDatabaseQueries
                    .getSessionSpeakers(sessionRow.id)
                    .executeAsList()
                    .map { it.id }

                // Get room name
                val roomName = sessionRow.roomId?.let { roomId ->
                    database.sessionDatabaseQueries.selectAllRooms()
                        .executeAsList()
                        .find { it.id == roomId }
                        ?.name
                } ?: "Unknown Room"

                // Get category titles (tags)
                val tags = database.sessionDatabaseQueries
                    .getSessionCategories(sessionRow.id)
                    .executeAsList()
                    .map { it.title }

                Session(
                    id = sessionRow.id,
                    title = sessionRow.title,
                    description = sessionRow.description,
                    speakerIds = speakerIds,
                    location = roomName,
                    startsAt = GMTDate(sessionRow.startsAt),
                    endsAt = GMTDate(sessionRow.endsAt),
                    tags = tags
                )
            }

        val speakers = database.sessionDatabaseQueries.selectAllSpeakers()
            .executeAsList()
            .map { speakerRow ->
                Speaker(
                    id = speakerRow.id,
                    name = "${speakerRow.firstName} ${speakerRow.lastName}",
                    position = speakerRow.tagLine ?: "",
                    description = speakerRow.bio ?: "",
                    photoUrl = speakerRow.profilePicture ?: ""
                )
            }

        Conference(sessions, speakers)
    }

    // Session Management
    suspend fun insertSession(session: SessionInfo) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertSession(
            id = session.id,
            title = session.title,
            description = session.description ?: "",
            roomId = session.roomId?.toLong(),
            startsAt = session.startsAt.timestamp,
            endsAt = session.endsAt.timestamp,
            isServiceSession = if (session.isServiceSession) 1L else 0L,
            isPlenumSession = if (session.isPlenumSession) 1L else 0L,
            status = session.status,
            isPending = 0L
        )

        // Insert speaker associations
        session.speakerIds.forEach { speakerId ->
            database.sessionDatabaseQueries.insertSessionSpeaker(
                sessionId = session.id,
                speakerId = speakerId,
            )
        }

        // Insert category associations
        session.categoryIds.forEach { categoryId ->
            database.sessionDatabaseQueries.insertSessionCategory(
                sessionId = session.id,
                categoryId = categoryId.toLong(),
            )
        }
    }

    suspend fun insertSessionThroughForm(session: SessionInfo) = withContext(Dispatchers.IO) {
        // Always insert the session, with empty string as default description if null
        database.sessionDatabaseQueries.insertSession(
            id = session.id,
            title = session.title,
            description = session.description ?: "",  // Use empty string if description is null
            roomId = session.roomId?.toLong(),
            startsAt = session.startsAt.timestamp,
            endsAt = session.endsAt.timestamp,
            isServiceSession = if (session.isServiceSession) 1L else 0L,
            isPlenumSession = if (session.isPlenumSession) 1L else 0L,
            status = session.status,
            isPending = 1L  // Keep the pending flag for syncing
        )

        // Insert speaker associations
        session.speakerIds.forEach { speakerId ->
            database.sessionDatabaseQueries.insertSessionSpeaker(
                sessionId = session.id,
                speakerId = speakerId,
            )
        }

        // Insert category associations
        session.categoryIds.forEach { categoryId ->
            database.sessionDatabaseQueries.insertSessionCategory(
                sessionId = session.id,
                categoryId = categoryId.toLong(),
            )
        }
    }

    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.selectAllSessions().executeAsList().map { sessionRow ->
            val speakers = database.sessionDatabaseQueries.getSessionSpeakers(sessionRow.id)
                .executeAsList()
                .map { it.id }

            val categories = database.sessionDatabaseQueries.getSessionCategories(sessionRow.id)
                .executeAsList()
                .map { it.title }

            Session(
                id = sessionRow.id,
                title = sessionRow.title,
                description = sessionRow.description,
                speakerIds = speakers,
                location = sessionRow.roomId?.toString() ?: "unknown",
                startsAt = GMTDate(sessionRow.startsAt),
                endsAt = GMTDate(sessionRow.endsAt),
                tags = categories
            )
        }
    }

    suspend fun getPendingSessions() = withContext(Dispatchers.IO) {
        // Get all pending sessions
        val pendingSessions = database.sessionDatabaseQueries.getPendingSessions().executeAsList()

        pendingSessions.map { session ->
            // Get speaker IDs for this session
            val speakerIds = database.sessionDatabaseQueries.getSessionSpeakers(session.id)
                .executeAsList()
                .map { it.id }

            // Get category IDs for this session
            val categoryIds = database.sessionDatabaseQueries.getSessionCategories(session.id)
                .executeAsList()
                .map { it.id.toInt() }

            // Convert to SessionInfo with proper type conversions
            SessionInfo(
                id = session.id,
                title = session.title,
                description = session.description,
                startsAt = GMTDate(session.startsAt),
                endsAt = GMTDate(session.endsAt),
                roomId = session.roomId?.toInt(),
                isServiceSession = session.isServiceSession == 1L,
                isPlenumSession = session.isPlenumSession == 1L,
                status = session.status ?: "Accepted",
                speakerIds = speakerIds,
                categoryIds = categoryIds
            )
        }
    }

    suspend fun updateSessionId(oldId: String, newId: String) = withContext(Dispatchers.IO) {
        database.transaction {
            // Update the main session table
            database.sessionDatabaseQueries.updateSessionId(
                newId = newId,
                oldId = oldId
            )
        }
    }

    suspend fun markSessionSynced(sessionId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markSessionSynced(
            currentTime = currentTimestamp,
            sessionId = sessionId
        )
    }

    // Room Management
    suspend fun insertRoom(room: RoomTable) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertRoom(
            id = room.id,
            name = room.name,
            sort = room.sort?.toLong()
        )
    }

    suspend fun insertRoomThroughForm(room: RoomTable) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertRoomThroughForm(
            name = room.name,
            sort = room.sort?.toLong(),
            isPending = 1L
        )
    }

    suspend fun updateRoomId(oldId: Long, newId: Long) = withContext(Dispatchers.IO) {
        database.transaction {
            // Update the main session table
            database.sessionDatabaseQueries.updateRoomId(
                newRoomId = newId,
                oldRoomId = oldId
            )
        }
    }

    suspend fun getPendingRooms() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingRooms()
            .executeAsList()
            .map { room ->
                RoomTable(
                    id = room.id,
                    name = room.name,
                    sort = room.sort?.toInt()
                )
            }
    }

    suspend fun markRoomSynced(roomId: Long, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markRoomSynced(
            currentTime = currentTimestamp,
            roomId = roomId
        )
    }

    suspend fun getAllRooms() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.selectAllRooms()
            .executeAsList()
            .map { room ->
                RoomInfo(
                    id = room.id,
                    name = room.name,
                    sort = room.sort?.toInt()
                )
            }
    }


    // Category Management
    suspend fun insertCategory(category: CategoriesTable) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertCategory(
            id = category.id,
            title = category.title,
            sort = category.sort?.toLong(),
            type = category.type
        )
    }

    suspend fun getPendingCategories() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingCategories()
            .executeAsList()
            .map { category ->
                CategoriesTable(
                    id = category.id,
                    title = category.title,
                    sort = category.sort?.toInt(),
                    type = category.type
                )
            }
    }

    suspend fun markCategorySynced(categoryId: Long, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markCategorySynced(
            currentTime = currentTimestamp,
            categoryId = categoryId
        )
    }

    suspend fun getAllCategories() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.selectAllCategories()
            .executeAsList()
            .map { category ->
                CategoriesTable(
                    id = category.id,
                    title = category.title,
                    sort = category.sort?.toInt(),
                    type = category.type
                )
            }
    }

    // Speaker Management
    suspend fun insertSpeaker(speaker: SpeakerInfo) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertSpeaker(
            id = speaker.id,
            firstName = speaker.firstName,
            lastName = speaker.lastName,
            bio = speaker.bio,
            tagLine = speaker.tagLine,
            profilePicture = speaker.profilePicture,
            isTopSpeaker = if (speaker.isTopSpeaker) 1L else 0L
        )
    }

    suspend fun getPendingSpeakers() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingSpeakers()
            .executeAsList()
            .map { speaker ->
                SpeakerInfo(
                    id = speaker.id,
                    firstName = speaker.firstName,
                    lastName = speaker.lastName,
                    bio = speaker.bio,
                    tagLine = speaker.tagLine,
                    profilePicture = speaker.profilePicture,
                    isTopSpeaker = speaker.isTopSpeaker == 1L
                )
            }
    }

    suspend fun markSpeakerSynced(speakerId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markSpeakerSynced(
            currentTime = currentTimestamp,
            speakerId = speakerId
        )
    }

    suspend fun getAllSpeakers() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.selectAllSpeakers()
            .executeAsList()
            .map { speaker ->
                SpeakerInfo(
                    id = speaker.id,
                    firstName = speaker.firstName,
                    lastName = speaker.lastName,
                    bio = speaker.bio,
                    tagLine = speaker.tagLine,
                    profilePicture = speaker.profilePicture,
                    isTopSpeaker = speaker.isTopSpeaker == 1L
                )
            }
    }

    // Junction table sync functions
    suspend fun insertSessionSpeaker(sessionId: String, speakerId: String) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertSessionSpeaker(
            sessionId = sessionId,
            speakerId = speakerId,
        )
    }

    suspend fun updateSessionSpeakersId(oldSessionId: String, newSessionId: String) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.updateSessionSpeakersId(
            newSessionId = newSessionId,
            oldSessionId = oldSessionId
        )
    }

    suspend fun getPendingSessionSpeakers() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingSessionSpeakers()
            .executeAsList()
    }

    suspend fun markSessionSpeakerSynced(sessionId: String, speakerId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markSessionSpeakerSynced(
            currentTime = currentTimestamp,
            sessionId = sessionId,
            speakerId = speakerId
        )
    }

    suspend fun insertSessionCategory(sessionId: String, categoryId: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.insertSessionCategory(
            sessionId = sessionId,
            categoryId = categoryId
        )
    }

    suspend fun updateSessionCategoryId(oldSessionId: String, newSessionId: String) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.updateSessionCategoriesId(
            newSessionId = newSessionId,
            oldSessionId = oldSessionId
        )
    }

    suspend fun getPendingSessionCategories() = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingSessionCategories()
            .executeAsList()
    }

    suspend fun markSessionCategorySynced(sessionId: String, categoryId: Long, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markSessionCategorySynced(
            currentTime = currentTimestamp,
            sessionId = sessionId,
            categoryId = categoryId
        )
    }



    //    Feedback Functions
    fun insertFeedback(sessionId: String, feedbackText: String) {
        database.sessionDatabaseQueries.insertFeedback(
            sessionId = sessionId,
            feedbackText = feedbackText
        )
    }

    fun getFeedback(): Flow<List<FeedbackInfo>> {
        return database.sessionDatabaseQueries.getAllFeedback()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { feedbackList ->
                feedbackList.map { feedback ->
                    FeedbackInfo(
                        sessionId = feedback.sessionId,
                        value = feedback.feedbackText
                    )
                }
            }
    }

    suspend fun getPendingFeedback(): List<FeedbackInfo> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingFeedback()
            .executeAsList()
            .map { feedback ->
                FeedbackInfo(
                    sessionId = feedback.sessionId,
                    value = feedback.feedbackText
                )
            }
    }

    suspend fun markFeedbackSynced(sessionId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markFeedbackSynced(
            currentTime = currentTimestamp,
            sessionId = sessionId
        )
    }

//Favourite Functions
    fun insertFavorite(sessionId: String, isFavorite: Boolean) {
        database.sessionDatabaseQueries.insertFavorite(
            sessionId = sessionId,
            isFavorite = if (isFavorite) 1L else 0L
        )
    }


    fun getFavorites(): Flow<List<FavoriteInfo>> {
        return database.sessionDatabaseQueries.getAllFavorites()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { favorites ->
                favorites.map { favorite ->
                    FavoriteInfo(
                        sessionId = favorite.sessionId,
                        isFavorite = favorite.isFavorite == 1L
                    )
                }
            }
    }

    suspend fun getPendingFavorites(): List<FavoriteInfo> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingFavorites()
            .executeAsList()
            .map { favorite ->
                FavoriteInfo(
                    sessionId = favorite.sessionId,
                    isFavorite = favorite.isFavorite == 1L
                )
            }
    }

    suspend fun markFavoriteSynced(sessionId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markFavoriteSynced(
            currentTime = currentTimestamp,
            sessionId = sessionId
        )
    }

//      VOTES FUNCTIONS
    fun insertVote(sessionId: String, score: Score) {
        database.sessionDatabaseQueries.insertVote(
            sessionId = sessionId,
            score = score.value.toLong()
        )
    }

    fun getVotes(): Flow<List<VoteInfo>> {
        return database.sessionDatabaseQueries.getAllVotes()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { votes ->
                votes.map { vote ->
                    VoteInfo(
                        sessionId = vote.sessionId,
                        score = Score.fromValue(vote.score.toInt())
                    )
                }
            }
    }

    suspend fun getPendingVotes(): List<VoteInfo> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getPendingVotes()
            .executeAsList()
            .map { vote ->
                VoteInfo(
                    sessionId = vote.sessionId,
                    score = Score.fromValue(vote.score.toInt())
                )
            }
    }

    suspend fun markVoteSynced(sessionId: String, currentTimestamp: Long) = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.markVoteSynced(
            currentTime = currentTimestamp,
            sessionId = sessionId
        )
    }

    suspend  fun clearSyncedSessions() = withContext(Dispatchers.IO) {
        database.transaction {
            database.sessionDatabaseQueries.run {
                deleteCompletedSessionCategories()
                deleteCompletedSessionSpeakers()
                deleteCompletedSessions()
            }
        }
    }

//    PODCAST MANAGEMENT
    fun upsertChannelData(channelData: ChannelFullData) {
    // First check if channel with channelData.id exists
    val existing = database.sessionDatabaseQueries.selectChannelById(channelData.id.toLong()).executeAsOneOrNull()
    if (existing == null) {
        // Insert
        database.sessionDatabaseQueries.insertChannel(
            id = channelData.id.toLong(),
            title = channelData.title,
            link = channelData.link,
            description = channelData.description,
            copyright = channelData.copyright,
            language = channelData.language ?: "",
            author = channelData.author ?: "",
            ownerEmail = channelData.ownerEmail ?: "",
            ownerName = channelData.ownerName ?: "",
            imageUrl = channelData.imageUrl ?: "",
            lastBuildDate = parseDateString(channelData.lastBuildDate) // convert string -> Long or store as Long
        )
    } else {
        // Update
        database.sessionDatabaseQueries.updateChannel(
            title = channelData.title,
            link = channelData.link,
            description = channelData.description,
            copyright = channelData.copyright,
            language = channelData.language ?: "",
            author = channelData.author ?: "",
            ownerEmail = channelData.ownerEmail ?: "",
            ownerName = channelData.ownerName ?: "",
            imageUrl = channelData.imageUrl ?: "",
            lastBuildDate = parseDateString(channelData.lastBuildDate),
            id = channelData.id.toLong()
        )
    }
}

    /** Upsert or insert an episode. */
    fun upsertEpisodeData(channelId: Int, episodeData: EpisodeData) {
        // We can try looking up by the 'id' or 'guid' if the server is guaranteeing that 'guid' is unique
        // If your local table primary key is 'id' but your server doesn't provide it, you can rely on 'guid' as a unique index.
        val existing =
            episodeData.id?.let { database.sessionDatabaseQueries.selectEpisodesById(it.toLong()).executeAsOneOrNull() }
        if (existing == null) {
            // Insert
            database.sessionDatabaseQueries.insertEpisode(
                id = episodeData.id?.toLong(), // or you store a server-provided ID if you have one
                channelId = channelId.toLong(),
                guid = episodeData.guid,
                title = episodeData.title,
                description = episodeData.description,
                link = episodeData.link,
                pubDate = episodeData.pubDate.toEpochMilliseconds(),
                duration = (episodeData.duration ?: 0).toLong(),
                explicit = if (episodeData.explicit) 1L else 0L,
                imageUrl = episodeData.imageUrl,
                mediaUrl = episodeData.mediaUrl ?: "",
                mediaType = episodeData.mediaType ?: "audio/mpeg",
                mediaLength = episodeData.mediaLength ?: 0
            )
        } else {
            // Update
            database.sessionDatabaseQueries.updateEpisode(
                channelId = channelId.toLong(),
                guid = episodeData.guid,
                title = episodeData.title,
                description = episodeData.description,
                link = episodeData.link,
                pubDate = episodeData.pubDate.toEpochMilliseconds(),
                duration = (episodeData.duration ?: 0).toLong(),
                explicit = if (episodeData.explicit) 1L else 0L,
                imageUrl = episodeData.imageUrl,
                mediaUrl = episodeData.mediaUrl ?: "",
                mediaType = episodeData.mediaType ?: "audio/mpeg",
                mediaLength = episodeData.mediaLength ?: 0,
                id = existing.id // your local primary key
            )
        }
    }

    suspend fun insertPodcastChannels(dto: ChannelDTO)= withContext(Dispatchers.IO){
        database.transaction {
            database.sessionDatabaseQueries.insertChannel(
                id = dto.id.toLong(),
                title = dto.title,
                link = dto.link,
                description = dto.description,
                copyright = dto.copyright,
                language = dto.language,
                author = dto.author,
                ownerEmail = dto.ownerEmail,
                ownerName = dto.ownerName,
                imageUrl = dto.imageUrl,
                lastBuildDate = dto.lastBuildDate
            )
        }
    }

    suspend fun insertPodcastEpisodes(dto: EpisodeDTO)= withContext(Dispatchers.IO){
        database.transaction {
            database.sessionDatabaseQueries.insertEpisode(
                id = dto.id.toLong(),
                channelId = dto.channelId.toLong(),
                guid = dto.guid,
                title = dto.title,
                description = dto.description,
                link = dto.link,
                pubDate = dto.pubDate,
                duration = dto.duration.toLong(),
                explicit = if (dto.explicit) 1L else 0L,
                imageUrl = dto.imageUrl,
                mediaUrl = dto.mediaUrl,
                mediaType = dto.mediaType,
                mediaLength = dto.mediaLength
            )
        }
    }

    fun getAllChannelsFlow(): Flow<List<PodcastChannels>> {
        return database.sessionDatabaseQueries.selectAllChannels()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getEpisodesForChannelFlow(channelId: Long): Flow<List<PodcastEpisodes>> {
        return database.sessionDatabaseQueries.selectEpisodesByChannelId(channelId)
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

}

fun parseDateString(dateString: String?): Long {
    return dateString?.let {
        try {
            Instant.parse(it).toEpochMilliseconds()
        } catch (e: Exception) {
            // Log or handle the exception if needed
            0L // Return a default value (or handle differently)
        }
    } ?: 0L
}