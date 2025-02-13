package org.jetbrains.kotlinconf

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioService : MediaSessionService() {
    companion object {
        private var mediaSession: MediaSession? = null
    }

    override fun onCreate() {
        super.onCreate()

        if (mediaSession == null) {
            val player = ExoPlayer.Builder(this).build()

            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, Class.forName("org.jetbrains.kotlinconf.android.MainActivity")),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}