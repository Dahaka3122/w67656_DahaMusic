package com.example.dahamusic.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

@androidx.annotation.OptIn(UnstableApi::class)
internal class LocalPlaybackCallback(private val ownPackage: String) : MediaSession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        if (controller.packageName != ownPackage) {
            // System/headset controls may operate playback, but not replace the local queue.
            commands.remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            commands.remove(Player.COMMAND_SET_MEDIA_ITEM)
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(commands.build()).build()
    }
}
