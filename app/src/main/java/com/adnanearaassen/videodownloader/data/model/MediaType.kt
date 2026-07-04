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
    /** Default container/extension for the finished file when this type is remuxed. */
    val defaultContainer: String,
) {
    HLS("HLS Stream", isHls = true, defaultContainer = "mp4"),
    MP4("MP4", isHls = false, defaultContainer = "mp4"),
    MKV("MKV", isHls = false, defaultContainer = "mkv"),
    WEBM("WebM", isHls = false, defaultContainer = "webm"),
    MOV("MOV", isHls = false, defaultContainer = "mov"),
}
