package br.com.fpagency.fptube.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun testDetectYouTube() {
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://m.youtube.com/shorts/xxxx"))
    }

    @Test
    fun testDetectTikTok() {
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@user/video/123456"))
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://vm.tiktok.com/ZMe6xxxx/"))
    }

    @Test
    fun testDetectInstagram() {
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reels/Cxxxx/"))
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/p/Cxxxx/"))
    }

    @Test
    fun testDetectFacebook() {
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/reel/12345"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://fb.watch/xxxx/"))
    }

    @Test
    fun testDetectTwitter() {
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://twitter.com/user/status/12345"))
        assertEquals(Platform.TWITTER, PlatformDetector.detect("https://x.com/user/status/12345"))
    }

    @Test
    fun testNormalizeUrl() {
        val original = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=tracking_id&utm_source=share"
        val expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(expected, PlatformDetector.normalize(original))
    }
}
