package com.example.dahamusic.interfaces

import android.view.View
import com.example.dahamusic.room.RoomFolderModel
import java.io.Serializable

interface OnFolderListener:Serializable {
    fun onFolderItemClick(view:View,folder:RoomFolderModel,position:Int)
    fun onFolderClick(folder:RoomFolderModel)
}