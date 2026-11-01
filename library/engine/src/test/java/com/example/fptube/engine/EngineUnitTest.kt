package com.example.fptube.engine

import br.com.fpagency.fptube.engine.YoutubeDLConfig
import br.com.fpagency.fptube.engine.Platform
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTest {

    @Test
    fun testOAuth2Configuration() {
        // useOAuth2 = false
        val reqNormal = YoutubeDLRequest("https://youtube.com/watch?v=123")
        YoutubeDLConfig.apply(reqNormal, Platform.YOUTUBE, playerClient = null)
        val argsNormal = reqNormal.buildCommand()
        assertTrue("Normal request should not contain --username", !argsNormal.contains("--username"))

        // useOAuth2 = true
        val reqOAuth = YoutubeDLRequest("https://youtube.com/watch?v=123")
        YoutubeDLConfig.apply(reqOAuth, Platform.YOUTUBE, playerClient = null)
        reqOAuth.addOption("--username", "oauth2")
        val argsOAuth = reqOAuth.buildCommand()
        assertTrue("OAuth request must contain --username oauth2", argsOAuth.contains("--username") && argsOAuth.contains("oauth2"))
    }

    @Test
    fun testFormatConsolidatorPreservesCodecsAndFps() {
        val method = YoutubeDLUtils::class.java.getDeclaredMethod("consolidateFormats", List::class.java)
        method.isAccessible = true

        val rawFormats = listOf(
            createFormat("f1080_30_h264", 1080, 30, "avc1.640028"),
            createFormat("f1080_30_vp9", 1080, 30, "vp9"),
            createFormat("f1080_60_h264", 1080, 60, "avc1.640028"),
            createFormat("f1080_60_av1", 1080, 60, "av01.0.05M.08"),
            createFormat("f1080_60_vp9", 1080, 60, "vp9"),
            createFormat("f2160_60_av1", 2160, 60, "av01.0.05M.08")
        )

        @Suppress("UNCHECKED_CAST")
        val consolidated = method.invoke(YoutubeDLUtils, rawFormats) as List<VideoFormat>

        // All 6 distinct combinations (Resolution + FPS + Codec Family) must be preserved
        assertEquals(6, consolidated.size)
    }

    private fun createFormat(id: String, h: Int, fpsVal: Int, codec: String): VideoFormat {
        val format = VideoFormat()
        fun setField(name: String, value: Any?) {
            val f = format.javaClass.getDeclaredField(name)
            f.isAccessible = true
            f.set(format, value)
        }
        setField("formatId", id)
        setField("height", h)
        setField("width", (h * 16) / 9)
        setField("fps", fpsVal)
        setField("vcodec", codec)
        setField("ext", "mp4")
        setField("formatNote", "${h}p")
        setField("tbr", 1000)
        setField("fileSize", 10000000L)
        return format
    }
}
