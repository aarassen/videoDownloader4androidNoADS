package com.adnanearaassen.videodownloader.data.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.adnanearaassen.videodownloader.data.db.DownloadDao
import com.adnanearaassen.videodownloader.data.db.DownloadEntity
import com.adnanearaassen.videodownloader.data.model.DownloadStatus
import com.adnanearaassen.videodownloader.data.model.MediaFormat
import com.adnanearaassen.videodownloader.worker.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The app's download façade: owns the persistent queue (Room) and drives background
 * execution (WorkManager). The UI only ever talks to this class.
 *
 * WorkManager doesn't natively "pause" a running job, so pause/cancel are modelled as:
 * flip the DB status to the desired terminal/paused state *first*, then cancel the
 * worker. The worker treats those states as authoritative and won't overwrite them.
 */
class DownloadRepository(
    private val dao: DownloadDao,
    private val workManager: WorkManager,
) {

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    /** Creates a queued task for [format] and starts the background worker. */
    suspend fun enqueue(format: MediaFormat, title: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = DownloadEntity(
            id = id,
            title = title.ifBlank { format.label },
            sourceUrl = format.sourceUrl,
            audioUrl = format.audioUrl,
            formatLabel = format.label,
            outputContainer = format.outputContainer,
            isHls = format.kind != com.adnanearaassen.videodownloader.data.model.FormatKind.DIRECT_FILE,
            isEncrypted = format.isEncrypted,
            requiresReencode = CodecCompatibility.requiresReencode(format),
            audioIsAac = CodecCompatibility.audioIsAac(format),
            referer = format.referer,
            userAgent = format.userAgent,
            cookies = format.cookies,
            status = DownloadStatus.QUEUED,
            totalBytes = format.estimatedSizeBytes ?: 0L,
            durationSec = format.estimatedDurationSec ?: 0.0,
            currentOperation = "Queued",
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        enqueueWork(id)
        return id
    }

    /** Pause a running/queued download (cancels the worker, keeps the record). */
    suspend fun pause(id: String) {
        dao.updateStatus(id, DownloadStatus.PAUSED, System.currentTimeMillis())
        workManager.cancelUniqueWork(workName(id))
    }

    /** Resume a paused or failed download by re-running the worker from scratch. */
    suspend fun resume(id: String) {
        dao.updateStatus(id, DownloadStatus.QUEUED, System.currentTimeMillis())
        enqueueWork(id)
    }

    /** Retry is functionally identical to resume for our restart-based model. */
    suspend fun retry(id: String) = resume(id)

    /** Cancel a download and mark it cancelled (record kept for history). */
    suspend fun cancel(id: String) {
        dao.updateStatus(id, DownloadStatus.CANCELLED, System.currentTimeMillis())
        workManager.cancelUniqueWork(workName(id))
    }

    /** Remove a download record entirely (also cancels any running work). */
    suspend fun remove(id: String) {
        workManager.cancelUniqueWork(workName(id))
        dao.delete(id)
    }

    suspend fun clearFinished() = dao.clearFinished()

    // ---- WorkManager plumbing ---------------------------------------------

    private fun enqueueWork(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_DOWNLOAD)
            .build()

        // Unique per download id + REPLACE so resume/retry cleanly supersedes any
        // previous run for the same task.
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "download_$id"

    companion object {
        const val TAG_DOWNLOAD = "video_download"
    }
}
