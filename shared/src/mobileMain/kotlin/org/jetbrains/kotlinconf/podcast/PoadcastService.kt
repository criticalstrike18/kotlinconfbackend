package org.jetbrains.kotlinconf.podcast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinconf.ApplicationContext

class PodcastService(context: ApplicationContext) {
    private val audioPlayer = createAudioPlayer(context)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Use your existing service scope; if it’s using Dispatchers.Default,
    // then switch when calling the player.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Poll every 500ms: update both current position and duration.
        serviceScope.launch {
            while (true) {
                if (_playerState.value.isPlaying) {
                    val pos = withContext(Dispatchers.Main) {
                        audioPlayer.getCurrentPosition()
                    }
                    val dur = withContext(Dispatchers.Main) {
                        audioPlayer.getDuration()
                    }
                    // Update both fields in our state.
                    _playerState.value = _playerState.value.copy(
                        currentPosition = pos,
                        duration = dur
                    )
                }
                delay(500L)
            }
        }
    }

    fun play(episode: PodcastEpisode) {
        audioPlayer.play(episode.audioUrl)
        // We set an initial duration; it will be updated by the polling loop.
        _playerState.value = PlayerState(
            isPlaying = true,
            currentEpisode = episode,
            currentPosition = 0,
            duration = audioPlayer.getDuration()
        )
    }

    fun pause() {
        audioPlayer.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun stop() {
        audioPlayer.stop()
        _playerState.value = PlayerState()
    }

    fun setSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun enableBoost(enabled: Boolean) {
        audioPlayer.enableAudioBoost(enabled)
    }

    fun seekTo(position: Long) {
        audioPlayer.seekTo(position)
        // Update state immediately so the UI reflects the change.
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun release() {
        serviceScope.cancel() // Cancel the polling loop.
        audioPlayer.release()
    }
}