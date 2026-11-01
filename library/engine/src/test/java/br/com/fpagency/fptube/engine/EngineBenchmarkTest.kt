package br.com.fpagency.fptube.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBenchmarkTest {

    @Test
    fun testPlatformDetectionSpeed() {
        // Warmup classloading
        PlatformDetector.detect("https://www.youtube.com")

        val testUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://vt.tiktok.com/ZS6e6H8M3/",
            "https://k.kwai.com/p/bVCIaBav",
            "https://www.instagram.com/p/C_12345678/"
        )

        for (url in testUrls) {
            val t0 = System.nanoTime()
            val normalized = PlatformDetector.normalize(url)
            val platform = PlatformDetector.detect(normalized)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

            assertTrue("Platform detection must be < 10ms (was ${elapsedMs}ms)", elapsedMs < 10.0)
            assertTrue("Platform should be recognized", platform != Platform.UNKNOWN)
        }
    }

    @Test
    fun testNormalizerPerformance() {
        // Warmup
        PlatformDetector.normalize("https://youtu.be/test")

        val raw = "  https://youtu.be/dQw4w9WgXcQ?si=test  "
        val t0 = System.nanoTime()
        val normalized = PlatformDetector.normalize(raw)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

        assertEquals("https://youtu.be/dQw4w9WgXcQ", normalized)
        assertTrue("Normalization must be < 10ms (was ${elapsedMs}ms)", elapsedMs < 10.0)
    }
}
