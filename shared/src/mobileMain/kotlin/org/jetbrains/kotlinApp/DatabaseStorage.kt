package org.jetbrains.kotlinApp

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.kotlinApp.Database.DatabaseWrapper
import org.jetbrains.kotlinApp.podcast.PodcastEpisode
import org.jetbrains.kotlinApp.podcast.PodcastPlaybackState
import org.jetbrains.kotlinApp.storage.ApplicationStorage
import org.jetbrains.kotlinconf.GetAllChannelDetails
import org.jetbrains.kotlinconf.GetChannelsPaginated
import org.jetbrains.kotlinconf.GetChannelsWithFilters
import org.jetbrains.kotlinconf.PodcastChannels
import org.jetbrains.kotlinconf.PodcastEpisodes
import org.jetbrains.kotlinconf.SearchChannelsPaginated
import org.jetbrains.kotlinconf.SearchEpisodesBasicPaginated
import org.jetbrains.kotlinconf.SearchEpisodesByCategory

class DatabaseStorage(
    private val database: SessionDatabase,
    private val databaseWrapper: DatabaseWrapper
) : ApplicationStorage {
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val dbMutex = Mutex()

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
private suspend fun upsertChannelData(channelData: ChannelFullData) = withContext(Dispatchers.IO) {
    database.transaction {
        // Handle the channel itself
        val existing = database.sessionDatabaseQueries
            .selectChannelById(channelData.id.toLong())
            .executeAsOneOrNull()

        if (existing == null) {
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
                lastBuildDate = parseDateString(channelData.lastBuildDate)
            )
        } else {
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

        // Handle channel categories
        channelData.categories.forEach { categoryName ->
            database.sessionDatabaseQueries.insertChannelCategory(categoryName)
            val categoryId = database.sessionDatabaseQueries
                .selectChannelCategoryByName(categoryName)
                .executeAsOne()
                .id
            database.sessionDatabaseQueries.insertChannelCategoryMap(
                channelId = channelData.id.toLong(),
                categoryId = categoryId
            )
        }
    }
}

    /** Upsert or insert an episode. */
    private suspend fun upsertEpisodeData(channelId: Int, episodeData: EpisodeData) = withContext(Dispatchers.IO) {
        database.transaction {
            // Handle the episode itself
            val existing = episodeData.id?.let {
                database.sessionDatabaseQueries
                    .selectEpisodesById(it.toLong())
                    .executeAsOneOrNull()
            }

            if (existing == null) {
                database.sessionDatabaseQueries.insertEpisode(
                    id = episodeData.id?.toLong(),
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
                    id = existing.id
                )
            }

            // Handle episode categories
            episodeData.episodeCategory.forEach { categoryName ->
                database.sessionDatabaseQueries.insertEpisodeCategory(categoryName)
                val categoryId = database.sessionDatabaseQueries
                    .selectEpisodeCategoryByName(categoryName)
                    .executeAsOne()
                    .id
                database.sessionDatabaseQueries.insertEpisodeCategoryMap(
                    episodeId = episodeData.id!!.toLong(),
                    categoryId = categoryId
                )
            }
        }
    }


    suspend fun insertPodcastChannels(dto: ChannelDTO) = withContext(Dispatchers.IO) {
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

    suspend fun insertPodcastEpisodes(dto: EpisodeDTO) = withContext(Dispatchers.IO) {
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

    suspend fun syncPodcastData(podcastsData: List<ChannelFullData>) = withContext(Dispatchers.IO) {
        try {
            podcastsData.forEach { channel ->
                upsertChannelData(channel)
                val batchSize = 10
                channel.episodes.chunked(batchSize).forEach { episodeBatch ->
                    episodeBatch.forEach { episode ->
                        upsertEpisodeData(channel.id, episode)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error syncing podcast data: ${e.message}")
            throw e
        }
    }



    suspend fun syncPodcastDataBatch(podcastsData: List<ChannelFullData>) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            podcastsData.chunked(50).forEach { chunk ->
                database.transaction {
                    chunk.forEach { channel ->
                        // Upsert channel
                        val existingChannel = database.sessionDatabaseQueries
                            .selectChannelById(channel.id.toLong())
                            .executeAsOneOrNull()

                        if (existingChannel == null) {
                            database.sessionDatabaseQueries.insertChannel(
                                id = channel.id.toLong(),
                                title = channel.title,
                                link = channel.link,
                                description = channel.description,
                                copyright = channel.copyright,
                                language = channel.language ?: "",
                                author = channel.author ?: "",
                                ownerEmail = channel.ownerEmail ?: "",
                                ownerName = channel.ownerName ?: "",
                                imageUrl = channel.imageUrl ?: "",
                                lastBuildDate = parseDateString(channel.lastBuildDate)
                            )
                        } else {
                            database.sessionDatabaseQueries.updateChannel(
                                title = channel.title,
                                link = channel.link,
                                description = channel.description,
                                copyright = channel.copyright,
                                language = channel.language ?: "",
                                author = channel.author ?: "",
                                ownerEmail = channel.ownerEmail ?: "",
                                ownerName = channel.ownerName ?: "",
                                imageUrl = channel.imageUrl ?: "",
                                lastBuildDate = parseDateString(channel.lastBuildDate),
                                id = channel.id.toLong()
                            )
                        }

                        // Upsert channel categories
                        channel.categories.forEach { categoryName ->
                            database.sessionDatabaseQueries.insertChannelCategory(categoryName)
                            val categoryId = database.sessionDatabaseQueries
                                .selectChannelCategoryByName(categoryName)
                                .executeAsOne().id
                            database.sessionDatabaseQueries.insertChannelCategoryMap(
                                channelId = channel.id.toLong(),
                                categoryId = categoryId
                            )
                        }

                        // Upsert episodes
                        channel.episodes.forEach { episode ->
                            val existingEpisode = episode.id?.let {
                                database.sessionDatabaseQueries
                                    .selectEpisodesById(it.toLong())
                                    .executeAsOneOrNull()
                            }
                            if (existingEpisode == null) {
                                database.sessionDatabaseQueries.insertEpisode(
                                    id = episode.id?.toLong(),
                                    channelId = channel.id.toLong(),
                                    guid = episode.guid,
                                    title = episode.title,
                                    description = episode.description,
                                    link = episode.link,
                                    pubDate = episode.pubDate.toEpochMilliseconds(),
                                    duration = (episode.duration ?: 0).toLong(),
                                    explicit = if (episode.explicit) 1L else 0L,
                                    imageUrl = episode.imageUrl,
                                    mediaUrl = episode.mediaUrl ?: "",
                                    mediaType = episode.mediaType ?: "audio/mpeg",
                                    mediaLength = episode.mediaLength ?: 0
                                )
                            } else {
                                database.sessionDatabaseQueries.updateEpisode(
                                    channelId = channel.id.toLong(),
                                    guid = episode.guid,
                                    title = episode.title,
                                    description = episode.description,
                                    link = episode.link,
                                    pubDate = episode.pubDate.toEpochMilliseconds(),
                                    duration = (episode.duration ?: 0).toLong(),
                                    explicit = if (episode.explicit) 1L else 0L,
                                    imageUrl = episode.imageUrl,
                                    mediaUrl = episode.mediaUrl ?: "",
                                    mediaType = episode.mediaType ?: "audio/mpeg",
                                    mediaLength = episode.mediaLength ?: 0,
                                    id = existingEpisode.id
                                )
                            }

                            // Upsert episode categories
                            episode.episodeCategory.forEach { categoryName ->
                                database.sessionDatabaseQueries.insertEpisodeCategory(categoryName)
                                val categoryId = database.sessionDatabaseQueries
                                    .selectEpisodeCategoryByName(categoryName)
                                    .executeAsOne().id
                                database.sessionDatabaseQueries.insertEpisodeCategoryMap(
                                    episodeId = episode.id!!.toLong(),
                                    categoryId = categoryId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun savePlaybackState(state: PodcastPlaybackState) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            database.transaction {
                database.sessionDatabaseQueries.insertPlaybackState(
                    episodeId = state.episodeId,
                    channelId = state.channelId,
                    position = state.position,
                    url = state.url,
                    speed = state.speed,
                    isBoostEnabled = if (state.isBoostEnabled) 1L else 0L
                )
            }
        }
    }

    suspend fun loadPlaybackState(): PodcastPlaybackState? = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.selectLastPlaybackState()
            .executeAsOneOrNull()
            ?.let { row ->
                PodcastPlaybackState(
                    episodeId = row.episodeId,
                    channelId = row.channelId,
                    position = row.position,
                    url = row.url,
                    speed = row.speed,
                    isBoostEnabled = row.isBoostEnabled == 1L
                )
            }
    }

    suspend fun updateEpisodePosition(episodeId: Long, newPosition: Long) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            database.transaction {
                val currentTime = System.currentTimeMillis()
                val updatedEpisode = database.sessionDatabaseQueries
                    .selectEpisodeProgressById(episodeId)
                    .executeAsOneOrNull()

                if (updatedEpisode == null) {
                    database.sessionDatabaseQueries.insertEpisodeProgress(
                        episode_id = episodeId,
                        position_ms = newPosition,
                        last_updated = currentTime
                    )
                } else {
                    database.sessionDatabaseQueries.updateEpisodeProgress(
                        position_ms = newPosition,
                        last_updated = currentTime,
                        episode_id = episodeId
                    )
                }
            }
        }
    }


    fun getEpisodesForChannelFlow(channelId: Long): Flow<List<PodcastEpisodes>> {
        return database.sessionDatabaseQueries.selectEpisodesByChannelId(channelId)
            .asFlow()
            .mapToList(Dispatchers.Default)
    }


    fun getEpisodeByIdFlow(episodeId: Long): Flow<PodcastEpisode> {
        return database.sessionDatabaseQueries
            .selectEpisodesById(episodeId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { episode ->
                PodcastEpisode(
                    id = episode.id.toString(),
                    channelId = episode.channelId.toString(),
                    title = episode.title,
                    audioUrl = episode.mediaUrl,
                    duration = episode.duration,
                    imageUrl = episode.imageUrl,
                    description = episode.description,
                    pubDate = episode.pubDate
                )
            }
    }

    fun getChannelByIdFlow(channelId: Long): Flow<PodcastChannels> {
        return database.sessionDatabaseQueries
            .selectChannelById(channelId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    suspend fun getChannelById(channelId: Long): GetAllChannelDetails? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            try {
                database.sessionDatabaseQueries.getAllChannelDetailsById(
                    channelId = channelId,
                    mapper = { id, title, link, description, copyright, language, author,
                               ownerEmail, ownerName, imageUrl, lastBuildDate, episodeCount,
                               earliestEpisodePubDate, latestEpisodePubDate, categories ->
                        GetAllChannelDetails(
                            id = id,
                            title = title,
                            link = link,
                            description = description,
                            copyright = copyright,
                            language = language,
                            author = author,
                            ownerEmail = ownerEmail,
                            ownerName = ownerName,
                            imageUrl = imageUrl,
                            lastBuildDate = lastBuildDate,
                            episodeCount = episodeCount,
                            earliestEpisodePubDate = earliestEpisodePubDate,
                            latestEpisodePubDate = latestEpisodePubDate,
                            categories = categories
                        )
                    }
                ).executeAsOneOrNull()
            } catch (e: Exception) {
                println("Error getting channel by ID $channelId: ${e.message}")
                null
            }
        }
    }

    /**
     * Get all episode tags for a specific channel
     */
    suspend fun getEpisodeTagsForChannel(channelId: Long): List<String> = withContext(Dispatchers.IO) {
        try {
            // Simple query to get all tags from this channel's episodes
            database.sessionDatabaseQueries.getChannelEpisodeTags(channelId)
                .executeAsList()
                .flatMap { it?.split(",") ?: emptyList() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        } catch (e: Exception) {
            println("Error getting episode tags for channel: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get tags for a specific episode - optimized query
     */
    fun getEpisodeTagsById(episodeId: Long): List<String> {
        return try {
            // Get episode categories
            val categories = database.sessionDatabaseQueries.getEpisodeTagsById(episodeId)
                .executeAsList()

            // Flatten and clean up
            categories.filterNotNull()
                .distinct()
        } catch (e: Exception) {
            println("Error getting tags for episode $episodeId: ${e.message}")
            emptyList()
        }
    }

    fun getEpisodePosition(episodeId: Long): Long? {
        // Return the stored position, if any. If no record found, returns null.
        return database.sessionDatabaseQueries
            .selectEpisodeProgressById(episodeId)
            .executeAsOneOrNull()
            ?.position_ms
    }

    /**
     * Get episode position flow with better error handling
     */
    fun getEpisodePositionFlow(episodeId: Long): Flow<Long?> {
        // The query selectEpisodeProgressById returns the row if it exists.
        return database.sessionDatabaseQueries
            .selectEpisodeProgressById(episodeId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.position_ms }  // We only need the "position_ms" field
            .catch { error ->
                // Log error but don't crash
                println("Error getting episode position: ${error.message}")
                emit(null)
            }
    }

    /**
     * Optimized batch fetch of episode tags
     */
    suspend fun getEpisodeTagsForEpisodes(episodeIds: List<Long>): Map<Long, List<String>> = withContext(Dispatchers.IO) {
        if (episodeIds.isEmpty()) return@withContext emptyMap()

        dbMutex.withLock {
            try {
                val result = mutableMapOf<Long, List<String>>()

                // Process in batches of 50 to avoid SQLite parameter limits
                episodeIds.chunked(50).forEach { chunk ->
                    // For each episode in this chunk, get its tags
                    chunk.forEach { episodeId ->
                        val tags = database.sessionDatabaseQueries.getEpisodeTagsById(episodeId)
                            .executeAsList()
                            .filterNotNull()
                            .distinct()

                        result[episodeId] = tags
                    }
                }

                result
            } catch (e: Exception) {
                println("Error getting episode tags in batch: ${e.message}")
                emptyMap()
            }
        }
    }

    /**
     * Get paginated channels
     */
    fun getChannelsPaginated(page: Int, pageSize: Int): Flow<List<GetChannelsPaginated>> {
        return database.sessionDatabaseQueries.getChannelsPaginated(
            limit = pageSize.toLong(),
            offset = (page * pageSize).toLong()
        )
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    /**
     * Get total channel count
     */
    suspend fun getChannelCount(): Long = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getChannelCount().executeAsOne()
    }

    /**
     * Get episodes for a channel with pagination
     */
    fun getEpisodesForChannelPaginated(channelId: Long, page: Int, pageSize: Int): Flow<List<PodcastEpisodes>> {
        return database.sessionDatabaseQueries.getEpisodesForChannelPaginated(
            channelId = channelId,
            limit = pageSize.toLong(),
            offset = (page * pageSize).toLong()
        )
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    /**
     * Get total episode count for a channel
     */
    suspend fun getEpisodeCountForChannel(channelId: Long): Long = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getEpisodeCountForChannel(channelId).executeAsOne()
    }

    /**
     * Get all session tags
     */
    suspend fun getAllSessionTags(): List<String> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getAllSessionTags().executeAsList()
    }

    /**
     * Get all channel categories
     */
    suspend fun getAllChannelCategories(): List<String?> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getAllChannelCategories().executeAsList()
    }

    /**
     * Get all episode categories
     */
    suspend fun getAllEpisodeCategories(): List<String?> = withContext(Dispatchers.IO) {
        database.sessionDatabaseQueries.getAllEpisodeCategories().executeAsList()
    }

    /**
     * Search channels with pagination - raw database access
     */
    suspend fun searchChannelsPaginated(
        query: String,
        activeTags: List<String>,
        page: Int,
        pageSize: Int
    ): List<SearchChannelsPaginated> = withContext(Dispatchers.IO) {
        val queryPattern = if (query.isNotBlank()) "%$query%" else "%"
        val hasTagFilter = if (activeTags.isNotEmpty()) 1L else 0L

        database.sessionDatabaseQueries.searchChannelsPaginated(
            query = queryPattern,
            hasTagFilter = hasTagFilter,
            tags = activeTags,
            limit = pageSize.toLong(),
            offset = (page * pageSize).toLong()
        ).executeAsList()
    }

    /**
     * Count search channels results
     */
    suspend fun countSearchChannels(
        query: String,
        activeTags: List<String>
    ): Long = withContext(Dispatchers.IO) {
        val queryPattern = if (query.isNotBlank()) "%$query%" else "%"
        val hasTagFilter = if (activeTags.isNotEmpty()) 1L else 0L

        database.sessionDatabaseQueries.countSearchChannels(
            query = queryPattern,
            hasTagFilter = hasTagFilter,
            tags = activeTags
        ).executeAsOne()
    }

    /**
     * Search episodes with pagination - raw database access
     */
    suspend fun searchEpisodesBasicPaginated(
        query: String,
        activeTags: List<String>,
        page: Int,
        pageSize: Int
    ): List<SearchEpisodesBasicPaginated> = withContext(Dispatchers.IO) {
        val queryPattern = if (query.isNotBlank()) "%$query%" else "%"
        val hasTagFilter = if (activeTags.isNotEmpty()) 1L else 0L

        println("Executing episode search with page=$page, pageSize=$pageSize")

        // Use this modified query instead
        database.sessionDatabaseQueries.searchEpisodesBasicPaginated(
            query = queryPattern,
            hasTagFilter = hasTagFilter,
            tags = activeTags,
            limit = pageSize.toLong(),
            offset = (page * pageSize).toLong()
        ).executeAsList()
    }

    /**
     * Count search episodes results
     */
    suspend fun countSearchEpisodes(
        query: String,
        activeTags: List<String>
    ): Long = withContext(Dispatchers.IO) {
        val queryPattern = if (query.isNotBlank()) "%$query%" else "%"
        val hasTagFilter = if (activeTags.isNotEmpty()) 1L else 0L

        database.sessionDatabaseQueries.countSearchEpisodes(
            query = queryPattern,
            hasTagFilter = hasTagFilter,
            tags = activeTags
        ).executeAsOne()
    }

    /** * Get channels with cursor-based pagination */
    suspend fun getChannelsList(cursor: Long? = null, limit: Int = 20): List<GetAllChannelDetails> =
        withContext(Dispatchers.IO) {
            dbMutex.withLock {
                try {
                    database.sessionDatabaseQueries.getChannelsCursor(
                        cursor = cursor,
                        limit = limit.toLong(),
                        mapper = { id, title, link, description, copyright, language, author,
                                   ownerEmail, ownerName, imageUrl, lastBuildDate, episodeCount,
                                   earliestEpisodePubDate, latestEpisodePubDate, categories ->
                            GetAllChannelDetails(id, title, link, description, copyright, language,
                                author, ownerEmail, ownerName, imageUrl, lastBuildDate,
                                episodeCount, earliestEpisodePubDate,
                                latestEpisodePubDate, categories)
                        }
                    ).executeAsList()
                } catch (e: Exception) {
                    println("Database error in getChannelsList: ${e.message}")
                    emptyList()
                }
            }
        }

    suspend fun getChannelsListBackward(cursor: Long, limit: Int = 20): List<GetAllChannelDetails> =
        withContext(Dispatchers.IO) {
            dbMutex.withLock {
                try {
                    database.sessionDatabaseQueries.getChannelsCursorBackward(
                        cursor = cursor,
                        limit = limit.toLong(),
                        mapper = { id, title, link, description, copyright, language, author,
                                   ownerEmail, ownerName, imageUrl, lastBuildDate, episodeCount,
                                   earliestEpisodePubDate, latestEpisodePubDate, categories ->
                            GetAllChannelDetails(id, title, link, description, copyright, language,
                                author, ownerEmail, ownerName, imageUrl, lastBuildDate,
                                episodeCount, earliestEpisodePubDate,
                                latestEpisodePubDate, categories)
                        }
                    ).executeAsList()
                } catch (e: Exception) {
                    println("Database error in getChannelsListBackward: ${e.message}")
                    emptyList()
                }
            }
        }

    /** * Get episodes with cursor-based pagination */
    fun getEpisodesForChannelCursor(
        channelId: Long,
        cursor: Long? = null,
        limit: Int = 20
    ): Flow<List<PodcastEpisodes>> {
        return database.sessionDatabaseQueries.getEpisodesForChannelCursor_Updated(
            channelId = channelId,
            cursor = cursor,
            limit = limit.toLong()
        )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    /** * Get episodes with cursor-based pagination (backward) */
    fun getEpisodesForChannelCursorBackward(
        channelId: Long,
        cursor: Long,
        limit: Int = 20
    ): Flow<List<PodcastEpisodes>> {
        return database.sessionDatabaseQueries.getEpisodesForChannelCursorBackward(
            channelId = channelId,
            cursor = cursor,
            limit = limit.toLong(),
            mapper = { id, channelId_, guid, title, description, link, pubDate, duration, explicit, imageUrl, mediaUrl, mediaType, mediaLength ->
                PodcastEpisodes(id, channelId_, guid, title, description, link, pubDate, duration, explicit, imageUrl, mediaUrl, mediaType, mediaLength)
            }
        )
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun getChannelIdsByTags(tags: List<String>): List<Long> = withContext(Dispatchers.IO) {
        if (tags.isEmpty()) return@withContext emptyList()

        try {
            database.sessionDatabaseQueries.getChannelIdsByTags(tags).executeAsList()
        } catch (e: Exception) {
            println("Error getting channel IDs by tags: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChannelsWithFilters(
        tags: List<String>,
        query: String?,
        cursor: Long?,
        limit: Long,
        backward: Long
    ): List<GetChannelsWithFilters> = withContext(Dispatchers.IO) {
        try {
            database.sessionDatabaseQueries.getChannelsWithFilters(
                useTagFilter = if (tags.isEmpty()) 0L else 1L,
                tags = tags,
                useTextFilter = if (query.isNullOrBlank()) 0L else 1L,
                query = query ?: "",
                cursor = cursor,
                limit = limit,
                backward = backward
            ).executeAsList()
        } catch (e: Exception) {
            println("Error getting channels with filters: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEpisodeIdsByTags(tags: List<String>): List<Long> = withContext(Dispatchers.IO) {
        if (tags.isEmpty()) return@withContext emptyList()

        try {
            database.sessionDatabaseQueries.getEpisodeIdsByTags(tags).executeAsList()
        } catch (e: Exception) {
            println("Error getting episode IDs by tags: ${e.message}")
            emptyList()
        }
    }

    // In DatabaseStorage.kt
    suspend fun searchEpisodesByCategory(
        query: String,
        tags: List<String>,
        cursor: Long?,
        limit: Long,
        backward: Long
    ): List<SearchEpisodesByCategory> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            try {
                database.sessionDatabaseQueries.searchEpisodesByCategory(
                    query = query,
                    hasTagFilter = if (tags.isEmpty()) 0L else 1L,
                    tags = tags,
                    cursor = cursor,
                    backward = backward,
                    limit = limit
                ).executeAsList()
            } catch (e: Exception) {
                println("Error searching episodes by category: ${e.message}")
                emptyList()
            }
        }
    }

    // Get all unique channel categories
    suspend fun getAllUniqueChannelCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            database.sessionDatabaseQueries.getAllUniqueChannelCategories()
                .executeAsList()
        } catch (e: Exception) {
            println("Error getting unique channel categories: ${e.message}")
            emptyList()
        }
    }

    // Get all unique episode categories
    suspend fun getAllUniqueEpisodeCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            database.sessionDatabaseQueries.getAllUniqueEpisodeCategories()
                .executeAsList()
        } catch (e: Exception) {
            println("Error getting unique episode categories: ${e.message}")
            emptyList()
        }
    }

    // Data class for episodes with channel names
    data class EpisodeWithChannel(
        val episode: PodcastEpisodes,
        val channelTitle: String
    )

    // Get episodes across all channels
    suspend fun getEpisodesAcrossChannels(limit: Int = 100): List<EpisodeWithChannel> = withContext(Dispatchers.IO) {
        try {
            database.sessionDatabaseQueries.getEpisodesAcrossChannels(
                channelId = -1,
                limit = limit.toLong()
            ).executeAsList().map { row ->
                EpisodeWithChannel(
                    episode = PodcastEpisodes(
                        id = row.id,
                        channelId = row.channelId,
                        guid = row.guid,
                        title = row.title,
                        description = row.description,
                        link = row.link,
                        pubDate = row.pubDate,
                        duration = row.duration,
                        explicit = row.explicit,
                        imageUrl = row.imageUrl,
                        mediaUrl = row.mediaUrl,
                        mediaType = row.mediaType,
                        mediaLength = row.mediaLength
                    ),
                    channelTitle = row.channelTitle
                )
            }
        } catch (e: Exception) {
            println("Error getting episodes across channels: ${e.message}")
            emptyList()
        }
    }

    // Get channel categories by IDs
    suspend fun getChannelCategoriesByIds(channelIds: List<Long>): Map<String, List<String>> {
        if (channelIds.isEmpty()) return emptyMap()

        return withContext(Dispatchers.IO) {
            try {
                val result = mutableMapOf<String, List<String>>()

                // Process in batches to avoid SQLite parameter limits
                channelIds.chunked(50).forEach { chunk ->
                    database.sessionDatabaseQueries.getChannelCategories(chunk)
                        .executeAsList()
                        .forEach { row ->
                            val categoryList = row.categories
                                ?.split(",")
                                ?.map { it.trim() }
                                ?.filter { it.isNotBlank() }
                                ?: emptyList()

                            result[row.channelId.toString()] = categoryList
                        }
                }

                result
            } catch (e: Exception) {
                println("Error getting channel categories by IDs: ${e.message}")
                emptyMap()
            }
        }
    }

    // Get episode categories by IDs
    suspend fun getEpisodeCategoriesByIds(episodeIds: List<Long>): Map<String, List<String>> {
        if (episodeIds.isEmpty()) return emptyMap()

        return withContext(Dispatchers.IO) {
            try {
                val result = mutableMapOf<String, List<String>>()

                // Process in batches to avoid SQLite parameter limits
                episodeIds.chunked(50).forEach { chunk ->
                    database.sessionDatabaseQueries.getEpisodeCategories(chunk)
                        .executeAsList()
                        .forEach { row ->
                            val categoryList = row.categories
                                ?.split(",")
                                ?.map { it.trim() }
                                ?.filter { it.isNotBlank() }
                                ?: emptyList()

                            result[row.episodeId.toString()] = categoryList
                        }
                }

                result
            } catch (e: Exception) {
                println("Error getting episode categories by IDs: ${e.message}")
                emptyMap()
            }
        }
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
private fun nowMs(): Long = System.currentTimeMillis()