package com.adnanearaassen.videodownloader.ui.browser

import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebViewClient that (a) mirrors navigation state back to the UI and (b) passively
 * observes every network request via [shouldInterceptRequest] to detect media URLs.
 *
 * Crucially it *returns null* from the interceptor so the request proceeds normally —
 * we only look, we never block or rewrite traffic.
 *
 * Note: [shouldInterceptRequest] is invoked on a WebView worker thread, so [onDetect]
 * must be thread-safe (it feeds a StateFlow, which is).
 */
class DetectingWebViewClient(
    private val onStarted: (url: String) -> Unit,
    private val onFinished: (url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onDetect: (url: String, headers: Map<String, String>) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        request?.let {
            val url = it.url?.toString()
            if (!url.isNullOrBlank()) {
                onDetect(url, it.requestHeaders ?: emptyMap())
            }
        }
        return null // let the WebView load the resource itself
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let(onStarted)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onFinished(url.orEmpty(), view?.canGoBack() == true, view?.canGoForward() == true)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // SPA navigations don't always fire onPageFinished; keep nav buttons accurate.
        onFinished(url ?: view?.url.orEmpty(), view?.canGoBack() == true, view?.canGoForward() == true)
    }
}

/**
 * WebChromeClient handling loading progress, page titles, and full-screen HTML5 video
 * (onShowCustomView / onHideCustomView), which the composable renders over the browser.
 */
class DetectingWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String?) -> Unit,
    // Named distinctly from the overridden methods below to avoid shadowing them.
    private val onEnterFullscreen: (View, CustomViewCallback) -> Unit,
    private val onExitFullscreen: () -> Unit,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        onTitle(title)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        onExitFullscreen()
    }
}
