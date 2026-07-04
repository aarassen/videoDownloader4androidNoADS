package com.adnanearaassen.videodownloader.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Central place for the download notification channel and the progress/terminal
 * notifications shown while the foreground worker runs.
 */
object DownloadNotifier {

    const val CHANNEL_ID = "downloads"
    private const val CHANNEL_NAME = "Downloads"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW, // low = no sound/heads-up for progress
        ).apply {
            description = "Ongoing video downloads and conversions"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Ongoing notification used by [DownloadWorker.getForegroundInfo]/setForeground.
     * @param progress 0..100, or negative for an indeterminate bar.
     */
    fun buildProgress(
        context: Context,
        title: String,
        operation: String,
        progress: Int,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(operation)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
}
