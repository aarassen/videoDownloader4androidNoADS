package com.adnanearaassen.videodownloader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.adnanearaassen.videodownloader.VideoDownloaderApp
import com.adnanearaassen.videodownloader.di.AppContainer

/**
 * Convenience accessor for the manual-DI [AppContainer] from within composables. Used by
 * the screens to build their ViewModels via `viewModel(factory = …)` — the manual-DI
 * equivalent of Hilt's `hiltViewModel()`.
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as VideoDownloaderApp).container
}
