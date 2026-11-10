package br.com.fpagency.fptube

import br.com.fpagency.fptube.core.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatQualityDisplayStandard() {
        assertEquals("1080p · 1920×1080", FormatUtils.formatQualityDisplay("1080p", "video"))
        assertEquals("720p · 1280×720", FormatUtils.formatQualityDisplay("720p", "video"))
        assertEquals("2160p · 3840×2160", FormatUtils.formatQualityDisplay("4k", "video"))
        assertEquals("1440p · 2560×1440", FormatUtils.formatQualityDisplay("1440p", "video"))
    }

    @Test
    fun testFormatQualityDisplayDimensions() {
        assertEquals("1080p · 1920×1080", FormatUtils.formatQualityDisplay("1920x1080", "video"))
        assertEquals("1080p · 1080×1920", FormatUtils.formatQualityDisplay("1080x1920", "video"))
    }

    @Test
    fun testFormatQualityDisplayAudio() {
        assertEquals("Áudio", FormatUtils.formatQualityDisplay(null, "audio"))
        assertEquals("Áudio (128k)", FormatUtils.formatQualityDisplay("128k", "audio"))
    }

    @Test
    fun testFormatContainerDisplay() {
        assertEquals("MP4", FormatUtils.formatContainerDisplay("/storage/downloads/video.mp4", null, "video"))
        assertEquals("WEBM", FormatUtils.formatContainerDisplay(null, "video/webm", "video"))
        assertEquals("MP3", FormatUtils.formatContainerDisplay("/storage/downloads/music.mp3", null, "audio"))
    }

    @Test
    fun testFormatCodecDisplay() {
        assertEquals("AVC", FormatUtils.formatCodecDisplay("avc1.640028"))
        assertEquals("VP9", FormatUtils.formatCodecDisplay("vp09.00.40.08"))
        assertEquals("AV1", FormatUtils.formatCodecDisplay("av01.0.08M.08"))
        assertEquals("Opus", FormatUtils.formatCodecDisplay("opus"))
        assertNull(FormatUtils.formatCodecDisplay(null))
        assertNull(FormatUtils.formatCodecDisplay("none"))
    }

    @Test
    fun testFormatFpsDisplay() {
        assertEquals("60 FPS", FormatUtils.formatFpsDisplay(60))
        assertNull(FormatUtils.formatFpsDisplay(null))
        assertNull(FormatUtils.formatFpsDisplay(0))
    }

    @Test
    fun testFormatSize() {
        assertEquals("1.00 MB", FormatUtils.formatSize(1024 * 1024))
        assertEquals("500.00 KB", FormatUtils.formatSize(500 * 1024))
    }

    @Test
    fun testDatabaseLoggerSanitizesUrlsIdsAndSecrets() {
        val rawLog = "Falha em https://rr1.googlevideo.com/videoplayback?expire=123&sig=SECRET123 com api_key=MY_SECRET_KEY e token: BEARER_999 para v=dQw4w9WgXcQ"
        val sanitized = br.com.fpagency.fptube.data.DatabaseLogger.sanitizeLogText(rawLog)
        org.junit.Assert.assertFalse(sanitized.contains("googlevideo.com"))
        org.junit.Assert.assertFalse(sanitized.contains("MY_SECRET_KEY"))
        org.junit.Assert.assertFalse(sanitized.contains("BEARER_999"))
        org.junit.Assert.assertFalse(sanitized.contains("dQw4w9WgXcQ"))
        org.junit.Assert.assertTrue(sanitized.contains("[URL_REMOVED]"))
        org.junit.Assert.assertTrue(sanitized.contains("api_key=[REDACTED]"))
        org.junit.Assert.assertTrue(sanitized.contains("token=[REDACTED]"))
    }

    @Test
    fun testFileMoverSanitizesPathTraversalAndLongNames() {
        val traversal = "../../etc/passwd/../../meu:video*teste?\"<>|"
        val safe = br.com.fpagency.fptube.download.FileMover.sanitizeFileName(traversal)
        org.junit.Assert.assertFalse(safe.contains(".."))
        org.junit.Assert.assertFalse(safe.contains("/"))
        org.junit.Assert.assertFalse(safe.contains("\\"))

        val hugeTitle = "X".repeat(300)
        val truncated = br.com.fpagency.fptube.download.FileMover.sanitizeFileName(hugeTitle)
        assertEquals(120, truncated.length)
    }
}