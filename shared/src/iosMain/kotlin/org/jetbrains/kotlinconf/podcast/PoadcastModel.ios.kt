package org.jetbrains.kotlinconf.podcast

import org.jetbrains.kotlinconf.ApplicationContext

actual class AudioPlayer {
    actual fun play(url: String) {
    }

    actual fun pause() {
    }

    actual fun stop() {
    }

    actual fun release() {
    }

    actual fun isPlaying(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun getCurrentPosition(): Long {
        TODO("Not yet implemented")
    }

    actual fun getDuration(): Long {
        TODO("Not yet implemented")
    }

    actual fun setPlaybackSpeed(speed: Float) {
        // Not yet implemented
    }
    actual fun enableAudioBoost(enabled: Boolean) {
        // Not yet implemented
    }
    actual fun seekTo(position: Long) {

    }
}

actual fun createAudioPlayer(context: ApplicationContext): AudioPlayer {
    TODO("Not yet implemented")
}