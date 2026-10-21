package com.example.dahamusic.room

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(indices = [Index(value = ["audioUri"], unique = true)])
data class RoomAudioModel (
    var audioTitle: String,
    var audioDuration: String,
    var audioArtist: String,
    var audioUri: String,
    var isFavorite:Int,
    var isSelected:Boolean,
    @ColumnInfo(defaultValue = "1")
    var isAvailable: Boolean = true
):Serializable{
    @PrimaryKey(autoGenerate = true)
    var id :Int = 0
}