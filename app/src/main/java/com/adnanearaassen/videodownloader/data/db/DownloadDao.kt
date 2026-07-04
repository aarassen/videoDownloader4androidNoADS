package com.adnanearaassen.videodownloader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adnanearaassen.videodownloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity): Int

    /**
     * Fine-grained progress update. Kept as a single UPDATE so the frequent writes
     * from FFmpeg statistics callbacks don't churn the whole row.
     */
    @Query(
        """
        UPDATE downloads
        SET status = :status,
            progress = :progress,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speed,
            etaSeconds = :eta,
            currentOperation = :operation,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        eta: Long,
        operation: String,
        now: Long,
    ): Int

    @Query("UPDATE downloads SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, now: Long): Int

    @Query(
        "UPDATE downloads SET status = :status, errorMessage = :error, updatedAt = :now WHERE id = :id"
    )
    suspend fun markFailed(id: String, status: DownloadStatus, error: String?, now: Long): Int

    @Query(
        """
        UPDATE downloads
        SET status = :status, outputUri = :uri, progress = 100, currentOperation = :operation,
            etaSeconds = 0, speedBytesPerSec = 0, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: String,
        status: DownloadStatus,
        uri: String,
        operation: String,
        now: Long,
    ): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished(): Int
}
