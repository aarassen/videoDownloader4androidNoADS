package com.adnanearaassen.videodownloader.ui.navigation

import android.net.Uri

/** Type-safe-ish route definitions for the app's three screens. */
object Destinations {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    // Browser takes the page URL as an encoded *query* argument. A query param avoids
    // the encoded-slash matching problems that path arguments have with full URLs.
    const val BROWSER_ARG_URL = "url"
    const val BROWSER = "browser?$BROWSER_ARG_URL={$BROWSER_ARG_URL}"

    fun browser(url: String): String = "browser?$BROWSER_ARG_URL=${Uri.encode(url)}"
}
