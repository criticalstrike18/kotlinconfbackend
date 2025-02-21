package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinApp.utils.GMTDateSerializer
import org.jetbrains.kotlinApp.utils.time

typealias GMTDateSerializable = @Serializable(GMTDateSerializer::class) GMTDate

@Serializable
class Conference(
    val sessions: List<Session> = emptyList(),
    val speakers: List<Speaker> = emptyList(),
)

@Serializable
data class ConferenceInfo(
    val sessions: List<Session>,
    val speakers: List<Speaker>
)

@Serializable
class Votes(
    val votes: List<VoteInfo> = emptyList()
)

@Serializable
data class Speaker(
    val id: String,
    val name: String,
    val position: String,
    val description: String,
    val photoUrl: String,
)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val description: String,
    val speakerIds: List<String>,
    val location: String,
    val startsAt: GMTDateSerializable,
    val endsAt: GMTDateSerializable,
    val tags: List<String>? = null
) {
    val timeLine get() = startsAt.time() + " - " + endsAt.time()
}

@Serializable
data class ConferenceSessionRequest(
    val title: String,
    val description: String?,
    val startsAt: GMTDateSerializable,
    val endsAt: GMTDateSerializable,
    val roomId: Int?,
    val isServiceSession: Boolean = false,
    val isPlenumSession: Boolean = false,
    val status: String = "Accepted",
    val speakerIds: List<String> = emptyList(),
    val categoryIds: List<Int> = emptyList()
)

@Serializable
data class SessionResponse(
    val success: Boolean,
    val sessionId: String?,
    val message: String?
)

@Serializable
class SessionInfo(
    val id: String,
    val title: String,
    val description: String?,
    val startsAt: GMTDateSerializable,
    val endsAt: GMTDateSerializable,
    val roomId: Int?,
    val isServiceSession: Boolean = false,
    val isPlenumSession: Boolean = false,
    val status: String = "Accepted",
    val speakerIds: List<String> = emptyList(),
    val categoryIds: List<Int> = emptyList()
)

@Serializable
data class SpeakerInfo(
    val id: String,
    val firstName: String,
    val lastName: String,
    val bio: String?,
    val tagLine: String?,
    val profilePicture: String?,
    val isTopSpeaker: Boolean = false
)

@Serializable
data class SessionSpeakerRequest(
    val sessionId: String,
    val speakerId: String
)

@Serializable
data class SessionCategoriesRequest(
    val sessionId: String,
    val categoryId:  Int,
)

@Serializable
data class CategoriesInfo(
    val title: String,
    val sort: Int?,
    val type: String?
)

@Serializable
data class RoomResponse(
    val success: Boolean,
    val roomId: Int?,
    val message: String?
)

@Serializable
class RoomInfo(
    val id: Long,
    val name: String,
    val sort: Int?
)

@Serializable
data class CategoriesTable(
    val id: Long,
    val title: String,
    val sort: Int?,
    val type: String?
)

@Serializable
class RoomTable(
    val id: Long?,
    val name: String,
    val sort: Int?
)

@Serializable
class RoomTableReq(
    val name: String,
    val sort: Int?
)

@Serializable
class VoteInfo(
    val sessionId: String,
    val score: Score?
)

@Serializable
class FeedbackInfo(
    val sessionId: String,
    val value: String
)

data class FavoriteInfo(
    val sessionId: String,
    val isFavorite: Boolean
)

@Serializable
enum class Score(val value: Int) {
    GOOD(1),
    OK(0),
    BAD(-1);

    companion object {
        fun fromValue(value: Int): Score? = when (value) {
            1 -> GOOD
            0 -> OK
            -1 -> BAD
            else -> null
        }
    }
}

@Serializable
data class ChannelDTO(
    val id: Int,
    val title: String,
    val link: String,
    val description: String,
    val copyright: String?,
    val language: String,
    val author: String,
    val ownerEmail: String,
    val ownerName: String,
    val imageUrl: String,
    val lastBuildDate: Long // epoch millis
)

@Serializable
data class EpisodeDTO(
    val id: Int,
    val channelId: Int,
    val guid: String,
    val title: String,
    val description: String,
    val link: String,
    val pubDate: Long,
    val duration: Int,
    val explicit: Boolean,
    val imageUrl: String?,
    val mediaUrl: String,
    val mediaType: String,
    val mediaLength: Long
)

@Serializable
data class EpisodeData(
    val id: Int? = null,
    val guid: String,
    val title: String,
    val description: String,
    val link: String,
    val pubDate: Instant,  // or store as string
    val duration: Int? = null,
    val explicit: Boolean,
    val imageUrl: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaLength: Long? = null,
    val episodeCategory: List<String> = emptyList()
)


@Serializable
data class ChannelFullData(
    val id: Int,
    val title: String,
    val link: String,
    val description: String,
    val copyright: String? = null,
    val language: String? = null,
    val author: String? = null,
    val ownerEmail: String? = null,
    val ownerName: String? = null,
    val imageUrl: String? = null,
    val lastBuildDate: String?,  // or null if missing

    // If you want categories
    val categories: List<String> = emptyList(),

    // All episodes for this channel
    val episodes: List<EpisodeData> = emptyList()
)