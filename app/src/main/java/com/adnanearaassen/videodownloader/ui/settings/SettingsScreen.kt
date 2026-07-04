package com.adnanearaassen.videodownloader.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearaassen.videodownloader.ui.rememberAppContainer

/**
 * Settings — lets the user choose the download destination folder using the Storage
 * Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). The picked folder can be anywhere the
 * user has access to (internal storage, SD card, USB). We persist read/write permission
 * so it keeps working across reboots. Clearing it reverts to the default gallery location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = rememberAppContainer().settingsRepository
    val folderUri by settings.folderUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF folder picker. On success we persist the grant so it survives process death.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            settings.setCustomFolder(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Download location",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Current destination preview.
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            "Videos are saved to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            folderUri?.let { friendlyTreePath(it) }
                                ?: "Default — Movies/VideoDownloader (gallery)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Button(
                onClick = { picker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Choose folder…")
            }

            if (folderUri != null) {
                OutlinedButton(
                    onClick = { settings.clearCustomFolder() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use default gallery location")
                }
            }

            Text(
                text = if (folderUri != null) {
                    "Files go to your chosen folder. Videos saved outside the standard " +
                        "media folders may not appear in the gallery automatically."
                } else {
                    "Files are added to your gallery automatically via the system media store."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Turns a SAF tree Uri into a readable path like "Internal storage/Movies/Clips".
 * Falls back to the raw last path segment if the document id isn't in the expected form.
 */
private fun friendlyTreePath(uri: Uri): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri) // e.g. "primary:Movies/Clips"
    val volume = docId.substringBefore(':', "")
    val path = docId.substringAfter(':', "")
    val volumeName = if (volume.equals("primary", ignoreCase = true)) "Internal storage" else volume
    if (path.isBlank()) volumeName else "$volumeName/$path"
}.getOrDefault(uri.lastPathSegment ?: uri.toString())
