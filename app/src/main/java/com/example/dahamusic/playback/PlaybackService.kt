package com.example.dahamusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.dahamusic.library.MediaStoreAudioSource
import com.example.dahamusic.R
import com.example.dahamusic.ui.MusicActivity

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object { const val EXTRA_RESTORING = "com.example.dahamusic.RESTORING" }

    private var mediaSession: MediaSession? = null
    private var persistence: PlaybackPersistence? = null

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
            .setSessionExtras(Bundle().apply { putBoolean(EXTRA_RESTORING, true) })
            .setCallback(LocalPlaybackCallback(packageName)).build()
        mediaSession = session
        addSession(session)
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("dahamusic_playback").setChannelName(R.string.app_name)
            .setNotificationId(1001).build().apply { setSmallIcon(R.drawable.ic_musical_note) })
        persistence = PlaybackPersistence(player,
            PreferencePlaybackStateStore(getSharedPreferences("playback_state", MODE_PRIVATE)),
            MediaStoreAudioSource(contentResolver),
            setShuffleOrder = { player.setShuffleOrder(DefaultShuffleOrder(it, System.nanoTime())) },
            onRestoreFinished = {
                session.setSessionExtras(Bundle().apply { putBoolean(EXTRA_RESTORING, false) })
            }).also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (persistence?.restoreIfNeeded() == true) {
            mediaSession?.setSessionExtras(Bundle().apply { putBoolean(EXTRA_RESTORING, true) })
        }
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistence?.checkpoint()
        super.onTaskRemoved(rootIntent)
    }

    // MediaSessionService keeps foreground playback on task removal and stops idle/paused playback.
    override fun onDestroy() {
        persistence?.close()
        persistence = null
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
