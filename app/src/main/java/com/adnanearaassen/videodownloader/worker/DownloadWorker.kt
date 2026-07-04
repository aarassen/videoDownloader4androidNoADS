package com.adnanearaassen.videodownloader.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.adnanearaassen.videodownloader.data.db.DownloadDao
import com.adnanearaassen.videodownloader.data.db.DownloadEntity
import com.adnanearaassen.videodownloader.data.download.FfmpegCommandBuilder
import com.adnanearaassen.videodownloader.data.download.MediaStoreSaver
import com.adnanearaassen.videodownloader.data.model.DownloadStatus
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.antonkarpenko.ffmpegkit.Statistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs a single download end-to-end using FFmpeg, as a WorkManager foreground worker so
 * it survives the app being backgrounded.
 *
 * Pipeline:
 *  1. Load the task from Room; bail if it was paused/cancelled before we started.
 *  2. Build the FFmpeg command (remux by default, re-encode when codecs demand it).
 *  3. Execute FFmpeg, translating its statistics callbacks into live progress / speed /
 *     ETA written back to Room (which the UI observes).
 *  4. On a failed *remux*, automatically retry once as a re-encode.
 *  5. Publish the finished MP4 to the gallery via [MediaStoreSaver] and delete the temp
 *     file — no `.ts` fragments or scratch files are left behind.
 *
 * Cancellation (pause/cancel/remove from the repository) cancels the coroutine, which
 * cancels the underlying FFmpeg session and cleans up the temp file.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val dao: DownloadDao,
    private val mediaStoreSaver: MediaStoreSaver,
) : CoroutineWorker(appContext, params) {

    private val downloadId: String? get() = inputData.getString(KEY_DOWNLOAD_ID)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val entity = downloadId?.let { dao.getById(it) }
        val title = entity?.title ?: "Downloading video"
        val notification = DownloadNotifier.buildProgress(
            applicationContext, title, entity?.currentOperation ?: "Preparing…", entity?.progress ?: -1,
        )
        return foregroundInfo(entity?.id, notification)
    }

    override suspend fun doWork(): Result = coroutineScope {
        val id = downloadId ?: return@coroutineScope Result.failure()
        val entity = dao.getById(id) ?: return@coroutineScope Result.failure()

        // If the user paused/cancelled between enqueue and start, honour that.
        if (entity.status == DownloadStatus.PAUSED || entity.status == DownloadStatus.CANCELLED) {
            return@coroutineScope Result.success()
        }

        setForeground(getForegroundInfo())
        val now = System.currentTimeMillis()
        dao.updateProgress(
            id, DownloadStatus.RUNNING, progress = -1,
            downloadedBytes = 0, totalBytes = entity.totalBytes,
            speed = 0, eta = -1, operation = "Preparing…", now = now,
        )

        val tempFile = tempOutputFile(id)
        tempFile.parentFile?.mkdirs()

        try {
            // First attempt honours the codec decision made at enqueue time.
            var result = convert(entity, tempFile, reencode = entity.requiresReencode)

            // Safety net: a failed remux often means the container/codec combo needed a
            // transcode after all. Retry once, re-encoding to H.264/AAC.
            if (result is FfResult.Failed && !entity.requiresReencode) {
                dao.updateProgress(
                    id, DownloadStatus.RUNNING, -1, 0, entity.totalBytes, 0, -1,
                    "Re-encoding (compatibility fallback)…", System.currentTimeMillis(),
                )
                result = convert(entity, tempFile, reencode = true)
            }

            when (result) {
                is FfResult.Success -> publish(entity, tempFile)
                is FfResult.Cancelled -> {
                    // Terminal status (PAUSED/CANCELLED) was already set by the repository.
                    tempFile.delete()
                    Result.success()
                }
                is FfResult.Failed -> {
                    tempFile.delete()
                    dao.markFailed(
                        id, DownloadStatus.FAILED,
                        result.message.take(400), System.currentTimeMillis(),
                    )
                    Result.failure()
                }
            }
        } catch (c: CancellationException) {
            // Worker stopped (pause/cancel/system). The specific FFmpeg session is
            // already cancelled by runFfmpeg's invokeOnCancellation; just clean up the
            // partial file and let the repository-set status stand.
            tempFile.delete()
            throw c
        } catch (e: Exception) {
            tempFile.delete()
            dao.markFailed(
                id, DownloadStatus.FAILED,
                e.message ?: "Unexpected error", System.currentTimeMillis(),
            )
            Result.failure()
        }
    }

    /** Publishes the finished temp file to the gallery and marks the task completed. */
    private suspend fun publish(entity: DownloadEntity, tempFile: File): Result {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            dao.markFailed(
                entity.id, DownloadStatus.FAILED,
                "FFmpeg produced an empty file", System.currentTimeMillis(),
            )
            return Result.failure()
        }
        dao.updateProgress(
            entity.id, DownloadStatus.RUNNING, 100, tempFile.length(), tempFile.length(),
            0, 0, "Saving to gallery…", System.currentTimeMillis(),
        )
        val uri = mediaStoreSaver.save(tempFile, entity.title)
        tempFile.delete()
        dao.markCompleted(
            entity.id, DownloadStatus.COMPLETED, uri.toString(),
            "Completed", System.currentTimeMillis(),
        )
        return Result.success()
    }

    /**
     * Runs one FFmpeg pass and streams progress to Room. Returns when FFmpeg finishes,
     * is cancelled, or fails.
     */
    private suspend fun convert(
        entity: DownloadEntity,
        tempFile: File,
        reencode: Boolean,
    ): FfResult = coroutineScope {
        val args = FfmpegCommandBuilder.build(
            sourceUrl = entity.sourceUrl,
            outputPath = tempFile.absolutePath,
            isHls = entity.isHls,
            reencode = reencode,
            headers = FfmpegCommandBuilder.RequestHeaders(
                referer = entity.referer,
                userAgent = entity.userAgent,
                cookies = entity.cookies,
            ),
            audioIsAac = entity.audioIsAac,
            audioUrl = entity.audioUrl,
        )

        val latestStats = MutableStateFlow<Statistics?>(null)
        val operation = if (reencode) "Re-encoding to MP4" else "Downloading & remuxing"
        val durationMs = entity.durationSec * 1000.0
        val startWall = System.nanoTime()

        // Sampler: throttled writes to Room + notification updates so we don't hammer
        // the DB on every statistics callback (which can fire many times per second).
        val sampler = launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS.milliseconds)
                val s = latestStats.value ?: continue
                val elapsedWallSec = (System.nanoTime() - startWall) / 1_000_000_000.0
                val processedMs = s.time
                val size = s.size.coerceAtLeast(0)

                val progress = if (durationMs > 0) {
                    ((processedMs / durationMs) * 100).roundToInt().coerceIn(0, 99)
                } else -1

                val speed = if (elapsedWallSec > 0) (size / elapsedWallSec).roundToLong() else 0L
                val eta = if (durationMs > 0 && processedMs > 0) {
                    val rate = processedMs / (elapsedWallSec * 1000.0) // media-ms per wall-ms
                    if (rate > 0) (((durationMs - processedMs) / rate) / 1000.0).roundToLong() else -1
                } else -1

                dao.updateProgress(
                    entity.id, DownloadStatus.RUNNING, progress,
                    downloadedBytes = size,
                    totalBytes = if (entity.totalBytes > 0) entity.totalBytes else size,
                    speed = speed, eta = eta, operation = operation,
                    now = System.currentTimeMillis(),
                )
                runCatching {
                    setForeground(
                        foregroundInfo(
                            entity.id,
                            DownloadNotifier.buildProgress(applicationContext, entity.title, operation, progress),
                        )
                    )
                }
            }
        }

        try {
            runFfmpeg(args) { stats -> latestStats.value = stats }
        } finally {
            sampler.cancel()
        }
    }

    /** Bridges FFmpegKit's async, callback-based execution into a cancellable suspend. */
    private suspend fun runFfmpeg(
        args: List<String>,
        onStats: (Statistics) -> Unit,
    ): FfResult = suspendCancellableCoroutine { cont ->
        val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { completed ->
                val rc = completed.returnCode
                val result = when {
                    ReturnCode.isSuccess(rc) -> FfResult.Success
                    ReturnCode.isCancel(rc) -> FfResult.Cancelled
                    else -> FfResult.Failed(
                        completed.failStackTrace
                            ?: completed.allLogsAsString?.takeLast(600)
                            ?: "FFmpeg failed with return code $rc"
                    )
                }
                if (cont.isActive) cont.resume(result)
            },
            { /* log callback: intentionally quiet; failures surface via allLogs */ },
            { stats -> onStats(stats) },
        )
        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    private fun tempOutputFile(id: String): File =
        File(File(applicationContext.filesDir, "downloads"), "$id.mp4")

    private fun foregroundInfo(id: String?, notification: android.app.Notification): ForegroundInfo {
        // minSdk is 29, so the typed ForegroundInfo constructor is always available.
        val notifId = (id?.hashCode() ?: DEFAULT_NOTIF_ID)
        return ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /** Internal result of one FFmpeg pass. */
    private sealed interface FfResult {
        data object Success : FfResult
        data object Cancelled : FfResult
        data class Failed(val message: String) : FfResult
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val PROGRESS_INTERVAL_MS = 700L
        private const val DEFAULT_NOTIF_ID = 42
    }
}
