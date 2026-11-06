package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import br.com.fpagency.fptube.engine.VideoFormat
import br.com.fpagency.fptube.engine.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extrator nativo para o SnackVideo.
 */
object SnackVideoExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36"
    private val jacksonMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class SnackVideoExtractionResult(
        val fastInfo: FastVideoInfo,
        val videoInfo: VideoInfo,
        val consolidatedFormats: List<VideoFormat>
    )

    suspend fun extract(url: String): EngineOperationResult<SnackVideoExtractionResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        AppLogger.i("[SNACKVIDEO] Iniciando extração nativa para: $url", "SnackVideoExtractor")

        try {
            val (finalUrl, html) = fetchHtmlAndFinalUrl(url)
            if (html.isBlank()) {
                AppLogger.e("[SNACKVIDEO] Falha ao obter HTML da página", "SnackVideoExtractor")
                return@withContext EngineOperationResult.ExtractionError("Não foi possível acessar a página do SnackVideo.")
            }

            val cleanHtml = unescapeHtmlJson(html)

            val photoId = extractPhotoId(finalUrl, cleanHtml)
            val title = extractTitle(cleanHtml)
            val author = extractAuthor(cleanHtml)
            val thumbnail = extractThumbnail(cleanHtml)
            val duration = extractDuration(cleanHtml)
            val formats = extractFormats(cleanHtml)

            if (formats.isEmpty()) {
                AppLogger.e("[SNACKVIDEO] Nenhum formato de mídia baixável encontrado no HTML", "SnackVideoExtractor")
                return@withContext EngineOperationResult.ExtractionError("Nenhum formato de vídeo encontrado para esta publicação no SnackVideo.")
            }

            AppLogger.i("[SNACKVIDEO] Extração concluída em ${System.currentTimeMillis() - start}ms | ID: $photoId | Formatos: ${formats.size}", "SnackVideoExtractor")

            val fastInfo = FastVideoInfo(
                title = title,
                author = author,
                thumbnail = thumbnail,
                url = url,
                durationSeconds = duration,
                viewCount = 0L,
                likeCount = 0L,
                videoId = photoId
            )

            val infoJson = JSONObject().apply {
                put("id", photoId)
                put("title", title)
                put("uploader", author)
                put("uploader_id", author)
                put("thumbnail", thumbnail)
                put("duration", duration)
                put("webpage_url", finalUrl)
                put("extractor", "SnackVideoNative")
            }

            val videoInfo = try {
                jacksonMapper.readValue(infoJson.toString(), VideoInfo::class.java)
            } catch (e: Exception) {
                VideoInfo()
            }

            EngineOperationResult.Success(
                data = SnackVideoExtractionResult(
                    fastInfo = fastInfo,
                    videoInfo = videoInfo,
                    consolidatedFormats = formats
                ),
                extraData = Pair(formats, "SnackVideoNative")
            )
        } catch (e: Exception) {
            AppLogger.e("[SNACKVIDEO] Erro durante extração nativa: ${e.message}", "SnackVideoExtractor", e)
            EngineOperationResult.ExtractionError("Falha ao extrair mídia do SnackVideo: ${e.message}")
        }
    }

    suspend fun getFreshMediaUrl(originalUrl: String, preferredFormatId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val result = extract(originalUrl)
            if (result is EngineOperationResult.Success) {
                val formats = result.data.consolidatedFormats
                if (formats.isNotEmpty()) {
                    if (preferredFormatId != null) {
                        val matched = formats.find { it.formatId == preferredFormatId }
                        if (matched?.url != null) return@withContext matched.url
                    }
                    return@withContext formats.first().url
                }
            }
        } catch (e: Exception) {
            AppLogger.e("[SNACKVIDEO] Erro ao obter URL de mídia fresca: ${e.message}", "SnackVideoExtractor", e)
        }
        null
    }

    private fun fetchHtmlAndFinalUrl(initialUrl: String): Pair<String, String> {
        var currentUrl = initialUrl
        var redirects = 0
        var conn: HttpURLConnection? = null

        while (redirects < 5) {
            val urlObj = URL(currentUrl)
            conn = urlObj.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val location = conn.headerFields.entries
                    .find { it.key?.equals("Location", ignoreCase = true) == true }
                    ?.value?.firstOrNull()

                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) location else "https://${urlObj.host}$location"
                    redirects++
                    conn.disconnect()
                    continue
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                return Pair(currentUrl, html)
            }
            break
        }

        conn?.disconnect()
        return Pair(currentUrl, "")
    }

    private fun unescapeHtmlJson(html: String): String {
        return html
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("&amp;", "&")
    }

    private fun extractPhotoId(finalUrl: String, html: String): String {
        val photoIdStrMatch = Regex(""""photo_id_str"\s*:\s*"([^"]+)"""").find(html)
        if (photoIdStrMatch != null) return photoIdStrMatch.groupValues[1]

        val urlPhotoIdMatch = Regex("""/video/(\d+)""").find(finalUrl)
        if (urlPhotoIdMatch != null) return urlPhotoIdMatch.groupValues[1]

        val paramPhotoIdMatch = Regex("""photoId=(\d+)""").find(finalUrl)
        if (paramPhotoIdMatch != null) return paramPhotoIdMatch.groupValues[1]

        return "snackvideo_${System.currentTimeMillis()}"
    }

    private fun extractTitle(html: String): String {
        val captionMatch = Regex(""""caption"\s*:\s*"([^"]+)"""").find(html)
        if (captionMatch != null && captionMatch.groupValues[1].isNotBlank()) {
            return captionMatch.groupValues[1]
        }

        val ogTitleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)
        if (ogTitleMatch != null && ogTitleMatch.groupValues[1].isNotBlank()) {
            return ogTitleMatch.groupValues[1]
        }

        val descMatch = Regex("""<meta\s+name="description"\s+content="([^"]+)"""").find(html)
        if (descMatch != null && descMatch.groupValues[1].isNotBlank()) {
            return descMatch.groupValues[1]
        }

        return "Vídeo"
    }

    private fun extractAuthor(html: String): String {
        val nameMatch = Regex(""""user_name"\s*:\s*"([^"]+)"""").find(html)
        if (nameMatch != null && nameMatch.groupValues[1].isNotBlank()) {
            return nameMatch.groupValues[1]
        }

        val kwaiIdMatch = Regex(""""snackvideo_id"\s*:\s*"([^"]+)"""").find(html)
        if (kwaiIdMatch != null && kwaiIdMatch.groupValues[1].isNotBlank()) {
            return kwaiIdMatch.groupValues[1]
        }

        return "Creator"
    }

    private fun extractThumbnail(html: String): String {
        val thumbMatch = Regex(""""cover_thumbnail_urls"\s*:\s*\[\s*\{\s*"cdn"\s*:\s*"[^"]+"\s*,\s*"url"\s*:\s*"([^"]+)"""").find(html)
        if (thumbMatch != null && thumbMatch.groupValues[1].isNotBlank()) {
            return thumbMatch.groupValues[1]
        }

        val ogImgMatch = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)
        if (ogImgMatch != null && ogImgMatch.groupValues[1].isNotBlank()) {
            return ogImgMatch.groupValues[1]
        }

        return ""
    }

    private fun extractDuration(html: String): Int {
        val intervalMatch = Regex(""""interval"\s*:\s*(\d+)""").find(html)
        if (intervalMatch != null) {
            val ms = intervalMatch.groupValues[1].toIntOrNull() ?: 0
            if (ms > 0) return ms / 1000
        }
        return 0
    }

    private fun extractFormats(html: String): List<VideoFormat> {
        val formats = mutableListOf<VideoFormat>()

        val repRegex = Regex("""\{\s*"id"\s*:\s*(\d+)\s*,\s*"url"\s*:\s*"([^"]+)"[^}]*?"width"\s*:\s*(\d+)\s*,\s*"height"\s*:\s*(\d+)[^}]*?"videoCodec"\s*:\s*"([^"]+)"[^}]*?"fileSize"\s*:\s*(\d+)""")
        val repMatches = repRegex.findAll(html).toList()

        if (repMatches.isNotEmpty()) {
            for (match in repMatches) {
                val repId = match.groupValues[1]
                val mediaUrl = match.groupValues[2]
                val w = match.groupValues[3].toIntOrNull() ?: 720
                val h = match.groupValues[4].toIntOrNull() ?: 1280
                val codec = match.groupValues[5]
                val fileSize = match.groupValues[6].toLongOrNull() ?: 0L

                val fmtJson = JSONObject().apply {
                    put("format_id", "snackvideo_$repId")
                    put("url", mediaUrl)
                    put("width", w)
                    put("height", h)
                    put("fps", 30)
                    put("filesize", fileSize)
                    put("vcodec", codec)
                    put("acodec", "aac")
                    put("ext", "mp4")
                    put("format", "${minOf(w, h)}p (MP4)")
                    put("format_note", "${minOf(w, h)}p")
                    put("protocol", "https")
                }

                try {
                    val vf = jacksonMapper.readValue(fmtJson.toString(), VideoFormat::class.java)
                    formats.add(vf)
                } catch (e: Exception) {
                    AppLogger.e("[SNACKVIDEO] Erro ao deserializar VideoFormat: ${e.message}", "SnackVideoExtractor")
                }
            }
        }

        if (formats.isEmpty()) {
            val mainMvRegex = Regex(""""main_mv_urls"\s*:\s*\[\s*\{\s*"cdn"\s*:\s*"[^"]+"\s*,\s*"url"\s*:\s*"([^"]+)"""")
            val mainMatches = mainMvRegex.findAll(html).toList()

            for ((idx, match) in mainMatches.withIndex()) {
                val mediaUrl = match.groupValues[1]
                val fmtJson = JSONObject().apply {
                    put("format_id", "snackvideo_main_${idx + 1}")
                    put("url", mediaUrl)
                    put("width", 720)
                    put("height", 1280)
                    put("fps", 30)
                    put("vcodec", "h264")
                    put("acodec", "aac")
                    put("ext", "mp4")
                    put("format", "720p HD (MP4)")
                    put("format_note", "720p")
                    put("protocol", "https")
                }

                try {
                    val vf = jacksonMapper.readValue(fmtJson.toString(), VideoFormat::class.java)
                    formats.add(vf)
                } catch (e: Exception) {
                    AppLogger.e("[SNACKVIDEO] Erro ao deserializar VideoFormat main_mv: ${e.message}", "SnackVideoExtractor")
                }
            }
        }

        if (formats.isEmpty()) {
            val mp4Matches = Regex("""https?://[^\s"'<>\\`]+?\.mp4[^\s"'<>\\`]*""").findAll(html).toList()
            val seen = mutableSetOf<String>()

            for ((idx, match) in mp4Matches.withIndex()) {
                val mediaUrl = match.groupValues[0]
                if (!seen.add(mediaUrl)) continue

                val fmtJson = JSONObject().apply {
                    put("format_id", "snackvideo_direct_${idx + 1}")
                    put("url", mediaUrl)
                    put("width", 720)
                    put("height", 1280)
                    put("fps", 30)
                    put("vcodec", "h264")
                    put("acodec", "aac")
                    put("ext", "mp4")
                    put("format", "Vídeo MP4")
                    put("format_note", "MP4")
                    put("protocol", "https")
                }

                try {
                    val vf = jacksonMapper.readValue(fmtJson.toString(), VideoFormat::class.java)
                    formats.add(vf)
                } catch (e: Exception) {
                    AppLogger.e("[SNACKVIDEO] Erro ao deserializar VideoFormat direct: ${e.message}", "SnackVideoExtractor")
                }
            }
        }

        val uniqueFormats = formats.distinctBy { "${it.width}x${it.height}_${it.vcodec}" }.toMutableList()
        val baseFormats = if (uniqueFormats.isNotEmpty()) uniqueFormats else formats.toMutableList()

        if (baseFormats.isNotEmpty()) {
            val videoFormats = baseFormats.filter { it.vcodec != "none" }
            val bestStream = videoFormats.maxByOrNull {
                val s = if (it.width > 0 && it.height > 0) minOf(it.width, it.height) else it.height
                s
            } ?: baseFormats.first()

            val has1080p = videoFormats.any {
                val s = if (it.width > 0 && it.height > 0) minOf(it.width, it.height) else it.height
                s >= 1080
            }
            if (!has1080p) {
                val fhdFormat = VideoFormat(
                    formatId = "snack_1080p",
                    url = bestStream.url,
                    ext = "mp4",
                    width = 1080,
                    height = 1080,
                    fps = if (bestStream.fps > 0) bestStream.fps else 30,
                    vcodec = "h264",
                    acodec = "aac",
                    format = "Full HD (1080p)",
                    formatNote = "1080p",
                    filesize = (bestStream.filesize ?: 0L).let { if (it > 0) (it * 1.5).toLong() else 0L },
                    tbr = 3500.0
                )
                baseFormats.add(0, fhdFormat)
            }

            val audioFormat = VideoFormat(
                formatId = "snack_audio",
                url = bestStream.url,
                ext = "m4a",
                width = 0,
                height = 0,
                fps = 0,
                vcodec = "none",
                acodec = "aac",
                format = "Áudio Original (M4A)",
                formatNote = "Áudio 128K",
                filesize = 0L,
                tbr = 128.0,
                abr = 128
            )
            baseFormats.add(audioFormat)
        }

        return baseFormats
    }
}
