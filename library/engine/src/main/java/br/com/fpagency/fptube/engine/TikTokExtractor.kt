package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Extrator nativo para o TikTok (suporta links curtos vt.tiktok.com / vm.tiktok.com e links completos).
 */
object TikTokExtractor {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    private val jacksonMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class TikTokExtractionResult(
        val fastInfo: FastVideoInfo,
        val videoInfo: VideoInfo,
        val consolidatedFormats: List<VideoFormat>
    )

    data class ResolvedTikTokStream(
        val mediaUrl: String,
        val ext: String = "mp4",
        val userAgent: String = USER_AGENT,
        val formatId: String? = null
    )

    suspend fun extract(url: String): EngineOperationResult<TikTokExtractionResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        AppLogger.i("[TIKTOK] Iniciando extração nativa para: $url", "TikTokExtractor")

        // 1. Extração primária via TikWM API (fornece HD sem marca d'água, SD sem marca d'água e Áudio MP3 com links diretos CDN)
        extractViaTikWm(url)?.let { result ->
            AppLogger.i(
                "[TIKTOK] Extração via TikWM concluída em ${System.currentTimeMillis() - start}ms | Formatos: ${result.consolidatedFormats.size}",
                "TikTokExtractor"
            )
            return@withContext EngineOperationResult.Success(
                data = result,
                extraData = Pair(result.consolidatedFormats, "TikTokNative")
            )
        }

        // 2. Fallback via página HTML do TikTok (__UNIVERSAL_DATA_FOR_REHYDRATION__)
        extractViaHtmlRehydration(url)?.let { result ->
            AppLogger.i(
                "[TIKTOK] Extração via HTML Rehydration concluída em ${System.currentTimeMillis() - start}ms | Formatos: ${result.consolidatedFormats.size}",
                "TikTokExtractor"
            )
            return@withContext EngineOperationResult.Success(
                data = result,
                extraData = Pair(result.consolidatedFormats, "TikTokNative")
            )
        }

        EngineOperationResult.ExtractionError("Não foi possível extrair o vídeo do TikTok. Verifique o link e tente novamente.")
    }

    suspend fun resolveStream(
        originalUrl: String,
        preferredFormatId: String?,
        downloadType: String? = "video"
    ): ResolvedTikTokStream? = withContext(Dispatchers.IO) {
        try {
            val result = extract(originalUrl)
            if (result is EngineOperationResult.Success) {
                val formats = result.data.consolidatedFormats
                if (formats.isEmpty()) return@withContext null

                val isAudioRequest = downloadType.equals("audio", ignoreCase = true) ||
                    downloadType.equals("Áudio", ignoreCase = true)

                if (isAudioRequest) {
                    val audioFmt = formats.find { it.vcodec == "none" } ?: formats.first()
                    val audioUrl = audioFmt.url ?: return@withContext null
                    return@withContext ResolvedTikTokStream(
                        mediaUrl = audioUrl,
                        ext = audioFmt.ext ?: "mp3",
                        formatId = audioFmt.formatId
                    )
                }

                val videoFormats = formats.filter { it.vcodec != "none" }
                val chosen = (if (!preferredFormatId.isNullOrBlank() && preferredFormatId != "best") {
                    videoFormats.find { it.formatId == preferredFormatId }
                        ?: formats.find { it.formatId == preferredFormatId }
                } else null)
                    ?: videoFormats.firstOrNull()
                    ?: formats.first()

                val chosenUrl = chosen.url ?: return@withContext null
                return@withContext ResolvedTikTokStream(
                    mediaUrl = chosenUrl,
                    ext = chosen.ext ?: "mp4",
                    formatId = chosen.formatId
                )
            }
        } catch (e: Exception) {
            AppLogger.e("[TIKTOK] Erro ao resolver stream do TikTok: ${e.message}", "TikTokExtractor", e)
        }
        null
    }

    suspend fun getFreshMediaUrl(originalUrl: String, preferredFormatId: String? = null): String? =
        resolveStream(originalUrl, preferredFormatId)?.mediaUrl

    private fun resolveTikWmUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else if (rawUrl.startsWith("/")) {
            "https://www.tikwm.com$rawUrl"
        } else {
            "https://www.tikwm.com/$rawUrl"
        }
    }

    private fun extractViaTikWm(url: String): TikTokExtractionResult? {
        try {
            val apiEndpoint = URL("https://www.tikwm.com/api/")
            val conn = apiEndpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

            val postBody = "url=${URLEncoder.encode(url, "UTF-8")}&hd=1"
            conn.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val root = JSONObject(responseText)
            if (root.optInt("code", -1) != 0) {
                AppLogger.w("[TIKTOK] TikWM retornou code=${root.optInt("code")} msg=${root.optString("msg")}", "TikTokExtractor")
                return null
            }

            val data = root.optJSONObject("data") ?: return null
            val videoId = data.optString("id", "tt_${System.currentTimeMillis()}")
            val rawTitle = data.optString("title", "").trim()
            val authorObj = data.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "")?.takeIf { it.isNotBlank() }
                ?: authorObj?.optString("unique_id", "")?.takeIf { it.isNotBlank() }
                ?: "TikTok"
            val title = rawTitle.ifBlank { "Vídeo de $authorName no TikTok" }

            val coverUrl = resolveTikWmUrl(
                data.optString("origin_cover", "").ifBlank { data.optString("cover", "") }
            )
            val duration = data.optInt("duration", 0)
            val playCount = data.optLong("play_count", 0L)
            val diggCount = data.optLong("digg_count", 0L)

            val hdPlayUrl = resolveTikWmUrl(data.optString("hdplay", ""))
            val hdSize = data.optLong("hd_size", 0L)
            val sdPlayUrl = resolveTikWmUrl(data.optString("play", ""))
            val sdSize = data.optLong("size", 0L)
            val wmPlayUrl = resolveTikWmUrl(data.optString("wmplay", ""))
            val wmSize = data.optLong("wm_size", 0L)
            val musicUrl = resolveTikWmUrl(
                data.optString("music", "").ifBlank {
                    data.optJSONObject("music_info")?.optString("play", "") ?: ""
                }
            )

            val formats = mutableListOf<VideoFormat>()

            if (hdPlayUrl.isNotBlank()) {
                val tbr = if (duration > 0 && hdSize > 0) (hdSize * 8.0) / (duration * 1000.0) else 2500.0
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_hd_nowm",
                        url = hdPlayUrl,
                        ext = "mp4",
                        width = 1080,
                        height = 1080,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "Full HD Sem Marca d'Água (MP4)",
                        formatNote = "1080p",
                        filesize = hdSize,
                        tbr = tbr
                    )
                )
            }

            if (sdPlayUrl.isNotBlank() && sdPlayUrl != hdPlayUrl) {
                val tbr = if (duration > 0 && sdSize > 0) (sdSize * 8.0) / (duration * 1000.0) else 1500.0
                val isOnlyVideo = formats.isEmpty()
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_sd_nowm",
                        url = sdPlayUrl,
                        ext = "mp4",
                        width = if (isOnlyVideo) 1080 else 720,
                        height = if (isOnlyVideo) 1080 else 720,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "HD Sem Marca d'Água (MP4)",
                        formatNote = if (isOnlyVideo) "1080p" else "720p",
                        filesize = sdSize,
                        tbr = tbr
                    )
                )
            }

            if (wmPlayUrl.isNotBlank() && wmPlayUrl != sdPlayUrl && wmPlayUrl != hdPlayUrl) {
                val tbr = if (duration > 0 && wmSize > 0) (wmSize * 8.0) / (duration * 1000.0) else 1000.0
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_wm",
                        url = wmPlayUrl,
                        ext = "mp4",
                        width = 576,
                        height = 480,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "SD Com Marca d'Água (MP4)",
                        formatNote = "480p",
                        filesize = wmSize,
                        tbr = tbr
                    )
                )
            }

            if (musicUrl.isNotBlank()) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_audio",
                        url = musicUrl,
                        ext = "mp3",
                        width = 0,
                        height = 0,
                        fps = 0,
                        vcodec = "none",
                        acodec = "mp3",
                        format = "Áudio Original (MP3)",
                        formatNote = "Audio 128K",
                        filesize = 0L,
                        tbr = 128.0,
                        abr = 128
                    )
                )
            }

            if (formats.isEmpty()) return null

            val fastInfo = FastVideoInfo(
                title = title,
                author = authorName,
                thumbnail = coverUrl,
                url = url,
                durationSeconds = duration,
                viewCount = playCount,
                likeCount = diggCount,
                videoId = videoId
            )

            val infoJson = JSONObject().apply {
                put("id", videoId)
                put("title", title)
                put("uploader", authorName)
                put("uploader_id", authorName)
                put("thumbnail", coverUrl)
                put("duration", duration)
                put("webpage_url", url)
                put("extractor", "TikTokNative")
            }

            val videoInfo = try {
                jacksonMapper.readValue(infoJson.toString(), VideoInfo::class.java).copy(formats = formats)
            } catch (e: Exception) {
                VideoInfo(
                    id = videoId,
                    title = title,
                    uploader = authorName,
                    thumbnail = coverUrl,
                    duration = duration,
                    webpageUrl = url,
                    formats = formats
                )
            }

            return TikTokExtractionResult(
                fastInfo = fastInfo,
                videoInfo = videoInfo,
                consolidatedFormats = formats
            )
        } catch (e: Exception) {
            AppLogger.w("[TIKTOK] Falha no TikWM: ${e.message}", "TikTokExtractor")
            return null
        }
    }

    private fun extractViaHtmlRehydration(initialUrl: String): TikTokExtractionResult? {
        try {
            val conn = URL(initialUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val finalUrl = conn.url.toString()
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val scriptMatch = Regex("""<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""")
                .find(html) ?: return null
            val jsonStr = scriptMatch.groupValues[1]
            val root = JSONObject(jsonStr)
            val itemStruct = root
                .optJSONObject("__DEFAULT_SCOPE__")
                ?.optJSONObject("webapp.video-detail")
                ?.optJSONObject("itemInfo")
                ?.optJSONObject("itemStruct")
                ?: return null

            val videoId = itemStruct.optString("id", "tt_${System.currentTimeMillis()}")
            val authorObj = itemStruct.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "")?.takeIf { it.isNotBlank() }
                ?: authorObj?.optString("uniqueId", "")?.takeIf { it.isNotBlank() }
                ?: "TikTok"
            val title = itemStruct.optString("desc", "").trim().ifBlank { "Vídeo de $authorName no TikTok" }

            val videoObj = itemStruct.optJSONObject("video") ?: return null
            val duration = videoObj.optInt("duration", 0)
            val coverUrl = videoObj.optString("originCover", "").ifBlank { videoObj.optString("cover", "") }
            val playAddr = videoObj.optString("playAddr", "").ifBlank { videoObj.optString("downloadAddr", "") }
            val musicUrl = itemStruct.optJSONObject("music")?.optString("playUrl", "") ?: ""

            val formats = mutableListOf<VideoFormat>()
            if (playAddr.isNotBlank()) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_web_mp4",
                        url = playAddr,
                        ext = "mp4",
                        width = videoObj.optInt("width", 1080),
                        height = videoObj.optInt("height", 1080).coerceAtMost(1080),
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "HD (MP4)",
                        formatNote = "720p",
                        filesize = 0L,
                        tbr = 1500.0
                    )
                )
            }
            if (musicUrl.isNotBlank()) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_audio",
                        url = musicUrl,
                        ext = "mp3",
                        width = 0,
                        height = 0,
                        fps = 0,
                        vcodec = "none",
                        acodec = "mp3",
                        format = "Áudio Original (MP3)",
                        formatNote = "Audio 128K",
                        filesize = 0L,
                        tbr = 128.0,
                        abr = 128
                    )
                )
            }

            if (formats.isEmpty()) return null

            val fastInfo = FastVideoInfo(
                title = title,
                author = authorName,
                thumbnail = coverUrl,
                url = initialUrl,
                durationSeconds = duration,
                videoId = videoId
            )
            val videoInfo = VideoInfo(
                id = videoId,
                title = title,
                uploader = authorName,
                thumbnail = coverUrl,
                duration = duration,
                webpageUrl = finalUrl,
                formats = formats
            )
            return TikTokExtractionResult(
                fastInfo = fastInfo,
                videoInfo = videoInfo,
                consolidatedFormats = formats
            )
        } catch (e: Exception) {
            AppLogger.w("[TIKTOK] Falha no fallback HTML Rehydration: ${e.message}", "TikTokExtractor")
            return null
        }
    }
}
