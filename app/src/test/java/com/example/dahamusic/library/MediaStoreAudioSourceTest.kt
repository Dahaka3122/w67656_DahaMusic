package com.example.dahamusic.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MediaStoreAudioSourceTest {
    private class MediaProvider : ContentProvider() {
        var cursor: Cursor? = null
        var denied = false
        var columns: List<String>? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (denied) throw SecurityException("permission revoked")
            columns = projection?.toList()
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private fun provider(): Pair<MediaProvider, MediaStoreAudioSource> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = MediaProvider()
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        return provider to MediaStoreAudioSource(context.contentResolver)
    }

    @Test fun scansMetadataAndContentUriAndClosesCursor() = runBlocking {
        val (provider, source) = provider()
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        val cursor = MatrixCursor(columns).apply { addRow(arrayOf<Any>(42, "Title", "Artist", 123000)) }
        provider.cursor = cursor
        val song = source.scan().single()
        assertEquals("content://media/external/audio/media/42", song.audioUri)
        assertEquals("Title", song.audioTitle)
        assertEquals("Artist", song.audioArtist)
        assertEquals("123000", song.audioDuration)
        assertEquals(columns.toList(), provider.columns)
        assertTrue(cursor.isClosed)
    }

    @Test fun emptyCursorIsSuccessfulButNullCursorIsAFailure() = runBlocking {
        val (provider, source) = provider()
        val empty = MatrixCursor(arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION))
        provider.cursor = empty
        assertTrue(source.scan().isEmpty())
        assertTrue(empty.isClosed)
        provider.cursor = null
        try { source.scan(); fail("Null cursor must not clear the library") }
        catch (expected: IOException) { /* distinct from an empty successful result */ }
    }

    @Test fun permissionRevocationIsReportedToSynchronizer() = runBlocking {
        val (provider, source) = provider()
        provider.denied = true
        try { source.scan(); fail("Permission denial must not be an empty result") }
        catch (expected: SecurityException) { /* caller retains the previous library */ }
    }
}
