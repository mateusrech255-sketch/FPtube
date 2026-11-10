package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeInt
import br.com.fpagency.fptube.engine.JsonSafeUtils.optSafeLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ResolvedYouTubeStream(
    val mediaUrl: String,
    val audioUrl: String? = null,
    val ext: String = "mp4",
    val userAgent: String = YoutubeDLUtils.VISIONOS_USER_AGENT,
    val formatId: String? = null
)

object YoutubeDLUtils {

    const val VISIONOS_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"

    private const val FORMATS_CACHE_TTL_MS = 5 * 60 * 1000L

    internal val VISITOR_DATA_REGEX = Regex(""""visitorData"\s*:\s*"([^"]+)"""")
    internal val CODECS_REGEX = Regex("""codecs="([^"]+)"""")
    internal val YOUTUBE_VIDEO_ID_REGEX = Regex("""(?:v=|\/shorts\/|\/live\/|youtu\.be\/|\/embed\/|\/v\/|vi\/|e\/|watch\?v=|&v=)([^#\&\?]{11})""")

    @Volatile
    private var cachedVisitorData: String? = null

    private val fastInfoCache = object : java.util.LinkedHashMap<String, FastVideoInfo>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FastVideoInfo>): Boolean {
            return size > 100
        }
    }

    private val formatsCache = object : java.util.LinkedHashMap<String, Pair<Long, EngineOperationResult.Success<VideoInfo>>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, EngineOperationResult.Success<VideoInfo>>>): Boolean {
            return size > 50
        }
    }

    suspend fun fetchOEmbedMetadata(videoUrl: String): EngineOperationResult<FastVideoInfo> = withContext(Dispatchers.IO) {
        synchronized(fastInfoCache) {
            fastInfoCache[videoUrl]
        }?.let { return@withContext EngineOperationResult.Success(it) }

        var connection: HttpURLConnection? = null
        try {
            val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

            val url = URL(oEmbedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (connection.responseCode == 200) {
                val response = NetworkSecurityUtils.readBoundedText(connection.inputStream)
                val json = JSONObject(response)
                val info = FastVideoInfo(
                    title = json.optCleanString("title", "Vídeo do YouTube"),
                    author = json.optCleanString("author_name", ""),
                    thumbnail = json.optCleanString("thumbnail_url", ""),
                    url = videoUrl
                )
                synchronized(fastInfoCache) {
                    fastInfoCache[videoUrl] = info
                }
                EngineOperationResult.Success(info)
            } else {
                EngineOperationResult.ExtractionError("Erro HTTP ${connection.responseCode} no oEmbed")
            }
        } catch (e: Exception) {
            AppLogger.w("oEmbed fail: ${e.message}", "YoutubeDLUtils")
            EngineOperationResult.ExtractionError(e.message ?: "Erro desconhecido")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchGenericMetadata(videoUrl: String): EngineOperationResult<FastVideoInfo> = withContext(Dispatchers.IO) {
        synchronized(fastInfoCache) {
            fastInfoCache[videoUrl]
        }?.let { return@withContext EngineOperationResult.Success(it) }

        val platform = PlatformDetector.detect(videoUrl)
        if (platform == Platform.KWAI) {
            val kwaiResult = KwaiExtractor.extract(videoUrl)
            if (kwaiResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(kwaiResult.data.fastInfo)
            }
        }

        if (platform == Platform.SNACKVIDEO) {
            val snackResult = SnackVideoExtractor.extract(videoUrl)
            if (snackResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(snackResult.data.fastInfo)
            }
        }

        if (platform == Platform.INSTAGRAM) {
            val igResult = InstagramExtractor.extract(videoUrl)
            if (igResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(igResult.data.fastInfo)
            }
        }

        if (platform == Platform.TIKTOK) {
            val ttResult = TikTokExtractor.extract(videoUrl)
            if (ttResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(ttResult.data.fastInfo)
            }
        }

        return@withContext fetchOEmbedMetadata(videoUrl)
    }

    private fun getOrFetchVisitorData(videoId: String): String? {
        cachedVisitorData?.takeIf { it.isNotBlank() }?.let { return it }

        // 1. Obter visitorData rapidamente via InnerTube responseContext
        var conn1: HttpURLConnection? = null
        try {
            conn1 = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", VISIONOS_USER_AGENT)
            }

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20250220.01.00")
                    })
                })
                put("videoId", videoId)
            }
            conn1.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (conn1.responseCode == 200) {
                val body = NetworkSecurityUtils.readBoundedText(conn1.inputStream)
                val json = JSONObject(body)
                val vd = json.optJSONObject("responseContext")?.optCleanString("visitorData", "") ?: ""
                if (vd.isNotBlank()) {
                    cachedVisitorData = vd
                    return vd
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Falha ao buscar visitorData via InnerTube: ${e.message}", "YoutubeDLUtils")
        } finally {
            conn1?.disconnect()
        }

        // 2. Fallback via página watch
        var conn2: HttpURLConnection? = null
        try {
            conn2 = (URL("https://www.youtube.com/watch?v=$videoId&hl=en")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", VISIONOS_USER_AGENT)
            }
            if (conn2.responseCode == 200) {
                val html = NetworkSecurityUtils.readBoundedText(conn2.inputStream)
                val match = VISITOR_DATA_REGEX.find(html)
                val vd = match?.groupValues?.get(1)
                if (!vd.isNullOrBlank()) {
                    cachedVisitorData = vd
                    return vd
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Falha ao buscar visitorData via watch page: ${e.message}", "YoutubeDLUtils")
        } finally {
            conn2?.disconnect()
        }

        return null
    }

    internal data class InnerTubeExtraction(
        val videoId: String,
        val title: String,
        val uploader: String,
        val thumbnail: String,
        val duration: Int,
        val formats: List<VideoFormat>,
        val clientName: String
    )

    internal fun parseInnerTubePlayerJson(videoId: String, responseBody: String): InnerTubeExtraction? {
        if (responseBody.isBlank()) return null
        return try {
            val json = JSONObject(responseBody)

            val playability = json.optJSONObject("playabilityStatus")
            val status = playability?.optCleanString("status", "") ?: ""
            if (status != "OK") {
                return null
            }

            json.optJSONObject("responseContext")?.optCleanString("visitorData", "")?.takeIf { it.isNotBlank() }?.let {
                cachedVisitorData = it
            }

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optCleanString("title", "Vídeo do YouTube") ?: "Vídeo do YouTube"
            val author = videoDetails?.optCleanString("author", "") ?: ""
            val duration = videoDetails?.optSafeInt("lengthSeconds", 0, minValue = 0) ?: 0
            val thumbs = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            var thumbUrl = ""
            if (thumbs != null && thumbs.length() > 0) {
                for (i in thumbs.length() - 1 downTo 0) {
                    val candidate = thumbs.optJSONObject(i)?.optCleanString("url", "") ?: ""
                    if (candidate.startsWith("http")) {
                        thumbUrl = candidate
                        break
                    }
                }
            }
            if (thumbUrl.isBlank()) {
                thumbUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val extracted = mutableListOf<VideoFormat>()

            fun parseFormatArray(arr: JSONArray?, isAdaptive: Boolean) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    if (f.optBoolean("isDrc", false)) continue
                    val streamUrl = f.optCleanString("url", "")
                    if (streamUrl.isBlank() || (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://"))) continue

                    val itag = f.optSafeInt("itag", 0, minValue = 0)
                    val mimeType = f.optCleanString("mimeType", "video/mp4")
                    val containerPart = mimeType.substringBefore(";").trim().lowercase()
                    val codecsPart = CODECS_REGEX.find(mimeType)?.groupValues?.get(1)?.lowercase() ?: ""

                    val width = f.optSafeInt("width", 0, minValue = 0)
                    val height = f.optSafeInt("height", 0, minValue = 0)
                    val fps = f.optSafeInt("fps", 0, minValue = 0)
                    val bitrate = f.optSafeLong("averageBitrate", f.optSafeLong("bitrate", 0L, minValue = 0L), minValue = 0L)
                    val contentLength = f.optSafeLong("contentLength", 0L, minValue = 0L)
                    val qualityLabel = f.optCleanString("qualityLabel", if (height > 0) "${height}p" else "Audio")
                    val colorTransfer = f.optJSONObject("colorInfo")?.optCleanString("transferCharacteristics", "") ?: ""
                    val isHdr = colorTransfer.contains("SMPTEST2084") ||
                        colorTransfer.contains("ARIB") ||
                        qualityLabel.contains("HDR", ignoreCase = true)

                    val isAudioOnly = containerPart.startsWith("audio/") || (height <= 0 && width <= 0)
                    val ext = when {
                        isAudioOnly && containerPart.contains("mp4") -> "m4a"
                        isAudioOnly && containerPart.contains("webm") -> "webm"
                        containerPart.contains("webm") -> "webm"
                        else -> "mp4"
                    }

                    val vcodec = when {
                        isAudioOnly -> "none"
                        codecsPart.contains("av01") -> "av01"
                        codecsPart.contains("vp9") || codecsPart.contains("vp09") -> "vp9"
                        else -> "h264"
                    }

                    val acodec = when {
                        !isAdaptive && !isAudioOnly -> "aac"
                        !isAudioOnly -> "none"
                        codecsPart.contains("opus") -> "opus"
                        else -> "aac"
                    }

                    val tbrKbps = if (bitrate > 0) bitrate.toDouble() / 1000.0 else 0.0
                    val abrKbps = if (isAudioOnly && bitrate > 0) (bitrate / 1000L).toInt() else null
                    val hdrSuffix = if (isHdr) " HDR" else ""

                    extracted.add(
                        VideoFormat(
                            formatId = if (itag > 0) itag.toString() else "yt_${height}p_$i",
                            url = streamUrl,
                            ext = ext,
                            width = width,
                            height = height,
                            fps = fps,
                            vcodec = vcodec,
                            acodec = acodec,
                            format = "$qualityLabel$hdrSuffix (${ext.uppercase()})",
                            formatNote = if (isAudioOnly) "Audio ${abrKbps ?: 128}K" else "${height}p",
                            filesize = contentLength,
                            tbr = tbrKbps,
                            abr = abrKbps
                        )
                    )
                }
            }

            parseFormatArray(streamingData.optJSONArray("formats"), isAdaptive = false)
            parseFormatArray(streamingData.optJSONArray("adaptiveFormats"), isAdaptive = true)

            if (extracted.isNotEmpty()) {
                InnerTubeExtraction(
                    videoId = videoId,
                    title = title,
                    uploader = author,
                    thumbnail = thumbUrl,
                    duration = duration,
                    formats = extracted,
                    clientName = "VISIONOS"
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractViaInnerTubeVisionOs(videoId: String): InnerTubeExtraction? {
        for (attempt in 0..1) {
            var conn: HttpURLConnection? = null
            try {
                if (attempt > 0) {
                    cachedVisitorData = null
                }
                val visitorData = getOrFetchVisitorData(videoId)

                conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", VISIONOS_USER_AGENT)
                    setRequestProperty("Origin", "https://www.youtube.com")
                    if (!visitorData.isNullOrBlank()) {
                        setRequestProperty("X-Goog-Visitor-Id", visitorData)
                    }
                }

                val clientObj = JSONObject().apply {
                    put("clientName", "VISIONOS")
                    put("clientVersion", "0.1")
                    put("deviceMake", "Apple")
                    put("deviceModel", "RealityDevice14,1")
                    put("osName", "visionOS")
                    put("osVersion", "1.3.21O771")
                    put("hl", "pt")
                    put("gl", "BR")
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                }
                val bodyJson = JSONObject().apply {
                    put("context", JSONObject().apply { put("client", clientObj) })
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode != 200) continue

                val responseBody = NetworkSecurityUtils.readBoundedText(conn.inputStream)
                val parsed = parseInnerTubePlayerJson(videoId, responseBody)
                if (parsed != null) return parsed
            } catch (e: Exception) {
                AppLogger.w("InnerTube VISIONOS attempt $attempt failed: ${e.message}", "YoutubeDLUtils")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    internal fun consolidateYouTubeFormats(rawFormats: List<VideoFormat>): List<VideoFormat> {
        val videoFormats = rawFormats.filter { it.vcodec != "none" && it.height > 0 }
        val audioFormats = rawFormats.filter { it.vcodec == "none" }

        val bestVideosByHeight = videoFormats
            .groupBy { it.height }
            .mapNotNull { (_, group) ->
                group.sortedWith(
                    compareByDescending<VideoFormat> { it.ext == "mp4" }
                        .thenByDescending { !(it.format?.contains("HDR", ignoreCase = true) ?: false) }
                        .thenByDescending { it.acodec != null && it.acodec != "none" }
                        .thenByDescending { it.fps }
                        .thenByDescending { it.tbr ?: 0.0 }
                ).firstOrNull()
            }
            .sortedByDescending { it.height }

        val bestAudios = audioFormats
            .groupBy { it.ext ?: "m4a" }
            .mapNotNull { (_, group) ->
                group.maxByOrNull { it.tbr ?: 0.0 }
            }
            .sortedWith(
                compareByDescending<VideoFormat> { it.ext == "m4a" }
                    .thenByDescending { it.tbr ?: 0.0 }
            )

        return bestVideosByHeight + bestAudios
    }

    suspend fun fetchVideoFormats(
        url: String,
        useOAuth2: Boolean = false,
        forceRefresh: Boolean = false
    ): EngineOperationResult<VideoInfo> = withContext(Dispatchers.IO) {
        if (!NetworkSecurityUtils.isSafeExternalUrl(url)) {
            return@withContext EngineOperationResult.ExtractionError("URL bloqueada por política de segurança.")
        }
        val platform = PlatformDetector.detect(url)
        if (platform == Platform.YOUTUBE) {
            if (!forceRefresh) {
                synchronized(formatsCache) {
                    val cached = formatsCache[url]
                    if (cached != null && System.currentTimeMillis() - cached.first < FORMATS_CACHE_TTL_MS) {
                        val hasDirectFormats = cached.second.data.formats?.any { isDirectMediaUrl(it.url) } == true
                        if (hasDirectFormats) {
                            return@withContext cached.second
                        }
                    }
                }
            } else {
                synchronized(formatsCache) {
                    formatsCache.remove(url)
                }
            }

            val extractedFormats = mutableListOf<VideoFormat>()
            var videoTitle = "Vídeo do YouTube"
            var videoUploader = ""
            var videoThumbnail = ""
            var videoDuration = 0
            val vId = extractYouTubeVideoId(url)
            var videoId = vId ?: "video_${System.currentTimeMillis()}"
            var winningClient = "VISIONOS"

            // 1. Extração primária via InnerTube (VISIONOS)
            if (vId != null) {
                val innerTube = extractViaInnerTubeVisionOs(vId)
                if (innerTube != null && innerTube.formats.isNotEmpty()) {
                    videoTitle = innerTube.title
                    videoUploader = innerTube.uploader
                    videoThumbnail = innerTube.thumbnail
                    videoDuration = innerTube.duration
                    videoId = innerTube.videoId
                    winningClient = innerTube.clientName
                    extractedFormats.addAll(innerTube.formats)
                }
            }

            // 2. Fallback Piped API se InnerTube falhar
            if (extractedFormats.isEmpty() && vId != null) {
                var conn: HttpURLConnection? = null
                try {
                    val pipedUrl = "https://pipedapi.kavin.rocks/streams/$vId"
                    conn = (URL(pipedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }

                    if (conn.responseCode == 200) {
                        val responseBody = NetworkSecurityUtils.readBoundedText(conn.inputStream)
                        val json = JSONObject(responseBody)
                        videoTitle = json.optCleanString("title", videoTitle)
                        videoUploader = json.optCleanString("uploader", videoUploader)
                        videoThumbnail = json.optJSONArray("thumbnails")?.optJSONObject(0)?.optCleanString("url", videoThumbnail) ?: videoThumbnail
                        videoDuration = json.optSafeInt("duration", videoDuration, minValue = 0)

                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null) {
                            for (i in 0 until videoStreams.length()) {
                                val s = videoStreams.optJSONObject(i) ?: continue
                                val sUrl = s.optCleanString("url")
                                if (sUrl.isBlank()) continue
                                val quality = s.optCleanString("quality", "720p")
                                val width = s.optSafeInt("width", 1280, minValue = 1)
                                val height = s.optSafeInt("height", 720, minValue = 1)
                                val fps = s.optSafeInt("fps", 30, minValue = 1)
                                val codec = s.optCleanString("codec", "h264")
                                val ext = s.optCleanString("format", "mp4").lowercase()
                                val bitrate = s.optSafeLong("bitrate", 0L, minValue = 0L)
                                val filesize = s.optSafeLong("contentLength", 0L, minValue = 0L)

                                extractedFormats.add(
                                    VideoFormat(
                                        formatId = "piped_v_$i",
                                        url = sUrl,
                                        ext = if (ext.contains("webm")) "webm" else "mp4",
                                        width = width,
                                        height = height,
                                        fps = fps,
                                        vcodec = if (codec.contains("vp9")) "vp9" else if (codec.contains("av01")) "av01" else "h264",
                                        acodec = "none",
                                        format = "$quality ($ext)",
                                        formatNote = quality,
                                        filesize = filesize,
                                        tbr = bitrate.toDouble() / 1024.0
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("Piped API fail: ${e.message}", "YoutubeDLUtils")
                } finally {
                    conn?.disconnect()
                }
            }

            val oEmbed = fetchOEmbedMetadata(url)
            if (oEmbed is EngineOperationResult.Success) {
                if (videoTitle == "Vídeo do YouTube") videoTitle = oEmbed.data.title
                if (videoUploader.isBlank()) videoUploader = oEmbed.data.author
                if (videoThumbnail.isBlank()) videoThumbnail = oEmbed.data.thumbnail
            }

            val allFormats: List<VideoFormat>
            val consolidatedFormats: List<VideoFormat>
            if (extractedFormats.isNotEmpty()) {
                allFormats = extractedFormats
                consolidatedFormats = consolidateYouTubeFormats(extractedFormats)
            } else {
                val guaranteed = getGuaranteedYouTubeFormats(url)
                allFormats = guaranteed
                consolidatedFormats = guaranteed
                winningClient = "Fallback"
            }

            val info = VideoInfo(
                id = videoId,
                title = videoTitle,
                uploader = videoUploader,
                thumbnail = videoThumbnail.ifBlank { "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" },
                duration = videoDuration,
                webpageUrl = url,
                formats = allFormats
            )
            val successResult = EngineOperationResult.Success(info, extraData = Pair(consolidatedFormats, winningClient))
            if (extractedFormats.isNotEmpty()) {
                synchronized(formatsCache) {
                    formatsCache[url] = System.currentTimeMillis() to successResult
                }
            }
            return@withContext successResult
        }

        if (platform == Platform.KWAI) {
            val kwaiResult = KwaiExtractor.extract(url, forceRefresh = forceRefresh)
            if (kwaiResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(kwaiResult.data.videoInfo)
            }
        }
        if (platform == Platform.SNACKVIDEO) {
            val snackResult = SnackVideoExtractor.extract(url, forceRefresh = forceRefresh)
            if (snackResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(snackResult.data.videoInfo)
            }
        }
        if (platform == Platform.INSTAGRAM) {
            val igResult = InstagramExtractor.extract(url, forceRefresh = forceRefresh)
            if (igResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(
                    igResult.data.videoInfo,
                    extraData = Pair(igResult.data.consolidatedFormats, "InstagramNative")
                )
            }
        }
        if (platform == Platform.TIKTOK) {
            val ttResult = TikTokExtractor.extract(url, forceRefresh = forceRefresh)
            if (ttResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(
                    ttResult.data.videoInfo,
                    extraData = Pair(ttResult.data.consolidatedFormats, "TikTokNative")
                )
            }
        }

        val oEmbed = fetchOEmbedMetadata(url)
        if (oEmbed is EngineOperationResult.Success) {
            val info = VideoInfo(
                id = "video_${System.currentTimeMillis()}",
                title = oEmbed.data.title,
                uploader = oEmbed.data.author,
                thumbnail = oEmbed.data.thumbnail,
                webpageUrl = url,
                formats = listOf(
                    VideoFormat(
                        formatId = "default_mp4",
                        url = url,
                        ext = "mp4",
                        format = "HD (MP4)",
                        formatNote = "720p",
                        width = 1280,
                        height = 720
                    )
                )
            )
            return@withContext EngineOperationResult.Success(info)
        }

        EngineOperationResult.ExtractionError("Não foi possível extrair formatos para esta URL.")
    }

    private fun isDirectMediaUrl(candidateUrl: String?): Boolean {
        if (candidateUrl.isNullOrBlank()) return false
        val lower = candidateUrl.lowercase()
        return !lower.contains("youtube.com/watch") &&
            !lower.contains("youtu.be/") &&
            !lower.contains("youtube.com/shorts/") &&
            !lower.contains("youtube.com/live/")
    }

    suspend fun resolveYouTubeStream(
        originalUrl: String,
        preferredFormatId: String?,
        downloadType: String? = "video",
        forceRefresh: Boolean = false
    ): ResolvedYouTubeStream? = withContext(Dispatchers.IO) {
        try {
            val result = fetchVideoFormats(originalUrl, forceRefresh = forceRefresh)
            if (result is EngineOperationResult.Success) {
                val formats = result.data.formats ?: emptyList()
                val validFormats = formats.filter { isDirectMediaUrl(it.url) }
                if (validFormats.isEmpty()) return@withContext null

                val videoFormats = validFormats.filter { it.vcodec != "none" && it.height > 0 }
                val audioFormats = validFormats.filter { it.vcodec == "none" }

                val isAudioRequest = downloadType.equals("audio", ignoreCase = true) ||
                    downloadType.equals("Áudio", ignoreCase = true)

                if (isAudioRequest) {
                    val chosenAudio = (if (preferredFormatId != null) {
                        audioFormats.find { it.formatId == preferredFormatId }
                    } else null)
                        ?: audioFormats.filter { it.ext == "m4a" }.maxByOrNull { it.tbr ?: 0.0 }
                        ?: audioFormats.maxByOrNull { it.tbr ?: 0.0 }
                        ?: validFormats.firstOrNull()

                    if (chosenAudio?.url != null) {
                        return@withContext ResolvedYouTubeStream(
                            mediaUrl = chosenAudio.url,
                            audioUrl = null,
                            ext = chosenAudio.ext ?: "m4a",
                            formatId = chosenAudio.formatId
                        )
                    }
                    return@withContext null
                }

                val chosenVideo: VideoFormat? = when (preferredFormatId) {
                    "yt_4k" -> {
                        videoFormats.filter { it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.maxByOrNull { it.height }
                    }
                    "yt_1080p" -> {
                        videoFormats.filter { it.height in 1000..1920 && it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.filter { it.height in 1000..1920 }.maxByOrNull { it.height }
                    }
                    "yt_720p" -> {
                        videoFormats.filter { it.height in 700..900 && it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.filter { it.height in 700..900 }.maxByOrNull { it.height }
                    }
                    "yt_360p" -> {
                        videoFormats.filter { it.height in 300..480 && it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.filter { it.height in 300..480 }.maxByOrNull { it.height }
                    }
                    null, "", "best", "0", "unknown" -> {
                        videoFormats.filter { it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.maxByOrNull { it.height }
                    }
                    else -> {
                        videoFormats.find { it.formatId == preferredFormatId }
                            ?: validFormats.find { it.formatId == preferredFormatId }
                            ?: videoFormats.filter { it.ext == "mp4" }.maxByOrNull { it.height }
                            ?: videoFormats.maxByOrNull { it.height }
                    }
                }

                if (chosenVideo?.url != null) {
                    val videoExt = chosenVideo.ext ?: "mp4"
                    val needsAudioTrack = chosenVideo.acodec == null || chosenVideo.acodec == "none"
                    val matchingAudio = if (needsAudioTrack) {
                        if (videoExt == "webm") {
                            audioFormats.filter { it.ext == "webm" }.maxByOrNull { it.tbr ?: 0.0 }
                                ?: audioFormats.maxByOrNull { it.tbr ?: 0.0 }
                        } else {
                            audioFormats.filter { it.ext == "m4a" }.maxByOrNull { it.tbr ?: 0.0 }
                                ?: audioFormats.maxByOrNull { it.tbr ?: 0.0 }
                        }
                    } else null

                    return@withContext ResolvedYouTubeStream(
                        mediaUrl = chosenVideo.url,
                        audioUrl = matchingAudio?.url,
                        ext = videoExt,
                        formatId = chosenVideo.formatId
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Erro ao resolver stream do YouTube: ${e.message}", "YoutubeDLUtils", e)
        }
        null
    }

    suspend fun getFreshYouTubeMediaUrl(
        originalUrl: String,
        preferredFormatId: String?,
        forceRefresh: Boolean = false
    ): String? =
        resolveYouTubeStream(originalUrl, preferredFormatId, forceRefresh = forceRefresh)?.mediaUrl

    private fun getGuaranteedYouTubeFormats(webpageUrl: String): List<VideoFormat> {
        return listOf(
            VideoFormat(
                formatId = "yt_4k",
                url = webpageUrl,
                ext = "mp4",
                width = 3840,
                height = 2160,
                fps = 60,
                vcodec = "av01",
                acodec = "aac",
                format = "4K Ultra HD (MP4)",
                formatNote = "2160p",
                filesize = 0L,
                tbr = 15000.0
            ),
            VideoFormat(
                formatId = "yt_1080p",
                url = webpageUrl,
                ext = "mp4",
                width = 1920,
                height = 1080,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "Full HD (MP4)",
                formatNote = "1080p",
                filesize = 0L,
                tbr = 5000.0
            ),
            VideoFormat(
                formatId = "yt_720p",
                url = webpageUrl,
                ext = "mp4",
                width = 1280,
                height = 720,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "HD (MP4)",
                formatNote = "720p",
                filesize = 0L,
                tbr = 2500.0
            ),
            VideoFormat(
                formatId = "yt_360p",
                url = webpageUrl,
                ext = "mp4",
                width = 640,
                height = 360,
                fps = 30,
                vcodec = "h264",
                acodec = "aac",
                format = "SD (MP4)",
                formatNote = "360p",
                filesize = 0L,
                tbr = 1000.0
            )
        )
    }

    internal fun extractYouTubeVideoId(url: String): String? {
        return YOUTUBE_VIDEO_ID_REGEX.find(url)?.groupValues?.get(1)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun parseIso8601Duration(durationStr: String?): Int {
        if (durationStr.isNullOrBlank()) return 0
        return try {
            if (durationStr.startsWith("PT", ignoreCase = true)) {
                var hours = 0
                var minutes = 0
                var seconds = 0
                var currentNumStr = ""
                for (char in durationStr) {
                    if (char.isDigit()) {
                        currentNumStr += char
                    } else {
                        val num = currentNumStr.toIntOrNull() ?: 0
                        currentNumStr = ""
                        when (char.uppercaseChar()) {
                            'H' -> hours = num
                            'M' -> minutes = num
                            'S' -> seconds = num
                        }
                    }
                }
                hours * 3600 + minutes * 60 + seconds
            } else {
                durationStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
