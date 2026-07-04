package com.adnanearaassen.videodownloader.data.detection

import com.adnanearaassen.videodownloader.data.model.DetectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide store of media URLs detected in the current browsing session.
 *
 * The WebView layer writes here as requests fly by; the browser UI observes
 * [detected] to drive the animated download FAB and its badge count. Held as a single
 * shared instance (via the app container) so the interceptor (background thread) and the
 * Compose UI share one source of truth.
 */
class DetectionRepository {

    private val _detected = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detected: StateFlow<List<DetectedMedia>> = _detected.asStateFlow()

    /**
     * Records a detected media URL. De-duplicates by URL; if the same URL is seen
     * again we keep the earliest entry (its request context is usually the freshest
     * that actually played). Safe to call from any thread.
     */
    fun add(media: DetectedMedia) {
        _detected.update { current ->
            if (current.any { it.url == media.url }) current
            else current + media
        }
    }

    /** Clears detections — called when a new browsing session starts. */
    fun clear() {
        _detected.value = emptyList()
    }

    fun snapshot(): List<DetectedMedia> = _detected.value
}
