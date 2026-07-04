package com.adnanearaassen.videodownloader.data.model

/** Where a [MediaFormat] came from, which drives how FFmpeg is invoked. */
enum class FormatKind {
    /** A direct progressive file (mp4/mkv/webm/mov). */
    DIRECT_FILE,

    /** A specific quality rendition extracted from an HLS master playlist. */
    HLS_VARIANT,

    /** A media playlist used directly (no master / single quality). */
    HLS_MEDIA,
}

/**
 * A single, selectable download option shown to the user in the format sheet.
 *
 * One [DetectedMedia] can expand into many [MediaFormat]s (e.g. a master playlist with
 * 360p/720p/1080p renditions). Estimated size/bitrate are best-effort and may be null
 * when the source doesn't advertise enough information.
 */
data class MediaFormat(
    val id: String,
    /** The exact URL FFmpeg should open (playlist URL or direct file URL). */
    val sourceUrl: String,
    /**
     * For demuxed HLS, the separate audio-rendition playlist URL to mux in as a second
     * FFmpeg input. Null when audio is already muxed into [sourceUrl] (or for direct files).
     */
    val audioUrl: String? = null,
    val kind: FormatKind,
    /** Display label, e.g. "1080p" or "MP4 Direct". */
    val label: String,
    val width: Int?,
    val height: Int?,
    /** Advertised/estimated peak bitrate in bits per second. */
    val bandwidthBps: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    /** Target output container after processing; always "mp4" per requirements. */
    val outputContainer: String = "mp4",
    val estimatedDurationSec: Double?,
    val estimatedSizeBytes: Long?,
    val isEncrypted: Boolean = false,
    /** Request-replay context inherited from the detected media. */
    val referer: String?,
    val userAgent: String?,
    val cookies: String?,
) {
    /** e.g. "1920x1080" or null if unknown. */
    val resolutionLabel: String?
        get() = if (width != null && height != null) "${width}x$height" else null

    /** Human quality tier used for sorting/labels, derived from height when present. */
    val qualityRank: Int get() = height ?: (bandwidthBps?.div(1000)?.toInt() ?: 0)
}

/**
 * The outcome of analyzing one [DetectedMedia]: the flattened list of selectable
 * formats, or an [error] describing why analysis failed (expired/invalid playlist, etc.).
 */
data class AnalysisResult(
    val source: DetectedMedia,
    val formats: List<MediaFormat>,
    val error: String? = null,
)
