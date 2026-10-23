package com.example.dahamusic.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import com.example.dahamusic.library.AudioSource
import com.example.dahamusic.room.RoomAudioModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("persistence-unit-test", Context.MODE_PRIVATE)
    private val players = mutableListOf<ExoPlayer>()
    private val persistence = mutableListOf<PlaybackPersistence>()
    private fun song(id: Int) = RoomAudioModel("Track $id", "60000", "Artist",
        "content://media/external/audio/media/$id", 0, false)
    private fun state() = SavedPlayback(listOf(song(1).audioUri, song(2).audioUri), song(2).audioUri,
        23000, Player.REPEAT_MODE_ONE, true, listOf(song(2).audioUri, song(1).audioUri))
    private fun player() = ExoPlayer.Builder(context).build().also { players.add(it) }
    private fun TestScope.coordinator(engine: ExoPlayer, source: AudioSource,
        observedPlayer: Player = engine): PlaybackPersistence {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return PlaybackPersistence(observedPlayer, PreferencePlaybackStateStore(preferences), source,
            { engine.setShuffleOrder(DefaultShuffleOrder(it, 123L)) }, dispatcher, dispatcher)
            .also { persistence.add(it); it.start() }
    }
    private fun TestScope.flush() { shadowOf(Looper.getMainLooper()).idle(); runCurrent() }

    private fun playbackTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { persistence.forEach { it.close() } }
    }

    @Before fun clearPreferences() { preferences.edit().clear().commit() }
    @After fun releasePlayers() {
        persistence.forEach { it.close() }
        players.forEach { it.release() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun persistedRecordCanBeReadByNewStoreInstance() {
        PreferencePlaybackStateStore(preferences).write(state())
        assertEquals(state(), PreferencePlaybackStateStore(preferences).read())
    }

    @Test fun malformedOrFutureRecordDoesNotCrashAndNextSaveRepairsIt() {
        preferences.edit().putInt("version", 1).putString("queue", "not json").commit()
        val store = PreferencePlaybackStateStore(preferences)
        assertNull(store.read())
        preferences.edit().putInt("version", 9).commit()
        assertNull(store.read())
        store.write(state())
        assertEquals(state(), PreferencePlaybackStateStore(preferences).read())
    }

    @Test fun restoreIsPausedAndKeepsQueuePositionRepeatAndShuffleOrder() = playbackTest {
        PreferencePlaybackStateStore(preferences).write(state())
        val engine = player()
        coordinator(engine, AudioSource { listOf(song(1), song(2)) })
        flush()
        assertEquals(2, engine.mediaItemCount)
        assertEquals(song(2).audioUri, engine.currentMediaItem!!.mediaId)
        assertEquals(23000L, engine.currentPosition)
        assertFalse(engine.playWhenReady)
        assertEquals(Player.STATE_IDLE, engine.playbackState) // no decoder/audio focus/autoplay
        assertEquals(Player.REPEAT_MODE_ONE, engine.repeatMode)
        assertTrue(engine.shuffleModeEnabled)
        assertEquals(1, engine.currentTimeline.getFirstWindowIndex(true))
    }

    @Test fun removedCurrentTrackChoosesNextSurvivorAtStartAndClampsPositions() {
        val original = state().copy(queue = listOf(song(1).audioUri, song(2).audioUri, song(3).audioUri))
        val plan = original.resolve(listOf(song(1), song(3)))!!
        assertEquals(listOf(song(1).audioUri, song(3).audioUri), plan.items.map { it.mediaId })
        assertEquals(song(3).audioUri, plan.items[plan.index].mediaId)
        assertEquals(0L, plan.positionMs)
        assertEquals(60000L, state().copy(positionMs = Long.MAX_VALUE).resolve(listOf(song(2)))!!.positionMs)
        assertEquals(0L, state().copy(positionMs = -1).resolve(listOf(song(2)))!!.positionMs)
        assertNull(state().resolve(listOf(song(2).apply { isAvailable = false })))
    }

    @Test fun permissionFailurePreservesRecordAndRetryCanRestoreIt() = playbackTest {
        val saved = state()
        val store = PreferencePlaybackStateStore(preferences)
        store.write(saved)
        var allowed = false
        val engine = player()
        val savedPlayer = coordinator(engine, AudioSource {
            if (!allowed) throw SecurityException("revoked")
            listOf(song(1), song(2))
        })
        flush()
        assertEquals(0, engine.mediaItemCount)
        assertEquals(saved, store.read())
        allowed = true
        assertTrue(savedPlayer.restoreIfNeeded())
        flush()
        assertEquals(song(2).audioUri, engine.currentMediaItem!!.mediaId)
        assertFalse(engine.playWhenReady)
    }

    @Test fun temporarilyMissingVolumeDoesNotEraseSavedQueue() = playbackTest {
        val store = PreferencePlaybackStateStore(preferences)
        store.write(state())
        val engine = player()
        val savedPlayer = coordinator(engine, AudioSource { emptyList() })
        flush()
        savedPlayer.close()
        assertEquals(state(), store.read())
        assertEquals(0, engine.mediaItemCount)
    }

    @Test fun explicitSelectionDuringSlowRestoreWins() = playbackTest {
        PreferencePlaybackStateStore(preferences).write(state())
        val gate = CompletableDeferred<List<RoomAudioModel>>()
        val engine = player()
        coordinator(engine, AudioSource { gate.await() })
        runCurrent()
        engine.setMediaItems(PlaybackQueue.items(listOf(song(3))), 0, 7000)
        engine.playWhenReady = true
        shadowOf(Looper.getMainLooper()).idle()
        gate.complete(listOf(song(1), song(2)))
        flush()
        assertEquals(song(3).audioUri, engine.currentMediaItem!!.mediaId)
        assertEquals(7000L, engine.currentPosition)
        assertTrue(engine.playWhenReady) // restore must not interrupt an explicit play request
        assertEquals(song(3).audioUri, PreferencePlaybackStateStore(preferences).read()!!.currentUri)
    }

    @Test fun checkpointsOnSeekAndFinalCloseSurvivePlayerRelease() = playbackTest {
        val engine = player()
        engine.setMediaItems(PlaybackQueue.items(listOf(song(1))), 0, 1000)
        val savedPlayer = coordinator(engine, AudioSource { emptyList() })
        flush()
        engine.seekTo(12000)
        flush()
        assertEquals(12000L, PreferencePlaybackStateStore(preferences).read()!!.positionMs)
        engine.seekTo(18000)
        savedPlayer.close()
        engine.release()
        players.remove(engine)
        flush()
        assertEquals(18000L, PreferencePlaybackStateStore(preferences).read()!!.positionMs)
        assertEquals(listOf(song(1).audioUri), PreferencePlaybackStateStore(preferences).read()!!.queue)
    }

    @Test fun periodicCheckpointCapturesProgressWithoutASeekEvent() = playbackTest {
        val engine = player()
        engine.setMediaItems(PlaybackQueue.items(listOf(song(1))))
        var progress = 0L
        val clockDrivenPlayer = object : ForwardingPlayer(engine) {
            override fun isPlaying() = true
            override fun getCurrentPosition() = progress
        }
        coordinator(engine, AudioSource { emptyList() }, clockDrivenPlayer)
        flush()
        progress = 14000
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(14000L, PreferencePlaybackStateStore(preferences).read()!!.positionMs)
    }
}
