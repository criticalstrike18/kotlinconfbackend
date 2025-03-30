package org.jetbrains.kotlinApp.backend

import com.benasher44.uuid.uuid4
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.kotlinApp.CategoriesTable
import org.jetbrains.kotlinApp.ChannelFullData
import org.jetbrains.kotlinApp.Conference
import org.jetbrains.kotlinApp.ConferenceSessionRequest
import org.jetbrains.kotlinApp.EpisodeData
import org.jetbrains.kotlinApp.FeedbackInfo
import org.jetbrains.kotlinApp.RoomTable
import org.jetbrains.kotlinApp.Score
import org.jetbrains.kotlinApp.Session
import org.jetbrains.kotlinApp.SessionInfo
import org.jetbrains.kotlinApp.Speaker
import org.jetbrains.kotlinApp.SpeakerInfo
import org.jetbrains.kotlinApp.VoteInfo
import org.jetbrains.kotlinApp.backend.Votes.sessionId
import java.time.LocalDateTime


internal class Store(application: Application) {

    init {
        val hikariConfig = HikariConfig()
        val dbConfig = application.environment.config.config("database")
        val dbHost = dbConfig.getOrNull("host")
        val dbPoolSize = dbConfig.property("poolSize").getString().toInt()
        val database = dbConfig.getOrNull("database")

        if (dbHost?.isNotBlank() == true) {
            hikariConfig.apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = "jdbc:postgresql://$dbHost/$database"
                username = dbConfig.getOrNull("user")
                password = dbConfig.getOrNull("password")
                maximumPoolSize = dbPoolSize
            }
        } else {
            application.log.info("Host not found")
//            hikariConfig.jdbcUrl = "jdbc:h2:file:./kotlinconfg"
//            hikariConfig.validate()
        }

        application.log.info("Connecting to database at '${hikariConfig.jdbcUrl}")

        val connectionPool = HikariDataSource(hikariConfig)
        Database.connect(connectionPool)

        transaction {
            SchemaUtils.create(
                Users,
                Votes,
                Feedback,
                ConferenceSessions,
                ConferenceSpeakers,
                ConferenceRooms,
                ConferenceCategories,
                SessionSpeakers,
                SessionCategories,
                PodcastChannels,
                PodcastEpisodes,
                PodcastChannelCategories,
                PodcastEpisodeCategories,
                ChannelCategoryMap,
                EpisodeCategoryMap,
                PodcastRequest
            )
        }
    }

    suspend fun validateUser(uuid: String): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().where { Users.userId eq uuid }.count() != 0L
    }

    suspend fun createUser(
        uuidValue: String, timestampValue: LocalDateTime
    ): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val count = Users.selectAll().where { Users.userId eq uuidValue }.count()
        if (count != 0L) return@newSuspendedTransaction false

        Users.insert {
            it[userId] = uuidValue
            it[timestamp] = timestampValue.toString()
        }

        return@newSuspendedTransaction true
    }

    suspend fun getVotes(uuid: String): List<VoteInfo> = newSuspendedTransaction(Dispatchers.IO) {
        Votes.selectAll().where { Votes.userId eq uuid }
            .map { VoteInfo(it[sessionId], Score.fromValue(it[Votes.rating])) }

    }

    suspend fun getAllVotes(): List<VoteInfo> = newSuspendedTransaction(Dispatchers.IO) {
        Votes.selectAll()
            .map { VoteInfo(it[sessionId], Score.fromValue(it[Votes.rating])) }
    }

    suspend fun changeVote(
        userIdValue: String,
        sessionIdValue: String,
        scoreValue: Score?,
        timestampValue: LocalDateTime
    ) {
        if (scoreValue == null) {
            deleteVote(userIdValue, sessionIdValue)
            return
        }

        newSuspendedTransaction(Dispatchers.IO) {
            val count = Votes.selectAll()
                .where { (Votes.userId eq userIdValue) and (sessionId eq sessionIdValue) }.count()

            if (count == 0L) {
                Votes.insert {
                    it[userId] = userIdValue
                    it[sessionId] = sessionIdValue
                    it[rating] = scoreValue.value
                    it[timestamp] = timestampValue.toString()
                }
                return@newSuspendedTransaction
            }

            Votes.update({ (Votes.userId eq userIdValue) and (sessionId eq sessionIdValue) }) {
                it[rating] = scoreValue.value
            }
        }

    }

    suspend fun setFeedback(
        userIdValue: String,
        sessionIdValue: String,
        feedbackValue: String,
        timestampValue: LocalDateTime
    ): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        Feedback.insert {
            it[userId] = userIdValue
            it[sessionId] = sessionIdValue
            it[feedback] = feedbackValue
            it[timestamp] = timestampValue.toString()
        }.insertedCount > 0
    }

    private suspend fun deleteVote(uuid: String, sessionId: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            Votes.deleteWhere { (userId eq uuid) and (Votes.sessionId eq sessionId) }
        }
    }

    suspend fun getFeedbackSummary(): List<FeedbackInfo> = newSuspendedTransaction {
        Feedback.selectAll().map {
            FeedbackInfo(
                it[Feedback.sessionId], it[Feedback.feedback]
            )
        }
    }

    suspend fun getConferenceData(): Conference = newSuspendedTransaction(Dispatchers.IO) {
        val sessions =  ConferenceSessions.selectAll().map { sessionRow ->
            // Fetch all speaker IDs for the current session
            val speakerIds = SessionSpeakers.selectAll()
                .where { SessionSpeakers.sessionId eq sessionRow[ConferenceSessions.id] }
                .map { it[SessionSpeakers.speakerId] }

            // Match the roomId with ConferenceRooms to fetch the room name
            val roomName = sessionRow[ConferenceSessions.roomId]?.let { roomId ->
                ConferenceRooms.selectAll().where { ConferenceRooms.id eq roomId }
                    .map { it[ConferenceRooms.name] }
                    .singleOrNull()
            } ?: "Unknown Room"

            // Fetch all category titles for the current session
            val tags = SessionCategories.selectAll()
                .where { SessionCategories.sessionId eq sessionRow[ConferenceSessions.id] }
                .mapNotNull { categoryRow ->
                    ConferenceCategories.selectAll()
                        .where { ConferenceCategories.id eq categoryRow[SessionCategories.categoryId] }
                        .map { it[ConferenceCategories.title] }
                        .singleOrNull()
                }
            val startsAt = sessionRow[ConferenceSessions.startsAt]
            val endsAt = sessionRow[ConferenceSessions.endsAt]
            // Map session data to the Session data class
            Session(
                id = sessionRow[ConferenceSessions.id],
                title = sessionRow[ConferenceSessions.title],
                description = sessionRow[ConferenceSessions.description] ?: "",
                speakerIds = speakerIds,
                location = roomName,
                startsAt = startsAt.toGMTDate(),
                endsAt = endsAt.toGMTDate(),
                tags = tags
            )
        }
        val speakers = ConferenceSpeakers.selectAll().map { speakerRow ->
            Speaker(
                id = speakerRow[ConferenceSpeakers.id],
                name = "${speakerRow[ConferenceSpeakers.firstName]} ${speakerRow[ConferenceSpeakers.lastName]}",
                position = speakerRow[ConferenceSpeakers.tagLine] ?: "",
                description = speakerRow[ConferenceSpeakers.bio] ?: "",
                photoUrl = speakerRow[ConferenceSpeakers.profilePicture] ?: ""
            )
        }
        Conference(sessions, speakers)
    }

    suspend fun addSession(session: ConferenceSessionRequest): String = newSuspendedTransaction(Dispatchers.IO) {
        val duplicate = ConferenceSessions.selectAll().where {
            (ConferenceSessions.title eq session.title) and
                    (ConferenceSessions.description eq session.description)
        }.firstOrNull()
        if (duplicate != null) {
            // You can throw an exception or handle the error as needed
            throw IllegalArgumentException("A session with the same title, description already exists.")
        }
        val generatedId = uuid4().toString()
        ConferenceSessions.insert {
            it[id] = generatedId
            it[title] = session.title
            it[description] = session.description
            it[startsAt] = session.startsAt.toKotlinInstant()
            it[endsAt] = session.endsAt.toKotlinInstant()
            it[roomId] = session.roomId
            it[isServiceSession] = session.isServiceSession
            it[isPlenumSession] = session.isPlenumSession
            it[status] = session.status
        }

        // Insert speaker associations
        session.speakerIds.forEach { speakerId ->
            SessionSpeakers.insert {
                it[sessionId] = generatedId
                it[SessionSpeakers.speakerId] = speakerId
            }
        }

        // Insert category associations
        session.categoryIds.forEach { categoryId ->
            SessionCategories.insert {
                it[sessionId] = generatedId
                it[SessionCategories.categoryId] = categoryId
            }
        }
        generatedId
    }

    suspend fun addSpeaker(speaker:ConferenceSpeakerRequest) = newSuspendedTransaction(Dispatchers.IO) {
        val generatedId = uuid4().toString()
        ConferenceSpeakers.insert {
            it[id] = generatedId
            it[firstName] = speaker.firstName
            it[lastName] = speaker.lastName
            it[bio] = speaker.bio
            it[tagLine] = speaker.tagLine
            it[profilePicture] = speaker.profilePicture
            it[isTopSpeaker] = speaker.isTopSpeaker
        }
    }

    suspend fun addRoom(room: ConferenceRoomRequest): Int = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceRooms.insert {
            it[name] = room.name
            it[sort] = room.sort
        }[ConferenceRooms.id]  // Returns the generated ID
    }

    suspend fun addCategory(category:ConferenceCategoriesRequest) = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceCategories.insert {
            it[title] = category.title
            it[sort] = category.sort
            it[type] = category.type
        }
    }

    suspend fun getAllSessions(): List<SessionInfo> = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceSessions.selectAll().map { row ->
            val speakerIds = SessionSpeakers.selectAll()
                .where { SessionSpeakers.sessionId eq row[ConferenceSessions.id] }
                .map { it[SessionSpeakers.speakerId] }

            val categoryIds = SessionCategories.selectAll()
                .where { SessionCategories.sessionId eq row[ConferenceSessions.id] }
                .map { it[SessionCategories.categoryId] }

            SessionInfo(
                id = row[ConferenceSessions.id],
                title = row[ConferenceSessions.title],
                description = row[ConferenceSessions.description],
                startsAt = row[ConferenceSessions.startsAt].toGMTDate(),
                endsAt = row[ConferenceSessions.endsAt].toGMTDate(),
                roomId = row[ConferenceSessions.roomId],
                isServiceSession = row[ConferenceSessions.isServiceSession],
                isPlenumSession = row[ConferenceSessions.isPlenumSession],
                status = row[ConferenceSessions.status],
                speakerIds = speakerIds,
                categoryIds = categoryIds
            )
        }
    }

    suspend fun getSessionById(sessionId: String): SessionInfo? = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceSessions.selectAll().where { ConferenceSessions.id eq sessionId }
            .mapNotNull { row ->
                val speakerIds = SessionSpeakers.selectAll()
                    .where { SessionSpeakers.sessionId eq sessionId }
                    .map { it[SessionSpeakers.speakerId] }

                val categoryIds = SessionCategories.selectAll()
                    .where { SessionCategories.sessionId eq sessionId }
                    .map { it[SessionCategories.categoryId] }

                SessionInfo(
                    id = row[ConferenceSessions.id],
                    title = row[ConferenceSessions.title],
                    description = row[ConferenceSessions.description],
                    startsAt = row[ConferenceSessions.startsAt].toGMTDate(),
                    endsAt = row[ConferenceSessions.endsAt].toGMTDate(),
                    roomId = row[ConferenceSessions.roomId],
                    isServiceSession = row[ConferenceSessions.isServiceSession],
                    isPlenumSession = row[ConferenceSessions.isPlenumSession],
                    status = row[ConferenceSessions.status],
                    speakerIds = speakerIds,
                    categoryIds = categoryIds
                )
            }.singleOrNull()
    }

    suspend fun getSpeakerById(speakerId: String): SpeakerInfo? = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceSpeakers.selectAll().where { ConferenceSpeakers.id eq speakerId }
            .mapNotNull { row ->
                SpeakerInfo(
                    id = row[ConferenceSpeakers.id],
                    firstName = row[ConferenceSpeakers.firstName],
                    lastName = row[ConferenceSpeakers.lastName],
                    bio = row[ConferenceSpeakers.bio],
                    tagLine = row[ConferenceSpeakers.tagLine],
                    profilePicture = row[ConferenceSpeakers.profilePicture],
                )
            }.singleOrNull()
    }

    suspend fun addSessionSpeaker(sessionId: String, speakerId: String) = newSuspendedTransaction(Dispatchers.IO) {
        // First check if the relationship already exists
        val exists = SessionSpeakers.selectAll()
            .where { (SessionSpeakers.sessionId eq sessionId) and (SessionSpeakers.speakerId eq speakerId) }
            .count() > 0

        if (!exists) {
            // Only insert if it doesn't exist
            SessionSpeakers.insert {
                it[SessionSpeakers.sessionId] = sessionId
                it[SessionSpeakers.speakerId] = speakerId
            }
        }
    }

    suspend fun getCategoryById(categoryId: Int): CategoriesTable? = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceCategories.selectAll()
            .where { ConferenceCategories.id eq categoryId }
            .mapNotNull { row ->
                CategoriesTable(
                    id = row[ConferenceCategories.id].toLong(),
                    title = row[ConferenceCategories.title],
                    sort = row[ConferenceCategories.sort]?.toInt(),
                    type = row[ConferenceCategories.type]
                )
            }.singleOrNull()
    }

    suspend fun addSessionCategory(sessionId: String, categoryId: Int) = newSuspendedTransaction(Dispatchers.IO) {
        // First check if the relationship already exists
        val exists = SessionCategories.selectAll()
            .where {
                (SessionCategories.sessionId eq sessionId) and
                        (SessionCategories.categoryId eq categoryId)
            }
            .count() > 0

        if (!exists) {
            // Only insert if it doesn't exist
            SessionCategories.insert {
                it[SessionCategories.sessionId] = sessionId
                it[SessionCategories.categoryId] = categoryId
            }
        }
    }


    suspend fun getAllCategories(): List<CategoriesTable> = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceCategories.selectAll().map { row ->
            CategoriesTable(
                id = row[ConferenceCategories.id].toLong(),
                title = row[ConferenceCategories.title],
                sort = row[ConferenceCategories.sort],
                type = row[ConferenceCategories.type]
            )
        }
    }

    suspend fun getAllRooms(): List<RoomTable> = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceRooms.selectAll().map { row ->
            RoomTable(
                id = row[ConferenceRooms.id].toLong(),
                name = row[ConferenceRooms.name],
                sort = row[ConferenceRooms.sort]
            )
        }
    }

    suspend fun getAllSpeakers(): List<SpeakerInfo> = newSuspendedTransaction(Dispatchers.IO) {
        ConferenceSpeakers.selectAll().map { row ->
            SpeakerInfo(
                id = row[ConferenceSpeakers.id],
                firstName = row[ConferenceSpeakers.firstName],
                lastName = row[ConferenceSpeakers.lastName],
                bio = row[ConferenceSpeakers.bio],
                tagLine = row[ConferenceSpeakers.tagLine],
                profilePicture = row[ConferenceSpeakers.profilePicture],
                isTopSpeaker = row[ConferenceSpeakers.isTopSpeaker]
            )
        }
    }

    suspend fun getSessionSpeakers(sessionId: String): List<String> = newSuspendedTransaction(Dispatchers.IO) {
        SessionSpeakers.selectAll().where { SessionSpeakers.sessionId eq sessionId }
            .map { it[SessionSpeakers.speakerId] }
    }

    suspend fun getSessionCategories(sessionId: String): List<Int> = newSuspendedTransaction(Dispatchers.IO) {
        SessionCategories.selectAll().where { SessionCategories.sessionId eq sessionId }
            .map { it[SessionCategories.categoryId]}
    }

    suspend fun storePodcastQuery(
        userIdValue: String,
        titleValue: String,
        authorValue: String,
        rssLinkValue: String,
    ): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        PodcastRequest.insert {
            it[userId] = userIdValue
            it[title] = titleValue
            it[author] = authorValue
            it[rssLink] = rssLinkValue
        }.insertedCount > 0
    }

    suspend fun storePodcastData(importRequest: PodcastImportRequest): Int =
        newSuspendedTransaction(Dispatchers.IO) {
            // 1. Insert the channel and get its id.
            val channelId = PodcastChannels.insert { ch ->
                ch[title] = importRequest.channel.title
                ch[link] = importRequest.channel.link
                ch[description] = importRequest.channel.description
                ch[copyright] = importRequest.channel.copyright
                ch[language] = importRequest.channel.language ?: ""
                ch[author] = importRequest.channel.author ?: ""
                ch[ownerEmail] = importRequest.channel.ownerEmail ?: ""
                ch[ownerName] = importRequest.channel.ownerName ?: ""
                ch[imageUrl] = importRequest.channel.imageUrl ?: ""

                // Parse lastBuildDate if available
                val dateStr = importRequest.channel.lastBuildDate
                ch[lastBuildDate] = if (dateStr != null) {
                    try {
                        Instant.parse(dateStr)
                    } catch (e: Exception) {
                        throw ServiceUnavailable()
                    }
                } else {
                    throw ServiceUnavailable()
                }
            } get PodcastChannels.id

            // 2. Insert/get channel categories and map them to the channel
            importRequest.categories.forEach { catName ->
                // Find or create channel category
                val categoryId = PodcastChannelCategories
                    .selectAll().where { PodcastChannelCategories.name eq catName }
                    .map { it[PodcastChannelCategories.id] }
                    .singleOrNull() ?: (PodcastChannelCategories.insert {
                    it[name] = catName
                } get PodcastChannelCategories.id)

                // Create mapping between channel and channel category
                ChannelCategoryMap.insert {
                    it[ChannelCategoryMap.channelId] = channelId
                    it[ChannelCategoryMap.categoryId] = categoryId
                }
            }

            // 3. Insert episodes and their categories
            importRequest.episodes.forEach { ep ->
                val episodeId = PodcastEpisodes.insert { e ->
                    e[PodcastEpisodes.channelId] = channelId
                    e[guid] = ep.guid
                    e[title] = ep.title
                    e[description] = ep.description
                    e[link] = ep.link
                    e[pubDate] = ep.pubDate
                    e[duration] = ep.duration ?: 0
                    e[explicit] = ep.explicit
                    e[imageUrl] = ep.imageUrl
                    e[mediaUrl] = ep.mediaUrl ?: ""
                    e[mediaType] = ep.mediaType ?: "audio/mpeg"
                    e[mediaLength] = ep.mediaLength ?: 0
                } get PodcastEpisodes.id

                // Insert episode categories if present
                ep.episodeCategory.forEach { catName ->
                    // Find or create episode category
                    val categoryId = PodcastEpisodeCategories
                        .selectAll().where { PodcastEpisodeCategories.name eq catName }
                        .map { it[PodcastEpisodeCategories.id] }
                        .singleOrNull() ?: (PodcastEpisodeCategories.insert {
                        it[name] = catName
                    } get PodcastEpisodeCategories.id)

                    // Create mapping between episode and episode category
                    EpisodeCategoryMap.insert {
                        it[EpisodeCategoryMap.episodeId] = episodeId
                        it[EpisodeCategoryMap.categoryId] = categoryId
                    }
                }
            }

            channelId
        }

    suspend fun getAllPodcastData(): List<ChannelFullData> = newSuspendedTransaction(Dispatchers.IO) {
        // 1. Get all channel categories in one query for efficient lookup
        val allChannelCategories = PodcastChannelCategories
            .selectAll()
            .associate { it[PodcastChannelCategories.id] to it[PodcastChannelCategories.name] }

        // 2. Get all episode categories in one query for efficient lookup
        val allEpisodeCategories = PodcastEpisodeCategories
            .selectAll()
            .associate { it[PodcastEpisodeCategories.id] to it[PodcastEpisodeCategories.name] }

        // 3. Get all channel-category mappings in one query
        val channelCategories = ChannelCategoryMap
            .selectAll()
            .groupBy({ it[ChannelCategoryMap.channelId] }) { it[ChannelCategoryMap.categoryId] }
            .mapValues { (_, categoryIds) -> categoryIds.mapNotNull { allChannelCategories[it] } }

        // 4. Get all episode-category mappings in one query
        val episodeCategories = EpisodeCategoryMap
            .selectAll()
            .groupBy({ it[EpisodeCategoryMap.episodeId] }) { it[EpisodeCategoryMap.categoryId] }
            .mapValues { (_, categoryIds) -> categoryIds.mapNotNull { allEpisodeCategories[it] } }

        // 5. Get all channels
        val channels = PodcastChannels
            .selectAll()
            .orderBy(PodcastChannels.id to SortOrder.ASC)
            .map { channelRow ->
                val channelId = channelRow[PodcastChannels.id]

                // 6. Get episodes for this channel
                val episodes = PodcastEpisodes
                    .selectAll()
                    .where { PodcastEpisodes.channelId eq channelId }
                    .orderBy(PodcastEpisodes.pubDate to SortOrder.DESC)
                    .map { episodeRow ->
                        val episodeId = episodeRow[PodcastEpisodes.id]
                        EpisodeData(
                            id = episodeId,
                            guid = episodeRow[PodcastEpisodes.guid],
                            title = episodeRow[PodcastEpisodes.title],
                            description = episodeRow[PodcastEpisodes.description],
                            link = episodeRow[PodcastEpisodes.link],
                            pubDate = episodeRow[PodcastEpisodes.pubDate],
                            duration = episodeRow[PodcastEpisodes.duration],
                            explicit = episodeRow[PodcastEpisodes.explicit],
                            imageUrl = episodeRow[PodcastEpisodes.imageUrl],
                            mediaUrl = episodeRow[PodcastEpisodes.mediaUrl],
                            mediaType = episodeRow[PodcastEpisodes.mediaType],
                            mediaLength = episodeRow[PodcastEpisodes.mediaLength],
                            episodeCategory = episodeCategories[episodeId] ?: emptyList()
                        )
                    }

                ChannelFullData(
                    id = channelId,
                    title = channelRow[PodcastChannels.title],
                    link = channelRow[PodcastChannels.link],
                    description = channelRow[PodcastChannels.description],
                    copyright = channelRow[PodcastChannels.copyright],
                    language = channelRow[PodcastChannels.language],
                    author = channelRow[PodcastChannels.author],
                    ownerEmail = channelRow[PodcastChannels.ownerEmail],
                    ownerName = channelRow[PodcastChannels.ownerName],
                    imageUrl = channelRow[PodcastChannels.imageUrl],
                    lastBuildDate = channelRow[PodcastChannels.lastBuildDate].toString(),
                    categories = channelCategories[channelId] ?: emptyList(),
                    episodes = episodes
                )
            }
        channels
    }
}



private fun ApplicationConfig.getOrNull(name: String): String? = kotlin.runCatching {
    property(name).getString()
}.getOrNull()
