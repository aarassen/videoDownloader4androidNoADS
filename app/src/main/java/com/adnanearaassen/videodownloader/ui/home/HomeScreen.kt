package com.adnanearaassen.videodownloader.ui.home

import android.content.ClipboardManager
import android.content.Context
import com.adnanearaassen.videodownloader.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adnanearaassen.videodownloader.data.detection.MediaUrlClassifier
import com.adnanearaassen.videodownloader.ui.components.FormatBottomSheet
import com.adnanearaassen.videodownloader.ui.rememberAppContainer

/**
 * Step 1 — Home. The user enters/pastes a URL (or shares one into the app). If it's a
 * webpage we open the in-app browser; if it's *itself* a media link (M3U/M3U8 or a direct
 * file) we analyze it immediately and show the format sheet — no browsing needed.
 *
 * @param sharedUrl a URL delivered via an incoming Share/VIEW intent, auto-handled once.
 * @param onSharedUrlConsumed called after [sharedUrl] has been handled, to clear it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBrowse: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
) {
    val container = rememberAppContainer()
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.mediaAnalyzer, container.downloadRepository) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun paste() {
        readClipboardText(context)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            url = it
            error = null
        }
    }

    // Route a URL: media links are analyzed in place, webpages open the browser.
    fun handleUrl(raw: String) {
        val normalized = normalizeUrl(raw)
        if (normalized == null) {
            error = "Enter a valid website address"
            return
        }
        error = null
        val type = MediaUrlClassifier.classify(normalized)
        if (type != null) vm.analyzeDirect(normalized, type) else onBrowse(normalized)
    }

    fun submit() = handleUrl(url)

    // Auto-handle a link shared into the app.
    LaunchedEffect(sharedUrl) {
        sharedUrl?.let {
            url = it
            handleUrl(it)
            onSharedUrlConsumed()
        }
    }

    // Surface download-queued confirmations.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Video Downloader") },
                actions = {
                    OutlinedButton(onClick = onOpenDownloads) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Downloads")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
          Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "App logo",
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Paste a webpage address",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Open any page in the built-in browser, play the video, and we'll detect " +
                    "downloadable streams automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("https://example.com/video-page") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { paste() }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { submit() },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Browse")
            }
          }
        }
    }

    if (state.showFormatSheet) {
        FormatBottomSheet(
            analysis = state.analysis,
            isAnalyzing = state.isAnalyzing,
            onSelect = vm::download,
            onDismiss = vm::dismissFormatSheet,
        )
    }
}

/**
 * Accepts bare hosts ("example.com") by defaulting to https, trims whitespace, and
 * rejects obviously non-URL input. Returns null when the input can't be a web address.
 */
private fun normalizeUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || ' ' in trimmed) return null
    val withScheme = when {
        trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
        else -> "https://$trimmed"
    }
    // Must contain a dot in the host part to be plausibly a domain.
    val host = withScheme.substringAfter("://").substringBefore('/')
    return if ('.' in host) withScheme else null
}

/** Reads the current clipboard's primary text, or null if empty/unavailable. */
private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
