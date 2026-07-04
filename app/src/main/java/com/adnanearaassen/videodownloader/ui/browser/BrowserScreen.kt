package com.adnanearaassen.videodownloader.ui.browser

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adnanearaassen.videodownloader.ui.components.FormatBottomSheet
import com.adnanearaassen.videodownloader.ui.rememberAppContainer

/**
 * Steps 2–7 combined: the in-app browser. Hosts a fully-featured WebView, monitors its
 * traffic for media (via [DetectingWebViewClient]), surfaces an animated download FAB
 * with a live count badge, and opens the format sheet when the user taps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    startUrl: String,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val container = rememberAppContainer()
    val vm: BrowserViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BrowserViewModel(
                    container.detectionRepository,
                    container.mediaAnalyzer,
                    container.downloadRepository,
                )
            }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Full-screen HTML5 video surface handed to us by the chrome client.
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Build & configure the WebView once, kept across recompositions.
    val context = LocalContext.current
    val browser = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            applyBrowserSettings()
            webViewClient = DetectingWebViewClient(
                onStarted = vm::onPageStarted,
                onFinished = { url, back, fwd -> vm.onPageFinished(url, back, fwd) },
                onDetect = vm::onNetworkRequest,
            )
            webChromeClient = DetectingWebChromeClient(
                onProgress = vm::onProgress,
                onTitle = vm::onTitle,
                onEnterFullscreen = { view, cb -> customView = view; customViewCallback = cb },
                onExitFullscreen = {
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                },
            )
            // `this` here is the WebView being configured.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    LaunchedEffect(Unit) {
        vm.setUserAgent(browser.settings.userAgentString)
        if (browser.url == null) browser.loadUrl(startUrl)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            browser.stopLoading()
            browser.destroy()
        }
    }

    // Back: exit fullscreen video -> go back in history -> leave the browser.
    BackHandler(enabled = true) {
        when {
            customView != null -> {
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }
            browser.canGoBack() -> browser.goBack()
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = state.pageTitle.ifBlank { hostOf(state.currentUrl.ifBlank { startUrl }) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.Close, contentDescription = "Exit browser")
                        }
                    },
                    actions = {
                        IconButton(onClick = { browser.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onOpenDownloads) {
                            Icon(Icons.Filled.Download, contentDescription = "Downloads")
                        }
                    },
                )
                AnimatedVisibility(visible = state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { browser.goBack() }, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { browser.goForward() }, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { browser.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            }
        },
        floatingActionButton = {
            DetectionFab(
                count = state.detectedCount,
                analyzing = state.isAnalyzing,
                onClick = { vm.analyzeDetected() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { browser },
                modifier = Modifier.fillMaxSize(),
            )

            // Full-screen video overlay covers the page while playing.
            if (customView != null) {
                AndroidView(
                    factory = { ctx -> android.widget.FrameLayout(ctx) },
                    update = { frame ->
                        frame.removeAllViews()
                        customView?.let { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
                            frame.addView(view)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
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

/** Animated, badge-bearing download FAB. Pulses when media has been detected. */
@Composable
private fun DetectionFab(count: Int, analyzing: Boolean, onClick: () -> Unit) {
    val hasMedia = count > 0

    // Gentle pulsing scale to draw attention once something is detected.
    val transition = rememberInfiniteTransition(label = "fab-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (hasMedia) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fab-scale",
    )

    ExtendedFloatingActionButton(
        onClick = { if (hasMedia && !analyzing) onClick() },
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        containerColor = if (hasMedia) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (hasMedia) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Icon(Icons.Filled.Download, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(if (hasMedia) "Download ($count)" else "No media yet")
    }
}

/** Applies the browser feature set required by the spec: JS, storage, cookies, video. */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyBrowserSettings() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true            // localStorage / sessionStorage (covers WebSQL too)
        javaScriptCanOpenWindowsAutomatically = true
        mediaPlaybackRequiresUserGesture = false // allow autoplay for detection
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        displayZoomControls = false
        // Allow HTTP media to load inside HTTPS pages (common for CDNs).
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
    }
}

private fun hostOf(url: String): String =
    url.substringAfter("://", url).substringBefore('/').ifBlank { url }
