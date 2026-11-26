package com.wifix.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SpeedTestRecord::class,
        DownloadTaskEntity::class,
        DownloadSegmentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WiFiXDatabase : RoomDatabase() {

    abstract fun speedTestDao(): SpeedTestDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: WiFiXDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `url` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `supportsRange` INTEGER NOT NULL,
                        `threadCount` INTEGER NOT NULL,
                        `networkPolicy` TEXT NOT NULL,
                        `etag` TEXT,
                        `lastModified` TEXT,
                        `partFilePath` TEXT NOT NULL,
                        `destinationUri` TEXT,
                        `destinationDisplay` TEXT NOT NULL,
                        `expectedSha256` TEXT,
                        `computedSha256` TEXT,
                        `integrityVerified` INTEGER,
                        `scheduledAtMs` INTEGER,
                        `errorMessage` TEXT,
                        `createdAtMs` INTEGER NOT NULL,
                        `completedAtMs` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_segments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `segmentIndex` INTEGER NOT NULL,
                        `startByte` INTEGER NOT NULL,
                        `endByte` INTEGER NOT NULL,
                        `currentOffset` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `download_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_download_segments_taskId` ON `download_segments` (`taskId`)"
                )
            }
        }

        fun getInstance(context: Context): WiFiXDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WiFiXDatabase::class.java,
                    "wifix.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
