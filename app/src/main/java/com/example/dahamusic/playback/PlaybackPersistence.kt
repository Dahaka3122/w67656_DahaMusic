package com.example.dahamusic.playback

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.dahamusic.library.AudioSource
import kotlinx.coroutines.*

/** Player access and preference edits stay on the player's main looper. Discovery/reading uses IO. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackPersistence(
    private val player: Player,
    private val store: PlaybackStateStore,
    private val source: AudioSource,
    private val setShuffleOrder: (IntArray) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onRestoreFinished: () -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var restoreJob: Job? = null
    private var finishedRestore = false
    private var initialized = false
    private var applyingRestore = false
    private var revision = 0
    private var closed = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (closed || applyingRestore) return
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                revision++
                if (initialized) checkpoint()
            }
        }
    }

    fun start() {
        player.addListener(listener)
        restoreIfNeeded()
        scope.launch {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) checkpoint()
            }
        }
    }

    fun restoreIfNeeded(): Boolean {
        if (closed || finishedRestore || restoreJob?.isActive == true) return false
        val initialRevision = revision
        restoreJob = scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val saved = store.read()
                    saved to saved?.resolve(source.scan())
                }
                if (revision == initialRevision && player.mediaItemCount == 0) {
                    val plan = result.second
                    if (plan != null) {
                        applyingRestore = true
                        try {
                            player.setMediaItems(plan.items, plan.index, plan.positionMs)
                            setShuffleOrder(plan.shuffleOrder)
                            player.repeatMode = plan.repeatMode
                            player.shuffleModeEnabled = plan.shuffle
                            player.playWhenReady = false
                            // Do not prepare/play: restore is deliberately silent and paused.
                        } finally { applyingRestore = false }
                    }
                    // Keep an unavailable snapshot so reconnecting after permission/volume recovery can retry.
                    finishedRestore = result.first == null || plan != null
                } else finishedRestore = true // an explicit user selection always wins
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("PlaybackRestore", "Cannot restore playback yet; keeping saved state", error)
            } finally {
                initialized = true
                checkpoint()
                if (!closed) onRestoreFinished()
            }
        }
        return true
    }

    fun checkpoint() {
        if (closed || !initialized) return
        SavedPlayback.capture(player)?.let(store::write)
    }

    fun close() {
        if (closed) return
        // Publish the final position before release() can empty/change the player state.
        if (initialized || revision != 0) SavedPlayback.capture(player)?.let(store::write)
        closed = true
        player.removeListener(listener)
        scope.cancel()
    }
}
