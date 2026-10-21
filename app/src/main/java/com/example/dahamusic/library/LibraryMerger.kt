package com.example.dahamusic.library

import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomFolderModel

/** A complete successful scan is required. Never pass a partial or failed query here. */
object LibraryMerger {
    const val ALL_MUSIC = "Your musics"
    const val FAVORITES = "Favorites"

    fun merge(stored: List<RoomAudioModel>, scanned: List<RoomAudioModel>): List<RoomAudioModel> {
        val previous = stored.associateBy { it.audioUri }
        val found = scanned.distinctBy { it.audioUri }.map { fresh ->
            val old = previous[fresh.audioUri]
            fresh.copy(isFavorite = old?.isFavorite ?: 0, isSelected = false, isAvailable = true)
                .apply { id = old?.id ?: 0 }
        }
        val present = found.mapTo(hashSetOf()) { it.audioUri }
        return found + stored.filter { it.audioUri !in present }.map { old ->
            old.copy(isSelected = false, isAvailable = false).apply { id = old.id }
        }
    }

    fun folderContents(folder: RoomFolderModel, songs: List<RoomAudioModel>): List<RoomAudioModel> {
        val byUri = songs.associateBy { it.audioUri }
        return when (folder.folderName) {
            ALL_MUSIC -> songs.filter { it.isAvailable }
            FAVORITES -> songs.filter { it.isFavorite == 1 && it.isAvailable }
            // Retain missing entries so a temporarily removed volume does not erase a playlist.
            else -> folder.audioList.distinctBy { it.audioUri }.map { entry ->
                byUri[entry.audioUri] ?: entry.copy(isAvailable = false).apply { id = entry.id }
            }
        }
    }
}
