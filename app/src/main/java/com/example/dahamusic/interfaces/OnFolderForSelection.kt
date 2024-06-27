package com.example.dahamusic.interfaces

import com.example.dahamusic.room.RoomFolderModel

interface OnFolderForSelection {
    fun onFolderForSelectionClick(model:RoomFolderModel,position: Int)
}