package com.adnanearaassen.videodownloader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class],
    // v2: added DownloadEntity.audioUrl (separate-audio HLS muxing). Schema changes are
    // handled by fallbackToDestructiveMigration() since the download queue is transient.
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "video_downloader.db"
    }
}
