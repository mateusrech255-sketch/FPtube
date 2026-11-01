package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extrator nativo para o Instagram.
 * Extrai mídias públicas do Instagram utilizando User-Agents otimizados para bots de busca,
 * evitando bloqueios por necessidade de login e fornecendo links diretos de mídia MP4.
 */
object InstagramExtractor {

    private val uas = listOf(
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"
    )

    private val jacksonMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class InstagramExtractionResult(
        val fastInfo: FastVideoInfo,
        val videoInfo: VideoInfo,
        val consolidatedFormats: List<VideoFormat>
    )

    suspend fun extract(url: String): EngineOperationResult<InstagramExtractionResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        AppLogger.i("[INSTAGRAM] Iniciando extração nativa para: $url", "InstagramExtractor")

        try {
            val shortcode = extractShortcode(url)
            if (shortcode.isNullOrBlank()) {
                AppLogger.e("[INSTAGRAM] Não foi possível extrair o shortcode da URL: $url", "InstagramExtractor")
                return@withContext EngineOperationResult.ExtractionError("Link do Instagram inválido.")
            }

            val targetUrl = "https://www.instagram.com/reel/$shortcode/"
            val rawHtml = fetchHtml(targetUrl)

            if (rawHtml.isBlank()) {
                AppLogger.e("[INSTAGRAM] Falha ao carregar HTML para: $targetUrl", "InstagramExtractor")
                return@withContext EngineOperationResult.ExtractionError("Não foi possível carregar a página do Instagram.")
            }

            val cleanHtml = unescapeHtml(rawHtml)

            val caption = extractCaption(cleanHtml)
            val title = if (!caption.isNullOrBlank()) caption else extractOgTitle(cleanHtml)
            val uploader = extractUploader(cleanHtml)
            val thumbnail = extractThumbnail(cleanHtml)
            val duration = extractDuration(cleanHtml)
            val formats = extractFormats(cleanHtml)

            if (formats.isEmpty()) {
                AppLogger.e("[INSTAGRAM] Nenhum formato de vídeo encontrado no HTML", "InstagramExtractor")
                return@withContext EngineOperationResult.VideoUnavailable("Conteúdo restrito ou sem vídeo disponível no Instagram.")
            }

            AppLogger.i("[INSTAGRAM] Extração concluída em ${System.currentTimeMillis() - start}ms | Shortcode: $shortcode | Formatos: ${formats.size}", "InstagramExtractor")

            val fastInfo = FastVideoInfo(
                title = title,
                author = uploader,
                thumbnail = thumbnail,
                url = url,
                durationSeconds = duration,
                viewCount = 0L,
                likeCount = 0L,
                videoId = shortcode
            )

            val infoJson = JSONObject().apply {
                put("id", shortcode)
                put("title", title)
                put("uploader", uploader)
                put("uploader_id", uploader)
                put("thumbnail", thumbnail)
                put("duration", duration)
                put("webpage_url", "https://www.instagram.com/reel/$shortcode/")
                put("extractor", "InstagramNative")
            }

            val videoInfo = try {
                jacksonMapper.readValue(infoJson.toString(), VideoInfo::class.java)
            } catch (e: Exception) {
                VideoInfo()
            }

            EngineOperationResult.Success(
                data = InstagramExtractionResult(
                    fastInfo = fastInfo,
                    videoInfo = videoInfo,
                    consolidatedFormats = formats
                ),
                extraData = Pair(formats, "InstagramNative")
            )
        } catch (e: Exception) {
            AppLogger.e("[INSTAGRAM] Erro durante a extração: ${e.message}", "InstagramExtractor", e)
            EngineOperationResult.ExtractionError("Falha ao extrair vídeo do Instagram: ${e.message}")
        }
    }

    suspend fun getFreshMediaUrl(url: String, formatId: String?): String? = withContext(Dispatchers.IO) {
        when (val result = extract(url)) {
            is EngineOperationResult.Success -> {
                val formats = result.data.consolidatedFormats
                if (!formatId.isNullOrBlank() && formatId != "best") {
                    formats.find { it.formatId == formatId }?.url ?: formats.firstOrNull()?.url
                } else {
                    formats.firstOrNull()?.url
                }
            }
            else -> null
        }
    }

    private fun extractShortcode(url: String): String? {
        val regex = Regex("""/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url)
        return match?.groupValues?.get(1)
    }

    private fun fetchHtml(initialUrl: String): String {
        for (ua in uas) {
            try {
                val urlObj = URL(initialUrl)
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", ua)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    conn.disconnect()
                    if (html.contains(".mp4")) {
                        return html
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                AppLogger.w("[INSTAGRAM] Erro ao buscar HTML com UA $ua: ${e.message}", "InstagramExtractor")
            }
        }
        return ""
    }

    private fun unescapeHtml(html: String): String {
        return html
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\u00253D", "=")
    }

    private fun extractCaption(html: String): String? {
        val captionRegex = Regex(""""caption"\s*:\s*\{[^}]*"text"\s*:\s*"([^"]+)"""")
        val match = captionRegex.find(html)
        if (match != null) {
            return decodeUnicode(match.groupValues[1])
        }
        return null
    }

    private fun extractOgTitle(html: String): String {
        val ogTitleRegex = Regex("""property="og:title"\s*content="([^"]+)"""")
        val match = ogTitleRegex.find(html)
        if (match != null) {
            val title = decodeHtmlEntities(match.groupValues[1])
            return title.substringBefore(" on Instagram:").trim()
        }
        return "Instagram Video"
    }

    private fun extractUploader(html: String): String {
        val ownerRegex = Regex(""""owner"\s*:\s*\{[^}]*"username"\s*:\s*"([^"]+)"""")
        val match = ownerRegex.find(html)
        if (match != null) return match.groupValues[1]

        val userRegex = Regex(""""username"\s*:\s*"([^"]+)"""")
        val uMatch = userRegex.find(html)
        if (uMatch != null) return uMatch.groupValues[1]

        val ogTitleRegex = Regex("""property="og:title"\s*content="([^"]+)"""")
        val titleMatch = ogTitleRegex.find(html)
        if (titleMatch != null) {
            val fullTitle = titleMatch.groupValues[1]
            if (fullTitle.contains(" on Instagram")) {
                return fullTitle.substringBefore(" on Instagram").trim()
            }
        }
        return "Instagram"
    }

    private fun extractThumbnail(html: String): String {
        val ogImageRegex = Regex("""property="og:image"\s*content="([^"]+)"""")
        val match = ogImageRegex.find(html)
        if (match != null) return decodeHtmlEntities(match.groupValues[1])

        val displayUrlRegex = Regex(""""display_url"\s*:\s*"([^"]+)"""")
        val displayMatch = displayUrlRegex.find(html)
        if (displayMatch != null) return displayMatch.groupValues[1]

        return ""
    }

    private fun extractDuration(html: String): Int {
        val durationRegex = Regex(""""video_duration"\s*:\s*([0-9.]+)""")
        val match = durationRegex.find(html)
        if (match != null) {
            return match.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
        }
        return 0
    }

    private fun parseHeight(mediaUrl: String, obj: JSONObject? = null): Int {
        if (obj != null) {
            val h = obj.optInt("height", obj.optInt("original_height", 0))
            if (h > 0) return h
        }

        try {
            val efgMatch = Regex("""efg=([^&]+)""").find(mediaUrl)
            if (efgMatch != null) {
                val rawB64 = efgMatch.groupValues[1]
                val paddedB64 = rawB64 + "=".repeat((4 - rawB64.length % 4) % 4)
                val decoded = String(android.util.Base64.decode(paddedB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val hMatch = Regex("""\.(\d{3,4})\.(?:dash|progressive)""").find(decoded)
                if (hMatch != null) {
                    return hMatch.groupValues[1].toIntOrNull() ?: 720
                }
                val hMatch2 = Regex("""\b(\d{3,4})p?\b""").find(decoded)
                if (hMatch2 != null) {
                    val num = hMatch2.groupValues[1].toIntOrNull() ?: 0
                    if (num in listOf(240, 360, 480, 540, 720, 1080, 1440, 2160)) {
                        return num
                    }
                }
            }
        } catch (e: Exception) {
            // silent
        }

        val pMatch = Regex("""_(\d{3,4})p""").find(mediaUrl)
        if (pMatch != null) {
            return pMatch.groupValues[1].toIntOrNull() ?: 720
        }

        return 720
    }

    private fun extractFormats(html: String): List<VideoFormat> {
        val formats = mutableListOf<VideoFormat>()
        val seenUrls = mutableSetOf<String>()

        // Opção 1: Parse do array "video_versions"
        val vvRegex = Regex(""""video_versions"\s*:\s*(\[[^\]]+\])""")
        val vvMatch = vvRegex.find(html)
        if (vvMatch != null) {
            try {
                val jsonArray = JSONArray(vvMatch.groupValues[1])
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val mediaUrl = obj.optString("url")
                    if (!mediaUrl.isNullOrBlank() && seenUrls.add(mediaUrl)) {
                        val height = parseHeight(mediaUrl, obj)
                        val width = obj.optInt("width", if (height > 0) (height * 9 / 16) else 720)
                        val note = "${height}p"
                        val fmtStr = "${height}p (MP4)"

                        val fmtJson = JSONObject().apply {
                            put("format_id", "instagram_native_$i")
                            put("url", mediaUrl)
                            put("width", width)
                            put("height", height)
                            put("fps", 30)
                            put("vcodec", "h264")
                            put("acodec", "aac")
                            put("ext", "mp4")
                            put("format", fmtStr)
                            put("format_note", note)
                            put("protocol", "https")
                        }

                        val vf = jacksonMapper.readValue(fmtJson.toString(), VideoFormat::class.java)
                        formats.add(vf)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("[INSTAGRAM] Falha ao analisar o JSON video_versions: ${e.message}", "InstagramExtractor")
            }
        }

        // Opção 2: Fallback por expressão regular buscando URLs .mp4
        if (formats.isEmpty()) {
            val mp4Regex = Regex("""https?://[^\s"'<>]+?\.mp4[^\s"'<>]*""")
            val matches = mp4Regex.findAll(html)
                .map { it.value.substringBefore("<").substringBefore("\"").substringBefore("'") }
                .distinct()

            var count = 0
            for (mediaUrl in matches) {
                if (seenUrls.add(mediaUrl)) {
                    val height = parseHeight(mediaUrl)
                    val width = if (height > 0) (height * 9 / 16) else 720
                    val note = "${height}p"
                    val fmtStr = "${height}p (MP4)"

                    val fmtJson = JSONObject().apply {
                        put("format_id", "instagram_mp4_$count")
                        put("url", mediaUrl)
                        put("width", width)
                        put("height", height)
                        put("fps", 30)
                        put("vcodec", "h264")
                        put("acodec", "aac")
                        put("ext", "mp4")
                        put("format", fmtStr)
                        put("format_note", note)
                        put("protocol", "https")
                    }

                    try {
                        val vf = jacksonMapper.readValue(fmtJson.toString(), VideoFormat::class.java)
                        formats.add(vf)
                        count++
                    } catch (e: Exception) {
                        AppLogger.w("[INSTAGRAM] Erro ao deserializar mp4 fallback: ${e.message}", "InstagramExtractor")
                    }
                }
            }
        }

        return formats
    }

    private fun decodeUnicode(input: String): String {
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i < input.length) {
                if (input[i] == '\\' && i + 5 < input.length && input[i + 1] == 'u') {
                    val code = input.substring(i + 2, i + 6).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                sb.append(input[i])
                i++
            }
            sb.toString()
        } catch (e: Exception) {
            input
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x1f1e7;", "")
            .replace("&#x1f1f7;", "")
    }
}
