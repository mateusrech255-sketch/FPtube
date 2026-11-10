package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class InstagramExtractionResult(
    val fastInfo: FastVideoInfo,
    val videoInfo: VideoInfo,
    val consolidatedFormats: List<VideoFormat>,
    val mediaUrl: String
)

object InstagramExtractor {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private val extractionCache = object : java.util.LinkedHashMap<String, Pair<Long, InstagramExtractionResult>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, InstagramExtractionResult>>): Boolean {
            return size > 50
        }
    }

    internal val SHORTCODE_REGEX = Regex("""instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    internal val VIDEO_URL_ESCAPED_REGEX = Regex("""\\"video_url\\"\s*:\s*\\"((?:[^"\\]|\\.)+?)\\"""")
    internal val VIDEO_URL_DIRECT_REGEX = Regex(""""video_url"\s*:\s*"([^"]+)"""")
    internal val VIDEO_VERSIONS_URL_REGEX = Regex(""""video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""")
    internal val CONTENT_URL_REGEX = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
    internal val DISPLAY_URL_ESCAPED_REGEX = Regex("""\\"display_url\\"\s*:\s*\\"((?:[^"\\]|\\.)+?)\\"""")
    internal val DISPLAY_URL_DIRECT_REGEX = Regex(""""display_url"\s*:\s*"([^"]+)"""")
    internal val OG_IMAGE_REGEX = Regex("""<meta[^>]*property=["']og:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    internal val EMBED_THUMB_REGEX = Regex("""class=["']EmbeddedMediaImage["'][^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    internal val USERNAME_ESCAPED_REGEX = Regex("""\\"username\\"\s*:\s*\\"((?:[^"\\]|\\.)+?)\\"""")
    internal val USERNAME_DIRECT_REGEX = Regex(""""username"\s*:\s*"([^"]+)"""")
    internal val EMBED_USERNAME_REGEX = Regex("""class=["']UsernameText["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE)
    internal val CAPTION_TEXT_ESCAPED_REGEX = Regex("""\\"edge_media_to_caption\\"[\s\S]*?\\"text\\"\s*:\s*\\"((?:[^"\\]|\\.)*?)\\"""")
    internal val CAPTION_TEXT_DIRECT_REGEX = Regex(""""caption"\s*:\s*\{[^}]*"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    internal val OG_TITLE_REGEX = Regex("""<meta[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    internal val DURATION_ESCAPED_REGEX = Regex("""\\"video_duration\\"\s*:\s*([0-9.]+)""")
    internal val DURATION_DIRECT_REGEX = Regex(""""video_duration"\s*:\s*([0-9.]+)""")
    internal val VIEW_COUNT_ESCAPED_REGEX = Regex("""\\"video_view_count\\"\s*:\s*(\d+)""")
    internal val VIEW_COUNT_DIRECT_REGEX = Regex(""""(?:video_view_count|play_count)"\s*:\s*(\d+)""")

    private fun getCached(url: String): InstagramExtractionResult? = synchronized(extractionCache) {
        val entry = extractionCache[url] ?: return null
        if (System.currentTimeMillis() - entry.first < CACHE_TTL_MS) {
            entry.second
        } else {
            extractionCache.remove(url)
            null
        }
    }

    private fun putCached(url: String, result: InstagramExtractionResult) = synchronized(extractionCache) {
        extractionCache[url] = System.currentTimeMillis() to result
    }

    suspend fun extract(url: String, forceRefresh: Boolean = false): EngineOperationResult<InstagramExtractionResult> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            getCached(url)?.let { return@withContext EngineOperationResult.Success(it) }
        }

        try {
            val shortcode = extractShortcode(url)
                ?: return@withContext EngineOperationResult.ExtractionError("Não foi possível identificar o código do post/reel do Instagram.")

            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val html = fetchUrl(embedUrl)

            var parsed = if (!html.isNullOrBlank()) {
                parseHtml(url, shortcode, html)
            } else {
                null
            }

            if (parsed == null) {
                val directHtml = fetchUrl("https://www.instagram.com/reel/$shortcode/")
                if (!directHtml.isNullOrBlank()) {
                    parsed = parseHtml(url, shortcode, directHtml)
                }
            }

            if (parsed == null) {
                return@withContext EngineOperationResult.ExtractionError("Não foi possível extrair a URL de vídeo do Instagram.")
            }

            putCached(url, parsed)
            EngineOperationResult.Success(parsed)
        } catch (e: Exception) {
            AppLogger.e("Erro na extração nativa do Instagram: ${e.message}", "InstagramExtractor", e)
            EngineOperationResult.ExtractionError(e.message ?: "Erro ao extrair vídeo do Instagram")
        }
    }

    internal fun parseHtml(originalUrl: String, shortcode: String, html: String): InstagramExtractionResult? {
        if (html.isBlank()) return null

        val ldVideoObj = JsonSafeUtils.extractLdJsonVideoObject(html)

        // 1. Extrair URL do vídeo (escapada no contextJSON, direta, video_versions ou LD+JSON)
        val rawVideoUrl = VIDEO_URL_ESCAPED_REGEX.find(html)?.groupValues?.get(1)
            ?: VIDEO_URL_DIRECT_REGEX.find(html)?.groupValues?.get(1)
            ?: VIDEO_VERSIONS_URL_REGEX.find(html)?.groupValues?.get(1)
            ?: ldVideoObj?.optCleanString("contentUrl")?.takeIf { it.isNotEmpty() }
            ?: CONTENT_URL_REGEX.find(html)?.groupValues?.get(1)

        if (rawVideoUrl.isNullOrBlank()) {
            return null
        }

        val mediaUrl = unescapeInstagramString(rawVideoUrl)
        if (!mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")) {
            return null
        }

        // 2. Extrair thumbnail
        val ldThumb = ldVideoObj?.let { obj ->
            obj.optJSONArray("thumbnailUrl")?.optString(0)?.takeIf { it != "null" && it.isNotBlank() }
                ?: obj.optCleanString("thumbnailUrl").takeIf { it.isNotEmpty() }
        }
        val rawThumb = DISPLAY_URL_ESCAPED_REGEX.find(html)?.groupValues?.get(1)
            ?: DISPLAY_URL_DIRECT_REGEX.find(html)?.groupValues?.get(1)
            ?: ldThumb
            ?: EMBED_THUMB_REGEX.find(html)?.groupValues?.get(1)
            ?: OG_IMAGE_REGEX.find(html)?.groupValues?.get(1)
            ?: ""
        val thumbnail = unescapeInstagramString(rawThumb)

        // 3. Extrair autor/username
        val ldAuthor = ldVideoObj?.optJSONObject("author")?.optCleanString("name")
            ?: ldVideoObj?.optJSONObject("author")?.optCleanString("alternateName")
        val rawAuthor = USERNAME_ESCAPED_REGEX.find(html)?.groupValues?.get(1)
            ?: EMBED_USERNAME_REGEX.find(html)?.groupValues?.get(1)
            ?: USERNAME_DIRECT_REGEX.find(html)?.groupValues?.get(1)
            ?: ldAuthor
            ?: ""
        val author = unescapeInstagramString(rawAuthor)

        // 4. Extrair título/legenda
        val rawCaption = CAPTION_TEXT_ESCAPED_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: CAPTION_TEXT_DIRECT_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: ldVideoObj?.optCleanString("caption")?.takeIf { it.isNotBlank() }
            ?: ldVideoObj?.optCleanString("description")?.takeIf { it.isNotBlank() }
            ?: ldVideoObj?.optCleanString("name")?.takeIf { it.isNotBlank() }
            ?: OG_TITLE_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: if (author.isNotBlank()) "Reel de @$author" else "Vídeo do Instagram"

        val title = unescapeInstagramString(rawCaption)
            .replace("\n", " ")
            .trim()
            .take(140)
            .ifBlank { "Vídeo do Instagram" }

        // 5. Duração e visualizações
        val durationSeconds = (
            DURATION_ESCAPED_REGEX.find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: DURATION_DIRECT_REGEX.find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: ldVideoObj?.let { YoutubeDLUtils.parseIso8601Duration(it.optCleanString("duration")).toDouble() }
                ?: 0.0
            ).toInt().coerceAtLeast(0)

        val viewCount = VIEW_COUNT_ESCAPED_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: VIEW_COUNT_DIRECT_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L

        val width = ldVideoObj?.optSafeInt("width", 720, minValue = 1) ?: 720
        val height = ldVideoObj?.optSafeInt("height", 1280, minValue = 1) ?: 1280

        val formats = listOf(
            VideoFormat(
                formatId = "instagram_1080p",
                url = mediaUrl,
                ext = "mp4",
                width = 1080,
                height = 1920,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "1080p Full HD (MP4)",
                formatNote = "1080p",
                filesize = 0L,
                tbr = 4500.0
            ),
            VideoFormat(
                formatId = "instagram_720p",
                url = mediaUrl,
                ext = "mp4",
                width = 720,
                height = 1280,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "720p HD (MP4)",
                formatNote = "720p",
                filesize = 0L,
                tbr = 2500.0
            ),
            VideoFormat(
                formatId = "instagram_original",
                url = mediaUrl,
                ext = "mp4",
                width = width,
                height = height,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "Original (${minOf(width, height)}p MP4)",
                formatNote = "${minOf(width, height)}p",
                filesize = 0L,
                tbr = 2000.0
            ),
            VideoFormat(
                formatId = "instagram_audio",
                url = mediaUrl,
                ext = "m4a",
                width = 0,
                height = 0,
                fps = 0,
                vcodec = "none",
                acodec = "aac",
                format = "Audio M4A (128K)",
                formatNote = "Audio 128K",
                filesize = 0L,
                tbr = 128.0,
                abr = 128
            )
        )

        val fastInfo = FastVideoInfo(
            title = title,
            author = author,
            thumbnail = thumbnail,
            url = originalUrl,
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            likeCount = 0L,
            videoId = shortcode
        )

        val videoInfo = VideoInfo(
            id = shortcode,
            title = title,
            uploader = author,
            duration = durationSeconds,
            thumbnail = thumbnail,
            webpageUrl = originalUrl,
            formats = formats
        )

        return InstagramExtractionResult(
            fastInfo = fastInfo,
            videoInfo = videoInfo,
            consolidatedFormats = formats,
            mediaUrl = mediaUrl
        )
    }

    suspend fun getFreshMediaUrl(
        url: String,
        preferredFormatId: String? = null,
        forceRefresh: Boolean = false
    ): String? {
        val result = extract(url, forceRefresh = forceRefresh)
        if (result is EngineOperationResult.Success) {
            if (!preferredFormatId.isNullOrBlank()) {
                result.data.consolidatedFormats.find { it.formatId == preferredFormatId }?.url?.let { return it }
            }
            return result.data.mediaUrl
        }
        return null
    }

    internal fun extractShortcode(url: String): String? {
        return SHORTCODE_REGEX.find(url)?.groupValues?.get(1)
    }

    private fun fetchUrl(targetUrl: String): String? {
        val opened = NetworkSecurityUtils.openSafeConnectionWithRedirects(
            startUrl = targetUrl,
            maxRedirects = 6,
            connectTimeoutMs = 10000,
            readTimeoutMs = 10000,
            headers = mapOf(
                "User-Agent" to DESKTOP_USER_AGENT,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
            ),
            preserveCookies = true
        ) ?: return null

        val conn = opened.second
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { NetworkSecurityUtils.readBoundedText(it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    internal fun unescapeInstagramString(raw: String): String {
        val preCleaned = raw
            .replace("\\\\/", "/")
            .replace("\\\\u0026", "&")
            .replace("\\\\u0025", "%")
            .replace("\\\\u003C", "<")
            .replace("\\\\u003E", ">")
            .replace("\\\\n", "\n")
            .replace("\\u0025", "%")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
        return JsonSafeUtils.decodeEscapedString(preCleaned)
    }
}
