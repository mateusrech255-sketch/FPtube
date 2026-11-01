package com.example.fptube

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import org.json.JSONObject
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormatConsolidatorTest {

    @Test
    fun testConsolidationPreservesCodecsAndFps() {
        // Reflection to call private method
        val consolidateFormatsMethod = YoutubeDLUtils::class.java.getDeclaredMethod("consolidateFormats", List::class.java)
        consolidateFormatsMethod.isAccessible = true

        val formats = listOf(
            createFormat("f1", 1080, 30, "avc1.640028"),
            createFormat("f2", 1080, 30, "vp9"),
            createFormat("f3", 1080, 60, "avc1.640028"),
            createFormat("f4", 1080, 60, "av01.0.05M.08"),
            createFormat("f5", 1080, 60, "vp9"),
            createFormat("f6", 2160, 60, "av01.0.05M.08")
        )

        @Suppress("UNCHECKED_CAST")
        val consolidated = consolidateFormatsMethod.invoke(YoutubeDLUtils, formats) as List<VideoFormat>

        // Expect: 1080p30 h264, 1080p30 vp9, 1080p60 h264, 1080p60 av1, 1080p60 vp9, 2160p60 av1 -> 6 items
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
        setField("tbr", 1000.0)
        setField("fileSize", 10000000L)
        
        return format
    }
}
