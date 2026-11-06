package com.example.fptube

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import br.com.fpagency.fptube.engine.EngineOperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class EngineAuditTest {

    @Before
    fun setup() {
    }

    @Test
    fun testYouTubeFormats() {
        runBlocking {
            val url = "https://www.youtube.com/watch?v=iNJdPyoqt8U"
            val result = YoutubeDLUtils.fetchVideoFormats(url, false)
            assertTrue(result is EngineOperationResult.Success)
        }
    }
}
