package com.adnanearaassen.videodownloader.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Small formatting helpers used across the UI and notifications. */
object Formatters {

    /** e.g. 1_500_000 -> "1.4 MB". Returns "—" for null/negative. */
    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val v = value / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %s", v, units[exp - 1])
    }

    /** Bytes-per-second -> "2.3 MB/s". */
    fun speed(bytesPerSec: Long?): String {
        if (bytesPerSec == null || bytesPerSec <= 0) return "—"
        return "${bytes(bytesPerSec)}/s"
    }

    /** Seconds -> "1:02:03" / "3:07" / "12s". */
    fun duration(totalSeconds: Long?): String {
        if (totalSeconds == null || totalSeconds < 0) return "—"
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return when {
            h > 0 -> String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            m > 0 -> String.format(Locale.US, "%d:%02d", m, s)
            else -> "${s}s"
        }
    }

    /** ETA phrasing for the download manager, e.g. "~2:05 left". */
    fun eta(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return "—"
        return "~${duration(seconds)} left"
    }

    /** Bandwidth bits/sec -> "5.0 Mbps". */
    fun bitrate(bps: Long?): String {
        if (bps == null || bps <= 0) return "—"
        val mbps = bps / 1_000_000.0
        return if (abs(mbps) >= 1.0) String.format(Locale.US, "%.1f Mbps", mbps)
        else String.format(Locale.US, "%d kbps", bps / 1000)
    }
}
