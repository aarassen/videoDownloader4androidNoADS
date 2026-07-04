package com.adnanearaassen.videodownloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adnanearaassen.videodownloader.data.model.DownloadStatus

/**
 * Persistent record of a single download/conversion task. This is the single source of
 * truth for the Download Manager UI: the [com.adnanearaassen.videodownloader.worker.DownloadWorker]
 * writes live progress here and the UI observes it via [DownloadDao] Flows.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,

    val title: String,
    /** URL FFmpeg opens: an HLS playlist or a direct file. */
    val sourceUrl: String,
    /** Separate audio-rendition playlist to mux in (demuxed HLS); null if audio is muxed. */
    val audioUrl: String? = null,
    val formatLabel: String,
    val outputContainer: String,

    /** True if the source is an HLS playlist (affects FFmpeg flags). */
    val isHls: Boolean,
    val isEncrypted: Boolean,
    /**
     * Decided at enqueue time from the known codecs/container: true means transcode to
     * H.264/AAC, false means attempt a stream-copy remux. The worker may still fall
     * back to re-encoding at runtime if a remux attempt fails.
     */
    val requiresReencode: Boolean = false,
    /** Whether the source audio is AAC (drives the aac_adtstoasc bitstream filter). */
    val audioIsAac: Boolean = true,

    // Request-replay context.
    val referer: String?,
    val userAgent: String?,
    val cookies: String?,

    val status: DownloadStatus,
    /** 0..100, or -1 when progress is indeterminate (duration unknown). */
    val progress: Int = -1,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = -1,
    /** Human label of the current FFmpeg phase, e.g. "Remuxing to MP4". */
    val currentOperation: String = "",

    /** MediaStore content:// URI once the file is published to the gallery. */
    val outputUri: String? = null,
    val errorMessage: String? = null,

    val durationSec: Double = 0.0,
    val createdAt: Long,
    val updatedAt: Long,
)
