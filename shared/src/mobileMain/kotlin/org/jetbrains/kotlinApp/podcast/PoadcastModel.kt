@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.jetbrains.kotlinApp.podcast

import org.jetbrains.kotlinApp.ApplicationContext

expect class AudioPlayer {
    fun prepare(url: String, startPosition: Long = 0)
    fun play(url: String, startPosition: Long = 0)
    fun pause()
    fun stop()
    fun release()
    fun isPlaying(): Boolean
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun setPlaybackSpeed(speed: Float)
    fun enableAudioBoost(enabled: Boolean)
    fun seekTo(position: Long)
}


// Factory
expect fun createAudioPlayer(context: ApplicationContext): AudioPlayer

// Data class to represent a podcast episode
data class PodcastEpisode(
    val id: String,
    val channelId: String,
    val title: String,
    val audioUrl: String,
    val duration: Long,
    val imageUrl: String? = null,  // Added imageUrl field
    val description: String? = null, // Added description for potential future use
    val pubDate: Long? = null
)

data class PodcastChannel(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?
)

data class PodcastPlaybackState(
    val episodeId: String?,
    val channelId: String?,  // Add channelId for faster channel loading
    val position: Long,
    val url: String?,
    val speed: Double,
    val isBoostEnabled: Boolean
)

// State class to hold player state
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val currentEpisode: PodcastEpisode? = null,
    val currentChannel: PodcastChannel? = null
)
