package com.adnanearaassen.videodownloader.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.adnanearaassen.videodownloader.data.analyze.MediaAnalyzer
import com.adnanearaassen.videodownloader.data.db.AppDatabase
import com.adnanearaassen.videodownloader.data.db.DownloadDao
import com.adnanearaassen.videodownloader.data.detection.DetectionRepository
import com.adnanearaassen.videodownloader.data.download.DownloadRepository
import com.adnanearaassen.videodownloader.data.download.MediaStoreSaver
import com.adnanearaassen.videodownloader.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual dependency-injection container (a ServiceLocator).
 *
 * We use manual DI instead of Hilt because Hilt's Gradle plugin is not compatible with
 * AGP 9 (it looks up the removed `BaseExtension` API and fails to apply). This container
 * gives us the same singletons with zero annotation processing and a fully deterministic
 * build. It's created once in [com.adnanearaassen.videodownloader.VideoDownloaderApp].
 *
 * Everything is `by lazy` so nothing is constructed until first used — importantly, the
 * WorkManager instance isn't touched during Application startup, which avoids a cycle
 * with the [androidx.work.Configuration.Provider] callback.
 */
class AppContainer(private val app: Context) {

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    val downloadDao: DownloadDao by lazy { database.downloadDao() }

    val detectionRepository: DetectionRepository by lazy { DetectionRepository() }

    val mediaAnalyzer: MediaAnalyzer by lazy { MediaAnalyzer(okHttpClient) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app) }

    val mediaStoreSaver: MediaStoreSaver by lazy {
        MediaStoreSaver(app.applicationContext, settingsRepository)
    }

    val workManager: WorkManager by lazy { WorkManager.getInstance(app) }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(downloadDao, workManager)
    }
}
