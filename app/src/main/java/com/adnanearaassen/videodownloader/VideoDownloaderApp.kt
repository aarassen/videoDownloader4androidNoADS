package com.adnanearaassen.videodownloader

import android.app.Application
import androidx.work.Configuration
import com.adnanearaassen.videodownloader.di.AppContainer
import com.adnanearaassen.videodownloader.worker.DownloadNotifier
import com.adnanearaassen.videodownloader.worker.DownloadWorkerFactory

/**
 * Application entry point.
 *
 * Owns the manual DI [AppContainer] (see that class for why we don't use Hilt on AGP 9)
 * and configures WorkManager to build [com.adnanearaassen.videodownloader.worker.DownloadWorker]
 * with constructor dependencies via [DownloadWorkerFactory]. The default WorkManager
 * initializer is disabled in the manifest so this on-demand configuration is used.
 */
class VideoDownloaderApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DownloadNotifier.createChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DownloadWorkerFactory(container.downloadDao, container.mediaStoreSaver)
            )
            .build()
}
