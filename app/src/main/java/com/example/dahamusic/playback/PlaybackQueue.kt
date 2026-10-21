package com.example.dahamusic.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.dahamusic.room.RoomAudioModel

/** Queue identity is the content URI, never an index or an auto-generated database ID. */
object PlaybackQueue {
    fun isLocal(uri: Uri): Boolean = uri.scheme in setOf("content", "file", "android.resource")

    fun items(songs: List<RoomAudioModel>): List<MediaItem> = songs
        .filter { it.isAvailable && isLocal(Uri.parse(it.audioUri)) }
        .distinctBy { it.audioUri }
        .map { song ->
            MediaItem.Builder().setMediaId(song.audioUri).setUri(song.audioUri)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.audioTitle).setArtist(song.audioArtist).build())
                .build()
        }

    /** Reopening the current song reconnects to playback instead of starting it over. */
    fun open(player: Player, songs: List<RoomAudioModel>, selectedUri: String): Boolean {
        val requested = items(songs)
        val selected = requested.indexOfFirst { it.mediaId == selectedUri }
        if (selected == -1) return false
        val sameQueue = requested.size == player.mediaItemCount &&
            requested.indices.all { requested[it].mediaId == player.getMediaItemAt(it).mediaId }
        if (!sameQueue) {
            player.setMediaItems(requested, selected, 0L)
        } else {
            requested.indices.forEach { index ->
                if (player.getMediaItemAt(index).mediaMetadata != requested[index].mediaMetadata) {
                    player.replaceMediaItem(index, requested[index])
                }
            }
            if (player.currentMediaItem?.mediaId != selectedUri || player.playbackState == Player.STATE_ENDED) {
                player.seekToDefaultPosition(selected)
            }
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        return true
    }

}
