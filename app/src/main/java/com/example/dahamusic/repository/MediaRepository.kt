package com.example.dahamusic.repository

import androidx.lifecycle.LiveData
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomDao
import com.example.dahamusic.room.RoomFolderModel

class MediaRepository (private val mediaDao: RoomDao){

    fun getFolders():LiveData<List<RoomFolderModel>> = mediaDao.getFolders()

    fun getMusics():LiveData<List<RoomAudioModel>> = mediaDao.getMusics()

    suspend fun getFoldersCount():Int = mediaDao.getFoldersCount()

    suspend fun checkForExist(newFolderName: String):Boolean = mediaDao.checkForExists(newFolderName)

    suspend fun getFolder(name: String):RoomFolderModel = mediaDao.getFolder(name)

    fun getMusic(position:Int) = mediaDao.getMusic(position)

    suspend fun setFavorite(intState:Int, position: Int){
        mediaDao.setFavorite(intState,position)
    }

    suspend fun insertMusics(musics:List<RoomAudioModel>){
        mediaDao.insertMusics(musics)
    }

    suspend fun updateFolder(folder:RoomFolderModel ){
        mediaDao.updateFolder(folder)
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