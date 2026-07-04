package com.adnanearaassen.videodownloader.data.analyze

import com.adnanearaassen.videodownloader.data.model.AnalysisResult
import com.adnanearaassen.videodownloader.data.model.DetectedMedia
import com.adnanearaassen.videodownloader.data.model.FormatKind
import com.adnanearaassen.videodownloader.data.model.MediaFormat
import com.adnanearaassen.videodownloader.data.model.MediaType
import com.adnanearaassen.videodownloader.data.playlist.CodecNames
import com.adnanearaassen.videodownloader.data.playlist.HlsPlaylist
import com.adnanearaassen.videodownloader.data.playlist.HlsPlaylistParser
import com.adnanearaassen.videodownloader.data.playlist.HlsVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Turns a [DetectedMedia] URL into the concrete list of [MediaFormat]s the user can pick.
 *
 * Responsibilities:
 *  - Direct files: emit a single format (with a best-effort size from Content-Length).
 *  - HLS master playlists: emit one format per advertised quality, and (best-effort,
 *    in parallel) fetch each rendition's media playlist to refine duration/size and to
 *    detect AES-128 encryption.
 *  - HLS media playlists: emit a single format with computed duration/size.
 *
 * All network calls replay the page's Referer / Cookie / User-Agent so credentialed
 * streams keep working.
 */
class MediaAnalyzer(
    private val client: OkHttpClient,
) {

    suspend fun analyze(media: DetectedMedia): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            if (media.type.isHls) analyzeHls(media)
            else AnalysisResult(media, listOf(directFormat(media)))
        } catch (e: HlsPlaylistParser.InvalidPlaylistException) {
            AnalysisResult(media, emptyList(), "Invalid playlist: ${e.message}")
        } catch (e: IOException) {
            AnalysisResult(media, emptyList(), friendlyNetworkError(e))
        } catch (e: Exception) {
            AnalysisResult(media, emptyList(), e.message ?: "Failed to analyze media")
        }
    }

    // ---- HLS ---------------------------------------------------------------

    private suspend fun analyzeHls(media: DetectedMedia): AnalysisResult {
        val body = fetchText(media.url, media)
        return when (val playlist = HlsPlaylistParser.parse(body, media.url)) {
            is HlsPlaylist.Master -> AnalysisResult(media, expandMaster(media, playlist))
            is HlsPlaylist.Media -> AnalysisResult(media, listOf(mediaPlaylistFormat(media, media.url, playlist)))
        }
    }

    /** One format per variant; enrich each with a parallel media-playlist probe. */
    private suspend fun expandMaster(
        media: DetectedMedia,
        master: HlsPlaylist.Master,
    ): List<MediaFormat> = coroutineScope {
        master.variants.mapIndexed { index, variant ->
            async(Dispatchers.IO) { variantFormat(media, variant, index, master) }
        }.awaitAll()
    }

    private fun variantFormat(
        media: DetectedMedia,
        variant: HlsVariant,
        index: Int,
        master: HlsPlaylist.Master,
    ): MediaFormat {
        // Best-effort probe of the rendition's media playlist for exact duration & key.
        var durationSec: Double? = null
        var encrypted = false
        runCatching {
            val text = fetchText(variant.uri, media)
            val parsed = HlsPlaylistParser.parse(text, variant.uri)
            if (parsed is HlsPlaylist.Media) {
                durationSec = parsed.totalDurationSec
                encrypted = parsed.key?.isEncrypted == true
            }
        }

        val bw = variant.averageBandwidthBps ?: variant.bandwidthBps
        val size = estimateSize(bw, durationSec)
        val label = variant.height?.let { "${it}p" }
            ?: variant.bandwidthBps?.let { "${it / 1000} kbps" }
            ?: "Variant ${index + 1}"

        // Demuxed HLS: the variant's video-only stream references an audio GROUP-ID whose
        // rendition lives in a separate playlist. Resolve it so we can mux the audio back
        // in at download time (prefer the group's DEFAULT rendition, else the first with a URI).
        val audioUrl: String? = variant.audioGroupId?.let { group ->
            val candidates = master.audioRenditions.filter {
                it.groupId == group && it.uri != null
            }
            (candidates.firstOrNull { it.isDefault } ?: candidates.firstOrNull())?.uri
        }

        return MediaFormat(
            id = "${media.id}#v$index",
            sourceUrl = variant.uri,
            audioUrl = audioUrl,
            kind = FormatKind.HLS_VARIANT,
            label = label,
            width = variant.width,
            height = variant.height,
            bandwidthBps = bw,
            videoCodec = CodecNames.video(variant.codecs),
            audioCodec = CodecNames.audio(variant.codecs),
            estimatedDurationSec = durationSec,
            estimatedSizeBytes = size,
            isEncrypted = encrypted,
            referer = media.referer,
            userAgent = media.userAgent,
            cookies = media.cookies,
        )
    }

    private fun mediaPlaylistFormat(
        media: DetectedMedia,
        url: String,
        playlist: HlsPlaylist.Media,
    ): MediaFormat {
        return MediaFormat(
            id = "${media.id}#media",
            sourceUrl = url,
            kind = FormatKind.HLS_MEDIA,
            label = "HLS (auto)",
            width = null,
            height = null,
            bandwidthBps = null,
            videoCodec = null,
            audioCodec = null,
            estimatedDurationSec = playlist.totalDurationSec,
            estimatedSizeBytes = null, // unknown without a bitrate hint
            isEncrypted = playlist.key?.isEncrypted == true,
            referer = media.referer,
            userAgent = media.userAgent,
            cookies = media.cookies,
        )
    }

    // ---- Direct files ------------------------------------------------------

    private fun directFormat(media: DetectedMedia): MediaFormat {
        val size = runCatching { probeContentLength(media) }.getOrNull()
        val containerLabel = media.type.displayName
        return MediaFormat(
            id = "${media.id}#direct",
            sourceUrl = media.url,
            kind = FormatKind.DIRECT_FILE,
            label = "$containerLabel Direct",
            width = null,
            height = null,
            bandwidthBps = null,
            videoCodec = null,
            audioCodec = null,
            estimatedDurationSec = null,
            estimatedSizeBytes = size,
            isEncrypted = false,
            referer = media.referer,
            userAgent = media.userAgent,
            cookies = media.cookies,
        )
    }

    private fun probeContentLength(media: DetectedMedia): Long? {
        val req = requestBuilder(media.url, media).head().build()
        client.newCall(req).execute().use { resp ->
            val len = resp.header("Content-Length")?.toLongOrNull()
            if (len != null && len > 0) return len
        }
        return null
    }

    // ---- shared HTTP -------------------------------------------------------

    private fun fetchText(url: String, media: DetectedMedia): String {
        val req = requestBuilder(url, media).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(httpErrorMessage(resp.code))
            }
            return resp.body?.string()
                ?: throw IOException("Empty response body for playlist")
        }
    }

    private fun requestBuilder(url: String, media: DetectedMedia): Request.Builder {
        val b = Request.Builder().url(url)
        media.referer?.let { b.header("Referer", it) }
        media.userAgent?.let { b.header("User-Agent", it) }
        media.cookies?.takeIf { it.isNotBlank() }?.let { b.header("Cookie", it) }
        return b
    }

    private fun estimateSize(bandwidthBps: Long?, durationSec: Double?): Long? {
        if (bandwidthBps == null || durationSec == null || durationSec <= 0) return null
        return (bandwidthBps / 8.0 * durationSec).toLong()
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401, 403 -> "Access denied ($code) — the stream may require login/cookies"
        404 -> "Playlist not found (404)"
        410 -> "Playlist expired (410) — reopen the page and try again"
        in 500..599 -> "Server error ($code) — try again later"
        else -> "Request failed with HTTP $code"
    }

    private fun friendlyNetworkError(e: IOException): String =
        e.message ?: "Network error while contacting the server"
}
