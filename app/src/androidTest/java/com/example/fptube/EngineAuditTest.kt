package com.example.fptube

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import br.com.fpagency.fptube.engine.EngineOperationResult
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import com.yausername.youtubedl_android.mapper.VideoFormat

@RunWith(AndroidJUnit4::class)
class EngineAuditTest {

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        YoutubeDL.getInstance().init(context)
    }

    @Test
    fun testYouTubeFormats() {
        runBlocking {
            val url = "https://www.youtube.com/watch?v=iNJdPyoqt8U"
            Log.e("AUDIT_ENGINE", "START Formats: $url")
            val result = YoutubeDLUtils.fetchVideoFormats(url, false)
            when (result) {
                is EngineOperationResult.Success -> {
                    val formats = (result.extraData as? Pair<*, *>)?.first as? List<VideoFormat>
                    Log.e("AUDIT_ENGINE", "RES Found ${formats?.size} consolidated formats")
                    var maxRes = 0
                    formats?.forEach {
                        Log.e("AUDIT_ENGINE", "FMT Format: ${it.formatId} - ${it.formatNote} - ${it.vcodec} - ${it.acodec}")
                        val height = YoutubeDLUtils.parseHeight(it)
                        if (height > maxRes) maxRes = height
                    }
                    Log.e("AUDIT_ENGINE", "MAX Max resolution mapped: ${maxRes}p")
                }
                is EngineOperationResult.ExtractionError -> {
                    Log.e("AUDIT_ENGINE", "ERR Extraction error: ${result.message}")
                }
                is EngineOperationResult.VideoUnavailable -> {
                    Log.e("AUDIT_ENGINE", "ERR Video Unavailable: ${result.reason}")
                }
                else -> {
                    Log.e("AUDIT_ENGINE", "ERR Other error: $result")
                }
            }
        }
    }

    @Test
    fun testYouTubeMusicVideo() {
        runBlocking {
            val url = "https://www.youtube.com/watch?v=fJ9rUzIMcZQ"
            Log.e("AUDIT_ENGINE", "START MusicVideo: $url")
            val result = YoutubeDLUtils.fetchVideoFormats(url, false)
            when (result) {
                is EngineOperationResult.Success -> {
                    Log.e("AUDIT_ENGINE", "RES Music Video OK")
                }
                is EngineOperationResult.ExtractionError -> {
                    Log.e("AUDIT_ENGINE", "ERR Music Video Extraction error: ${result.message}")
                }
                is EngineOperationResult.VideoUnavailable -> {
                    Log.e("AUDIT_ENGINE", "ERR Music Video Unavailable: ${result.reason}")
                }
                else -> {
                    Log.e("AUDIT_ENGINE", "ERR Other error: $result")
                }
            }
        }
    }
}
