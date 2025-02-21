package org.jetbrains.kotlinApp.podcast

import androidx.lifecycle.ViewModel
import org.jetbrains.kotlinApp.ApplicationContext

class PodcastViewModel(context: ApplicationContext) : ViewModel() {
    private val podcastService = PodcastService(context)

    // Expose states
    val playerState = podcastService.playerState
    val playbackState = podcastService.playbackState

    // Delegate player controls
    fun play(episode: PodcastEpisode) = podcastService.play(episode)
    fun pause() = podcastService.pause()
    fun seekTo(position: Long) = podcastService.seekTo(position)
    fun setSpeed(speed: Float) = podcastService.setSpeed(speed)
    fun enableBoost(enabled: Boolean) = podcastService.enableBoost(enabled)

    override fun onCleared() {
        super.onCleared()
        podcastService.release()
    }
}