package com.adnanearaassen.videodownloader

import com.adnanearaassen.videodownloader.data.playlist.HlsPlaylist
import com.adnanearaassen.videodownloader.data.playlist.HlsPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    @Test
    fun parsesMasterPlaylistWithVariantsSortedByHeight() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            high/index.m3u8
        """.trimIndent()

        val result = HlsPlaylistParser.parse(master, "https://cdn.example.com/video/master.m3u8")
        assertTrue(result is HlsPlaylist.Master)
        result as HlsPlaylist.Master

        assertEquals(2, result.variants.size)
        // Highest quality first.
        assertEquals(1080, result.variants[0].height)
        assertEquals(3_000_000L, result.variants[0].bandwidthBps)
        // Relative URIs resolved against the master URL.
        assertEquals("https://cdn.example.com/video/high/index.m3u8", result.variants[0].uri)
    }

    @Test
    fun parsesMediaPlaylistDurationSegmentsAndAesKey() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.com/key.bin",IV=0x1234
            #EXTINF:9.5,
            seg0.ts
            #EXTINF:8.0,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = HlsPlaylistParser.parse(media, "https://cdn.example.com/video/index.m3u8")
        assertTrue(result is HlsPlaylist.Media)
        result as HlsPlaylist.Media

        assertEquals(2, result.segments.size)
        assertEquals(17.5, result.totalDurationSec, 0.001)
        assertTrue(result.isComplete)
        assertNotNull(result.key)
        assertEquals("AES-128", result.key!!.method)
        assertTrue(result.key!!.isEncrypted)
        assertTrue(result.key!!.isSupported)
        assertEquals("https://cdn.example.com/key.bin", result.key!!.uri)
    }

    @Test
    fun attributeParserHonorsQuotedCommas() {
        val attrs = HlsPlaylistParser.parseAttributes(
            """BANDWIDTH=3000000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080"""
        )
        assertEquals("3000000", attrs["BANDWIDTH"])
        assertEquals("avc1.640028,mp4a.40.2", attrs["CODECS"])
        assertEquals("1920x1080", attrs["RESOLUTION"])
    }

    @Test(expected = HlsPlaylistParser.InvalidPlaylistException::class)
    fun rejectsNonPlaylistContent() {
        HlsPlaylistParser.parse("<html>not a playlist</html>", "https://example.com/x")
    }

    @Test
    fun classifierIgnoresNonMediaAndDetectsHls() {
        val cls = com.adnanearaassen.videodownloader.data.detection.MediaUrlClassifier
        assertNull(cls.classify("https://a.com/app.js"))
        assertNull(cls.classify("https://a.com/pixel.gif"))
        assertNotNull(cls.classify("https://a.com/stream/master.m3u8"))
        assertNotNull(cls.classify("https://a.com/movie.mp4"))
    }
}
