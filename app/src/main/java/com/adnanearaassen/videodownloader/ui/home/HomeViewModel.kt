package com.adnanearaassen.videodownloader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearaassen.videodownloader.data.analyze.MediaAnalyzer
import com.adnanearaassen.videodownloader.data.download.DownloadRepository
import com.adnanearaassen.videodownloader.data.model.AnalysisResult
import com.adnanearaassen.videodownloader.data.model.DetectedMedia
import com.adnanearaassen.videodownloader.data.model.MediaFormat
import com.adnanearaassen.videodownloader.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the Home screen's direct-link analysis + format sheet. */
data class HomeUiState(
    val isAnalyzing: Boolean = false,
    val analysis: List<AnalysisResult> = emptyList(),
    val showFormatSheet: Boolean = false,
    val message: String? = null,
)

/**
 * Handles the case where the entered/shared link is *itself* a media URL (an M3U/M3U8
 * playlist or a direct file): it analyzes it right away and shows the format sheet, so the
 * user never has to open the browser. Webpage URLs are handled by the browser instead.
 */
class HomeViewModel(
    private val analyzer: MediaAnalyzer,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Analyze a direct media URL and reveal the format sheet with the results. */
    fun analyzeDirect(url: String, type: MediaType) {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, showFormatSheet = true, analysis = emptyList()) }
            val media = DetectedMedia(
                url = url,
                type = type,
                pageUrl = null,
                pageTitle = null,
                referer = null,
                userAgent = null,
                cookies = null,
                detectedAtMillis = System.currentTimeMillis(),
            )
            val result = analyzer.analyze(media)
            _state.update { it.copy(isAnalyzing = false, analysis = listOf(result)) }
        }
    }

    fun download(format: MediaFormat) {
        viewModelScope.launch {
            val title = titleFor(format)
            downloadRepository.enqueue(format, title)
            _state.update { it.copy(showFormatSheet = false, message = "Added \"$title\" to downloads") }
        }
    }

    fun dismissFormatSheet() = _state.update { it.copy(showFormatSheet = false) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun titleFor(format: MediaFormat): String {
        val name = format.sourceUrl
            .substringBefore('?')
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "video" }
            .take(60)
        return "$name - ${format.label}"
    }
}
