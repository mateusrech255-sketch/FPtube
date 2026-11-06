package br.com.fpagency.fptube.engine

import android.content.Context
import android.util.LruCache
import br.com.fpagency.fptube.core.AppLogger
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

    val isInitialized = MutableStateFlow(false)

    private val _dlpVersion = MutableStateFlow<String?>("1.0.0-pure-kotlin")
    val dlpVersion = _dlpVersion.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable = _isUpdateAvailable.asStateFlow()

    private val _oauth2Code = MutableStateFlow<String?>(null)
    val oauth2Code = _oauth2Code.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating = _isAuthenticating.asStateFlow()

    private val manifestCache = LruCache<String, Pair<Long, EngineOperationResult<VideoInfo>>>(50)
    private val CACHE_TTL = 10 * 60 * 1000L

    private val initMutex = Mutex()
    private var initDeferred: Deferred<Boolean>? = null

    private var failureCount = 0
    private var lastFailureTimestamp = 0L
    private val COOLDOWN_MILLIS = 5 * 60 * 1000L

    suspend fun initialize(): Boolean = initMutex.withLock {
        if (_state.value == EngineState.READY) {
            return true
        }

        val now = System.currentTimeMillis()
        if (failureCount >= 3 && (now - lastFailureTimestamp) < COOLDOWN_MILLIS) {
            return false
        }

        initDeferred?.let {
            return it.await()
        }

        val deferred = CoroutineScope(Dispatchers.IO).async {
            performInitialization()
        }
        initDeferred = deferred

        try {
            val result = deferred.await()
            if (!result) {
                failureCount++
                lastFailureTimestamp = System.currentTimeMillis()
                initDeferred = null
            } else {
                failureCount = 0
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
            _dlpVersion.value = "1.0.0-pure-kotlin"
            _state.value = EngineState.READY
            isInitialized.value = true
            true
        } catch (e: Exception) {
            AppLogger.e("ENGINE INIT FAILED", "EngineManager", e)
            _state.value = EngineState.FAILED
            isInitialized.value = false
            false
        }
    }

    suspend fun awaitEngineReady(): Boolean {
        if (_state.value == EngineState.READY) return true
        return initialize()
    }

    suspend fun checkUpdate(): Boolean = false

    suspend fun updateYtDlp(): String? = null

    suspend fun clearBinaries() = withContext(Dispatchers.IO) {
        _state.value = EngineState.READY
        isInitialized.value = true
        _dlpVersion.value = "1.0.0-pure-kotlin"
        initDeferred = null
    }

    fun isFeatureAvailable(id: String): Boolean = pluginManager.isPluginInstalled(id)
    fun getFeaturePath(id: String): String? = pluginManager.getPluginPath(id)

    suspend fun fetchFastMetadata(url: String): EngineOperationResult<FastVideoInfo> {
        val normalizedUrl = PlatformDetector.normalize(url)
        val platform = PlatformDetector.detect(normalizedUrl)

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

            val oEmbedResult = YoutubeDLUtils.fetchOEmbedMetadata(normalizedUrl)
            if (oEmbedResult is EngineOperationResult.Success) {
                return oEmbedResult
            }
        }

        if (platform == Platform.KWAI) {
            return when (val kwaiResult = KwaiExtractor.extract(normalizedUrl)) {
                is EngineOperationResult.Success -> {
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

        if (platform == Platform.TIKTOK) {
            return when (val ttResult = TikTokExtractor.extract(normalizedUrl)) {
                is EngineOperationResult.Success -> {
                    val fullSuccessResult = EngineOperationResult.Success(
                        ttResult.data.videoInfo,
                        extraData = Pair(ttResult.data.consolidatedFormats, "TikTokNative")
                    )
                    manifestCache.put(normalizedUrl, System.currentTimeMillis() to fullSuccessResult)
                    EngineOperationResult.Success(ttResult.data.fastInfo)
                }
                is EngineOperationResult.ExtractionError -> EngineOperationResult.ExtractionError(ttResult.message)
                is EngineOperationResult.VideoUnavailable -> EngineOperationResult.VideoUnavailable(ttResult.reason)
                is EngineOperationResult.NetworkError -> EngineOperationResult.NetworkError(ttResult.cause)
                else -> EngineOperationResult.ExtractionError("Falha na extração nativa do TikTok")
            }
        }

        return YoutubeDLUtils.fetchGenericMetadata(normalizedUrl)
    }

    suspend fun fetchFormats(url: String, useOAuth2: Boolean, forceRefresh: Boolean = false): EngineOperationResult<VideoInfo> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            manifestCache.get(url)?.let { (timestamp, result) ->
                if (now - timestamp < CACHE_TTL && result is EngineOperationResult.Success) {
                    val extra = result.extraData as? Pair<*, *>
                    val source = extra?.second as? String
                    if (source == "FullYtDlp" || source == "KwaiNative" || source == "SnackVideoNative" || source == "InstagramNative" || source == "TikTokNative") {
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
        _oauth2Code.value = null
        _isAuthenticating.value = false
    }
}
