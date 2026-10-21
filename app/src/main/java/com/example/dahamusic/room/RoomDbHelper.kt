package com.example.dahamusic.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(entities = [RoomAudioModel::class,RoomFolderModel::class], version = 4,exportSchema = true)
@TypeConverters(Converters::class)
abstract class RoomDbHelper : RoomDatabase() {

    abstract fun roomDao():RoomDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE RoomAudioModel ADD COLUMN isAvailable INTEGER NOT NULL DEFAULT 1")
                // Old imports could duplicate URIs. Keep the oldest ID and any favorite flag.
                db.execSQL("""UPDATE RoomAudioModel SET isFavorite = CASE WHEN EXISTS
                    (SELECT 1 FROM RoomAudioModel AS other
                     WHERE other.audioUri = RoomAudioModel.audioUri AND other.isFavorite = 1)
                    THEN 1 ELSE 0 END""")
                db.execSQL("DELETE FROM RoomAudioModel WHERE id NOT IN (SELECT MIN(id) FROM RoomAudioModel GROUP BY audioUri)")
                db.execSQL("CREATE UNIQUE INDEX index_RoomAudioModel_audioUri ON RoomAudioModel(audioUri)")
            }
        }
    }

    object DatabaseBuilder {
        @Volatile private var instance :RoomDbHelper? = null

        fun getInstance(context: Context):RoomDbHelper{
            return instance ?: synchronized(this) {
                instance ?: buildRoomDb(context).also { instance = it }
            }
        }

        private fun buildRoomDb(context: Context):RoomDbHelper = Room.databaseBuilder(
            context.applicationContext,
            RoomDbHelper::class.java,
            "media_player.db")
            .addMigrations(MIGRATION_3_4)
            .build()
    }
}