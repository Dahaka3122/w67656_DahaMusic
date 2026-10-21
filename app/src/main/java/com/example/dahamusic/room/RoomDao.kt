package com.example.dahamusic.room

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RoomDao {

    @Insert
    suspend fun insertMusics(musics: List<RoomAudioModel>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(roomFolderModel: RoomFolderModel)

    @Query("UPDATE RoomFolderModel SET folderName = :newFolderName WHERE folderName = :oldFolderName")
    suspend fun setNewFolderName(newFolderName: String, oldFolderName: String)

    @Query("UPDATE ROOMAUDIOMODEL SET isFavorite= :intState WHERE audioUri = :uri")
    suspend fun setFavorite(intState:Int,uri: String)

    @Query("SELECT EXISTS ( SELECT * FROM roomfoldermodel WHERE folderName = :newFolderName)")
    suspend fun checkForExists(newFolderName: String):Boolean

    @Query("select * from roomaudiomodel WHERE isAvailable = 1 ORDER BY audioTitle COLLATE NOCASE, audioUri")
    fun getMusics():LiveData<List<RoomAudioModel>>

    @Query("select * from roomfoldermodel")
    fun getFolders():LiveData<List<RoomFolderModel>>

    @Query("SELECT COUNT(*) FROM roomfoldermodel")
    suspend fun getFoldersCount():Int

    @Query("SELECT * FROM roomfoldermodel WHERE folderName = :name ")
    suspend fun getFolder(name: String):RoomFolderModel

    @Query("SELECT * FROM roomaudiomodel WHERE audioUri = :uri")
    suspend fun getMusic(uri:String):RoomAudioModel?

    @Query("SELECT * FROM RoomAudioModel WHERE audioUri = :uri LIMIT 1")
    fun observeMusic(uri: String): LiveData<RoomAudioModel?>

    @Query("SELECT * FROM RoomAudioModel ORDER BY id")
    suspend fun readAllMusics(): List<RoomAudioModel>

    @Query("SELECT * FROM RoomFolderModel ORDER BY id")
    suspend fun readAllFolders(): List<RoomFolderModel>

    @Query("SELECT * FROM RoomFolderModel WHERE folderName = :name LIMIT 1")
    fun observeFolder(name: String): LiveData<RoomFolderModel?>

    @Insert
    suspend fun insertNewMusics(songs: List<RoomAudioModel>): List<Long>

    @Update
    suspend fun updateMusics(songs: List<RoomAudioModel>)

    @Update
    suspend fun updateFolders(folders: List<RoomFolderModel>)

    @Delete()
    suspend fun deleteFolder(roomFolderModel: RoomFolderModel)

    @Update
    suspend fun updateFolder(folder:RoomFolderModel )

}