package com.adnanearaassen.videodownloader.ui.downloads

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adnanearaassen.videodownloader.data.db.DownloadEntity
import com.adnanearaassen.videodownloader.ui.rememberAppContainer
import com.adnanearaassen.videodownloader.data.model.DownloadStatus
import com.adnanearaassen.videodownloader.util.Formatters

/**
 * Step 9 — Download Manager. Shows every task with live progress, speed, ETA and the
 * current FFmpeg operation, plus per-item pause/resume/cancel/retry/delete. Completed
 * items open in the system player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val vm: DownloadsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DownloadsViewModel(container.downloadRepository) }
        }
    )
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearFinished() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear finished")
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No downloads yet.\nDetect a video in the browser to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onPause = { vm.pause(item.id) },
                        onResume = { vm.resume(item.id) },
                        onCancel = { vm.cancel(item.id) },
                        onRetry = { vm.retry(item.id) },
                        onRemove = { vm.remove(item.id) },
                        onOpen = { openVideo(context, item.outputUri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(item.status)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.formatLabel} · MP4",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            // Progress bar: determinate when we know duration, else indeterminate.
            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.QUEUED) {
                if (item.progress in 0..100) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = statusLine(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = statusLine(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == DownloadStatus.FAILED)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                when (item.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                        ActionButton(Icons.Filled.Pause, "Pause", onPause)
                        ActionButton(Icons.Filled.Cancel, "Cancel", onCancel)
                    }
                    DownloadStatus.PAUSED -> {
                        ActionButton(Icons.Filled.PlayArrow, "Resume", onResume)
                        ActionButton(Icons.Filled.Delete, "Remove", onRemove)
                    }
                    DownloadStatus.FAILED -> {
                        ActionButton(Icons.Filled.Refresh, "Retry", onRetry)
                        ActionButton(Icons.Filled.Delete, "Remove", onRemove)
                    }
                    DownloadStatus.COMPLETED -> {
                        ActionButton(Icons.Filled.PlayArrow, "Play", onOpen)
                        ActionButton(Icons.Filled.Delete, "Remove", onRemove)
                    }
                    DownloadStatus.CANCELLED -> {
                        ActionButton(Icons.Filled.Refresh, "Retry", onRetry)
                        ActionButton(Icons.Filled.Delete, "Remove", onRemove)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    val (icon, tint) = when (status) {
        DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle to Color(0xFF2E7D32)
        DownloadStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED -> Icons.Filled.Pause to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.CANCELLED -> Icons.Filled.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
    }
    Icon(icon, contentDescription = status.name, tint = tint, modifier = Modifier.size(28.dp))
}

/** Builds the single-line status/metrics text shown under the progress bar. */
private fun statusLine(item: DownloadEntity): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued…"
    DownloadStatus.RUNNING -> buildString {
        append(item.currentOperation.ifBlank { "Working" })
        if (item.progress in 0..100) append(" · ${item.progress}%")
        val speed = Formatters.speed(item.speedBytesPerSec)
        if (speed != "—") append(" · $speed")
        val eta = Formatters.eta(item.etaSeconds)
        if (eta != "—") append(" · $eta")
    }
    DownloadStatus.PAUSED -> "Paused · ${Formatters.bytes(item.downloadedBytes)} so far"
    DownloadStatus.COMPLETED -> "Saved to your gallery"
    DownloadStatus.FAILED -> item.errorMessage?.let { "Failed: $it" } ?: "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

/** Opens a completed MP4 in the system video player. */
private fun openVideo(context: android.content.Context, uri: String?) {
    val target = uri?.toUri() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(target, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
