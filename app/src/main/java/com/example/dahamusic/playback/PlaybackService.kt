package com.example.dahamusic.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.dahamusic.R
import com.example.dahamusic.ui.MusicActivity

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            repeatMode = Player.REPEAT_MODE_ALL
        }
        val openPlayer = Intent(this, MusicActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val activity = PendingIntent.getActivity(this, 0, openPlayer,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(LocalPlaybackCallback(packageName)).build()
        mediaSession = session
        addSession(session)
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("dahamusic_playback").setChannelName(R.string.app_name)
            .setNotificationId(1001).build().apply { setSmallIcon(R.drawable.ic_musical_note) })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // MediaSessionService keeps foreground playback on task removal and stops idle/paused playback.
    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
