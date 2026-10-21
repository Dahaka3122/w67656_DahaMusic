package com.example.dahamusic.room

import androidx.room.TypeConverter
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    @TypeConverter
    fun fromList(list:List<RoomAudioModel>) : String{
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun toList(string:String):List<RoomAudioModel>{
        val gson = Gson()
        val type = object : TypeToken<List<RoomAudioModel>>() {}.type
        val json = JsonParser.parseString(string).asJsonArray
        // Gson bypasses Kotlin constructor defaults when reading legacy playlist JSON.
        json.forEach { element ->
            val song = element.asJsonObject
            if (!song.has("isAvailable")) song.addProperty("isAvailable", true)
        }
        return gson.fromJson(json,type)
    }

}