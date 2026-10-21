package com.example.dahamusic.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.dahamusic.repository.MediaRepository
import com.example.dahamusic.room.RoomAudioModel
import com.example.dahamusic.room.RoomDbHelper
import com.example.dahamusic.room.RoomFolderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.example.dahamusic.library.MediaStoreAudioSource
import android.util.Log

class MediaViewModel(application:Application) : AndroidViewModel(application){

    val musics : LiveData<List<RoomAudioModel>>
    var folders : LiveData<List<RoomFolderModel>>
    private val repository : MediaRepository

    init {
        repository = MediaRepository(RoomDbHelper.DatabaseBuilder.getInstance(application))
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

    fun getMusic(uri: String): LiveData<RoomAudioModel?> {
        val music = MutableLiveData<RoomAudioModel?>()
        viewModelScope.launch(Dispatchers.IO) { music.postValue(repository.getMusic(uri)) }
        return music
    }

    suspend fun loadPlaybackQueue(name: String) = withContext(Dispatchers.IO) {
        repository.getFolder(name).audioList
    }

    fun observeMusic(uri: String) = repository.observeMusic(uri)

    fun observeFolder(name: String) = repository.observeFolder(name)

    data class RefreshState(val isRefreshing: Boolean = false, val failed: Boolean = false)
    private val _refreshState = MutableLiveData(RefreshState())
    val refreshState: LiveData<RefreshState> = _refreshState
    private var refreshJob: Job? = null
    private var refreshPending = false

    fun refreshLibrary() {
        if (refreshJob?.isActive == true) {
            refreshPending = true
            return
        }
        refreshJob = viewModelScope.launch {
            do {
                refreshPending = false
                _refreshState.value = RefreshState(isRefreshing = true)
                try {
                    repository.refreshLibrary(MediaStoreAudioSource(getApplication<Application>().contentResolver))
                    _refreshState.value = RefreshState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("LibraryRefresh", "Unable to refresh library", error)
                    _refreshState.value = RefreshState(failed = true)
                }
            } while (refreshPending)
        }
    }

    fun removeMusicFromFolder(name: String, uri: String, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.removeMusicFromFolder(name, uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlaylistEdit", "Unable to remove track from playlist", error)
                onError()
            }
        }
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

    fun setFavorite(intState:Int,uri: String){
        viewModelScope.launch (Dispatchers.IO) {
            repository.setFavorite(intState, uri)
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