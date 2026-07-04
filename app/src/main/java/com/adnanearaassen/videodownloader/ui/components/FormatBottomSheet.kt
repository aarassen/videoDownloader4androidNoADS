package com.adnanearaassen.videodownloader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnanearaassen.videodownloader.data.model.AnalysisResult
import com.adnanearaassen.videodownloader.data.model.MediaFormat
import com.adnanearaassen.videodownloader.util.Formatters

/**
 * Step 6 — the format picker. Lists every analyzed source and its available qualities
 * (resolution, codec, bitrate, estimated size). Tapping a row queues that format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatBottomSheet(
    analysis: List<AnalysisResult>,
    isAnalyzing: Boolean,
    onSelect: (MediaFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Available formats",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Choose a quality to download as MP4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            when {
                isAnalyzing -> AnalyzingRow()

                analysis.all { it.formats.isEmpty() } -> {
                    val err = analysis.firstNotNullOfOrNull { it.error }
                    Text(
                        err ?: "No downloadable formats were found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                        analysis.forEachIndexed { index, result ->
                            if (result.formats.isEmpty()) return@forEachIndexed
                            item(key = "hdr_$index") {
                                SourceHeader(result)
                            }
                            items(
                                items = result.formats.sortedByDescending { it.qualityRank },
                                key = { it.id },
                            ) { format ->
                                FormatRow(format = format, onSelect = { onSelect(format) })
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Analyzing streams…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SourceHeader(result: AnalysisResult) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            result.source.pageTitle ?: result.source.shortName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatRow(format: MediaFormat, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.HighQuality,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    format.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (format.isEncrypted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "AES-128 encrypted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                format.resolutionLabel?.let { MetaChip(it) }
                codecLabel(format)?.let { MetaChip(it) }
                format.bandwidthBps?.let { MetaChip(Formatters.bitrate(it)) }
            }
            format.estimatedSizeBytes?.let {
                Spacer(Modifier.size(4.dp))
                Text(
                    "≈ ${Formatters.bytes(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onSelect) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("MP4")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaChip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(8.dp),
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

private fun codecLabel(format: MediaFormat): String? {
    val v = format.videoCodec
    val a = format.audioCodec
    return when {
        v != null && a != null -> "$v · $a"
        v != null -> v
        a != null -> a
        else -> null
    }
}
