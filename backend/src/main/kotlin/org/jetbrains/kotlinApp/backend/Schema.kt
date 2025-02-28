package org.jetbrains.kotlinApp.backend

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.kotlinApp.backend.PodcastChannels.autoIncrement
import org.jetbrains.kotlinApp.backend.PodcastEpisodes.channelId

abstract class BaseTable(name: String) : Table(name) {
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

internal object Users : BaseTable("users") {
    val userId: Column<String> = varchar("uuid", 50)
        .index()

    val timestamp: Column<String> = varchar("timestamp", 50)

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}

internal object Votes : BaseTable("votes") {
    val timestamp = varchar("timestamp", 50)

    val userId: Column<String> = varchar("uuid", 50)
        .index()

    val sessionId: Column<String> = varchar("sessionId", 50)
        .index()

    val rating = integer("rating")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, sessionId)
}

internal object Feedback: BaseTable("feedback") {
    val timestamp = varchar("timestamp", 50)

    val userId: Column<String> = varchar("uuid", 50)
        .index()

    val sessionId: Column<String> = varchar("sessionId", 50)
        .index()

    val feedback: Column<String> = varchar("feedback", length = 5000)

    override val primaryKey: PrimaryKey = PrimaryKey(Votes.userId, Votes.sessionId)
}

@Suppress("INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_WARNING")
object ConferenceSessions : BaseTable("conference_sessions") {
    // Primary identifier
    val id = varchar("id", 50)

    // Core session details with appropriate length constraints
    val title = varchar("title", 500)
    val description = text("description").nullable() // Using text for potentially long content

    // Temporal information with proper datetime type
    val startsAt = timestamp("starts_at")
    val endsAt = timestamp("ends_at")

    // Foreign key with proper constraint
    val roomId = integer("room_id").references(ConferenceRooms.id).nullable()

    // Session flags
    val isServiceSession = bool("is_service_session").default(false)
    val isPlenumSession = bool("is_plenum_session").default(false)
    val status = varchar("status", 50).default("draft")

    override val primaryKey = PrimaryKey(id)
    // Ensure data integrity with constraints
    init {
        check("starts_before_ends") { startsAt lessEq endsAt }
        index(isUnique = true, id)  // For faster lookups
        index(false, startsAt, endsAt)
    }
}

object ConferenceSpeakers : BaseTable("conference_speakers") {
    val id: Column<String> = varchar("id", 50)
    val firstName: Column<String> = varchar("first_name", 255)
    val lastName: Column<String> = varchar("last_name", 255)
    val bio: Column<String?> = varchar("bio", 5000).nullable()
    val tagLine: Column<String?> = varchar("tag_line", 500).nullable()
    val profilePicture: Column<String?> = varchar("profile_picture", 500).nullable()
    val isTopSpeaker: Column<Boolean> = bool("is_top_speaker")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, id)
    }
}

object ConferenceRooms : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val sort = integer("sort").nullable()

    override val primaryKey = PrimaryKey(id)
    init {
        index(isUnique = true, id)
    }
}

object ConferenceCategories : Table() {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val sort = integer("sort").nullable()
    val type = varchar("type", 50).nullable()

    override val primaryKey = PrimaryKey(id)
    init {
        index(isUnique = true, id)
    }
}

object SessionSpeakers : Table() {
    val sessionId = varchar("session_id", 50) references ConferenceSessions.id
    val speakerId = varchar("speaker_id", 50) references ConferenceSpeakers.id

    override val primaryKey = PrimaryKey(sessionId, speakerId)
    init {
        index(false, sessionId)
        index(false, speakerId)
    }
}

object SessionCategories : Table() {
    val sessionId = varchar("session_id", 50) references ConferenceSessions.id
    val categoryId = integer("category_item_id") references ConferenceCategories.id

    override val primaryKey = PrimaryKey(sessionId, categoryId)
    init {
        index(false, sessionId)  // For faster joins
        index(false, categoryId)
    }
}

internal object PodcastRequest: BaseTable("podcast_request_table") {
    val userId: Column<String> = varchar("uuid", 50)
        .index()
    private val id = integer("id").autoIncrement()
    val title: Column<String> = varchar("title", length = 5000)
    val author: Column<String> = varchar("author", length = 5000)
    val rssLink: Column<String> = varchar("rssUrl", length = 5000)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object PodcastChannels : BaseTable("podcast_channels") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 500)
    val link = varchar("link", 500)
    val description = text("description")
    val copyright = varchar("copyright", 500).nullable()
    val language = varchar("language", 50)
    val author = varchar("author", 255)
    val ownerEmail = varchar("owner_email", 255)
    val ownerName = varchar("owner_name", 255)
    val imageUrl = varchar("image_url", 500)
    val lastBuildDate = timestamp("last_build_date")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, id)
    }
}

object PodcastEpisodes : BaseTable("podcast_episodes") {
    val id = integer("id").autoIncrement()
    val channelId = reference("channel_id", PodcastChannels.id)
    val guid = varchar("guid", 500)
    val title = varchar("title", 500)
    val description = text("description")
    val link = varchar("link", 500)
    val pubDate = timestamp("pub_date")
    val duration = integer("duration")
    val explicit = bool("explicit")
    val imageUrl = varchar("image_url", 500).nullable()
    val mediaUrl = varchar("media_url", 500)
    val mediaType = varchar("media_type", 100)
    val mediaLength = long("media_length")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, id)
        index(isUnique = true, guid)
        index(false, channelId)
        index(false, pubDate)
    }
}

object PodcastCategories : BaseTable("podcast_categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object ChannelCategoryMap : BaseTable("channel_category_map") {
    val channelId = reference("channel_id", PodcastChannels.id)
    val categoryId = reference("category_id", PodcastCategories.id)

    override val primaryKey = PrimaryKey(channelId, categoryId)

    init {
        index(false, channelId)
        index(false, categoryId)
    }
}

object EpisodeCategoryMap : BaseTable("episode_category_map") {
    val episodeId = reference("episode_id", PodcastEpisodes.id)
    val categoryId = reference("category_id", PodcastCategories.id)

    override val primaryKey = PrimaryKey(episodeId, categoryId)

    init {
        index(false, episodeId)
        index(false, categoryId)
    }
}


