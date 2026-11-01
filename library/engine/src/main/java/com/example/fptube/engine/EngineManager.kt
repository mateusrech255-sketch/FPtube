package br.com.fpagency.fptube.engine

import android.content.Context
import android.util.LruCache
import br.com.fpagency.fptube.core.AppLogger
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineState {
    UNINITIALIZED, INITIALIZING, READY, FAILED
}

@Singleton
class EngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val nativeEngine: NativeEngine
) {
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    val state = _state.asStateFlow()

    // Para compatibilidade com código antigo enquanto migramos
    val isInitialized = MutableStateFlow(false)

    private val _dlpVersion = MutableStateFlow<String?>("N/A")
    val dlpVersion = _dlpVersion.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable = _isUpdateAvailable.asStateFlow()

    private val _oauth2Code = MutableStateFlow<String?>(null)
    val oauth2Code = _oauth2Code.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating = _isAuthenticating.asStateFlow()

    private val manifestCache = LruCache<String, Pair<Long, EngineOperationResult<com.yausername.youtubedl_android.mapper.VideoInfo>>>(50)
    private val CACHE_TTL = 10 * 60 * 1000L // 10 minutos

    private val initMutex = Mutex()
    private var initDeferred: Deferred<Boolean>? = null
    
    private var failureCount = 0
    private var lastFailureTimestamp = 0L
    private val COOLDOWN_MILLIS = 5 * 60 * 1000L // 5 minutos

    suspend fun initialize(): Boolean = initMutex.withLock {
        // Se já está pronto, retorna sucesso imediatamente
        if (_state.value == EngineState.READY) {
            return true
        }

        // Mecanismo de Cooldown para evitar spam de inicialização
        val now = System.currentTimeMillis()
        if (failureCount >= 3 && (now - lastFailureTimestamp) < COOLDOWN_MILLIS) {
            return false
        }

        // Se já existe uma inicialização em curso, aguarda ela
        initDeferred?.let {
            return it.await()
        }

        // Nova tentativa de inicialização
        val deferred = CoroutineScope(Dispatchers.IO).async {
            performInitialization()
        }
        initDeferred = deferred
        
        try {
            val result = deferred.await()
            if (!result) {
                failureCount++
                lastFailureTimestamp = System.currentTimeMillis()
                initDeferred = null // Permite nova tentativa após cooldown
            } else {
                failureCount = 0 // Reset em caso de sucesso
            }
            result
        } catch (e: Exception) {
            failureCount++
            lastFailureTimestamp = System.currentTimeMillis()
            initDeferred = null
            false
        }
    }

    private suspend fun performInitialization(): Boolean = withContext(Dispatchers.IO) {
        _state.value = EngineState.INITIALIZING

        try {
            // Passo 1: YoutubeDL (Extração de binários)
            YoutubeDL.getInstance().init(context)

            // Passo 2: FFmpeg
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            
            _dlpVersion.value = YoutubeDL.getInstance().version(context)
            _state.value = EngineState.READY
            isInitialized.value = true
            
            performDataCleanup()
            CoroutineScope(Dispatchers.IO).launch {
                checkUpdate()
            }
            true
        } catch (e: Exception) {
            AppLogger.e("ENGINE INIT FAILED", "EngineManager", e)
            _state.value = EngineState.FAILED
            isInitialized.value = false
            false
        }
    }

    private fun performDataCleanup() {
        try {
            val dlpDir = File(context.noBackupFilesDir, "youtubedl-android")
            if (dlpDir.exists() && dlpDir.isDirectory) {
                // Remove arquivos temporários ou manifestos de atualizações falhas
                dlpDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".tmp") || file.name.startsWith("update_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // fail silently
        }
    }

    suspend fun awaitEngineReady(): Boolean {
        if (_state.value == EngineState.READY) return true
        AppLogger.i("WAITING FOR ENGINE", "EngineManager")
        return initialize()
    }

    suspend fun checkUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            val available = status == YoutubeDL.UpdateStatus.DONE
            _isUpdateAvailable.value = available
            if (available) _dlpVersion.value = YoutubeDL.getInstance().version(context)
            available
        } catch (e: Exception) { false }
    }

    suspend fun updateYtDlp(): String? = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("Verificando atualização do yt-dlp", "EngineManager")
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            _dlpVersion.value = YoutubeDL.getInstance().version(context)
            _isUpdateAvailable.value = false
            status?.name
        } catch (e: Exception) {
            AppLogger.e("Erro ao atualizar yt-dlp", "EngineManager", e)
            null
        }
    }

    suspend fun clearBinaries() = withContext(Dispatchers.IO) {
        try {
            val dlpDir = File(context.noBackupFilesDir, "youtubedl-android")
            if (dlpDir.exists()) dlpDir.deleteRecursively()
            _state.value = EngineState.UNINITIALIZED
            isInitialized.value = false
            _dlpVersion.value = "N/A"
            initDeferred = null
        } catch (e: Exception) {
            AppLogger.e("Erro ao limpar binários", "EngineManager", e)
        }
    }

    fun isFeatureAvailable(id: String): Boolean = pluginManager.isPluginInstalled(id)
    fun getFeaturePath(id: String): String? = pluginManager.getPluginPath(id)

    /**
     * Fase 1: Busca Rápida de Metadados (Nativo/Cloud -> oEmbed -> Generic)
     */
    suspend fun fetchFastMetadata(url: String): EngineOperationResult<FastVideoInfo> {
        val normalizedUrl = PlatformDetector.normalize(url)
        val platform = PlatformDetector.detect(normalizedUrl)
        
        val start = System.currentTimeMillis()
        AppLogger.i("FETCH METADATA START | Platform: ${platform.name}", "EngineManager")
        
        // Se for YouTube, tentamos as otimizações específicas primeiro
        if (platform == Platform.YOUTUBE) {
            val nativeJson = try {
                nativeEngine.fetchNativeMetadata(normalizedUrl)
            } catch (e: Exception) {
                null
            }

            if (nativeJson != null) {
                val title = nativeJson.optString("title")
                val videoId = nativeJson.optString("id")
                if (title.isNotEmpty()) {
                    AppLogger.i("FETCH METADATA END (${System.currentTimeMillis() - start}ms)", "EngineManager")
                    val info = FastVideoInfo(
                        title = title,
                        author = nativeJson.optString("channelTitle", nativeJson.optString("author")),
                        thumbnail = nativeJson.optString("thumbnail", "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"),
                        url = normalizedUrl,
                        durationSeconds = YoutubeDLUtils.parseIso8601Duration(nativeJson.optString("duration")),
                        viewCount = nativeJson.optLong("viewCount", 0L),
                        likeCount = nativeJson.optLong("likeCount", 0L),
                        videoId = videoId
                    )
                    return EngineOperationResult.Success(info)
                }
            }
            
            // Fallback para oEmbed local (YouTube)
            val oEmbedResult = YoutubeDLUtils.fetchOEmbedMetadata(normalizedUrl)
            if (oEmbedResult is EngineOperationResult.Success) {
                AppLogger.i("FETCH METADATA END (oEmbed) (${System.currentTimeMillis() - start}ms)", "EngineManager")
                return oEmbedResult
            }
        }

        if (platform == Platform.KWAI) {
            return when (val kwaiResult = KwaiExtractor.extract(normalizedUrl)) {
                is EngineOperationResult.Success -> {
                    AppLogger.i("FETCH METADATA END (Kwai) (${System.currentTimeMillis() - start}ms)", "EngineManager")
                    val fullSuccessResult = EngineOperationResult.Success(
                        kwaiResult.data.videoInfo,
                        extraData = Pair(kwaiResult.data.consolidatedFormats, "KwaiNative")
                    )
                    manifestCache.put(normalizedUrl, System.currentTimeMillis() to fullSuccessResult)
                    EngineOperationResult.Success(kwaiResult.data.fastInfo)
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(kwaiResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(kwaiResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(kwaiResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha na extração nativa do Kwai")
            }
        }
        
        if (platform == Platform.SNACKVIDEO) {
            return when (val snackResult = SnackVideoExtractor.extract(normalizedUrl)) {
                is EngineOperationResult.Success -> {
                    AppLogger.i("FETCH METADATA END (SnackVideo) (${System.currentTimeMillis() - start}ms)", "EngineManager")
                    val fullSuccessResult = EngineOperationResult.Success(
                        snackResult.data.videoInfo,
                        extraData = Pair(snackResult.data.consolidatedFormats, "SnackVideoNative")
                    )
                    manifestCache.put(normalizedUrl, System.currentTimeMillis() to fullSuccessResult)
                    EngineOperationResult.Success(snackResult.data.fastInfo)
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(snackResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(snackResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(snackResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha na extração nativa do SnackVideo")
            }
        }

        if (platform == Platform.INSTAGRAM) {
            return when (val igResult = InstagramExtractor.extract(normalizedUrl)) {
                is EngineOperationResult.Success -> {
                    AppLogger.i("FETCH METADATA END (Instagram) (${System.currentTimeMillis() - start}ms)", "EngineManager")
                    val fullSuccessResult = EngineOperationResult.Success(
                        igResult.data.videoInfo,
                        extraData = Pair(igResult.data.consolidatedFormats, "InstagramNative")
                    )
                    manifestCache.put(normalizedUrl, System.currentTimeMillis() to fullSuccessResult)
                    EngineOperationResult.Success(igResult.data.fastInfo)
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(igResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(igResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(igResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha na extração nativa do Instagram")
            }
        }

        // Para outras plataformas ou se o YouTube falhou nas otimizações, usamos o yt-dlp genérico
        val genericResult = YoutubeDLUtils.fetchGenericMetadata(normalizedUrl)
        if (genericResult is EngineOperationResult.Success<FastVideoInfo>) {
            AppLogger.i("FETCH METADATA END (Generic) (${System.currentTimeMillis() - start}ms)", "EngineManager")
        }
        return genericResult
    }

    /**
     * Fase 2: Extração de Formatos com Cache e Consolidação
     */
    suspend fun fetchFormats(url: String, useOAuth2: Boolean, forceRefresh: Boolean = false): EngineOperationResult<com.yausername.youtubedl_android.mapper.VideoInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            manifestCache.get(url)?.let { (timestamp, result) ->
                if (now - timestamp < CACHE_TTL && result is EngineOperationResult.Success) {
                    val extra = result.extraData as? Pair<*, *>
                    val source = extra?.second as? String
                    if (source == "FullYtDlp" || source == "KwaiNative" || source == "SnackVideoNative" || source == "InstagramNative") {
                        AppLogger.i("FETCH FORMATS CACHE HIT (0ms)", "EngineManager")
                        return result
                    }
                }
            }
        }

        val result = YoutubeDLUtils.fetchVideoFormats(url, useOAuth2)
        
        if (result is EngineOperationResult.Success) {
            val extra = result.extraData as? Pair<*, *>
            @Suppress("UNCHECKED_CAST")
            val consolidated = (extra?.first as? List<VideoFormat>) ?: result.data.formats ?: emptyList()
            val fullResult = EngineOperationResult.Success(
                result.data,
                extraData = Pair<List<VideoFormat>, String>(consolidated, "FullYtDlp")
            )
            manifestCache.put(url, now to fullResult)
            return fullResult
        }

        return result
    }

    suspend fun authenticateOAuth2() = withContext(Dispatchers.IO) {
        try {
            _isAuthenticating.value = true
            _oauth2Code.value = null
            AppLogger.i("Iniciando fluxo OAuth2", "EngineManager")
            
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=7B5FYXm99O4")
            YoutubeDLConfig.apply(request, Platform.YOUTUBE)
            request.addOption("--username", "oauth2")
            request.addOption("--playlist-items", "0")
            
            YoutubeDL.getInstance().execute(request, "auth_job", true) { _, _, line ->
                AppLogger.i("Auth Output: $line", "EngineManager")
                val patterns = listOf(
                    Regex("""[A-Z0-9]{4}-[A-Z0-9]{4}"""),
                    Regex("""[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}""")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        _oauth2Code.value = match.value
                        AppLogger.i("Código OAuth2 capturado: ${match.value}", "EngineManager")
                        break
                    }
                }
                
                if (line.contains("google.com/device", ignoreCase = true) && _oauth2Code.value == null) {
                    val parts = line.split(" ")
                    parts.forEach { part ->
                        if (part.contains("-") && part.length >= 8 && part.length <= 15) {
                            _oauth2Code.value = part.trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Erro no fluxo OAuth2", "EngineManager", e)
        } finally {
            _oauth2Code.value = null
            _isAuthenticating.value = false
        }
    }
}
