package com.example.dahamusic.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.withTransaction
import com.example.dahamusic.library.AudioSource
import com.example.dahamusic.library.LibraryMerger
import com.example.dahamusic.room.RoomDbHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomFolderModel

class MediaRepository(private val database: RoomDbHelper) {
    private val mediaDao = database.roomDao()

    companion object {
        private val synchronization = Mutex()
    }

    suspend fun refreshLibrary(source: AudioSource) = synchronization.withLock {
        // Read the entire MediaStore result before making any database changes.
        val scanned = source.scan()
        database.withTransaction {
            val stored = mediaDao.readAllMusics()
            val folders = mediaDao.readAllFolders()
            val knownUris = stored.mapTo(hashSetOf()) { it.audioUri }
            val orphanedEntries = folders.flatMap { it.audioList }
                .filter { it.audioUri !in knownUris }.distinctBy { it.audioUri }
                .map { it.copy(isSelected = false).apply { id = 0 } }
            val merged = LibraryMerger.merge(stored + orphanedEntries, scanned)
            val oldByUri = stored.associateBy { it.audioUri }
            mediaDao.updateMusics(merged.filter { it.id != 0 && it != oldByUri[it.audioUri] })
            val newSongs = merged.filter { it.id == 0 }
            val ids = mediaDao.insertNewMusics(newSongs)
            val canonical = merged.filter { it.id != 0 } + newSongs.mapIndexed { index, song ->
                song.apply { id = ids[index].toInt() }
            }
            val systemNames = setOf(LibraryMerger.ALL_MUSIC, LibraryMerger.FAVORITES)
            val seen = hashSetOf<String>()
            val (retained, duplicates) = folders.partition { it.folderName !in systemNames || seen.add(it.folderName) }
            duplicates.forEach { mediaDao.deleteFolder(it) }
            updateFolderSnapshots(retained, canonical)
        }
    }

    private suspend fun updateFolderSnapshots(
        storedFolders: List<RoomFolderModel>, songs: List<RoomAudioModel>
    ) {
        val canonical = songs.sortedWith(compareBy<RoomAudioModel, String>(String.CASE_INSENSITIVE_ORDER) { it.audioTitle }.thenBy { it.audioUri })
        val folders = storedFolders.toMutableList()
        for (name in listOf(LibraryMerger.ALL_MUSIC, LibraryMerger.FAVORITES)) {
            if (folders.none { it.folderName == name }) {
                val folder = RoomFolderModel(name, emptyList())
                folder.audioList = LibraryMerger.folderContents(folder, canonical)
                mediaDao.insertFolder(folder)
            }
        }
        val updates = folders.mapNotNull { folder ->
            val contents = LibraryMerger.folderContents(folder, canonical)
            val unchanged = contents.size == folder.audioList.size &&
                contents.zip(folder.audioList).all { (a, b) -> a == b && a.id == b.id }
            if (unchanged) null else folder.copy(audioList = contents).apply { id = folder.id }
        }
        mediaDao.updateFolders(updates)
    }

    fun observeFolder(name: String): LiveData<RoomFolderModel?> =
        mediaDao.observeFolder(name).map { folder ->
            folder?.copy(audioList = folder.audioList.filter { it.isAvailable })?.apply { id = folder.id }
        }


    fun getFolders(): LiveData<List<RoomFolderModel>> = mediaDao.getFolders().map { folders ->
        folders.map { folder ->
            folder.copy(audioList = folder.audioList.filter { it.isAvailable }).apply { id = folder.id }
        }
    }

    fun getMusics():LiveData<List<RoomAudioModel>> = mediaDao.getMusics()

    suspend fun getFoldersCount():Int = mediaDao.getFoldersCount()

    suspend fun checkForExist(newFolderName: String):Boolean = mediaDao.checkForExists(newFolderName)

    suspend fun getFolder(name: String): RoomFolderModel = mediaDao.getFolder(name).let { folder ->
        folder.copy(audioList = folder.audioList.filter { it.isAvailable }).apply { id = folder.id }
    }

    suspend fun getMusic(uri: String) = mediaDao.getMusic(uri)

    suspend fun setFavorite(intState: Int, uri: String) = database.withTransaction {
        mediaDao.setFavorite(intState, uri)
        updateFolderSnapshots(mediaDao.readAllFolders(), mediaDao.readAllMusics())
    }

    suspend fun insertMusics(musics:List<RoomAudioModel>){
        mediaDao.insertMusics(musics)
    }

    suspend fun removeMusicFromFolder(folderName: String, uri: String) = database.withTransaction {
        if (folderName == LibraryMerger.ALL_MUSIC) return@withTransaction
        val folders = mediaDao.readAllFolders()
        val folder = folders.firstOrNull { it.folderName == folderName } ?: return@withTransaction
        if (folderName == LibraryMerger.FAVORITES) {
            mediaDao.setFavorite(0, uri)
            updateFolderSnapshots(folders, mediaDao.readAllMusics())
        } else {
            // Read the current playlist inside the transaction, not the filtered UI snapshot.
            val remaining = folder.audioList.filterNot { it.audioUri == uri }
            if (remaining.size != folder.audioList.size) {
                mediaDao.updateFolder(folder.copy(audioList = remaining).apply { id = folder.id })
            }
        }
    }

    suspend fun updateFolder(folder:RoomFolderModel ){
        database.withTransaction {
            if (folder.folderName == LibraryMerger.ALL_MUSIC) return@withTransaction
            if (folder.folderName == LibraryMerger.FAVORITES) {
                val desired = folder.audioList.mapTo(hashSetOf()) { it.audioUri }
                val songs = mediaDao.readAllMusics()
                val changed = songs.filter { it.isAvailable }.mapNotNull { song ->
                    val favorite = if (song.audioUri in desired) 1 else 0
                    if (song.isFavorite == favorite) null else song.copy(isFavorite = favorite).apply { id = song.id }
                }
                mediaDao.updateMusics(changed)
                updateFolderSnapshots(mediaDao.readAllFolders(), mediaDao.readAllMusics())
                return@withTransaction
            }
            val previous = mediaDao.readAllFolders().firstOrNull { it.id == folder.id }
            val visible = folder.audioList.associateByTo(linkedMapOf()) { it.audioUri }
            val retained = previous?.audioList.orEmpty().mapNotNull { old ->
                if (!old.isAvailable) {
                    visible.remove(old.audioUri)
                    old
                } else visible.remove(old.audioUri)
            }
            val contents = retained + visible.values
            mediaDao.updateFolder(folder.copy(audioList = contents).apply { id = folder.id })
        }
    }

    suspend fun setNewFolderName(newFolderName: String, oldFolderName: String){
        mediaDao.setNewFolderName(newFolderName,oldFolderName)
    }

    suspend fun deleteFolder(roomFolderModel: RoomFolderModel){
        mediaDao.deleteFolder(roomFolderModel)
    }

    suspend fun insertFolder(roomFolderModel: RoomFolderModel){
        mediaDao.insertFolder(roomFolderModel)
    }


}