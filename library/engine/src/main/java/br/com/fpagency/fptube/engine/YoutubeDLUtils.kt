package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
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

    @Volatile
    private var cachedVisitorData: String? = null

    private val fastInfoCache = object : java.util.LinkedHashMap<String, FastVideoInfo>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FastVideoInfo>): Boolean {
            return size > 100
        }
    }

    suspend fun fetchOEmbedMetadata(videoUrl: String): EngineOperationResult<FastVideoInfo> = withContext(Dispatchers.IO) {
        synchronized(fastInfoCache) {
            fastInfoCache[videoUrl]
        }?.let { return@withContext EngineOperationResult.Success(it) }

        try {
            val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

            val url = URL(oEmbedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val info = FastVideoInfo(
                    title = json.optString("title", "Vídeo do YouTube"),
                    author = json.optString("author_name", ""),
                    thumbnail = json.optString("thumbnail_url", ""),
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
        try {
            val conn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", VISIONOS_USER_AGENT)

            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20250220.01.00")
                    })
                })
                put("videoId", videoId)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val vd = json.optJSONObject("responseContext")?.optString("visitorData", "") ?: ""
                if (vd.isNotBlank()) {
                    cachedVisitorData = vd
                    return vd
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Falha ao buscar visitorData via InnerTube: ${e.message}", "YoutubeDLUtils")
        }

        // 2. Fallback via página watch
        try {
            val conn = URL("https://www.youtube.com/watch?v=$videoId&hl=en")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", VISIONOS_USER_AGENT)
            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val match = Regex(""""visitorData"\s*:\s*"([^"]+)"""").find(html)
                val vd = match?.groupValues?.get(1)
                if (!vd.isNullOrBlank()) {
                    cachedVisitorData = vd
                    return vd
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Falha ao buscar visitorData via watch page: ${e.message}", "YoutubeDLUtils")
        }

        return null
    }

    private data class InnerTubeExtraction(
        val videoId: String,
        val title: String,
        val uploader: String,
        val thumbnail: String,
        val duration: Int,
        val formats: List<VideoFormat>,
        val clientName: String
    )

    private fun extractViaInnerTubeVisionOs(videoId: String): InnerTubeExtraction? {
        for (attempt in 0..1) {
            try {
                if (attempt > 0) {
                    cachedVisitorData = null
                }
                val visitorData = getOrFetchVisitorData(videoId)

                val conn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", VISIONOS_USER_AGENT)
                conn.setRequestProperty("Origin", "https://www.youtube.com")
                if (!visitorData.isNullOrBlank()) {
                    conn.setRequestProperty("X-Goog-Visitor-Id", visitorData)
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

                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)

                val playability = json.optJSONObject("playabilityStatus")
                val status = playability?.optString("status", "") ?: ""
                if (status != "OK") {
                    AppLogger.w("InnerTube VISIONOS status=$status reason=${playability?.optString("reason")}", "YoutubeDLUtils")
                    continue
                }

                // Atualizar visitorData se o servidor enviou um novo
                json.optJSONObject("responseContext")?.optString("visitorData", "")?.takeIf { it.isNotBlank() }?.let {
                    cachedVisitorData = it
                }

                val videoDetails = json.optJSONObject("videoDetails")
                val title = videoDetails?.optString("title", "Vídeo do YouTube") ?: "Vídeo do YouTube"
                val author = videoDetails?.optString("author", "") ?: ""
                val duration = videoDetails?.optString("lengthSeconds", "0")?.toIntOrNull() ?: 0
                val thumbs = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
                } else {
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                }

                val streamingData = json.optJSONObject("streamingData") ?: continue
                val extracted = mutableListOf<VideoFormat>()

                fun parseFormatArray(arr: JSONArray?, isAdaptive: Boolean) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i) ?: continue
                        if (f.optBoolean("isDrc", false)) continue
                        val streamUrl = f.optString("url", "")
                        if (streamUrl.isBlank()) continue

                        val itag = f.optInt("itag", 0)
                        val mimeType = f.optString("mimeType", "video/mp4")
                        val containerPart = mimeType.substringBefore(";").trim().lowercase()
                        val codecsPart = Regex("""codecs="([^"]+)"""").find(mimeType)?.groupValues?.get(1)?.lowercase() ?: ""

                        val width = f.optInt("width", 0)
                        val height = f.optInt("height", 0)
                        val fps = f.optInt("fps", 0)
                        val bitrate = f.optLong("averageBitrate", f.optLong("bitrate", 0L))
                        val contentLength = f.optString("contentLength", "0").toLongOrNull() ?: 0L
                        val qualityLabel = f.optString("qualityLabel", if (height > 0) "${height}p" else "Audio")
                        val colorTransfer = f.optJSONObject("colorInfo")?.optString("transferCharacteristics", "") ?: ""
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
                    return InnerTubeExtraction(
                        videoId = videoId,
                        title = title,
                        uploader = author,
                        thumbnail = thumbUrl,
                        duration = duration,
                        formats = extracted,
                        clientName = "VISIONOS"
                    )
                }
            } catch (e: Exception) {
                AppLogger.w("InnerTube VISIONOS attempt $attempt failed: ${e.message}", "YoutubeDLUtils")
            }
        }
        return null
    }

    private fun consolidateYouTubeFormats(rawFormats: List<VideoFormat>): List<VideoFormat> {
        val videoFormats = rawFormats.filter { it.vcodec != "none" && it.height > 0 }
        val audioFormats = rawFormats.filter { it.vcodec == "none" }

        // Para cada resolução (height), selecionar o melhor formato (preferindo MP4 e SDR para máxima compatibilidade)
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

        // Para áudio, manter o melhor de cada extensão (m4a / webm)
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

    suspend fun fetchVideoFormats(url: String, useOAuth2: Boolean = false): EngineOperationResult<VideoInfo> = withContext(Dispatchers.IO) {
        val platform = PlatformDetector.detect(url)
        if (platform == Platform.YOUTUBE) {
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
                try {
                    val pipedUrl = "https://pipedapi.kavin.rocks/streams/$vId"
                    val conn = URL(pipedUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                    if (conn.responseCode == 200) {
                        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseBody)
                        videoTitle = json.optString("title", videoTitle)
                        videoUploader = json.optString("uploader", videoUploader)
                        videoThumbnail = json.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url") ?: videoThumbnail
                        videoDuration = json.optInt("duration", videoDuration)

                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null) {
                            for (i in 0 until videoStreams.length()) {
                                val s = videoStreams.optJSONObject(i) ?: continue
                                val sUrl = s.optString("url")
                                if (sUrl.isBlank()) continue
                                val quality = s.optString("quality", "720p")
                                val width = s.optInt("width", 1280)
                                val height = s.optInt("height", 720)
                                val fps = s.optInt("fps", 30)
                                val codec = s.optString("codec", "h264")
                                val ext = s.optString("format", "mp4").lowercase()
                                val bitrate = s.optLong("bitrate", 0L)
                                val filesize = s.optLong("contentLength", 0L)

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
                }
            }

            // 3. Fallback CloudWorker para metadados/formatos caso necessário
            if (extractedFormats.isEmpty()) {
                try {
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    val workerUrl = "https://fptube-cache.fpagency.com.br/?url=$encodedUrl"
                    val conn = URL(workerUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                    if (conn.responseCode == 200) {
                        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseBody)
                        if (json.optBoolean("success")) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                videoTitle = data.optString("title", videoTitle)
                                videoUploader = data.optString("uploader", data.optString("channelTitle", videoUploader))
                                videoThumbnail = data.optString("thumbnail", videoThumbnail)
                                videoDuration = data.optInt("duration", data.optInt("durationSeconds", videoDuration))
                                videoId = data.optString("id", videoId)

                                val rawFormats = data.optJSONArray("formats")
                                if (rawFormats != null) {
                                    for (i in 0 until rawFormats.length()) {
                                        val fObj = rawFormats.optJSONObject(i) ?: continue
                                        val fUrl = fObj.optString("url")
                                        if (fUrl.isBlank()) continue
                                        val formatId = fObj.optString("format_id", "fmt_$i")
                                        val ext = fObj.optString("ext", "mp4")
                                        val width = fObj.optInt("width", 1280)
                                        val height = fObj.optInt("height", 720)
                                        val fps = fObj.optInt("fps", 30)
                                        val vcodec = fObj.optString("vcodec", "h264")
                                        val acodec = fObj.optString("acodec", "aac")
                                        val formatNote = fObj.optString("format_note", "${height}p")
                                        val formatStr = fObj.optString("format", "$formatNote ($ext)")
                                        val filesize = fObj.optLong("filesize", 0L)
                                        val tbr = fObj.optDouble("tbr", 0.0)

                                        extractedFormats.add(
                                            VideoFormat(
                                                formatId = formatId,
                                                url = fUrl,
                                                ext = ext,
                                                width = width,
                                                height = height,
                                                fps = fps,
                                                vcodec = vcodec,
                                                acodec = acodec,
                                                format = formatStr,
                                                formatNote = formatNote,
                                                filesize = filesize,
                                                tbr = tbr
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("Cloud worker format fetch fail: ${e.message}", "YoutubeDLUtils")
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
            return@withContext EngineOperationResult.Success(info, extraData = Pair(consolidatedFormats, winningClient))
        }

        if (platform == Platform.KWAI) {
            val kwaiResult = KwaiExtractor.extract(url)
            if (kwaiResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(kwaiResult.data.videoInfo)
            }
        }
        if (platform == Platform.SNACKVIDEO) {
            val snackResult = SnackVideoExtractor.extract(url)
            if (snackResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(snackResult.data.videoInfo)
            }
        }
        if (platform == Platform.INSTAGRAM) {
            val igResult = InstagramExtractor.extract(url)
            if (igResult is EngineOperationResult.Success) {
                return@withContext EngineOperationResult.Success(
                    igResult.data.videoInfo,
                    extraData = Pair(igResult.data.consolidatedFormats, "InstagramNative")
                )
            }
        }
        if (platform == Platform.TIKTOK) {
            val ttResult = TikTokExtractor.extract(url)
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
        downloadType: String? = "video"
    ): ResolvedYouTubeStream? = withContext(Dispatchers.IO) {
        try {
            val result = fetchVideoFormats(originalUrl)
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

                // Seleção de formato de vídeo
                val chosenVideo: VideoFormat? = when (preferredFormatId) {
                    "yt_4k" -> {
                        // Para o placeholder yt_4k (MP4), prioriza o melhor stream MP4 (ex: 1080p60 H264/AV1) para muxing rápido e compatível
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
            AppLogger.e("Erro ao resolver stream do YouTube: ${e.message}", "YoutubeDLUtils")
        }
        null
    }

    suspend fun getFreshYouTubeMediaUrl(originalUrl: String, preferredFormatId: String?): String? =
        resolveYouTubeStream(originalUrl, preferredFormatId)?.mediaUrl

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

    private fun extractYouTubeVideoId(url: String): String? {
        val regex = Regex("""(?:v=|\/shorts\/|\/live\/|youtu\.be\/|\/embed\/|\/v\/|vi\/|e\/|watch\?v=|&v=)([^#\&\?]{11})""")
        return regex.find(url)?.groupValues?.get(1)
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
                durationStr.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
