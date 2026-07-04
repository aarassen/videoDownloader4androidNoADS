package com.adnanearaassen.videodownloader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adnanearaassen.videodownloader.ui.browser.BrowserScreen
import com.adnanearaassen.videodownloader.ui.downloads.DownloadsScreen
import com.adnanearaassen.videodownloader.ui.home.HomeScreen
import com.adnanearaassen.videodownloader.ui.navigation.Destinations
import com.adnanearaassen.videodownloader.ui.settings.SettingsScreen
import com.adnanearaassen.videodownloader.ui.theme.VideodownloaderTheme

class MainActivity : ComponentActivity() {

    // Holds a URL delivered via a Share (ACTION_SEND) or VIEW intent, observed by Compose.
    private val incomingUrl: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show the system splash screen until the first frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        incomingUrl.value = extractSharedUrl(intent)
        enableEdgeToEdge()
        setContent {
            VideodownloaderTheme {
                RequestNotificationPermission()
                AppNavHost(incomingUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A link shared while the app is already open.
        extractSharedUrl(intent)?.let { incomingUrl.value = it }
    }
}

/**
 * Pulls a URL out of an incoming intent: the shared text of an ACTION_SEND, or the data
 * of an ACTION_VIEW. Returns the first http(s) URL found (or a bare domain), else null.
 */
private fun extractSharedUrl(intent: Intent?): String? {
    if (intent == null) return null
    val raw = when (intent.action) {
        Intent.ACTION_SEND ->
            if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    } ?: return null
    return firstUrlOrNull(raw)
}

private fun firstUrlOrNull(text: String): String? {
    Regex("""https?://\S+""").find(text)?.let { return it.value }
    val trimmed = text.trim()
    return if (trimmed.isNotEmpty() && trimmed.none { it.isWhitespace() } && '.' in trimmed) {
        trimmed
    } else {
        null
    }
}

/** Asks for POST_NOTIFICATIONS on Android 13+ so download progress notifications show. */
@Composable
private fun RequestNotificationPermission() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: downloads still work, just without a visible notification */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppNavHost(sharedUrl: MutableState<String?>) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destinations.HOME) {

        composable(Destinations.HOME) {
            HomeScreen(
                onBrowse = { url -> navController.navigate(Destinations.browser(url)) },
                onOpenDownloads = { navController.navigate(Destinations.DOWNLOADS) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                sharedUrl = sharedUrl.value,
                onSharedUrlConsumed = { sharedUrl.value = null },
            )
        }

        composable(
            route = Destinations.BROWSER,
            arguments = listOf(navArgument(Destinations.BROWSER_ARG_URL) {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) { backStackEntry ->
            // Navigation already URL-decodes string args; guard against double-encoding.
            val raw = backStackEntry.arguments?.getString(Destinations.BROWSER_ARG_URL).orEmpty()
            val startUrl = if (raw.contains('%')) Uri.decode(raw) else raw
            BrowserScreen(
                startUrl = startUrl,
                onBack = { navController.popBackStack() },
                onOpenDownloads = { navController.navigate(Destinations.DOWNLOADS) },
            )
        }

        composable(Destinations.DOWNLOADS) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
