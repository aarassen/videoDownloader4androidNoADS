package com.adnanearaassen.videodownloader

import android.Manifest
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
    override fun onCreate(savedInstanceState: Bundle?) {
        // Show the system splash screen until the first frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideodownloaderTheme {
                RequestNotificationPermission()
                AppNavHost()
            }
        }
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
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destinations.HOME) {

        composable(Destinations.HOME) {
            HomeScreen(
                onBrowse = { url -> navController.navigate(Destinations.browser(url)) },
                onOpenDownloads = { navController.navigate(Destinations.DOWNLOADS) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
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
