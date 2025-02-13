package org.jetbrains.kotlinconf.podcast

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import org.jetbrains.kotlinconf.ApplicationContext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AudioPlayer(private val context: Context) {
    // Use a single ExoPlayer instance for all operations
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var mediaSession: MediaSession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Handle playback state changes
            }
        })

        // Create MediaSession using the same player instance
        mediaSession = MediaSession.Builder(context, player).build()
    }

    @OptIn(UnstableApi::class)
    actual fun play(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Lazy-initialize the LoudnessEnhancer if needed.
        // Note: audioSessionId is valid after prepare() is called.
        if (loudnessEnhancer == null) {
            val sessionId = player.audioSessionId
            if (sessionId != AudioManager.ERROR) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    // Set the target gain in millibels (e.g., 1000 = 10 dB boost)
                    setTargetGain(1000)
                    enabled = false  // Start with boost off
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
        mediaSession?.release()
        mediaSession = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        player.release()
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