package com.adnanearaassen.videodownloader.data.playlist

/** Parsed representation of an M3U8 playlist — either a master or a media playlist. */
sealed interface HlsPlaylist {

    /**
     * A master (a.k.a. multivariant) playlist: a menu of quality renditions plus
     * optional alternate audio/subtitle groups.
     */
    data class Master(
        val variants: List<HlsVariant>,
        val audioRenditions: List<HlsRendition>,
    ) : HlsPlaylist

    /**
     * A media playlist: the actual ordered list of segments for one rendition.
     *
     * @param isComplete true when the playlist carried `#EXT-X-ENDLIST` (VOD). A live
     *   stream without an end tag cannot be fully downloaded as a finite file.
     */
    data class Media(
        val segments: List<HlsSegment>,
        val totalDurationSec: Double,
        val key: HlsKey?,
        val isComplete: Boolean,
    ) : HlsPlaylist
}

/** One quality rendition from a master playlist's `#EXT-X-STREAM-INF`. */
data class HlsVariant(
    /** Absolute URL to the rendition's media playlist. */
    val uri: String,
    val bandwidthBps: Long?,
    val averageBandwidthBps: Long?,
    val width: Int?,
    val height: Int?,
    /** Raw CODECS attribute, e.g. "avc1.640028,mp4a.40.2". */
    val codecs: String?,
    val frameRate: Double?,
    val audioGroupId: String?,
)

/** An `#EXT-X-MEDIA` alternate rendition (audio/subtitles). */
data class HlsRendition(
    val type: String,
    val groupId: String?,
    val name: String?,
    val uri: String?,
    val isDefault: Boolean,
)

/** One media segment from a media playlist. */
data class HlsSegment(
    val uri: String,
    val durationSec: Double,
)

/** `#EXT-X-KEY` encryption info. [method] is NONE, AES-128, or SAMPLE-AES. */
data class HlsKey(
    val method: String,
    val uri: String?,
    val iv: String?,
) {
    val isEncrypted: Boolean get() = !method.equals("NONE", ignoreCase = true)
    /** We can fully handle AES-128 as long as the key URI is publicly fetchable. */
    val isSupported: Boolean get() = method.equals("AES-128", ignoreCase = true) || !isEncrypted
}
