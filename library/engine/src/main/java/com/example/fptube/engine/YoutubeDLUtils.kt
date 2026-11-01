package br.com.fpagency.fptube.engine

import android.util.Log
import br.com.fpagency.fptube.core.AppLogger
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.LruCache
import br.com.fpagency.fptube.core.BuildConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.UnknownHostException
import java.net.SocketTimeoutException

data class FastVideoInfo(
    val title: String,
    val author: String,
    val thumbnail: String,
    val url: String,
    val durationSeconds: Int = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val videoId: String? = null
)

object YoutubeDLUtils {

    private val fastInfoCache = LruCache<String, FastVideoInfo>(100)

    /**
     * Chave composta para identificar fluxos idênticos entre diferentes manifestos
     */
    private data class FormatKey(
        val w: Int,
        val h: Int,
        val fps: Int,
        val vcodec: String?,
        val acodec: String?
    )

    /**
     * Busca metadados via oEmbed (Fallback de segurança)
     */
    suspend fun fetchOEmbedMetadata(videoUrl: String): EngineOperationResult<FastVideoInfo> = withContext(Dispatchers.IO) {
        fastInfoCache.get(videoUrl)?.let { return@withContext EngineOperationResult.Success(it) }

        try {
            val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"
            
            val url = URL(oEmbedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val info = FastVideoInfo(
                    title = json.optString("title", "Vídeo do YouTube"),
                    author = json.optString("author_name", ""),
                    thumbnail = json.optString("thumbnail_url", ""),
                    url = videoUrl
                )
                fastInfoCache.put(videoUrl, info)
                EngineOperationResult.Success(info)
            } else {
                EngineOperationResult.ExtractionError("Erro HTTP ${connection.responseCode} no oEmbed")
            }
        } catch (e: Exception) {
            AppLogger.w("oEmbed fail: ${e.message}", "YoutubeDL")
            categorizeException(e)
        }
    }

    private suspend fun getInfoWithTimeout(request: YoutubeDLRequest, timeoutMs: Long = 40000L): VideoInfo = coroutineScope {
        val processId = "info_${System.currentTimeMillis()}_${(1000..9999).random()}"
        request.addOption("--dump-json")
        
        val deferred = async<VideoInfo>(Dispatchers.IO) {
            val response = YoutubeDL.getInstance().execute(request, processId)
            YoutubeDL.getInstance().objectMapper.readValue(response.out, VideoInfo::class.java)
        }

        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: Throwable) {
            deferred.cancel()
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (ignored: Exception) { /* silent */ }
            throw e
        }
    }

    /**
     * Busca metadados genéricos via yt-dlp para qualquer plataforma suportada
     */
    suspend fun fetchGenericMetadata(videoUrl: String): EngineOperationResult<FastVideoInfo> = withContext(Dispatchers.IO) {
        fastInfoCache.get(videoUrl)?.let { return@withContext EngineOperationResult.Success(it) }

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

        try {
            val platform = PlatformDetector.detect(videoUrl)
            val request = YoutubeDLRequest(videoUrl)
            YoutubeDLConfig.apply(request, platform)
            request.addOption("--no-playlist")
            
            val timeout = if (platform == Platform.TWITCH) 25000L else 15000L
            val response = getInfoWithTimeout(request, timeout)
            
            val info = FastVideoInfo(
                title = response.title ?: "Sem título",
                author = response.uploader ?: response.uploaderId ?: "",
                thumbnail = response.thumbnail ?: "",
                url = videoUrl,
                durationSeconds = response.duration,
                viewCount = response.viewCount?.toLongOrNull() ?: 0L,
                likeCount = response.likeCount?.toLongOrNull() ?: 0L,
                videoId = response.id
            )
            EngineOperationResult.Success(info)
        } catch (e: Exception) {
            AppLogger.e("Generic extraction fail: ${e.message}", "YoutubeDL")
            categorizeException(e)
        }
    }

    /**
     * Extração Adaptativa com Short-circuit e Consolidação Inteligente
     */
    suspend fun fetchVideoFormats(url: String, useOAuth2: Boolean = false): EngineOperationResult<VideoInfo> = withContext(Dispatchers.IO) {
        val platform = PlatformDetector.detect(url)
        
        if (platform == Platform.KWAI) {
            return@withContext when (val kwaiResult = KwaiExtractor.extract(url)) {
                is EngineOperationResult.Success -> {
                    val data = kwaiResult.data
                    EngineOperationResult.Success(
                        data.videoInfo,
                        extraData = Pair(data.consolidatedFormats, "KwaiNative")
                    )
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(kwaiResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(kwaiResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(kwaiResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha ao processar vídeo do Kwai")
            }
        }

        if (platform == Platform.SNACKVIDEO) {
            return@withContext when (val snackResult = SnackVideoExtractor.extract(url)) {
                is EngineOperationResult.Success -> {
                    val data = snackResult.data
                    EngineOperationResult.Success(
                        data.videoInfo,
                        extraData = Pair(data.consolidatedFormats, "SnackVideoNative")
                    )
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(snackResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(snackResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(snackResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha ao processar vídeo do SnackVideo")
            }
        }

        if (platform == Platform.INSTAGRAM) {
            return@withContext when (val igResult = InstagramExtractor.extract(url)) {
                is EngineOperationResult.Success -> {
                    val data = igResult.data
                    EngineOperationResult.Success(
                        data.videoInfo,
                        extraData = Pair(data.consolidatedFormats, "InstagramNative")
                    )
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(igResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(igResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(igResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha ao processar vídeo do Instagram")
            }
        }

        // Se não for YouTube, usamos uma extração direta do yt-dlp com config centralizada
        if (platform != Platform.YOUTUBE) {
            return@withContext try {
                val request = YoutubeDLRequest(url)
                YoutubeDLConfig.apply(request, platform)
                request.addOption("--no-playlist")
                val timeout = if (platform == Platform.TWITCH) 60000L else 30000L
                val info = getInfoWithTimeout(request, timeout)
                
                if (info.formats.isNullOrEmpty()) {
                    val isMeta = platform == Platform.FACEBOOK || platform == Platform.THREADS
                    val errMsg = if (isMeta) {
                        "Nenhum formato extraído. Pode ser um carrossel de fotos, ou a plataforma bloqueou o acesso anônimo (exige login)."
                    } else {
                        "Nenhum formato de mídia detectado nesta página."
                    }
                    return@withContext EngineOperationResult.ExtractionError(errMsg)
                }

                val formatsCount = info.formats?.size ?: 0
                AppLogger.i("Extraction [${platform.name}]: Found $formatsCount formats", "YoutubeDL")
                
                EngineOperationResult.Success(info)
            } catch (e: Exception) {
                categorizeException(e)
            }
        }

        // Lógica específica para YouTube para maximizar resoluções e velocidade
        return@withContext try {
            val request = YoutubeDLRequest(url)
            YoutubeDLConfig.apply(request, platform)
            
            if (useOAuth2) request.addOption("--username", "oauth2")
            
            request.addOption("--no-playlist")

            val info = getInfoWithTimeout(request, 35000L)
            val rawFormats = info.formats ?: emptyList()
            
            if (rawFormats.isNotEmpty()) {
                val consolidated = consolidateFormats(rawFormats)
                AppLogger.i("Extraction [YouTube]: Found ${rawFormats.size} raw -> ${consolidated.size} consolidated formats", "YoutubeDL")
                EngineOperationResult.Success(info, extraData = Pair(consolidated, "default"))
            } else {
                EngineOperationResult.ExtractionError("Nenhum formato encontrado para este vídeo.")
            }
        } catch (e: Exception) {
            Log.e("YOUTUBE_FETCH_ERROR", "YouTube fetch error: ${e.message}", e)
            categorizeException(e)
        }
    }

    /**
     * Extrai a altura de resolução num formato mesmo se height == 0
     */
    fun parseHeight(format: VideoFormat): Int {
        if (format.width > 0 && format.height > 0) {
            return minOf(format.width, format.height)
        }
        if (format.height > 0) return format.height

        format.formatNote?.let { note ->
            val match = Regex("""(\d{3,4})p""").find(note)
            if (match != null) return match.groupValues[1].toIntOrNull() ?: 0
        }

        format.format?.let { fmt ->
            val matchNote = Regex("""(\d{3,4})p""").find(fmt)
            if (matchNote != null) return matchNote.groupValues[1].toIntOrNull() ?: 0
            val matchRes = Regex("""\d+x(\d{3,4})""").find(fmt)
            if (matchRes != null) return matchRes.groupValues[1].toIntOrNull() ?: 0
        }

        return 0
    }

    /**
     * Consolida e agrupa os formatos de vídeo por resolução e áudio por qualidade
     */
    private fun getCodecFamily(vcodec: String?): String {
        if (vcodec == null || vcodec == "none") return "none"
        val lower = vcodec.lowercase()
        if (lower.contains("avc") || lower.contains("h264") || lower.contains("mp4v")) return "h264"
        if (lower.contains("vp9")) return "vp9"
        if (lower.contains("av01") || lower.contains("av1")) return "av1"
        return lower.split(".")[0]
    }

    private fun consolidateFormats(rawFormats: List<VideoFormat>): List<VideoFormat> {
        if (BuildConfig.DEBUG) {
            rawFormats.forEach { f ->
                AppLogger.i("RAW FORMAT -> id:${f.formatId}, h:${f.height}, note:${f.formatNote}, vcodec:${f.vcodec}, acodec:${f.acodec}, tbr:${f.tbr}, fmt:${f.format}", "YoutubeDL")
            }
        }

        val validFormats = rawFormats.filter { 
            it.ext != "mhtml" && it.formatId?.contains("storyboard") != true 
        }

        // Agrupa formatos de vídeo por altura de resolução, fps e família de codec
        val videoFormats = validFormats.filter { 
            val h = parseHeight(it)
            h > 0 || (it.vcodec != null && it.vcodec != "none") 
        }

        val videoByGroup = videoFormats.groupBy { 
            "${parseHeight(it)}_${it.fps}_${getCodecFamily(it.vcodec)}" 
        }

        val bestVideoFormats = videoByGroup.mapNotNull { (_, formats) ->
            formats.maxWithOrNull(
                compareBy<VideoFormat> { it.width * it.height }
                    .thenBy { it.fileSize }
                    .thenBy { it.tbr }
            )
        }.sortedWith(
            compareByDescending<VideoFormat> { parseHeight(it) }
                .thenByDescending { it.fps }
                // Prioriza codecs mais amplamente compatíveis (H264) em resoluções iguais quando possível (facilita UI listagem ordenada)
                .thenByDescending { getCodecFamily(it.vcodec) == "h264" }
                .thenByDescending { it.tbr }
        )

        // Agrupa formatos de áudio por taxa de bits (abr) e extensão
        val audioFormats = validFormats
            .filter { (it.vcodec == "none" || it.vcodec == null) && parseHeight(it) <= 0 && (it.acodec != null && it.acodec != "none") }
            .groupBy { "${it.abr.toInt()}_${it.ext}" }
            .mapNotNull { (_, formats) -> formats.maxByOrNull { it.tbr } }
            .sortedByDescending { it.abr }

        val consolidated = (bestVideoFormats + audioFormats)
        return if (consolidated.isNotEmpty()) consolidated else validFormats
    }

    private fun categorizeException(e: Exception): EngineOperationResult<Nothing> {
        val msg = e.message ?: ""
        AppLogger.e("Extraction Error: $msg", "YoutubeDL", e)
        
        return when (e) {
            is TimeoutCancellationException -> EngineOperationResult.ExtractionError("Tempo limite de busca excedido")
            is UnknownHostException -> EngineOperationResult.NetworkError(e)
            is SocketTimeoutException -> EngineOperationResult.NetworkError(e)
            is IllegalStateException -> {
                if (e.message?.contains("instance not initialized") == true) {
                    EngineOperationResult.EngineNotReady(e)
                } else {
                    EngineOperationResult.ExtractionError(e.message ?: "Erro de estado ilegal", null)
                }
            }
            else -> {
                when {
                    msg.contains("login", ignoreCase = true) || msg.contains("registered users", ignoreCase = true) || msg.contains("auth", ignoreCase = true) -> 
                        EngineOperationResult.VideoUnavailable("Conteúdo restrito. A plataforma exige autenticação/login para acessar este link.")
                    msg.contains("403") || msg.contains("Forbidden") -> 
                        EngineOperationResult.VideoUnavailable("Acesso negado (HTTP 403). O site bloqueou a extração automática.")
                    msg.contains("429") || msg.contains("Too Many Requests") -> 
                        EngineOperationResult.VideoUnavailable("Muitas requisições. Tente novamente em alguns minutos.")
                    msg.contains("not available") || msg.contains("private") -> 
                        EngineOperationResult.VideoUnavailable("Conteúdo privado ou indisponível na sua região.")
                    msg.contains("Incomplete YouTube ID") -> 
                        EngineOperationResult.ExtractionError("ID do vídeo inválido")
                    msg.contains("Unable to extract universal data") || msg.contains("rehydration") ->
                        EngineOperationResult.ExtractionError("Falha na extração de dados (Anti-bot detectado).")
                    else -> EngineOperationResult.ExtractionError(e.message ?: "Erro desconhecido ao processar link", null)
                }
            }
        }
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun parseIso8601Duration(isoDuration: String): Int {
        return try {
            val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
            val match = regex.find(isoDuration)
            if (match != null) {
                val hours = match.groups[1]?.value?.toInt() ?: 0
                val minutes = match.groups[2]?.value?.toInt() ?: 0
                val seconds = match.groups[3]?.value?.toInt() ?: 0
                (hours * 3600) + (minutes * 60) + seconds
            } else 0
        } catch (e: Exception) { 0 }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "Unknown"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var s = size.toDouble()
        var unitIndex = 0
        while (s >= 1024 && unitIndex < units.size - 1) {
            s /= 1024
            unitIndex++
        }
        return "%.2f %s".format(s, units[unitIndex])
    }

    fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
            count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fK".format(count / 1_000.0)
            else -> count.toString()
        }
    }
}
