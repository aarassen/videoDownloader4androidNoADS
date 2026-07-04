package com.adnanearaassen.videodownloader.data.detection

import com.adnanearaassen.videodownloader.data.model.MediaType

/**
 * Decides whether a network request seen in the WebView points at downloadable media.
 *
 * Classification uses two signals:
 *  1. The URL path extension (`.m3u8`, `.mp4`, …).
 *  2. The HTTP `Content-Type` when available (many CDNs serve extension-less URLs).
 *
 * Unrelated resources (images, CSS, JS, fonts, analytics beacons, ad pixels) are
 * explicitly rejected so the detection list stays clean.
 */
object MediaUrlClassifier {

    // Content types that unambiguously indicate an HLS playlist.
    private val HLS_MIME = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "vnd.apple.mpegurl",
    )

    // Extension -> type for progressive containers.
    private val DIRECT_EXT = mapOf(
        "mp4" to MediaType.MP4,
        "m4v" to MediaType.MP4,
        "mkv" to MediaType.MKV,
        "webm" to MediaType.WEBM,
        "mov" to MediaType.MOV,
    )

    // Obvious non-media extensions we never treat as downloads.
    private val IGNORED_EXT = setOf(
        "js", "css", "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp",
        "woff", "woff2", "ttf", "otf", "eot", "json", "html", "htm", "xml",
        "map", "txt", "php", "gz", "wasm",
    )

    // Hosts/paths that are almost always analytics/ads, never user media.
    private val NOISE_HINTS = listOf(
        "google-analytics", "googletagmanager", "doubleclick", "/analytics",
        "/beacon", "/pixel", "/collect?", "facebook.com/tr", "/gtm.js",
    )

    /**
     * @param url absolute request URL
     * @param contentType optional response `Content-Type` header (may be null for
     *   `shouldInterceptRequest`, which only exposes request info)
     * @return the [MediaType] if this looks like downloadable media, else null.
     */
    fun classify(url: String, contentType: String? = null): MediaType? {
        val lower = url.lowercase()
        if (NOISE_HINTS.any { it in lower }) return null

        val path = lower.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").takeIf { '.' in path }

        // 1) Content-Type wins when present and conclusive.
        contentType?.lowercase()?.substringBefore(';')?.trim()?.let { mime ->
            when {
                mime in HLS_MIME -> return MediaType.HLS
                mime == "video/mp4" -> return MediaType.MP4
                mime == "video/x-matroska" -> return MediaType.MKV
                mime == "video/webm" -> return MediaType.WEBM
                mime == "video/quicktime" -> return MediaType.MOV
                // A concrete non-media mime lets us bail early.
                mime.startsWith("image/") || mime.startsWith("text/") ||
                    mime.startsWith("font/") || mime == "application/javascript" -> return null
            }
        }

        // 2) Fall back to the path extension.
        if (ext != null) {
            if (ext in IGNORED_EXT) return null
            if (ext == "m3u8" || ext == "m3u") return MediaType.HLS
            DIRECT_EXT[ext]?.let { return it }
        }

        // 3) Last resort: some HLS URLs are extension-less but contain a hint.
        if ("m3u8" in lower || "/hls/" in lower || "playlist" in lower && "master" in lower) {
            return MediaType.HLS
        }

        return null
    }
}
