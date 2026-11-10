package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeInt
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TikTokExtractionResult(
    val fastInfo: FastVideoInfo,
    val videoInfo: VideoInfo,
    val consolidatedFormats: List<VideoFormat>,
    val mediaUrl: String
)

data class ResolvedTikTokStream(
    val mediaUrl: String,
    val ext: String = "mp4",
    val userAgent: String = TikTokExtractor.DESKTOP_USER_AGENT,
    val formatId: String? = null
)

object TikTokExtractor {

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private val extractionCache = object : java.util.LinkedHashMap<String, Pair<Long, TikTokExtractionResult>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, TikTokExtractionResult>>): Boolean {
            return size > 50
        }
    }

    internal val REHYDRATION_SCRIPT_REGEX = Regex(
        """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>\s*(\{.*?\})\s*</script>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun getCached(url: String): TikTokExtractionResult? = synchronized(extractionCache) {
        val entry = extractionCache[url] ?: return null
        if (System.currentTimeMillis() - entry.first < CACHE_TTL_MS) {
            entry.second
        } else {
            extractionCache.remove(url)
            null
        }
    }

    private fun putCached(url: String, result: TikTokExtractionResult) = synchronized(extractionCache) {
        extractionCache[url] = System.currentTimeMillis() to result
    }

    suspend fun extract(url: String, forceRefresh: Boolean = false): EngineOperationResult<TikTokExtractionResult> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            getCached(url)?.let { return@withContext EngineOperationResult.Success(it) }
        }

        try {
            // 1. Extração primária via API TikWM (fornece links diretos HD sem marca d'água, SD e MP3)
            val tikwmResult = extractViaTikWm(url)
            if (tikwmResult != null) {
                putCached(url, tikwmResult)
                return@withContext EngineOperationResult.Success(tikwmResult)
            }

            // 2. Fallback via página web (__UNIVERSAL_DATA_FOR_REHYDRATION__, SIGI_STATE ou LD+JSON)
            val webResult = extractViaWebPage(url)
            if (webResult != null) {
                putCached(url, webResult)
                return@withContext EngineOperationResult.Success(webResult)
            }

            EngineOperationResult.ExtractionError("Não foi possível extrair os formatos de vídeo do TikTok.")
        } catch (e: Exception) {
            AppLogger.e("Erro na extração nativa do TikTok: ${e.message}", "TikTokExtractor", e)
            EngineOperationResult.ExtractionError(e.message ?: "Erro ao extrair vídeo do TikTok")
        }
    }

    suspend fun resolveStream(
        originalUrl: String,
        preferredFormatId: String?,
        downloadType: String? = "video",
        forceRefresh: Boolean = false
    ): ResolvedTikTokStream? = withContext(Dispatchers.IO) {
        val res = extract(originalUrl, forceRefresh = forceRefresh)
        if (res is EngineOperationResult.Success) {
            val formats = res.data.consolidatedFormats
            val isAudioRequest = downloadType.equals("audio", ignoreCase = true) ||
                downloadType.equals("Áudio", ignoreCase = true)

            if (isAudioRequest) {
                val audioFmt = formats.find { it.vcodec == "none" } ?: formats.firstOrNull()
                if (audioFmt?.url != null) {
                    return@withContext ResolvedTikTokStream(
                        mediaUrl = audioFmt.url,
                        ext = audioFmt.ext ?: "mp3",
                        formatId = audioFmt.formatId
                    )
                }
            }

            val chosen = (if (!preferredFormatId.isNullOrBlank()) {
                formats.find { it.formatId == preferredFormatId }
            } else null)
                ?: formats.filter { it.vcodec != "none" }.maxByOrNull { it.height }
                ?: formats.firstOrNull()

            if (chosen?.url != null) {
                return@withContext ResolvedTikTokStream(
                    mediaUrl = chosen.url,
                    ext = chosen.ext ?: "mp4",
                    formatId = chosen.formatId
                )
            }
        }
        null
    }

    suspend fun getFreshMediaUrl(
        url: String,
        preferredFormatId: String? = null,
        forceRefresh: Boolean = false
    ): String? = resolveStream(url, preferredFormatId, forceRefresh = forceRefresh)?.mediaUrl

    private fun extractViaTikWm(originalUrl: String): TikTokExtractionResult? {
        if (!NetworkSecurityUtils.isSafeExternalUrl(originalUrl)) return null
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(originalUrl, "UTF-8")
            val apiUrl = "https://www.tikwm.com/api/?url=$encoded&hd=1"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != 200) return null

            val body = conn.inputStream.use { NetworkSecurityUtils.readBoundedText(it) }
            parseTikWmJson(originalUrl, body)
        } catch (e: Exception) {
            AppLogger.w("TikWM fallback warning: ${e.message}", "TikTokExtractor")
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseTikWmJson(originalUrl: String, body: String): TikTokExtractionResult? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.optSafeInt("code", -1, minValue = -999) != 0) return null

            val data = json.optJSONObject("data") ?: return null
            val videoId = data.optCleanString("id", "tiktok_${System.currentTimeMillis()}")
            val rawTitle = data.optCleanString("title", "Vídeo do TikTok")
            val title = rawTitle.ifBlank { "Vídeo do TikTok" }
            val duration = data.optSafeInt("duration", 0, minValue = 0)
            val cover = data.optCleanString("origin_cover", data.optCleanString("cover", ""))
            val playCount = data.optSafeLong("play_count", 0L, minValue = 0L)
            val diggCount = data.optSafeLong("digg_count", 0L, minValue = 0L)

            val authorObj = data.optJSONObject("author")
            val author = authorObj?.optCleanString("nickname", authorObj.optCleanString("unique_id", "")) ?: ""

            val hdPlay = data.optCleanString("hdplay")
            val play = data.optCleanString("play")
            val wmPlay = data.optCleanString("wmplay")
            val music = data.optCleanString("music")

            val hdSize = data.optSafeLong("hd_size", 0L, minValue = 0L)
            val sdSize = data.optSafeLong("size", 0L, minValue = 0L)
            val wmSize = data.optSafeLong("wm_size", 0L, minValue = 0L)

            val formats = mutableListOf<VideoFormat>()

            val primaryVideoUrl = hdPlay.ifBlank { play.ifBlank { wmPlay } }
            if (primaryVideoUrl.isBlank() || (!primaryVideoUrl.startsWith("http://") && !primaryVideoUrl.startsWith("https://"))) {
                return null
            }

            if (hdPlay.isNotBlank() && hdPlay.startsWith("http")) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_hd_nowm",
                        url = hdPlay,
                        ext = "mp4",
                        width = 1080,
                        height = 1920,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "1080p Full HD Sem Marca d'Água (MP4)",
                        formatNote = "1080p",
                        filesize = hdSize,
                        tbr = if (duration > 0 && hdSize > 0) (hdSize * 8.0) / (duration * 1000.0) else 4000.0
                    )
                )
            }

            if (play.isNotBlank() && play.startsWith("http")) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_sd_nowm",
                        url = play,
                        ext = "mp4",
                        width = 576,
                        height = 1024,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "720p HD Sem Marca d'Água (MP4)",
                        formatNote = "720p",
                        filesize = sdSize,
                        tbr = if (duration > 0 && sdSize > 0) (sdSize * 8.0) / (duration * 1000.0) else 2000.0
                    )
                )
            }

            if (wmPlay.isNotBlank() && wmPlay.startsWith("http") && wmPlay != play) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_wm",
                        url = wmPlay,
                        ext = "mp4",
                        width = 540,
                        height = 960,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "540p Com Marca d'Água (MP4)",
                        formatNote = "540p",
                        filesize = wmSize,
                        tbr = if (duration > 0 && wmSize > 0) (wmSize * 8.0) / (duration * 1000.0) else 1500.0
                    )
                )
            }

            if (music.isNotBlank() && music.startsWith("http")) {
                formats.add(
                    VideoFormat(
                        formatId = "tiktok_audio",
                        url = music,
                        ext = "mp3",
                        width = 0,
                        height = 0,
                        fps = 0,
                        vcodec = "none",
                        acodec = "mp3",
                        format = "Audio Original (MP3)",
                        formatNote = "Audio 128K",
                        filesize = 0L,
                        tbr = 128.0,
                        abr = 128
                    )
                )
            }

            val fastInfo = FastVideoInfo(
                title = title,
                author = author,
                thumbnail = cover,
                url = originalUrl,
                durationSeconds = duration,
                viewCount = playCount,
                likeCount = diggCount,
                videoId = videoId
            )

            val videoInfo = VideoInfo(
                id = videoId,
                title = title,
                uploader = author,
                duration = duration,
                thumbnail = cover,
                webpageUrl = originalUrl,
                formats = formats
            )

            TikTokExtractionResult(
                fastInfo = fastInfo,
                videoInfo = videoInfo,
                consolidatedFormats = formats,
                mediaUrl = primaryVideoUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractViaWebPage(originalUrl: String): TikTokExtractionResult? {
        val opened = NetworkSecurityUtils.openSafeConnectionWithRedirects(
            startUrl = originalUrl,
            maxRedirects = 8,
            connectTimeoutMs = 10000,
            readTimeoutMs = 10000,
            headers = mapOf(
                "User-Agent" to DESKTOP_USER_AGENT,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            ),
            preserveCookies = true
        ) ?: return null

        val conn = opened.second
        return try {
            if (conn.responseCode !in 200..299) return null
            val html = conn.inputStream.use { NetworkSecurityUtils.readBoundedText(it) }
            parseWebHtml(originalUrl, html)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseWebHtml(originalUrl: String, html: String): TikTokExtractionResult? {
        if (html.isBlank()) return null

        // 1. Tentar __UNIVERSAL_DATA_FOR_REHYDRATION__
        val jsonStr = REHYDRATION_SCRIPT_REGEX.find(html)?.groupValues?.get(1)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val root = JSONObject(jsonStr)
                val itemStruct = root
                    .optJSONObject("__DEFAULT_SCOPE__")
                    ?.optJSONObject("webapp.video-detail")
                    ?.optJSONObject("itemInfo")
                    ?.optJSONObject("itemStruct")

                if (itemStruct != null) {
                    parseItemStruct(originalUrl, itemStruct)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Fallback SIGI_STATE (SSR alternativo do TikTok)
        val sigiState = JsonSafeUtils.extractSigiStateJson(html)
        if (sigiState != null) {
            val itemModule = sigiState.optJSONObject("ItemModule")
            if (itemModule != null) {
                val keys = itemModule.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val itemObj = itemModule.optJSONObject(key) ?: continue
                    parseItemStruct(originalUrl, itemObj)?.let { return it }
                }
            }
        }

        // 3. Fallback LD+JSON
        val ldVideo = JsonSafeUtils.extractLdJsonVideoObject(html)
        if (ldVideo != null) {
            val contentUrl = ldVideo.optCleanString("contentUrl")
            if (contentUrl.startsWith("http")) {
                val title = ldVideo.optCleanString("name", ldVideo.optCleanString("description", "Vídeo do TikTok"))
                val author = ldVideo.optJSONObject("author")?.optCleanString("name")
                    ?: ldVideo.optJSONObject("creator")?.optCleanString("name")
                    ?: ""
                val thumb = ldVideo.optJSONArray("thumbnailUrl")?.optString(0)?.takeIf { it != "null" }
                    ?: ldVideo.optCleanString("thumbnailUrl")
                val width = ldVideo.optSafeInt("width", 720, minValue = 1)
                val height = ldVideo.optSafeInt("height", 1280, minValue = 1)
                val duration = YoutubeDLUtils.parseIso8601Duration(ldVideo.optCleanString("duration"))
                val videoId = "tiktok_${System.currentTimeMillis()}"
                val formats = listOf(
                    VideoFormat(
                        formatId = "tiktok_web_mp4",
                        url = contentUrl,
                        ext = "mp4",
                        width = width,
                        height = height,
                        fps = 30,
                        vcodec = "h264",
                        acodec = "aac",
                        format = "${minOf(width, height)}p HD (MP4)",
                        formatNote = "${minOf(width, height)}p",
                        filesize = 0L,
                        tbr = 2500.0
                    )
                )
                return TikTokExtractionResult(
                    fastInfo = FastVideoInfo(
                        title = title,
                        author = author,
                        thumbnail = thumb,
                        url = originalUrl,
                        durationSeconds = duration,
                        videoId = videoId
                    ),
                    videoInfo = VideoInfo(
                        id = videoId,
                        title = title,
                        uploader = author,
                        duration = duration,
                        thumbnail = thumb,
                        webpageUrl = originalUrl,
                        formats = formats
                    ),
                    consolidatedFormats = formats,
                    mediaUrl = contentUrl
                )
            }
        }

        return null
    }

    private fun parseItemStruct(originalUrl: String, itemStruct: JSONObject): TikTokExtractionResult? {
        val videoObj = itemStruct.optJSONObject("video") ?: return null
        val playAddr = videoObj.optCleanString("playAddr", videoObj.optCleanString("downloadAddr", ""))
            .ifBlank {
                videoObj.optJSONObject("playAddr")?.optJSONArray("UrlList")?.optString(0)?.takeIf { it != "null" } ?: ""
            }
        if (playAddr.isBlank() || (!playAddr.startsWith("http://") && !playAddr.startsWith("https://"))) {
            return null
        }

        val videoId = itemStruct.optCleanString("id", "tiktok_${System.currentTimeMillis()}")
        val title = itemStruct.optCleanString("desc", "Vídeo do TikTok").ifBlank { "Vídeo do TikTok" }
        val authorObj = itemStruct.optJSONObject("author")
        val author = when {
            authorObj != null -> authorObj.optCleanString("nickname", authorObj.optCleanString("uniqueId", ""))
            else -> itemStruct.optCleanString("author", itemStruct.optCleanString("nickname", ""))
        }
        val cover = videoObj.optCleanString("originCover", videoObj.optCleanString("cover", ""))
        val duration = videoObj.optSafeInt("duration", 0, minValue = 0)
        val width = videoObj.optSafeInt("width", 720, minValue = 1)
        val height = videoObj.optSafeInt("height", 1280, minValue = 1)

        val stats = itemStruct.optJSONObject("stats")
        val playCount = stats?.optSafeLong("playCount", 0L, minValue = 0L) ?: 0L
        val diggCount = stats?.optSafeLong("diggCount", 0L, minValue = 0L) ?: 0L

        val formats = listOf(
            VideoFormat(
                formatId = "tiktok_web_mp4",
                url = playAddr,
                ext = "mp4",
                width = width,
                height = height,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "${minOf(width, height)}p HD (MP4)",
                formatNote = "${minOf(width, height)}p",
                filesize = 0L,
                tbr = 2500.0
            )
        )

        val fastInfo = FastVideoInfo(
            title = title,
            author = author,
            thumbnail = cover,
            url = originalUrl,
            durationSeconds = duration,
            viewCount = playCount,
            likeCount = diggCount,
            videoId = videoId
        )

        val videoInfo = VideoInfo(
            id = videoId,
            title = title,
            uploader = author,
            duration = duration,
            thumbnail = cover,
            webpageUrl = originalUrl,
            formats = formats
        )

        return TikTokExtractionResult(
            fastInfo = fastInfo,
            videoInfo = videoInfo,
            consolidatedFormats = formats,
            mediaUrl = playAddr
        )
    }
}
