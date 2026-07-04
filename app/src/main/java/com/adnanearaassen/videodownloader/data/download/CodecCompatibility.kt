package com.adnanearaassen.videodownloader.data.download

import com.adnanearaassen.videodownloader.data.model.MediaFormat

/**
 * Heuristics for the remux-vs-re-encode decision, based on the codecs/container we know
 * about *before* downloading. The worker keeps a runtime safety net: if a stream-copy
 * remux fails, it retries with a full re-encode.
 *
 * MP4 plays best on Android with H.264/H.265 video and AAC/AC-3/MP3 audio. WebM's
 * VP8/VP9/AV1 + Opus/Vorbis need transcoding for a compatible MP4.
 */
object CodecCompatibility {

    private val COPYABLE_VIDEO = setOf("H.264", "H.265")
    private val COPYABLE_AUDIO = setOf("AAC", "AC-3", "E-AC-3", "MP3")

    /** @return true if the format should be transcoded to H.264/AAC. */
    fun requiresReencode(format: MediaFormat): Boolean {
        // WebM is effectively always incompatible with a clean MP4 copy.
        if (format.label.contains("WebM", ignoreCase = true)) return true

        val v = format.videoCodec
        val a = format.audioCodec

        // Unknown codecs (typical for plain HLS/MP4): optimistically remux; the worker
        // will fall back to re-encoding if the copy fails.
        val videoBad = v != null && v !in COPYABLE_VIDEO
        val audioBad = a != null && a !in COPYABLE_AUDIO
        return videoBad || audioBad
    }

    fun audioIsAac(format: MediaFormat): Boolean =
        format.audioCodec == null || format.audioCodec == "AAC"
}
