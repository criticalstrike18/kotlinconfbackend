package org.jetbrains.kotlinconf.backend

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.EpisodeData
import org.jetbrains.kotlinconf.GMTDateSerializable

@Serializable
data class ConferenceSpeakerRequest(
    val firstName: String,
    val lastName: String,
    val bio: String?,
    val tagLine: String?,
    val profilePicture: String?,
    val isTopSpeaker: Boolean = false
)

@Serializable
data class ConferenceRoomRequest(
    val name: String,
    val sort: Int?
)

@Serializable
data class ConferenceCategoriesRequest(
    val title: String,
    val sort: Int?,
    val type: String?
)


@Serializable
data class PodcastImportRequest(
    val channel: ChannelData,
    val categories: List<String>,
    val episodes: List<EpisodeData>
)
