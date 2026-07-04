package com.adnanearaassen.videodownloader.data.model

/** Lifecycle state of a download task shown in the Download Manager. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isActive: Boolean get() = this == QUEUED || this == RUNNING
    /** Whether the user can (re)start work from this state. */
    val canStart: Boolean get() = this == PAUSED || this == FAILED
}
