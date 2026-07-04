package com.adnanearaassen.videodownloader.ui.browser

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearaassen.videodownloader.data.detection.DetectionRepository
import com.adnanearaassen.videodownloader.data.detection.MediaUrlClassifier
import com.adnanearaassen.videodownloader.data.analyze.MediaAnalyzer
import com.adnanearaassen.videodownloader.data.download.DownloadRepository
import com.adnanearaassen.videodownloader.data.model.AnalysisResult
import com.adnanearaassen.videodownloader.data.model.DetectedMedia
import com.adnanearaassen.videodownloader.data.model.MediaFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the in-app browser. */
data class BrowserUiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val loadingProgress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val detectedCount: Int = 0,
    val isAnalyzing: Boolean = false,
    val analysis: List<AnalysisResult> = emptyList(),
    val showFormatSheet: Boolean = false,
    val message: String? = null,
)

/**
 * Coordinates the browser UI, the passive network detection feed, on-demand analysis of
 * detected streams, and enqueueing the chosen format for download.
 */
class BrowserViewModel(
    private val detectionRepository: DetectionRepository,
    private val analyzer: MediaAnalyzer,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    /** Captured once from the WebView so the interceptor thread can build DetectedMedia. */
    @Volatile
    private var userAgent: String? = null

    init {
        // Fresh browsing session: clear whatever was detected previously.
        detectionRepository.clear()
        viewModelScope.launch {
            detectionRepository.detected.collect { list ->
                _state.update { it.copy(detectedCount = list.size) }
            }
        }
    }

    fun setUserAgent(ua: String?) { userAgent = ua }

    // ---- WebView -> ViewModel callbacks ------------------------------------

    fun onPageStarted(url: String) {
        _state.update { it.copy(currentUrl = url, isLoading = true, loadingProgress = 0) }
    }

    fun onProgress(progress: Int) {
        _state.update { it.copy(loadingProgress = progress, isLoading = progress < 100) }
    }

    fun onTitle(title: String?) {
        if (!title.isNullOrBlank()) _state.update { it.copy(pageTitle = title) }
    }

    fun onPageFinished(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        _state.update {
            it.copy(
                currentUrl = url,
                isLoading = false,
                loadingProgress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
            )
        }
    }

    /**
     * Called from the WebView's request interceptor (off the main thread) for every
     * network request. Classifies the URL and, if it's media, records it with the
     * page's credentials so downloads can be replayed authentically.
     */
    fun onNetworkRequest(url: String, requestHeaders: Map<String, String>) {
        val type = MediaUrlClassifier.classify(url) ?: return
        val referer = requestHeaders["Referer"]
            ?: requestHeaders["referer"]
            ?: _state.value.currentUrl.takeIf { it.isNotBlank() }
        val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

        detectionRepository.add(
            DetectedMedia(
                url = url,
                type = type,
                pageUrl = _state.value.currentUrl.ifBlank { null },
                pageTitle = _state.value.pageTitle.ifBlank { null },
                referer = referer,
                userAgent = requestHeaders["User-Agent"] ?: userAgent,
                cookies = cookies,
                detectedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    // ---- Analysis & download -----------------------------------------------

    /** Step 5 — analyze every detected URL (in parallel) and open the format sheet. */
    fun analyzeDetected() {
        val detected = detectionRepository.snapshot()
        if (detected.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val results = detected
                .map { media -> async { analyzer.analyze(media) } }
                .awaitAll()
            _state.update {
                it.copy(isAnalyzing = false, analysis = results, showFormatSheet = true)
            }
        }
    }

    fun dismissFormatSheet() {
        _state.update { it.copy(showFormatSheet = false) }
    }

    /** Step 7 — user picked a format; queue it for background download. */
    fun download(format: MediaFormat) {
        viewModelScope.launch {
            val title = buildTitle(format)
            downloadRepository.enqueue(format, title)
            _state.update { it.copy(showFormatSheet = false, message = "Added \"$title\" to downloads") }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }

    private fun buildTitle(format: MediaFormat): String {
        val base = _state.value.pageTitle.ifBlank {
            _state.value.currentUrl.substringAfter("://").substringBefore('/')
        }.take(60).ifBlank { "video" }
        return "$base - ${format.label}"
    }
}
