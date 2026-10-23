package com.example.dahamusic.playback

import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.dahamusic.room.RoomAudioModel
import com.google.gson.Gson
import com.google.gson.JsonParser

internal data class SavedPlayback(
    val queue: List<String>,
    val currentUri: String,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val shuffleOrder: List<String>
) {
    companion object {
        fun capture(player: Player): SavedPlayback? {
            val current = player.currentMediaItem ?: return null
            val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
            val order = mutableListOf<String>()
            val timeline = player.currentTimeline
            var index = timeline.getFirstWindowIndex(true)
            while (index != C.INDEX_UNSET && order.size < queue.size) {
                if (index !in queue.indices) break
                order.add(queue[index])
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
            return SavedPlayback(queue, current.mediaId, player.currentPosition.coerceAtLeast(0),
                player.repeatMode, player.shuffleModeEnabled, order)
        }
    }

    fun resolve(available: List<RoomAudioModel>): RestoredPlayback? {
        val byUri = available.filter { it.isAvailable }.associateBy { it.audioUri }
        val songs = queue.distinct().mapNotNull(byUri::get)
        val items = PlaybackQueue.items(songs)
        if (items.isEmpty()) return null
        val retained = items.map { it.mediaId }
        val selected = if (currentUri in retained) currentUri else {
            val oldIndex = queue.indexOf(currentUri).coerceAtLeast(0)
            queue.drop(oldIndex).firstOrNull { it in retained } ?: retained.first()
        }
        val duration = byUri[selected]?.audioDuration?.toLongOrNull()?.takeIf { it > 0 }
        val position = if (selected == currentUri) positionMs.coerceAtLeast(0) else 0L
        val order = (shuffleOrder.filter { it in retained } + retained).distinct()
        return RestoredPlayback(items, retained.indexOf(selected),
            if (duration == null) position else position.coerceAtMost(duration),
            repeatMode.takeIf { it in listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL) }
                ?: Player.REPEAT_MODE_ALL,
            shuffle, order.map { retained.indexOf(it) }.toIntArray())
    }
}

internal data class RestoredPlayback(
    val items: List<androidx.media3.common.MediaItem>, val index: Int, val positionMs: Long,
    val repeatMode: Int, val shuffle: Boolean, val shuffleOrder: IntArray
)

internal interface PlaybackStateStore {
    fun read(): SavedPlayback?
    fun write(state: SavedPlayback)
}

/** One preference edit publishes queue and position together; apply writes the file asynchronously. */
internal class PreferencePlaybackStateStore(private val preferences: SharedPreferences) : PlaybackStateStore {
    private val gson = Gson()
    private var cachedQueue: List<String>? = null
    private var cachedOrder: List<String>? = null
    private var queueJson: String? = null
    private var orderJson: String? = null

    override fun read(): SavedPlayback? {
        val data = preferences.all // one consistent snapshot, not separate reads racing an edit
        if (data["version"] != 1) return null
        return try {
            fun strings(key: String) = JsonParser.parseString(data[key] as String).asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString
            }
            val queue = strings("queue")
            val uri = data["currentUri"] as String
            require(uri in queue)
            SavedPlayback(queue, uri, data["positionMs"] as Long, data["repeatMode"] as Int,
                data["shuffle"] as Boolean, strings("shuffleOrder"))
        } catch (invalid: Exception) {
            // A truncated/unsupported record must never prevent opening the library.
            null
        }
    }

    override fun write(state: SavedPlayback) {
        // Encoding a large queue is needed only when it changes, not on every position checkpoint.
        if (cachedQueue != state.queue) {
            cachedQueue = state.queue.toList()
            queueJson = gson.toJson(state.queue)
        }
        if (cachedOrder != state.shuffleOrder) {
            cachedOrder = state.shuffleOrder.toList()
            orderJson = gson.toJson(state.shuffleOrder)
        }
        preferences.edit().putInt("version", 1).putString("queue", queueJson)
            .putString("currentUri", state.currentUri).putLong("positionMs", state.positionMs)
            .putInt("repeatMode", state.repeatMode).putBoolean("shuffle", state.shuffle)
            .putString("shuffleOrder", orderJson).apply()
    }
}
