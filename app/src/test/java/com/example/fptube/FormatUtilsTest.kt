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
}