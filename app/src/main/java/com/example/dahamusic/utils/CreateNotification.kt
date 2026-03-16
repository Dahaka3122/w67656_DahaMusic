package com.example.dahamusic.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dahamusic.R
import com.example.dahamusic.room.RoomAudioModel

class CreateNotification {
    var CHANNEL_ID = "channel1"

    val ACTION_PREVIOUS = "action_previous"
    val ACTION_PLAY = "action_play"
    val ACTION_NEXT = "action_next"

    lateinit var notification: Notification

    @SuppressLint("MissingPermission")
    fun createNotification(context: Context, track: RoomAudioModel, playButton: Int, playbackPosition: Long = 0L, mediaSessionCompat: MediaSessionCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManagerCompat = NotificationManagerCompat.from(context)

            var albumArtBitmap: android.graphics.Bitmap? = null
            var duration = 0L

            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, android.net.Uri.parse(track.audioUri))

                val art = retriever.embeddedPicture
                if (art != null) {
                    albumArtBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                }

                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    duration = durationStr.toLong()
                }
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            mediaSessionCompat.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.audioTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.audioArtist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .build()
            )

            val state = if (playButton == R.drawable.ic_pause) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }

            mediaSessionCompat.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    .setState(state, playbackPosition, 1.0f)
                    .build()
            )

            val icon = albumArtBitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.music_photo)

            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val intentPrevious = Intent("TRACKS_TRACKS").putExtra("actionname", ACTION_PREVIOUS).setPackage(context.packageName)
            val pendingIntentPrevious = PendingIntent.getBroadcast(context, 1, intentPrevious, pendingIntentFlag)
            val drw_previous = R.drawable.ic_baseline_skip_previous_24

            val intentPlay = Intent("TRACKS_TRACKS").putExtra("actionname", ACTION_PLAY).setPackage(context.packageName)
            val pendingIntentPlay = PendingIntent.getBroadcast(context, 2, intentPlay, pendingIntentFlag)

            val intentNext = Intent("TRACKS_TRACKS").putExtra("actionname", ACTION_NEXT).setPackage(context.packageName)
            val pendingIntentNext = PendingIntent.getBroadcast(context, 3, intentNext, pendingIntentFlag)
            val drw_next = R.drawable.ic_baseline_skip_next_24

            notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_musical_note)
                .setContentTitle(track.audioTitle)
                .setContentText(track.audioArtist)
                .setLargeIcon(icon)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(drw_previous, "Previous", pendingIntentPrevious)
                .addAction(playButton, "Play", pendingIntentPlay)
                .addAction(drw_next, "Next", pendingIntentNext)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSessionCompat.sessionToken)
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            try {
                notificationManagerCompat.notify(1, notification)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}