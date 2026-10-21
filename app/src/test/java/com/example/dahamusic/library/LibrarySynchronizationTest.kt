package com.example.dahamusic.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dahamusic.repository.MediaRepository
import com.example.dahamusic.room.*
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibrarySynchronizationTest {
    private lateinit var database: RoomDbHelper
    private lateinit var repository: MediaRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun song(uri: String = "content://media/external/audio/media/42", title: String = "Old") =
        RoomAudioModel(title, "1000", "Artist", uri, 0, false)

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, RoomDbHelper::class.java)
            .allowMainThreadQueries().build()
        repository = MediaRepository(database)
    }
    @After fun closeDatabase() { database.close() }

    @Test fun repeatedScanUpdatesMetadataWithoutDuplicatingOrLosingFavoritesAndPlaylistOrder() = runBlocking<Unit> {
        val first = song().apply { id = 7; isFavorite = 1 }
        val second = song("content://media/external/audio/media/99", "Second").apply { id = 19 }
        database.roomDao().insertMusics(listOf(first, second))
        database.roomDao().insertFolder(RoomFolderModel("My playlist", listOf(second, first)))
        database.roomDao().insertFolder(RoomFolderModel(LibraryMerger.ALL_MUSIC, listOf(first)))
        database.roomDao().insertFolder(RoomFolderModel(LibraryMerger.ALL_MUSIC, listOf(second)))
        val source = AudioSource { listOf(first.copy(audioTitle = "Renamed"), second, song("content://media/external/audio/media/100", "New")) }
        repository.refreshLibrary(source)
        val ids = database.roomDao().readAllMusics().map { it.id }
        repository.refreshLibrary(source)
        val songs = database.roomDao().readAllMusics()
        assertEquals(3, songs.size)
        assertEquals(1, database.roomDao().readAllFolders().count { it.folderName == LibraryMerger.ALL_MUSIC })
        assertEquals(ids, songs.map { it.id })
        assertEquals("Renamed", songs.first { it.id == 7 }.audioTitle)
        assertEquals(1, songs.first { it.id == 7 }.isFavorite)
        val playlist = database.roomDao().getFolder("My playlist").audioList
        assertEquals(listOf(19, 7), playlist.map { it.id })
        assertEquals("Renamed", playlist[1].audioTitle)
        assertEquals(listOf(7), database.roomDao().getFolder(LibraryMerger.FAVORITES).audioList.map { it.id })
    }

    @Test fun unavailableMusicKeepsMembershipAndFavoriteAndReturnsWithSameId() = runBlocking<Unit> {
        val original = song().apply { id = 7; isFavorite = 1 }
        database.roomDao().insertMusics(listOf(original))
        database.roomDao().insertFolder(RoomFolderModel("My playlist", listOf(original)))
        repository.refreshLibrary(AudioSource { emptyList() })
        val hidden = database.roomDao().readAllMusics().single()
        assertFalse(hidden.isAvailable)
        assertEquals(1, hidden.isFavorite)
        assertEquals(7, database.roomDao().getFolder("My playlist").audioList.single().id)
        assertTrue(repository.getFolder("My playlist").audioList.isEmpty())
        repository.refreshLibrary(AudioSource { listOf(song()) })
        val restored = database.roomDao().readAllMusics().single()
        assertTrue(restored.isAvailable)
        assertEquals(7, restored.id)
        assertEquals(1, restored.isFavorite)
        assertEquals(7, repository.getFolder("My playlist").audioList.single().id)
    }

    @Test fun failedOrDeniedQueryDoesNotTreatLibraryAsEmpty() = runBlocking<Unit> {
        repository.refreshLibrary(AudioSource { listOf(song()) })
        val before = database.roomDao().readAllMusics()
        for (failure in listOf(IOException("unavailable"), SecurityException("revoked"))) {
            try {
                repository.refreshLibrary(AudioSource { throw failure })
                fail("Expected scan failure")
            } catch (expected: Exception) { assertSame(failure, expected) }
            assertEquals(before, database.roomDao().readAllMusics())
            assertEquals(1, database.roomDao().getFolder(LibraryMerger.ALL_MUSIC).audioList.size)
        }
    }

    @Test fun transactionRollsBackSongsWhenWritingFoldersFails() = runBlocking<Unit> {
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_folders
            BEFORE INSERT ON RoomFolderModel BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        try {
            repository.refreshLibrary(AudioSource { listOf(song()) })
            fail("Expected transaction failure")
        } catch (expected: android.database.sqlite.SQLiteException) { /* rollback is asserted below */ }
        assertTrue(database.roomDao().readAllMusics().isEmpty())
        assertTrue(database.roomDao().readAllFolders().isEmpty())
    }

    @Test fun concurrentRefreshesSerializeAndKeepOneRecordPerUri() = runBlocking<Unit> {
        val first = async { repository.refreshLibrary(AudioSource { delay(50); listOf(song()) }) }
        val second = async { MediaRepository(database).refreshLibrary(AudioSource { listOf(song(title = "Updated")) }) }
        first.await(); second.await()
        assertEquals(1, database.roomDao().readAllMusics().size)
        assertEquals("Updated", database.roomDao().readAllMusics().single().audioTitle)
    }

    @Test fun favoritesUseUriRatherThanPositionAndUpdateAllPlaylistSnapshots() = runBlocking<Unit> {
        val selected = song().apply { id = 19 }
        database.roomDao().insertMusics(listOf(selected))
        database.roomDao().insertFolder(RoomFolderModel("My playlist", listOf(selected)))
        repository.setFavorite(1, selected.audioUri)
        assertEquals(19, database.roomDao().getFolder(LibraryMerger.FAVORITES).audioList.single().id)
        assertEquals(1, database.roomDao().getFolder("My playlist").audioList.single().isFavorite)
        repository.setFavorite(0, selected.audioUri)
        assertTrue(database.roomDao().getFolder(LibraryMerger.FAVORITES).audioList.isEmpty())
    }

    @Test fun playlistOnlyEntriesAreRetainedAndReconciledWhenTheyReappear() = runBlocking<Unit> {
        val orphan = song().apply { id = 99; isFavorite = 1 }
        database.roomDao().insertFolder(RoomFolderModel("My playlist", listOf(orphan)))
        repository.refreshLibrary(AudioSource { emptyList() })
        val retained = database.roomDao().readAllMusics().single()
        assertFalse(retained.isAvailable)
        assertEquals(1, retained.isFavorite)
        repository.refreshLibrary(AudioSource { listOf(song()) })
        assertEquals(retained.id, repository.getFolder("My playlist").audioList.single().id)
        assertEquals(1, database.roomDao().readAllMusics().single().isFavorite)
    }

    @Test fun editingVisiblePlaylistDoesNotRemoveHiddenEntriesOrTheirOrder() = runBlocking<Unit> {
        val first = song(title = "A").apply { id = 1 }
        val hidden = song("content://media/external/audio/media/43", "B").apply { id = 2 }
        val last = song("content://media/external/audio/media/44", "C").apply { id = 3 }
        database.roomDao().insertMusics(listOf(first, hidden, last))
        database.roomDao().insertFolder(RoomFolderModel("My playlist", listOf(first, hidden, last)))
        repository.refreshLibrary(AudioSource { listOf(first, last) })
        val visible = repository.getFolder("My playlist")
        visible.audioList = visible.audioList.filterNot { it.id == 1 }
        repository.updateFolder(visible)
        repository.refreshLibrary(AudioSource { listOf(first, hidden, last) })
        assertEquals(listOf(2, 3), repository.getFolder("My playlist").audioList.map { it.id })
    }

    @Test fun favoritesFolderEditsUpdateCanonicalFlags() = runBlocking<Unit> {
        repository.refreshLibrary(AudioSource { listOf(song()) })
        val favoriteFolder = repository.getFolder(LibraryMerger.FAVORITES)
        favoriteFolder.audioList = database.roomDao().readAllMusics()
        repository.updateFolder(favoriteFolder)
        assertEquals(1, database.roomDao().readAllMusics().single().isFavorite)
        val refreshed = repository.getFolder(LibraryMerger.FAVORITES)
        assertEquals(1, refreshed.audioList.size)
        refreshed.audioList = emptyList()
        repository.updateFolder(refreshed)
        assertEquals(0, database.roomDao().readAllMusics().single().isFavorite)
        repository.refreshLibrary(AudioSource { listOf(song()) })
        assertTrue(repository.getFolder(LibraryMerger.FAVORITES).audioList.isEmpty())
    }

    @Test fun migrationFromExportedVersion3PreservesLegacyPlaylistAndConsolidatesDuplicateUris() = runBlocking<Unit> {
        database.close()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.example.dahamusic.room.RoomDbHelper/3.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            for (entity in schema.getAsJsonArray("entities")) {
                val table = entity.asJsonObject
                old.execSQL(table["createSql"].asString.replace("\${TABLE_NAME}", table["tableName"].asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { old.execSQL(it.asString) }
            old.execSQL("INSERT INTO RoomAudioModel (id, audioTitle, audioDuration, audioArtist, audioUri, isFavorite, isSelected) VALUES (7, 'Old', 'null', 'Artist', 'content://media/external/audio/media/42', 0, 0)")
            old.execSQL("INSERT INTO RoomAudioModel (id, audioTitle, audioDuration, audioArtist, audioUri, isFavorite, isSelected) VALUES (19, 'Duplicate', 'null', 'Artist', 'content://media/external/audio/media/42', 1, 0)")
            val legacyJson = """[{"id":19,"audioTitle":"Old","audioDuration":"null","audioArtist":"Artist","audioUri":"content://media/external/audio/media/42","isFavorite":0,"isSelected":false}]"""
            old.execSQL("INSERT INTO RoomFolderModel (id, folderName, audioList) VALUES (4, 'My playlist', ?)", arrayOf(legacyJson))
            old.version = 3
        }
        database = Room.databaseBuilder(context, RoomDbHelper::class.java, name)
            .addMigrations(RoomDbHelper.MIGRATION_3_4).allowMainThreadQueries().build()
        repository = MediaRepository(database)
        val migrated = database.roomDao().readAllMusics().single()
        assertEquals(7, migrated.id)
        assertEquals(1, migrated.isFavorite)
        assertTrue(migrated.isAvailable)
        assertTrue(database.roomDao().getFolder("My playlist").audioList.single().isAvailable)
        repository.refreshLibrary(AudioSource { listOf(song(title = "New metadata")) })
        val playlist = database.roomDao().getFolder("My playlist")
        assertEquals(4, playlist.id)
        assertEquals(7, playlist.audioList.single().id)
        assertEquals("New metadata", playlist.audioList.single().audioTitle)
        assertEquals(1, playlist.audioList.single().isFavorite)
        database.close()
        context.deleteDatabase(name)
    }
}
