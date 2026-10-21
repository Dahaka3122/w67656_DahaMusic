package com.example.dahamusic.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun readsPlaylistStoredByPreviousAppVersion() {
        val legacyJson = """[{"audioTitle":"Żółty utwór","audioDuration":"null","audioArtist":"Wykonawca","audioUri":"content://media/external/audio/media/42","isFavorite":1,"isSelected":false,"id":7}]"""

        val song = converters.toList(legacyJson).single()

        assertEquals(7, song.id)
        assertEquals("Żółty utwór", song.audioTitle)
        assertEquals("null", song.audioDuration)
        assertEquals("Wykonawca", song.audioArtist)
        assertEquals("content://media/external/audio/media/42", song.audioUri)
        assertEquals(1, song.isFavorite)
        assertFalse(song.isSelected)
    }

    @Test
    fun playlistRoundTripPreservesIdsFavoritesAndOrder() {
        val first = RoomAudioModel("Pierwszy", "null", "A", "content://media/external/audio/media/42", 1, false).apply { id = 7 }
        val second = RoomAudioModel("Drugi", "120000", "B", "content://media/external/audio/media/84", 0, true).apply { id = 19 }

        val restored = converters.toList(converters.fromList(listOf(first, second)))

        assertEquals(listOf(first, second), restored)
        // data class equality excludes id because it is outside the primary constructor.
        assertEquals(listOf(7, 19), restored.map { it.id })
        assertEquals(listOf(1, 0), restored.map { it.isFavorite })
        assertTrue(restored[1].isSelected)
        assertTrue(converters.toList(converters.fromList(emptyList())).isEmpty())
    }
}
