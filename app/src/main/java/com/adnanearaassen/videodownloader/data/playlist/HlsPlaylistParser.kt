package com.adnanearaassen.videodownloader.data.playlist

import java.net.URI

/**
 * A small, dependency-free M3U8 parser.
 *
 * It intentionally does *not* fetch anything from the network — callers pass in the
 * already-downloaded playlist text plus the URL it came from (used to resolve
 * relative segment/variant URIs). This keeps parsing pure and unit-testable.
 *
 * Supports the tags this app actually needs:
 *  - Master:  #EXT-X-STREAM-INF, #EXT-X-MEDIA
 *  - Media:   #EXTINF, #EXT-X-KEY, #EXT-X-ENDLIST, #EXT-X-MAP
 */
object HlsPlaylistParser {

    class InvalidPlaylistException(message: String) : Exception(message)

    /**
     * Parses [content] into a [HlsPlaylist]. Detects master vs media by tag presence:
     * a playlist with `#EXT-X-STREAM-INF` is a master; one with `#EXTINF` is a media
     * playlist. Throws [InvalidPlaylistException] for anything that isn't valid M3U8.
     */
    fun parse(content: String, baseUrl: String): HlsPlaylist {
        val text = content.trim()
        if (!text.startsWith("#EXTM3U")) {
            throw InvalidPlaylistException("Missing #EXTM3U header — not a valid M3U8 playlist")
        }

        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        return if (isMaster) parseMaster(lines, baseUrl) else parseMedia(lines, baseUrl)
    }

    private fun parseMaster(lines: List<String>, baseUrl: String): HlsPlaylist.Master {
        val variants = mutableListOf<HlsVariant>()
        val renditions = mutableListOf<HlsRendition>()

        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    pending = parseAttributes(line.substringAfter(':'))
                }

                line.startsWith("#EXT-X-MEDIA:") -> {
                    val a = parseAttributes(line.substringAfter(':'))
                    renditions += HlsRendition(
                        type = a["TYPE"].orEmpty(),
                        groupId = a["GROUP-ID"],
                        name = a["NAME"],
                        uri = a["URI"]?.let { resolve(baseUrl, it) },
                        isDefault = a["DEFAULT"].equals("YES", ignoreCase = true),
                    )
                }

                !line.startsWith("#") -> {
                    // URI line that follows a #EXT-X-STREAM-INF.
                    val a = pending
                    if (a != null) {
                        val (w, h) = parseResolution(a["RESOLUTION"])
                        variants += HlsVariant(
                            uri = resolve(baseUrl, line),
                            bandwidthBps = a["BANDWIDTH"]?.toLongOrNull(),
                            averageBandwidthBps = a["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                            width = w,
                            height = h,
                            codecs = a["CODECS"],
                            frameRate = a["FRAME-RATE"]?.toDoubleOrNull(),
                            audioGroupId = a["AUDIO"],
                        )
                        pending = null
                    }
                }
            }
        }

        if (variants.isEmpty()) {
            throw InvalidPlaylistException("Master playlist contained no usable variants")
        }
        // Highest quality first.
        variants.sortByDescending { it.height ?: 0 }
        return HlsPlaylist.Master(variants, renditions)
    }

    private fun parseMedia(lines: List<String>, baseUrl: String): HlsPlaylist.Media {
        val segments = mutableListOf<HlsSegment>()
        var key: HlsKey? = null
        var isComplete = false
        var pendingDuration = 0.0

        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> {
                    // Format: #EXTINF:<duration>,[title]
                    pendingDuration = line.substringAfter(':')
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull() ?: 0.0
                }

                line.startsWith("#EXT-X-KEY:") -> {
                    val a = parseAttributes(line.substringAfter(':'))
                    val method = a["METHOD"].orEmpty()
                    key = HlsKey(
                        method = method,
                        uri = a["URI"]?.let { resolve(baseUrl, it) },
                        iv = a["IV"],
                    )
                }

                line.startsWith("#EXT-X-ENDLIST") -> isComplete = true

                !line.startsWith("#") -> {
                    segments += HlsSegment(resolve(baseUrl, line), pendingDuration)
                    pendingDuration = 0.0
                }
            }
        }

        if (segments.isEmpty()) {
            throw InvalidPlaylistException("Media playlist contained no segments")
        }
        val total = segments.sumOf { it.durationSec }
        return HlsPlaylist.Media(segments, total, key, isComplete)
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Parses a comma-separated HLS attribute list, honoring quoted values that may
     * themselves contain commas (e.g. CODECS="avc1.640028,mp4a.40.2"). Keys are
     * upper-cased; surrounding quotes are stripped from values.
     */
    internal fun parseAttributes(input: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        val n = input.length
        while (i < n) {
            // key
            val keyStart = i
            while (i < n && input[i] != '=') i++
            if (i >= n) break
            val key = input.substring(keyStart, i).trim().uppercase()
            i++ // skip '='

            // value (quoted or bare)
            val value: String
            if (i < n && input[i] == '"') {
                i++
                val vStart = i
                while (i < n && input[i] != '"') i++
                value = input.substring(vStart, i)
                if (i < n) i++ // skip closing quote
            } else {
                val vStart = i
                while (i < n && input[i] != ',') i++
                value = input.substring(vStart, i).trim()
            }
            out[key] = value

            // skip trailing comma / whitespace
            while (i < n && (input[i] == ',' || input[i] == ' ')) i++
        }
        return out
    }

    private fun parseResolution(res: String?): Pair<Int?, Int?> {
        if (res.isNullOrBlank()) return null to null
        val parts = res.lowercase().split('x')
        if (parts.size != 2) return null to null
        return parts[0].trim().toIntOrNull() to parts[1].trim().toIntOrNull()
    }

    /** Resolves a possibly-relative URI against the playlist's own URL. */
    internal fun resolve(baseUrl: String, ref: String): String {
        val trimmed = ref.trim()
        return try {
            URI(baseUrl).resolve(URI(trimmed)).toString()
        } catch (_: Exception) {
            // Fall back to naive resolution if the ref isn't strictly URI-legal.
            if (trimmed.startsWith("http", ignoreCase = true)) trimmed
            else baseUrl.substringBeforeLast('/', baseUrl) + "/" + trimmed.removePrefix("/")
        }
    }
}

/** Maps RFC-6381 codec identifiers to friendly names for display. */
object CodecNames {

    fun video(codecs: String?): String? {
        val c = codecs?.lowercase() ?: return null
        return when {
            "avc1" in c || "avc3" in c || "h264" in c -> "H.264"
            "hvc1" in c || "hev1" in c || "h265" in c || "hevc" in c -> "H.265"
            "vp09" in c || "vp9" in c -> "VP9"
            "vp08" in c || "vp8" in c -> "VP8"
            "av01" in c -> "AV1"
            else -> null
        }
    }

    fun audio(codecs: String?): String? {
        val c = codecs?.lowercase() ?: return null
        return when {
            "mp4a" in c || "aac" in c -> "AAC"
            "ac-3" in c || "ac3" in c -> "AC-3"
            "ec-3" in c || "eac3" in c -> "E-AC-3"
            "opus" in c -> "Opus"
            "mp3" in c || ".40.34" in c -> "MP3"
            "vorbis" in c -> "Vorbis"
            else -> null
        }
    }
}
