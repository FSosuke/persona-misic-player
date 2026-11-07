package com.fsosuke.personamusic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, LikedSong::class, Playlist::class, PlaylistEntry::class, PlayEvent::class, DownloadEntry::class, BlockedItem::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "persona_music.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocked (`key` TEXT NOT NULL, type TEXT NOT NULL, label TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS downloads (songId TEXT NOT NULL, fileUri TEXT NOT NULL, " +
                        "sizeBytes INTEGER NOT NULL, downloadedAt INTEGER NOT NULL, PRIMARY KEY(songId))"
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_events ADD COLUMN listenedMs INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
