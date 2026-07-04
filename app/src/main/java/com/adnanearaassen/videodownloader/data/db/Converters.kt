package com.adnanearaassen.videodownloader.data.db

import androidx.room.TypeConverter
import com.adnanearaassen.videodownloader.data.model.DownloadStatus

/**
 * Room type converters. Enums aren't persisted automatically, so map to/from String.
 * Referenced by Room's generated code, so the IDE's "unused" report is a false positive.
 */
@Suppress("unused")
class Converters {
    @TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}
