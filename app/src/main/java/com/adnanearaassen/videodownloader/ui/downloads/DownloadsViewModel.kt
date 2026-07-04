package com.adnanearaassen.videodownloader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearaassen.videodownloader.data.db.DownloadEntity
import com.adnanearaassen.videodownloader.data.download.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Download Manager screen (Step 9). Thin wrapper over the repository. */
class DownloadsViewModel(
    private val repository: DownloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        repository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun pause(id: String) = viewModelScope.launch { repository.pause(id) }
    fun resume(id: String) = viewModelScope.launch { repository.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { repository.cancel(id) }
    fun retry(id: String) = viewModelScope.launch { repository.retry(id) }
    fun remove(id: String) = viewModelScope.launch { repository.remove(id) }
    fun clearFinished() = viewModelScope.launch { repository.clearFinished() }
}
