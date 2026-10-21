package com.example.dahamusic.library

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.example.dahamusic.room.RoomAudioModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

fun interface AudioSource {
    suspend fun scan(): List<RoomAudioModel>
}

class MediaStoreAudioSource(private val resolver: ContentResolver) : AudioSource {
    override suspend fun scan(): List<RoomAudioModel> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        val cursor = resolver.query(uri, projection, null, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE, ${MediaStore.Audio.Media._ID}")
            ?: throw IOException("MediaStore returned no cursor")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            buildList {
                while (it.moveToNext()) {
                    coroutineContext.ensureActive()
                    add(RoomAudioModel(
                        audioTitle = it.getString(titleColumn).orEmpty(),
                        audioDuration = it.getLong(durationColumn).toString(),
                        audioArtist = it.getString(artistColumn).orEmpty(),
                        audioUri = ContentUris.withAppendedId(uri, it.getLong(idColumn)).toString(),
                        isFavorite = 0, isSelected = false
                    ))
                }
            }
        }
    }
}
