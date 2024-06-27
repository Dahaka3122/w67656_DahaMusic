package com.example.dahamusic.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.dahamusic.repository.MediaRepository
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomDbHelper
import com.example.dahamusic.room.RoomFolderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaViewModel(application:Application) : AndroidViewModel(application){

    val musics : LiveData<List<RoomAudioModel>>
    var folders : LiveData<List<RoomFolderModel>>
    private val repository : MediaRepository

    init {
        val mediaDao = RoomDbHelper.DatabaseBuilder.getInstance(application).roomDao()
        repository = MediaRepository(mediaDao)
        musics = repository.getMusics()
        folders = repository.getFolders()
    }

    fun getFolder(name:String):MutableLiveData<RoomFolderModel>{
        val folder = MutableLiveData<RoomFolderModel>()
        viewModelScope.launch (Dispatchers.IO) {
          folder.postValue(repository.getFolder(name))
        }
        return folder
    }

    fun getMusic(position:Int):MutableLiveData<RoomAudioModel>{
        val music = MutableLiveData<RoomAudioModel>()
        viewModelScope.launch (Dispatchers.IO) {
            music.postValue(repository.getMusic(position))
        }
        return music
    }

    fun updateFolder(folder:RoomFolderModel ){
        viewModelScope.launch (Dispatchers.IO) {
            repository.updateFolder(folder)
        }
    }

    fun insertMusics(musics: List<RoomAudioModel>){
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMusics(musics)
        }
    }

    fun getFoldersCount():Int{
        var folderCount = 2
        viewModelScope.launch (Dispatchers.IO) {
            folderCount = repository.getFoldersCount()
        }
        return folderCount
    }

    fun checkForExist(newFolderName: String):Boolean{
        var existing = false
        viewModelScope.launch(Dispatchers.IO){
            existing = repository.checkForExist(newFolderName)
        }
        return existing
    }

    fun setNewFolderName(newFolderName: String, oldFolderName: String){
        viewModelScope.launch (Dispatchers.IO) {
            repository.setNewFolderName(newFolderName, oldFolderName)
        }
    }

    fun setFavorite(intState:Int,position: Int){
        viewModelScope.launch (Dispatchers.IO) {
            repository.setFavorite(intState, position)
        }
    }

    fun deleteFolder(roomFolderModel: RoomFolderModel){
        viewModelScope.launch (Dispatchers.IO) {
            repository.deleteFolder(roomFolderModel)
        }
    }

    fun insertFolder(roomFolderModel: RoomFolderModel){
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertFolder(roomFolderModel)
        }
    }
}