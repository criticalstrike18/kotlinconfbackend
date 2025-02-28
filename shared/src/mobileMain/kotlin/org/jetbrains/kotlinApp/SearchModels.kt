package org.jetbrains.kotlinApp

// Raw data models returned by ConferenceService
data class SessionSearchItem(
    val id: String,
    val title: String,
    val speakerLine: String,
    val description: String,
    val tags: List<String>,
    val timeLine: String
)

data class PodcastChannelSearchItem(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val episodeCount: Long,
    val categories: List<String>
)

data class EpisodeSearchItem(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val channelTitle: String,
    val imageUrl: String?,
    val pubDate: Long,
    val duration: Long,
    val categories: List<String>
)