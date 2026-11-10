package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SnackVideoExtractionResult(
    val fastInfo: FastVideoInfo,
    val videoInfo: VideoInfo,
    val consolidatedFormats: List<VideoFormat>,
    val mediaUrl: String
)

object SnackVideoExtractor {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private val extractionCache = object : java.util.LinkedHashMap<String, Pair<Long, SnackVideoExtractionResult>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, SnackVideoExtractionResult>>): Boolean {
            return size > 50
        }
    }

    internal val CONTENT_URL_REGEX = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
    internal val FALLBACK_MP4_REGEX = Regex("""(https?:(?:\\u002F|/){2}[^"'\s\\]+(?:snackvideo|kwai)\.net[^"'\s\\]+\.mp4[^"'\s\\]*)""")
    internal val CAPTION_REGEX = Regex("""caption\s*:\s*"((?:[^"\\]|\\.)*)"""")
    internal val DESCRIPTION_REGEX = Regex(""""description"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    internal val TITLE_TAG_REGEX = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
    internal val AUTHOR_NAME_REGEX = Regex(""""author"\s*:\s*\{[^}]*"name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    internal val THUMBNAIL_ARRAY_REGEX = Regex(""""thumbnailUrl"\s*:\s*\[\s*"([^"]+)"""")
    internal val THUMBNAIL_STR_REGEX = Regex(""""thumbnailUrl"\s*:\s*"([^"]+)"""")
    internal val OG_IMAGE_REGEX = Regex("""<meta[^>]*property=["']og:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    internal val WIDTH_REGEX = Regex(""""width"\s*:\s*(\d+)""")
    internal val HEIGHT_REGEX = Regex(""""height"\s*:\s*(\d+)""")
    internal val DURATION_REGEX = Regex(""""duration"\s*:\s*"([^"]+)"""")
    internal val LIKE_COUNT_REGEX = Regex("""likeCount\s*:\s*"?(\d+)"?""")
    internal val VIEW_COUNT_REGEX = Regex("""viewCount\s*:\s*"?(\d+)"?""")
    internal val INTERACTION_COUNT_REGEX = Regex(""""userInteractionCount"\s*:\s*(\d+)""")
    internal val PHOTO_ID_PATH_REGEX = Regex("""/(?:photo|video|p)/([a-zA-Z0-9_-]+)""")

    private fun getCached(url: String): SnackVideoExtractionResult? = synchronized(extractionCache) {
        val entry = extractionCache[url] ?: return null
        if (System.currentTimeMillis() - entry.first < CACHE_TTL_MS) {
            entry.second
        } else {
            extractionCache.remove(url)
            null
        }
    }

    private fun putCached(url: String, result: SnackVideoExtractionResult) = synchronized(extractionCache) {
        extractionCache[url] = System.currentTimeMillis() to result
    }

    suspend fun extract(url: String, forceRefresh: Boolean = false): EngineOperationResult<SnackVideoExtractionResult> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            getCached(url)?.let { return@withContext EngineOperationResult.Success(it) }
        }

        try {
            val htmlResult = fetchSnackVideoPage(url)
                ?: return@withContext EngineOperationResult.ExtractionError("Não foi possível acessar a página do SnackVideo.")

            val finalUrl = htmlResult.first
            val html = htmlResult.second

            val parsed = parseHtml(finalUrl, html)
                ?: return@withContext EngineOperationResult.ExtractionError("Não foi possível encontrar o stream de vídeo do SnackVideo.")

            putCached(url, parsed)
            if (finalUrl != url) {
                putCached(finalUrl, parsed)
            }
            EngineOperationResult.Success(parsed)
        } catch (e: Exception) {
            AppLogger.e("Erro na extração nativa do SnackVideo: ${e.message}", "SnackVideoExtractor", e)
            EngineOperationResult.ExtractionError(e.message ?: "Erro ao extrair vídeo do SnackVideo")
        }
    }

    internal fun parseHtml(finalUrl: String, html: String): SnackVideoExtractionResult? {
        if (html.isBlank()) return null

        val ldVideoObj = JsonSafeUtils.extractLdJsonVideoObject(html)
        val initialState = JsonSafeUtils.extractInitialStateJson(html)

        val rawContentUrl = CONTENT_URL_REGEX.find(html)?.groupValues?.get(1)
            ?: ldVideoObj?.optCleanString("contentUrl")?.takeIf { it.isNotEmpty() }
            ?: initialState?.let { extractVideoUrlFromInitialState(it) }
            ?: FALLBACK_MP4_REGEX.find(html)?.groupValues?.get(1)

        if (rawContentUrl.isNullOrBlank()) {
            return null
        }

        val mediaUrl = decodeJsString(rawContentUrl)
        if (!mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")) {
            return null
        }

        val rawTitle = CAPTION_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: ldVideoObj?.optCleanString("name")?.takeIf { it.isNotBlank() }
            ?: ldVideoObj?.optCleanString("description")?.takeIf { it.isNotBlank() }
            ?: DESCRIPTION_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: TITLE_TAG_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: "Vídeo do SnackVideo"

        val title = decodeJsString(rawTitle).ifBlank { "Vídeo do SnackVideo" }

        val ldAuthor = ldVideoObj?.optJSONObject("author")?.optCleanString("name")
            ?: ldVideoObj?.optJSONObject("creator")?.optCleanString("name")
        val rawAuthor = AUTHOR_NAME_REGEX.find(html)?.groupValues?.get(1)
            ?: ldAuthor
            ?: ""
        val author = decodeJsString(rawAuthor)

        val ldThumb = ldVideoObj?.let { obj ->
            obj.optJSONArray("thumbnailUrl")?.optString(0)?.takeIf { it != "null" && it.isNotBlank() }
                ?: obj.optCleanString("thumbnailUrl").takeIf { it.isNotEmpty() }
        }
        val rawThumbnail = THUMBNAIL_ARRAY_REGEX.find(html)?.groupValues?.get(1)
            ?: THUMBNAIL_STR_REGEX.find(html)?.groupValues?.get(1)
            ?: ldThumb
            ?: OG_IMAGE_REGEX.find(html)?.groupValues?.get(1)
            ?: ""
        val thumbnail = decodeJsString(rawThumbnail)

        val rawWidth = WIDTH_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: ldVideoObj?.optSafeInt("width", 720, minValue = 1)
            ?: 720
        val rawHeight = HEIGHT_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: ldVideoObj?.optSafeInt("height", 1280, minValue = 1)
            ?: 1280
        val width = if (rawWidth > 0) rawWidth else 720
        val height = if (rawHeight > 0) rawHeight else 1280

        val durationStr = DURATION_REGEX.find(html)?.groupValues?.get(1)
            ?: ldVideoObj?.optCleanString("duration")
        val durationSeconds = YoutubeDLUtils.parseIso8601Duration(durationStr)

        val likeCount = LIKE_COUNT_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val viewCount = VIEW_COUNT_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: INTERACTION_COUNT_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L

        val videoId = extractPhotoId(finalUrl) ?: "snack_${System.currentTimeMillis()}"

        val formats = listOf(
            VideoFormat(
                formatId = "snack_1080p",
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
                formatId = "snack_720p",
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
                formatId = "snack_mp4_original",
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
                tbr = 1800.0
            ),
            VideoFormat(
                formatId = "snack_audio",
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
            url = finalUrl,
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            likeCount = likeCount,
            videoId = videoId
        )

        val videoInfo = VideoInfo(
            id = videoId,
            title = title,
            uploader = author,
            duration = durationSeconds,
            thumbnail = thumbnail,
            webpageUrl = finalUrl,
            formats = formats
        )

        return SnackVideoExtractionResult(
            fastInfo = fastInfo,
            videoInfo = videoInfo,
            consolidatedFormats = formats,
            mediaUrl = mediaUrl
        )
    }

    private fun extractVideoUrlFromInitialState(state: org.json.JSONObject): String? {
        val keys = state.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = state.opt(k)
            if (v is org.json.JSONObject) {
                val url = v.optCleanString("mainMvUrl")
                    .ifEmpty { v.optCleanString("videoUrl") }
                    .ifEmpty { v.optCleanString("mp4Url") }
                    .ifEmpty { v.optCleanString("contentUrl") }
                if (url.startsWith("http")) return url
                extractVideoUrlFromInitialState(v)?.let { return it }
            }
        }
        return null
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

    private fun fetchSnackVideoPage(startUrl: String): Pair<String, String>? {
        val opened = NetworkSecurityUtils.openSafeConnectionWithRedirects(
            startUrl = startUrl,
            maxRedirects = 8,
            connectTimeoutMs = 10000,
            readTimeoutMs = 10000,
            headers = mapOf(
                "User-Agent" to DESKTOP_USER_AGENT,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
            ),
            preserveCookies = true
        ) ?: return null

        val finalUrl = opened.first
        val conn = opened.second
        return try {
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.use { NetworkSecurityUtils.readBoundedText(it) }
                Pair(finalUrl, body)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun extractPhotoId(url: String): String? {
        return PHOTO_ID_PATH_REGEX.find(url)?.groupValues?.get(1)
    }

    internal fun decodeJsString(raw: String): String = JsonSafeUtils.decodeEscapedString(raw)
}
