package com.example.dahamusic.interfaces

import android.view.View
import com.example.dahamusic.room.RoomAudioModel
import java.io.Serializable

interface OnMusicItemClick : Serializable {
    fun onMenuItemClick(model:RoomAudioModel,position: Int,view:View)
    fun onMusicModelLongClick(position: Int)

}