package com.adnanearaassen.videodownloader.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.adnanearaassen.videodownloader.data.db.DownloadDao
import com.adnanearaassen.videodownloader.data.download.MediaStoreSaver

/**
 * Supplies constructor dependencies to [DownloadWorker]. Registered on WorkManager via
 * the app's [androidx.work.Configuration] — this is the manual-DI replacement for what
 * Hilt's `HiltWorkerFactory` would have done.
 */
class DownloadWorkerFactory(
    private val dao: DownloadDao,
    private val mediaStoreSaver: MediaStoreSaver,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        DownloadWorker::class.java.name ->
            DownloadWorker(appContext, workerParameters, dao, mediaStoreSaver)
        else -> null // let the default factory handle anything else
    }
}
