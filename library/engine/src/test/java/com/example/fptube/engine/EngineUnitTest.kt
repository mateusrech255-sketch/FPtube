package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.engine.YoutubeDLConfig
import br.com.fpagency.fptube.engine.Platform
import br.com.fpagency.fptube.engine.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTest {

    @Test
    fun testConfiguration() {
        val ua = YoutubeDLConfig.getUserAgent(Platform.YOUTUBE)
        assertTrue(ua.isNotEmpty())
    }
}
