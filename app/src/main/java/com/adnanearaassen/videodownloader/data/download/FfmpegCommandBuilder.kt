package com.adnanearaassen.videodownloader.data.download

/**
 * Builds FFmpeg argument lists for the two download modes.
 *
 * We always produce an MP4. The key decision — **remux vs. re-encode** — is made by the
 * caller (the worker, after probing the real codecs) and passed in as the `reencode` flag:
 *
 *  - **Remux** (`-c copy`): stream-copies the existing video/audio into an MP4 container.
 *    Near-instant, no quality loss. Used when the source is already H.264/H.265 + AAC.
 *  - **Re-encode**: transcodes to H.264 (libx264) + AAC for maximum Android compatibility.
 *    Used for VP9/AV1/Opus (WebM) or other incompatible codecs.
 *
 * FFmpeg itself performs the HLS work: it reads the playlist, downloads every segment,
 * decrypts AES-128 (when the key URI is reachable) and concatenates — so no `.ts`
 * fragments are ever left on disk. Request headers (Referer/Cookie/User-Agent) are
 * forwarded to every HTTP request FFmpeg makes.
 *
 * Arguments are returned as an ordered list and executed via FFmpegKit's
 * argument-array API, which avoids all shell-quoting pitfalls.
 */
object FfmpegCommandBuilder {

    data class RequestHeaders(
        val referer: String?,
        val userAgent: String?,
        val cookies: String?,
    )

    /**
     * @param sourceUrl HLS playlist URL or direct file URL
     * @param outputPath absolute path of the MP4 FFmpeg should write
     * @param isHls whether the source is an HLS playlist
     * @param reencode true to transcode to H.264/AAC, false to stream-copy
     * @param audioIsAac hint used to decide whether the ADTS->ASC bitstream filter is
     *   needed when remuxing HLS (TS/ADTS AAC -> MP4). Ignored when re-encoding.
     */
    fun build(
        sourceUrl: String,
        outputPath: String,
        isHls: Boolean,
        reencode: Boolean,
        headers: RequestHeaders,
        audioIsAac: Boolean = true,
        audioUrl: String? = null,
    ): List<String> = buildList {
        add("-hide_banner")
        add("-y") // overwrite output if a stale temp exists

        // Input 0: the video (or muxed) stream. Input options must precede each -i.
        addInputOptions(headers, isHls)
        add("-i"); add(sourceUrl)

        // Input 1 (optional): the separate audio rendition for demuxed HLS. The same
        // headers/whitelist are repeated because input options apply per-input.
        val audio = audioUrl?.takeIf { it.isNotBlank() }
        val hasSeparateAudio = audio != null
        if (audio != null) {
            addInputOptions(headers, isHls)
            add("-i"); add(audio)
        }

        // --- stream mapping ---
        if (hasSeparateAudio) {
            // Video from input 0, audio from input 1 -> a single muxed MP4.
            add("-map"); add("0:v:0?")
            add("-map"); add("1:a:0?")
        } else {
            add("-map"); add("0:v?")
            add("-map"); add("0:a?")
        }

        if (reencode) {
            add("-c:v"); add("libx264")
            add("-preset"); add("veryfast")
            add("-crf"); add("23")
            add("-pix_fmt"); add("yuv420p")
            add("-c:a"); add("aac")
            add("-b:a"); add("160k")
        } else {
            add("-c"); add("copy")
            // ADTS AAC (as delivered in MPEG-TS) must be converted to the MP4/ASC
            // form when stream-copied into an MP4 container.
            if (isHls && audioIsAac) {
                add("-bsf:a"); add("aac_adtstoasc")
            }
        }

        // Put the moov atom up front so the file is streamable/scannable immediately.
        add("-movflags"); add("+faststart")

        add(outputPath)
    }

    /** Emits the per-input options (headers, UA, HLS protocol whitelist, reconnect). */
    private fun MutableList<String>.addInputOptions(headers: RequestHeaders, isHls: Boolean) {
        headers.userAgent?.takeIf { it.isNotBlank() }?.let {
            add("-user_agent"); add(it)
        }
        val headerBlob = buildHeaderBlob(headers)
        if (headerBlob.isNotEmpty()) {
            add("-headers"); add(headerBlob)
        }
        if (isHls) {
            // Allow the crypto/data protocols used by AES-128 keys and inline data URIs,
            // and permit segment extensions the server might use.
            add("-protocol_whitelist"); add("file,http,https,tcp,tls,crypto,data")
            add("-allowed_extensions"); add("ALL")
        }
        // Reconnect on transient network drops during long downloads.
        add("-reconnect"); add("1")
        add("-reconnect_streamed"); add("1")
        add("-reconnect_delay_max"); add("5")
    }

    /** Joins Referer/Cookie into the CRLF-delimited blob FFmpeg's `-headers` expects. */
    private fun buildHeaderBlob(headers: RequestHeaders): String {
        val lines = buildList {
            headers.referer?.takeIf { it.isNotBlank() }?.let { add("Referer: $it") }
            headers.cookies?.takeIf { it.isNotBlank() }?.let { add("Cookie: $it") }
        }
        return if (lines.isEmpty()) "" else lines.joinToString("\r\n") + "\r\n"
    }
}
