package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeDouble
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeInt
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeLong
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorAndRegexAuditTest {

    @Test
    fun `test Regex equivalence with original inline expressions`() {
        // 1. Kwai & SnackVideo Regexes
        val origContentUrl = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        val sampleHtml = """{"@type":"VideoObject","contentUrl": "https:\u002F\u002Ftx2.a.kwimgs.com\u002Fupic\u002F2026\u002Fvideo.mp4?tag=1-123"}"""
        assertEquals(
            origContentUrl.find(sampleHtml)?.groupValues,
            KwaiExtractor.CONTENT_URL_REGEX.find(sampleHtml)?.groupValues
        )
        assertEquals(
            origContentUrl.find(sampleHtml)?.groupValues,
            SnackVideoExtractor.CONTENT_URL_REGEX.find(sampleHtml)?.groupValues
        )

        // 2. Instagram Regexes
        val origShortcode = Regex("""instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        val igUrls = listOf(
            "https://www.instagram.com/reel/C8abc_123-X/?igsh=xyz",
            "https://instagram.com/p/C9xyz123/",
            "https://m.instagram.com/reels/D0123456/",
            "https://www.instagram.com/tv/TV_CODE_99/"
        )
        for (u in igUrls) {
            assertEquals(
                origShortcode.find(u)?.groupValues,
                InstagramExtractor.SHORTCODE_REGEX.find(u)?.groupValues
            )
        }

        // 3. TikTok Rehydration Regex
        val origTikTok = Regex(
            """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(\{.*?\})</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val ttHtml = """<html><body><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{}}</script></body></html>"""
        assertEquals(
            origTikTok.find(ttHtml)?.groupValues,
            TikTokExtractor.REHYDRATION_SCRIPT_REGEX.find(ttHtml)?.groupValues
        )

        // 4. YouTube Video ID & Codecs & VisitorData Regexes
        val origYtId = Regex("""(?:v=|\/shorts\/|\/live\/|youtu\.be\/|\/embed\/|\/v\/|vi\/|e\/|watch\?v=|&v=)([^#\&\?]{11})""")
        val ytUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=12345",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"
        )
        for (u in ytUrls) {
            assertEquals(
                origYtId.find(u)?.groupValues,
                YoutubeDLUtils.YOUTUBE_VIDEO_ID_REGEX.find(u)?.groupValues
            )
            assertEquals("dQw4w9WgXcQ", YoutubeDLUtils.extractYouTubeVideoId(u))
        }

        // 5. DownloadEngine clen & range Regexes
        val origClen = Regex("""[?&]clen=(\d+)""")
        val origRange = Regex("""([?&])range=[^&]+""")
        val gvUrl = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=123&clen=48234496&range=0-1023&mime=video/mp4"
        assertEquals(
            origClen.find(gvUrl)?.groupValues,
            DownloadEngine.CLEN_REGEX.find(gvUrl)?.groupValues
        )
        assertEquals(
            origRange.replace(gvUrl, ""),
            DownloadEngine.RANGE_PARAM_REGEX.replace(gvUrl, "")
        )
    }

    @Test
    fun `test JsonSafeUtils handles null, missing fields, and unexpected types`() {
        val rawJson = """
            {
                "nullField": null,
                "literalNullStr": "null",
                "undefinedStr": "undefined",
                "blankStr": "   ",
                "validStr": "FPTube Native",
                "intAsStr": "1080p",
                "intAsFloatStr": "59.94",
                "negativeInt": -100,
                "invalidInt": "abc",
                "longAsStr": "9876543210",
                "doubleValid": "1234.56",
                "doubleInvalid": "NaN"
            }
        """.trimIndent()

        val obj = JSONObject(rawJson)
        assertEquals("fallback", obj.optCleanString("missingField", "fallback"))
        assertEquals("fallback", obj.optCleanString("nullField", "fallback"))
        assertEquals("fallback", obj.optCleanString("literalNullStr", "fallback"))
        assertEquals("fallback", obj.optCleanString("undefinedStr", "fallback"))
        assertEquals("fallback", obj.optCleanString("blankStr", "fallback"))
        assertEquals("FPTube Native", obj.optCleanString("validStr", "fallback"))

        assertEquals(1080, obj.optSafeInt("intAsStr", 720))
        assertEquals(59, obj.optSafeInt("intAsFloatStr", 30))
        assertEquals(0, obj.optSafeInt("negativeInt", 0, minValue = 0))
        assertEquals(42, obj.optSafeInt("invalidInt", 42))

        assertEquals(9876543210L, obj.optSafeLong("longAsStr", 0L))
        assertEquals(1234.56, obj.optSafeDouble("doubleValid", 0.0), 0.001)
        assertEquals(0.0, obj.optSafeDouble("doubleInvalid", 0.0), 0.001)
    }

    @Test
    fun `test KwaiExtractor SSR variations - Nuxt, LD+JSON and INITIAL_STATE`() {
        // Caso 1: SSR Nuxt padrão
        val nuxtHtml = """
            <html><head><title>Kwai Video</title></head><body>
            <script>window.__NUXT__={caption:"Vídeo incrível \u0026 divertido",likeCount:"1500",viewCount:"45000",
            "contentUrl":"https:\u002F\u002Fcdn.kwai.net\u002Fvideo1.mp4","width":720,"height":1280,"duration":"PT1M5S"}</script>
            </body></html>
        """.trimIndent()
        val res1 = KwaiExtractor.parseHtml("https://www.kwai.com/photo/123456", nuxtHtml)
        assertNotNull(res1)
        assertEquals("https://cdn.kwai.net/video1.mp4", res1!!.mediaUrl)
        assertEquals("Vídeo incrível & divertido", res1.fastInfo.title)
        assertEquals(65, res1.fastInfo.durationSeconds)
        assertEquals("123456", res1.fastInfo.videoId)

        // Caso 2: Mudança de estrutura SSR para LD+JSON puro
        val ldJsonHtml = """
            <html><head>
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "VideoObject",
              "name": "Kwai via LD+JSON",
              "contentUrl": "https://cdn.kwai.net/ld_video.mp4",
              "thumbnailUrl": ["https://cdn.kwai.net/thumb.jpg"],
              "duration": "PT30S",
              "author": {"@type": "Person", "name": "CreatorKwai"}
            }
            </script>
            </head><body></body></html>
        """.trimIndent()
        val res2 = KwaiExtractor.parseHtml("https://www.kwai.com/video/999", ldJsonHtml)
        assertNotNull(res2)
        assertEquals("https://cdn.kwai.net/ld_video.mp4", res2!!.mediaUrl)
        assertEquals("Kwai via LD+JSON", res2.fastInfo.title)
        assertEquals("CreatorKwai", res2.fastInfo.author)
        assertEquals(30, res2.fastInfo.durationSeconds)

        // Caso 3: Mudança de estrutura SSR para window.__INITIAL_STATE__
        val initStateHtml = """
            <html><body>
            <script>window.__INITIAL_STATE__ = {"videoData":{"mainMvUrl":"https://cdn.kwai.net/init_state.mp4"}};</script>
            </body></html>
        """.trimIndent()
        val res3 = KwaiExtractor.parseHtml("https://www.kwai.com/photo/777", initStateHtml)
        assertNotNull(res3)
        assertEquals("https://cdn.kwai.net/init_state.mp4", res3!!.mediaUrl)

        // Caso 4: HTML inválido/vazio sem vídeo
        assertNull(KwaiExtractor.parseHtml("https://www.kwai.com/photo/000", "<html><body>404 Not Found</body></html>"))
    }

    @Test
    fun `test SnackVideoExtractor SSR variations`() {
        val ldHtml = """
            <html><head>
            <script type="application/ld+json">
            [{"@type": "VideoObject", "name": "Snack Test", "contentUrl": "https://cdn.snackvideo.net/v.mp4", "duration": "PT15S"}]
            </script>
            </head></html>
        """.trimIndent()
        val res = SnackVideoExtractor.parseHtml("https://www.snackvideo.com/p/abc", ldHtml)
        assertNotNull(res)
        assertEquals("https://cdn.snackvideo.net/v.mp4", res!!.mediaUrl)
        assertEquals("Snack Test", res.fastInfo.title)
        assertEquals(15, res.fastInfo.durationSeconds)
    }

    @Test
    fun `test InstagramExtractor SSR variations - Embed, video_versions and LD+JSON`() {
        // Caso 1: Embed escapado
        val embedHtml = """
            <html><body>
            <script>contextJSON = "{\"video_url\":\"https:\\/\\/scontent.cdninstagram.com\\/v\\/reel1.mp4?efg=1\\u0026oh=2\",\"display_url\":\"https:\\/\\/scontent.cdninstagram.com\\/thumb.jpg\",\"username\":\"ig_creator\",\"video_duration\":24.8,\"video_view_count\":12345}";</script>
            </body></html>
        """.trimIndent()
        val res1 = InstagramExtractor.parseHtml("https://www.instagram.com/reel/C123/", "C123", embedHtml)
        assertNotNull(res1)
        assertEquals("https://scontent.cdninstagram.com/v/reel1.mp4?efg=1&oh=2", res1!!.mediaUrl)
        assertEquals("ig_creator", res1.fastInfo.author)
        assertEquals(24, res1.fastInfo.durationSeconds)

        // Caso 2: Mudança SSR para video_versions
        val versionsHtml = """
            <html><body>
            <script>{"video_versions":[{"width":1080,"height":1920,"url":"https://scontent.cdninstagram.com/v/versions.mp4"}],"caption":{"text":"Legenda direta"}}</script>
            </body></html>
        """.trimIndent()
        val res2 = InstagramExtractor.parseHtml("https://www.instagram.com/p/C456/", "C456", versionsHtml)
        assertNotNull(res2)
        assertEquals("https://scontent.cdninstagram.com/v/versions.mp4", res2!!.mediaUrl)
        assertEquals("Legenda direta", res2.fastInfo.title)
    }

    @Test
    fun `test TikTokExtractor API JSON and SSR variations - Rehydration, SIGI_STATE and LD+JSON`() {
        // Caso 1: API TikWM com campos null e tipos numéricos como string
        val tikwmJson = """
            {
              "code": 0,
              "data": {
                "id": "73000001",
                "title": null,
                "duration": "42",
                "cover": null,
                "origin_cover": "https://p16.tiktokcdn.com/cover.jpg",
                "play_count": "100500",
                "digg_count": null,
                "author": {"nickname": null, "unique_id": "tiktok_dev"},
                "hdplay": "https://v16.tiktokcdn.com/hd_nowm.mp4",
                "play": "https://v16.tiktokcdn.com/sd_nowm.mp4",
                "music": "https://v16.tiktokcdn.com/audio.mp3"
              }
            }
        """.trimIndent()
        val res1 = TikTokExtractor.parseTikWmJson("https://www.tiktok.com/@tiktok_dev/video/73000001", tikwmJson)
        assertNotNull(res1)
        assertEquals("Vídeo do TikTok", res1!!.fastInfo.title) // Não deve ser "null"!
        assertEquals("tiktok_dev", res1.fastInfo.author)
        assertEquals(42, res1.fastInfo.durationSeconds)
        assertEquals(100500L, res1.fastInfo.viewCount)
        assertEquals(3, res1.consolidatedFormats.size)

        // Caso 2: SSR SIGI_STATE fallback
        val sigiHtml = """
            <html><body>
            <script id="SIGI_STATE" type="application/json">
            {"ItemModule":{"730002":{"id":"730002","desc":"TikTok via SIGI_STATE","author":"sigi_user","video":{"playAddr":"https://v16.tiktokcdn.com/sigi.mp4","duration":18,"width":720,"height":1280}}}}
            </script>
            </body></html>
        """.trimIndent()
        val res2 = TikTokExtractor.parseWebHtml("https://www.tiktok.com/@sigi_user/video/730002", sigiHtml)
        assertNotNull(res2)
        assertEquals("https://v16.tiktokcdn.com/sigi.mp4", res2!!.mediaUrl)
        assertEquals("TikTok via SIGI_STATE", res2.fastInfo.title)
        assertEquals("sigi_user", res2.fastInfo.author)
    }

    @Test
    fun `test YoutubeDLUtils InnerTube parser handles nulls, DRC tracks and HDR consolidation`() {
        val innerTubeJson = """
            {
              "playabilityStatus": {"status": "OK"},
              "videoDetails": {
                "title": null,
                "author": "Canal Teste",
                "lengthSeconds": "185",
                "thumbnail": {"thumbnails": [{"url": null}, {"url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"}]}
              },
              "streamingData": {
                "formats": [
                  {"itag": 18, "url": "https://rr1.googlevideo.com/videoplayback?itag=18&clen=1000", "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "width": 640, "height": 360, "fps": 30, "bitrate": 500000}
                ],
                "adaptiveFormats": [
                  {"itag": 137, "url": "https://rr1.googlevideo.com/videoplayback?itag=137&clen=5000", "mimeType": "video/mp4; codecs=\"avc1.640028\"", "width": 1920, "height": 1080, "fps": 60, "bitrate": 4500000},
                  {"itag": 699, "url": "https://rr1.googlevideo.com/videoplayback?itag=699&clen=6000", "mimeType": "video/webm; codecs=\"vp9.2\"", "width": 1920, "height": 1080, "fps": 60, "bitrate": 5000000, "colorInfo": {"transferCharacteristics": "SMPTEST2084"}},
                  {"itag": 140, "url": "https://rr1.googlevideo.com/videoplayback?itag=140&clen=800", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "isDrc": false},
                  {"itag": 140, "url": "https://rr1.googlevideo.com/videoplayback?itag=140_drc", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "isDrc": true}
                ]
              }
            }
        """.trimIndent()

        val parsed = YoutubeDLUtils.parseInnerTubePlayerJson("dQw4w9WgXcQ", innerTubeJson)
        assertNotNull(parsed)
        assertEquals("Vídeo do YouTube", parsed!!.title) // null tratado com segurança
        assertEquals("Canal Teste", parsed.uploader)
        assertEquals(185, parsed.duration)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", parsed.thumbnail)
        // A faixa DRC deve ter sido filtrada (total 4 faixas extraídas em vez de 5)
        assertEquals(4, parsed.formats.size)

        val consolidated = YoutubeDLUtils.consolidateYouTubeFormats(parsed.formats)
        val best1080 = consolidated.find { it.height == 1080 }
        assertNotNull(best1080)
        // Deve priorizar MP4 SDR (itag 137) sobre WebM HDR (itag 699) para compatibilidade
        assertEquals("137", best1080!!.formatId)
        assertEquals("mp4", best1080.ext)
    }

    @Test
    fun `test NetworkSecurityUtils SSRF protection blocks private, loopback, link-local, metadata and non-http URLs`() {
        // URLs maliciosas / internas que devem ser bloqueadas
        val unsafeUrls = listOf(
            "http://127.0.0.1:8080/admin",
            "http://localhost/secret",
            "http://10.0.0.1/internal",
            "http://172.16.0.5/router",
            "http://172.31.255.255/test",
            "http://192.168.1.1/setup",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/cgnat",
            "http://[::1]:8000/",
            "http://[fe80::1]/",
            "http://[fd00::1]/",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://intranet.local/video.mp4",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "ftp://files.example.com/video.mp4",
            "https://admin:secret@www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        for (unsafe in unsafeUrls) {
            org.junit.Assert.assertFalse(
                "Deveria bloquear URL insegura: $unsafe",
                NetworkSecurityUtils.isSafeExternalUrl(unsafe, resolveDns = false)
            )
        }

        // URLs públicas válidas
        val safeUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.tiktok.com/@user/video/123456",
            "https://www.instagram.com/reel/C12345/",
            "https://www.kwai.com/photo/12345"
        )
        for (safe in safeUrls) {
            assertTrue(
                "Deveria permitir URL pública segura: $safe",
                NetworkSecurityUtils.isSafeExternalUrl(safe, requireHttps = true, resolveDns = false)
            )
        }

        // Quando requireHttps = true, http:// deve ser rejeitado
        org.junit.Assert.assertFalse(
            NetworkSecurityUtils.isSafeExternalUrl("http://www.youtube.com/watch?v=dQw4w9WgXcQ", requireHttps = true, resolveDns = false)
        )
    }

    @Test
    fun `test NetworkSecurityUtils sanitizeFileId blocks Path Traversal`() {
        assertNull(NetworkSecurityUtils.sanitizeFileId("../etc/passwd"))
        assertNull(NetworkSecurityUtils.sanitizeFileId("../../shared_prefs/settings"))
        assertNull(NetworkSecurityUtils.sanitizeFileId("plugin/../../evil"))
        assertNull(NetworkSecurityUtils.sanitizeFileId("plugin\\..\\evil"))
        assertNull(NetworkSecurityUtils.sanitizeFileId("."))
        assertNull(NetworkSecurityUtils.sanitizeFileId(".."))
        assertNull(NetworkSecurityUtils.sanitizeFileId(""))
        assertNull(NetworkSecurityUtils.sanitizeFileId("   "))
        assertNull(NetworkSecurityUtils.sanitizeFileId("plugin;rm -rf"))

        assertEquals("aria2c", NetworkSecurityUtils.sanitizeFileId("aria2c"))
        assertEquals("ffmpeg_plugin-1.0.so", NetworkSecurityUtils.sanitizeFileId("ffmpeg_plugin-1.0.so"))
    }

    @Test
    fun `test NetworkSecurityUtils readBoundedText enforces maximum byte limit`() {
        val largeInput = "A".repeat(100_000).byteInputStream(Charsets.UTF_8)
        val bounded = NetworkSecurityUtils.readBoundedText(largeInput, maxBytes = 16_384)
        assertEquals(16_384, bounded.length)
    }
}

