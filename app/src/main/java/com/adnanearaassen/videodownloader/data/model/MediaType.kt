package com.adnanearaassen.videodownloader.data.model

/**
 * The kind of media a detected URL points at.
 *
 * We distinguish HLS playlists (which must be analyzed and then fed to FFmpeg as a
 * playlist) from progressive/direct container files (which can usually be remuxed
 * or downloaded directly).
 */
enum class MediaType(
    val displayName: String,
    /** True for M3U/M3U8 HLS playlists that need playlist analysis before download. */
    val isHls: Boolean,
) {
    HLS("HLS Stream", isHls = true),
    MP4("MP4", isHls = false),
    MKV("MKV", isHls = false),
    WEBM("WebM", isHls = false),
    MOV("MOV", isHls = false),
}
