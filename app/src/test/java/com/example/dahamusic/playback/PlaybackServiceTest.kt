package com.example.dahamusic.playback

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.example.dahamusic.room.RoomAudioModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackServiceTest {
    private lateinit var lifecycle: ServiceController<PlaybackService>
    private lateinit var service: PlaybackService
    private lateinit var session: MediaSession
    private val controllers = mutableListOf<MediaController>()
    private fun song(id: Int, title: String = "Track $id") =
        RoomAudioModel(title, "60000", "Artist", "content://media/external/audio/media/$id", 0, false)

    @Before fun createService() {
        lifecycle = Robolectric.buildService(PlaybackService::class.java).create()
        service = lifecycle.get()
        session = service.sessions.single()
    }

    @After fun destroyService() {
        controllers.forEach { it.release() }
        lifecycle.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun connect(): MediaController {
        val future = MediaController.Builder(service, session.token).buildAsync()
        await { future.isDone }
        return future.get().also { controllers.add(it) }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Media session operation must complete", condition())
    }

    @Test fun reconnectingControllerPreservesServiceQueuePositionAndRepeatState() {
        val player = session.player
        player.setMediaItems(PlaybackQueue.items(listOf(song(42), song(99))), 1, 12000)
        player.repeatMode = Player.REPEAT_MODE_ONE
        val first = connect()
        assertEquals(song(99).audioUri, first.currentMediaItem!!.mediaId)
        // Advance the engine directly: this fixture has no decoded/seekable audio timeline.
        // The test checks controller lifecycle and retention independently of device decoders.
        player.seekTo(23000)
        shadowOf(Looper.getMainLooper()).idle()
        first.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, player.mediaItemCount)
        assertEquals(23000L, player.currentPosition)
        assertSame(player, service.sessions.single().player)
        val second = connect()
        assertEquals(song(99).audioUri, second.currentMediaItem!!.mediaId)
        assertEquals(23000L, second.currentPosition)
        assertEquals(Player.REPEAT_MODE_ONE, second.repeatMode)
    }

    @Test fun transportCommandsControlOnePlayerAndPauseIsNotAToggle() {
        val controller = connect()
        controller.setMediaItems(PlaybackQueue.items(listOf(song(42), song(99))))
        await { session.player.mediaItemCount == 2 }
        assertEquals(song(42).audioUri, session.player.currentMediaItem!!.localConfiguration!!.uri.toString())
        controller.play()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(session.player.playWhenReady)
        controller.pause()
        controller.pause()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(session.player.playWhenReady)
        controller.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(song(99).audioUri, session.player.currentMediaItem!!.mediaId)
        assertFalse(session.player.playWhenReady)
    }

    @Test fun queueOpeningReusesPositionAndUpdatesMetadataButChangesExplicitSelection() {
        val engine = session.player
        // Exercise queue logic without decoding a nonexistent content URI in this test.
        val player = object : ForwardingPlayer(engine) { override fun prepare() = Unit }
        engine.setMediaItems(PlaybackQueue.items(listOf(song(42), song(99))), 1, 23000)
        assertTrue(PlaybackQueue.open(player, listOf(song(42), song(99, "Renamed")), song(99).audioUri))
        assertEquals(23000L, engine.currentPosition)
        assertEquals("Renamed", engine.currentMediaItem!!.mediaMetadata.title)
        assertTrue(PlaybackQueue.open(player, listOf(song(42), song(99, "Renamed")), song(42).audioUri))
        assertEquals(song(42).audioUri, engine.currentMediaItem!!.mediaId)
        assertEquals(0L, engine.currentPosition)
    }

    @Test fun invalidOrUnavailableSelectionDoesNotClearExistingQueue() {
        val player = session.player
        player.setMediaItems(PlaybackQueue.items(listOf(song(42))))
        val unavailable = song(99).apply { isAvailable = false }
        assertFalse(PlaybackQueue.open(player, listOf(unavailable), unavailable.audioUri))
        assertFalse(PlaybackQueue.open(player, emptyList(), song(42).audioUri))
        assertEquals(song(42).audioUri, player.currentMediaItem!!.mediaId)
        val remote = song(2).copy(audioUri = "https://example.com/song.mp3")
        assertTrue(PlaybackQueue.items(listOf(remote)).isEmpty())
        assertEquals(1, PlaybackQueue.items(listOf(song(42), song(42))).size)
    }

    @Test fun foreignControllersCanControlPlaybackButCannotReplaceLocalQueue() {
        val callback = LocalPlaybackCallback(service.packageName)
        fun info(name: String) = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            name, 1, 1, 0, 0, true, Bundle.EMPTY)
        val foreign = callback.onConnect(session, info("other.app")).availablePlayerCommands
        assertFalse(foreign.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(foreign.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertTrue(foreign.contains(Player.COMMAND_PLAY_PAUSE))
        val own = callback.onConnect(session, info(service.packageName)).availablePlayerCommands
        assertTrue(own.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
    }

    @Test fun manifestAllowsForegroundPlaybackWithoutInternetAccess() {
        val permissions = service.packageManager.getPackageInfo(service.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test fun defaultsLoopQueueAndPausedTaskRemovalStopsService() {
        assertEquals(Player.REPEAT_MODE_ALL, session.player.repeatMode)
        service.onTaskRemoved(null)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
