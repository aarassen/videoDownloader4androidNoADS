package com.adnanearaassen.videodownloader.data.model

/** Lifecycle state of a download task shown in the Download Manager. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}
