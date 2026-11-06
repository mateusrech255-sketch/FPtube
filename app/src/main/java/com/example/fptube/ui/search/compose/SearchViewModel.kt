package br.com.fpagency.fptube.ui.search.compose

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.core.ui.R
import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.data.DownloadEntity
import br.com.fpagency.fptube.data.DownloadRepository
import br.com.fpagency.fptube.data.DownloadStatus
import br.com.fpagency.fptube.data.SettingsManager
import br.com.fpagency.fptube.download.DownloadManager
import br.com.fpagency.fptube.engine.EngineManager
import br.com.fpagency.fptube.engine.EngineOperationResult
import br.com.fpagency.fptube.engine.FastVideoInfo
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import br.com.fpagency.fptube.engine.Platform
import br.com.fpagency.fptube.engine.PlatformDetector
import br.com.fpagency.fptube.engine.VideoFormat
import br.com.fpagency.fptube.engine.VideoInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val repository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val settingsManager: SettingsManager,
    private val engineManager: EngineManager
) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var prefetchingJob: Job? = null
    private var activeSearchJob: Job? = null
    private var lastUrlProcessed: String? = null

    fun onUrlChanged(url: String) {
        if (url.length > 20 && url != lastUrlProcessed) {
            prefetchingJob?.cancel()
            prefetchingJob = viewModelScope.launch {
                delay(300)
                val normUrl = PlatformDetector.normalize(url)
                if (normUrl.isEmpty()) return@launch
                lastUrlProcessed = normUrl

                val fastRes = engineManager.fetchFastMetadata(normUrl)
                if (fastRes is EngineOperationResult.Success) {
                    val fastInfo = fastRes.data
                    _uiState.value = SearchUiState.FastMetadataReady(fastInfo, isScanningFormats = true)

                    if (engineManager.awaitEngineReady()) {
                        val oauth2 = settingsManager.useOAuth2.first()
                        val result = engineManager.fetchFormats(normUrl, useOAuth2 = oauth2, forceRefresh = false)
                        if (result is EngineOperationResult.Success) {
                            val info = result.data
                            val extra = result.extraData as? Pair<*, *>
                            @Suppress("UNCHECKED_CAST")
                            val formats = (extra?.first as? List<VideoFormat>) ?: info.formats ?: emptyList()
                            val winningClient = extra?.second as? String

                            if (formats.isNotEmpty()) {
                                _uiState.value = SearchUiState.DeepScanReady(
                                    fastInfo = fastInfo,
                                    fullInfo = info,
                                    consolidatedFormats = formats,
                                    winningClient = winningClient
                                )
                            } else {
                                _uiState.value = SearchUiState.FastMetadataReady(fastInfo, isScanningFormats = false)
                            }
                        }
                    }
                }
            }
        }
    }

    fun startSearch(url: String) {
        val t0 = System.currentTimeMillis()
        val normalizedUrl = PlatformDetector.normalize(url)
        if (normalizedUrl.isEmpty()) return

        if (lastUrlProcessed == normalizedUrl && (_uiState.value is SearchUiState.DeepScanReady || prefetchingJob?.isActive == true)) {
            return
        }

        prefetchingJob?.cancel()
        if (activeSearchJob?.isActive == true) {
            activeSearchJob?.cancel()
        }
        lastUrlProcessed = normalizedUrl

        activeSearchJob = viewModelScope.launch {
            val tStartPlat = System.currentTimeMillis()
            val platform = PlatformDetector.detect(normalizedUrl)
            val platMs = System.currentTimeMillis() - tStartPlat

            val platformMsg = if (platform != Platform.UNKNOWN && platform != Platform.OTHER) {
                app.getString(R.string.platform_detected, platform.displayName)
            } else {
                app.getString(R.string.loading_info)
            }
            
            _uiState.value = SearchUiState.Loading(platformMsg)
            
            try {
                val tStartFast = System.currentTimeMillis()
                val fastResult = engineManager.fetchFastMetadata(normalizedUrl)
                val fastMs = System.currentTimeMillis() - tStartFast
                
                if (fastResult is EngineOperationResult.Success) {
                    val fastInfo = fastResult.data
                    _uiState.value = SearchUiState.FastMetadataReady(fastInfo, isScanningFormats = true)
                    
                    if (!engineManager.awaitEngineReady()) {
                        _uiState.value = SearchUiState.Error(app.getString(R.string.error_engine_init))
                        return@launch
                    }

                    val tStartFormats = System.currentTimeMillis()
                    val oauth2 = settingsManager.useOAuth2.first()
                    val result = engineManager.fetchFormats(normalizedUrl, useOAuth2 = oauth2, forceRefresh = false)
                    val formatsMs = System.currentTimeMillis() - tStartFormats
                    val totalMs = System.currentTimeMillis() - t0

                    AppLogger.i("[FPTUBE_PERF] platform=${platform.name} | fastMs=$fastMs | formatsMs=$formatsMs | totalSearchLatencyMs=$totalMs", "FPTUBE_PERF")
                    
                    when (result) {
                        is EngineOperationResult.Success -> {
                            val info = result.data
                            val extra = result.extraData as? Pair<*, *>
                            @Suppress("UNCHECKED_CAST")
                            val formats = (extra?.first as? List<VideoFormat>) ?: info.formats ?: emptyList()
                            val winningClient = extra?.second as? String

                            Log.e("YOUTUBE_4K_DEBUG", "SearchViewModel formats count: ${formats.size}, highest height: ${formats.maxOfOrNull { it.height } ?: 0}")
                            
                            if (formats.isNotEmpty()) {
                                _uiState.value = SearchUiState.DeepScanReady(
                                    fastInfo = fastInfo,
                                    fullInfo = info,
                                    consolidatedFormats = formats,
                                    winningClient = winningClient
                                )
                            } else {
                                _uiState.value = SearchUiState.FastMetadataReady(fastInfo, isScanningFormats = false)
                            }
                        }
                        is EngineOperationResult.VideoUnavailable -> {
                            _uiState.value = SearchUiState.Error(result.reason)
                        }
                        else -> {
                            AppLogger.w("Deep scan failed or timed out. Falling back to FastMetadataReady", "SearchVM")
                            _uiState.value = SearchUiState.FastMetadataReady(fastInfo, isScanningFormats = false)
                        }
                    }
                } else {
                    val errorMsg = when (fastResult) {
                        is EngineOperationResult.VideoUnavailable -> fastResult.reason
                        is EngineOperationResult.ExtractionError -> fastResult.message
                        else -> app.getString(R.string.error_unsupported_platform)
                    }
                    _uiState.value = SearchUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                AppLogger.e("FATAL SEARCH ERROR", "SearchVM", e)
                _uiState.value = SearchUiState.Error(app.getString(R.string.error_unexpected))
            } finally {
                if (_uiState.value is SearchUiState.Loading) {
                    _uiState.value = SearchUiState.Idle
                }
            }
        }
    }

    fun download(url: String, title: String, thumb: String, channel: String, format: VideoFormat?, type: String, playerClient: String? = null) {
        viewModelScope.launch {
            val platform = PlatformDetector.detect(url)
            val res = format?.let { it.formatNote ?: it.format }
            val newDownload = DownloadEntity(
                url = url,
                title = title,
                thumbnail = thumb,
                channel = channel,
                status = DownloadStatus.QUEUED,
                resolution = res,
                formatId = format?.formatId,
                downloadType = type,
                codec = format?.vcodec,
                fps = format?.fps,
                playerClient = playerClient,
                platform = platform.name
            )
            val id = repository.insert(newDownload)
            downloadManager.enqueueDownload(id)
            _uiState.value = SearchUiState.Idle
            lastUrlProcessed = null
        }
    }
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    data class Loading(val message: String) : SearchUiState()
    data class FastMetadataReady(
        val fastInfo: FastVideoInfo,
        val isScanningFormats: Boolean = true
    ) : SearchUiState()
    data class DeepScanReady(
        val fastInfo: FastVideoInfo,
        val fullInfo: VideoInfo,
        val consolidatedFormats: List<VideoFormat>,
        val winningClient: String? = null
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
