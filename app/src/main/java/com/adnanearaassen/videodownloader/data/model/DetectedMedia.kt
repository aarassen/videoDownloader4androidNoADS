package com.adnanearaassen.videodownloader.data.model

/**
 * A media URL observed on the network while the user browsed inside the in-app WebView.
 *
 * Captured together with the request context (referer, user-agent, cookies) so the
 * downloader can replay the request with the same credentials the page used — many
 * streams reject requests that lack the original Referer/Cookie headers.
 */
data class DetectedMedia(
    /** Absolute media URL (playlist or direct file). Also used as the dedupe key. */
    val url: String,
    val type: MediaType,
    /** The page the user was on when this URL was seen. */
    val pageUrl: String?,
    val pageTitle: String?,
    /** Request context to replay downloads with the page's credentials. */
    val referer: String?,
    val userAgent: String?,
    val cookies: String?,
    val detectedAtMillis: Long,
) {
    /** Stable identity for de-duplication and list keys. */
    val id: String get() = url

    /** A short, human-friendly label derived from the URL's last path segment. */
    val shortName: String
        get() = url.substringBefore('?')
            .substringAfterLast('/')
            .ifBlank { url }
            .take(60)
}
