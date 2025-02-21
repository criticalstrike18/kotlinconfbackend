package org.jetbrains.kotlinApp.podcast

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import org.jetbrains.kotlinApp.ApplicationContext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AudioPlayer(private val context: Context) {
    // Use a single ExoPlayer instance for all operations
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var mediaSession: MediaSession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentUrl: String? = null
    private var currentPlaybackState: Boolean = false
    private var onErrorCallback: ((Exception) -> Unit)? = null
    private var onPlaybackStateChange: ((Boolean) -> Unit)? = null

    companion object {
        private const val MEDIA_SESSION_ID = "KotlinConfPodcastSession"
    }

    init {
        setupMediaSession()
        setupPlayerListeners()

    }

    fun setPlaybackStateListener(listener: (Boolean) -> Unit) {
        onPlaybackStateChange = listener
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // We must handle all possible states to avoid NotImplementedError
                when (state) {
                    Player.STATE_IDLE -> {
                        // Player is idle - either playback failed or stopped
                        currentUrl = null
                        currentPlaybackState = false
                        onPlaybackStateChange?.invoke(false)
                    }
                    Player.STATE_BUFFERING -> {
                        // Keep current state during buffering
                        // Don't change playback state as buffering is temporary
                    }
                    Player.STATE_READY -> {
                        // Player is ready to play
                        // State will be updated by onIsPlayingChanged
                    }
                    Player.STATE_ENDED -> {
                        // Playback completed
                        currentUrl = null
                        currentPlaybackState = false
                        onPlaybackStateChange?.invoke(false)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (currentPlaybackState != isPlaying) {
                    currentPlaybackState = isPlaying
                    onPlaybackStateChange?.invoke(isPlaying)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentUrl = null
                currentPlaybackState = false
                onErrorCallback?.invoke(error)
                onPlaybackStateChange?.invoke(false)
            }
        })
    }

    private fun setupMediaSession() {
        mediaSession?.release()

        try {
            mediaSession = MediaSession.Builder(context, player)
                .setId(MEDIA_SESSION_ID)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, Class.forName("org.jetbrains.kotlinApp.android.MainActivity")),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        } catch (e: Exception) {
            println("Failed to create MediaSession: ${e.message}")
            // Instead of recreating the player, we'll try to continue without MediaSession
            onErrorCallback?.invoke(e)
        }
    }
    fun setErrorCallback(callback: (Exception) -> Unit) {
        onErrorCallback = callback
    }

    actual fun prepare(url: String, startPosition: Long) {
        try {
            if (url == currentUrl && player.playbackState != Player.STATE_IDLE) {
                if (startPosition > 0 && startPosition != player.currentPosition) {
                    player.seekTo(startPosition)
                }
                return
            }

            // Reset state for new URL
            currentUrl = url
            player.stop()
            player.clearMediaItems()

            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()

            if (startPosition > 0) {
                player.seekTo(startPosition)
            }
            setupAudioEnhancements()
        } catch (e: Exception) {
            currentUrl = null
            currentPlaybackState = false
            onErrorCallback?.invoke(e)
            throw e
        }
    }

    actual fun play(url: String, startPosition: Long) {
        prepare(url, startPosition)
        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun setupAudioEnhancements() {
        if (loudnessEnhancer == null) {
            val sessionId = player.audioSessionId
            if (sessionId != AudioManager.ERROR) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                        setTargetGain(1000)
                        enabled = false
                    }
                } catch (e: Exception) {
                    println("Failed to initialize audio enhancements: ${e.message}")
                }
            }
        }
    }

    actual fun pause() {
        player.pause()
    }

    actual fun stop() {
        player.stop()
    }

    actual fun release() {
        try {
            // Clear callback
            onPlaybackStateChange = null

            // Release MediaSession first
            mediaSession?.release()
            mediaSession = null

            // Release LoudnessEnhancer
            loudnessEnhancer?.release()
            loudnessEnhancer = null

            // Stop and release player
            player.stop()
            player.release()

            // Clear URL tracking
            currentUrl = null
            currentPlaybackState = false
        } catch (e: Exception) {
            println("Error during release: ${e.message}")
        }
    }

    actual fun isPlaying(): Boolean = player.isPlaying

    actual fun getCurrentPosition(): Long = player.currentPosition

    actual fun getDuration(): Long = player.duration

    actual fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    /**
     * Enable or disable audio boost.
     *
     * When enabled, the LoudnessEnhancer is turned on (applying the target gain).
     * When disabled, it is turned off.
     */
    @UnstableApi
    actual fun enableAudioBoost(enabled: Boolean) {
        // If not already initialized, try to initialize now.
        if (loudnessEnhancer == null) {
            val sessionId = player.audioSessionId
            if (sessionId != AudioManager.ERROR) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(1000) // Adjust the boost level as needed.
                }
            }
        }
        loudnessEnhancer?.enabled = enabled
    }


    actual fun seekTo(position: Long) {
        player.seekTo(position)
    }
}

actual fun createAudioPlayer(context: ApplicationContext): AudioPlayer {
    return AudioPlayer(context.application)
}