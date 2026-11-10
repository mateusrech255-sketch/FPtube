package br.com.fpagency.fptube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.core.FormatUtils
import br.com.fpagency.fptube.data.DownloadDao
import br.com.fpagency.fptube.data.DownloadStatus
import br.com.fpagency.fptube.data.SettingsManager
import br.com.fpagency.fptube.engine.DownloadEngine
import br.com.fpagency.fptube.engine.EngineManager
import br.com.fpagency.fptube.engine.InstagramExtractor
import br.com.fpagency.fptube.engine.KwaiExtractor
import br.com.fpagency.fptube.engine.SnackVideoExtractor
import br.com.fpagency.fptube.engine.TikTokExtractor
import br.com.fpagency.fptube.engine.YoutubeDLConfig
import br.com.fpagency.fptube.engine.YoutubeDLUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val settingsManager: SettingsManager,
    private val engine: DownloadEngine,
    private val fileMover: FileMover,
    private val engineManager: EngineManager
) : CoroutineWorker(context, workerParams) {

    private val channelId = "FPtube_Downloads"
    private var currentProcessId: String? = null

    companion object {
        private val SPEED_REGEX = Regex("""(\d+\.?\d*\s?[KMGT]i?B/s)""")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val downloadId = inputData.getLong("DOWNLOAD_ID", -1L)
        val download = downloadDao.getDownloadById(downloadId)
        val title = download?.title ?: "Download do FPtube"
        return createForegroundInfo(title, "Preparando download...", download?.platform)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong("DOWNLOAD_ID", -1L)
        if (downloadId == -1L) return@withContext Result.failure()

        val download = downloadDao.getDownloadById(downloadId) ?: return@withContext Result.failure()
        
        try {
            createNotificationChannel()
            
            // Garantir inicialização do motor no processo do Worker
            if (!engineManager.awaitEngineReady()) {
                throw Exception("Falha ao inicializar o motor de download")
            }

            try {
                setForeground(createForegroundInfo(download.title, "Iniciando...", download.platform))
            } catch (e: Exception) {
                AppLogger.w("Falha ao entrar em foreground (Android 12+ restriction)", "DownloadWorker", e)
            }
            
            AppLogger.i("Worker: Iniciando download ${download.id}", "DownloadWorker")
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
            
            val tempDir = File(context.noBackupFilesDir, "downloads")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            } else {
                // Limpeza de órfãos de sessões anteriores que falharam/travam
                tempDir.listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        file.delete()
                    }
                }
            }

            val speedLimit = settingsManager.speedLimit.first()
            val useOAuth2 = settingsManager.useOAuth2.first()
            
            val downloadFormat = if (download.formatId != null) {
                if (download.platform == "YOUTUBE") {
                    "${download.formatId}+(bestaudio[ext=m4a]/bestaudio)/best"
                } else {
                    if (download.formatId == "0" || download.formatId == "unknown") "best" else download.formatId!!
                }
            } else {
                if (download.platform == "YOUTUBE") {
                    "bv*+ba/bestvideo+bestaudio/best"
                } else {
                    "best"
                }
            }

            val options = YoutubeDLConfig.getBaseOptions(download.platform).toMutableMap()
            options["--merge-output-format"] = "mp4"

            val extraArgs = YoutubeDLConfig.getExtraArgs().toMutableList()

            if (useOAuth2) {
                options["--username"] = "oauth2"
            }

            if (engineManager.isFeatureAvailable("aria2c")) {
                val aria2Path = engineManager.getFeaturePath("aria2c")
                if (aria2Path != null) {
                    AppLogger.i("Aceleração de download ATIVADA via Aria2", "DownloadWorker")
                    options["--downloader"] = "http:$aria2Path"
                    options["--downloader-args"] = "aria2c:-x 16 -s 16 -j 16 -k 1M"
                }
            }

            if (speedLimit != "Ilimitado") {
                options["-r"] = FormatUtils.convertSpeedLimit(speedLimit)
            }
            
            val processId = "dl_${downloadId}_${System.currentTimeMillis()}"
            currentProcessId = processId

            var targetUrl = download.url
            var targetFormat = downloadFormat

            val isAudioRequest = download.downloadType.equals("audio", ignoreCase = true) ||
                download.downloadType.equals("Áudio", ignoreCase = true)

            suspend fun resolvePlatformStream(forceRefresh: Boolean) {
                if (download.platform == "KWAI") {
                    val freshMediaUrl = KwaiExtractor.getFreshMediaUrl(download.url, download.formatId, forceRefresh = forceRefresh)
                    if (!freshMediaUrl.isNullOrBlank()) {
                        AppLogger.i("[KWAI] URL de mídia fresca obtida para o download (refresh=$forceRefresh)", "DownloadWorker")
                        targetUrl = freshMediaUrl
                        targetFormat = "best"
                        if (isAudioRequest || download.formatId == "kwai_audio") {
                            options["ext"] = "m4a"
                            options["extract_audio"] = "true"
                        }
                        if (download.resolution?.contains("1080") == true || download.formatId == "kwai_1080p") {
                            options["target_height"] = "1080"
                        } else if (download.resolution?.contains("720") == true || download.formatId == "kwai_720p") {
                            options["target_height"] = "720"
                        }
                    }
                }

                if (download.platform == "INSTAGRAM") {
                    val freshMediaUrl = InstagramExtractor.getFreshMediaUrl(download.url, download.formatId, forceRefresh = forceRefresh)
                    if (!freshMediaUrl.isNullOrBlank()) {
                        AppLogger.i("[INSTAGRAM] URL de mídia fresca obtida para o download (refresh=$forceRefresh)", "DownloadWorker")
                        targetUrl = freshMediaUrl
                        targetFormat = "best"
                        if (isAudioRequest || download.formatId == "instagram_audio") {
                            options["ext"] = "m4a"
                            options["extract_audio"] = "true"
                        }
                        if (download.resolution?.contains("1080") == true || download.formatId == "instagram_1080p") {
                            options["target_height"] = "1080"
                        } else if (download.resolution?.contains("720") == true || download.formatId == "instagram_720p") {
                            options["target_height"] = "720"
                        }
                    }
                }

                if (download.platform == "SNACKVIDEO") {
                    val freshMediaUrl = SnackVideoExtractor.getFreshMediaUrl(download.url, download.formatId, forceRefresh = forceRefresh)
                    if (!freshMediaUrl.isNullOrBlank()) {
                        AppLogger.i("[SNACKVIDEO] URL de mídia fresca obtida para o download (refresh=$forceRefresh)", "DownloadWorker")
                        targetUrl = freshMediaUrl
                        targetFormat = "best"
                        if (isAudioRequest || download.formatId == "snack_audio") {
                            options["ext"] = "m4a"
                            options["extract_audio"] = "true"
                        }
                        if (download.resolution?.contains("1080") == true || download.formatId == "snack_1080p") {
                            options["target_height"] = "1080"
                        } else if (download.resolution?.contains("720") == true || download.formatId == "snack_720p") {
                            options["target_height"] = "720"
                        }
                    }
                }

                if (download.platform == "TIKTOK") {
                    val resolvedTikTok = TikTokExtractor.resolveStream(
                        originalUrl = download.url,
                        preferredFormatId = download.formatId,
                        downloadType = download.downloadType,
                        forceRefresh = forceRefresh
                    ) ?: throw Exception("Não foi possível extrair a URL de mídia do TikTok.")

                    AppLogger.i("[TIKTOK] Stream resolvido (format=${resolvedTikTok.formatId}, ext=${resolvedTikTok.ext}, refresh=$forceRefresh)", "DownloadWorker")
                    targetUrl = resolvedTikTok.mediaUrl
                    targetFormat = "best"
                    options["ext"] = resolvedTikTok.ext
                    options["User-Agent"] = resolvedTikTok.userAgent
                    if (download.resolution?.contains("1080") == true || download.formatId == "tiktok_hd_nowm") {
                        options["target_height"] = "1080"
                    } else if (download.resolution?.contains("720") == true || download.formatId == "tiktok_sd_nowm") {
                        options["target_height"] = "720"
                    }
                }

                if (download.platform == "YOUTUBE") {
                    val resolvedStream = YoutubeDLUtils.resolveYouTubeStream(
                        originalUrl = download.url,
                        preferredFormatId = download.formatId,
                        downloadType = download.downloadType,
                        forceRefresh = forceRefresh
                    ) ?: throw Exception("Não foi possível extrair a URL de mídia direta do YouTube.")

                    AppLogger.i("[YOUTUBE] Stream resolvido (format=${resolvedStream.formatId}, ext=${resolvedStream.ext}, refresh=$forceRefresh)", "DownloadWorker")
                    targetUrl = resolvedStream.mediaUrl
                    targetFormat = "best"
                    options["ext"] = resolvedStream.ext
                    options["User-Agent"] = resolvedStream.userAgent
                    if (!resolvedStream.audioUrl.isNullOrBlank()) {
                        options["audio_url"] = resolvedStream.audioUrl!!
                    } else {
                        options.remove("audio_url")
                    }
                }
            }

            resolvePlatformStream(forceRefresh = false)

            var lastUpdate = 0L
            val isEngineFinished = java.util.concurrent.atomic.AtomicBoolean(false)
            var activeProgressJob: kotlinx.coroutines.Job? = null
            val workerScope = this

            val progressCallback: (Float, Long, String) -> Unit = { progress, eta, speed ->
                if (progress >= 0f && !isStopped && !isEngineFinished.get()) {
                    val now = System.currentTimeMillis()
                    if ((now - lastUpdate) > 1000 && activeProgressJob?.isActive != true) {
                        lastUpdate = now
                        activeProgressJob = workerScope.launch {
                            if (isStopped || isEngineFinished.get()) return@launch
                            val current = downloadDao.getDownloadById(downloadId)
                            if (current != null && current.status == DownloadStatus.DOWNLOADING && !isEngineFinished.get()) {
                                val cleanSpeed = SPEED_REGEX.find(speed)?.value ?: speed.replace("[download]", "").trim()
                                
                                downloadDao.update(
                                    current.copy(
                                        progress = progress.toInt(),
                                        speed = cleanSpeed,
                                        eta = eta.toString()
                                    )
                                )
                                try {
                                    setForeground(createForegroundInfo(current.title, "Baixando • $cleanSpeed • ${eta}s", current.platform))
                                } catch (_: Exception) {
                                    // Silent fail for foreground update
                                }
                            }
                        }
                    }
                }
            }

            var result = engine.execute(targetUrl, tempDir, targetFormat, options, extraArgs, processId, progressCallback)

            // Se falhar por expiração de URL assinada em cache (HTTP 403/410), invalida cache e tenta 1 vez com URL recém-extraída
            if (result.exitCode != 0 && !isStopped) {
                AppLogger.w("Primeira tentativa falhou (${result.error}). Renovando URL sem cache...", "DownloadWorker")
                resolvePlatformStream(forceRefresh = true)
                result = engine.execute(targetUrl, tempDir, targetFormat, options, extraArgs, processId, progressCallback)
            }

            // Garante que nenhuma coroutine de progresso continue rodando ou sobrescreva o status final
            isEngineFinished.set(true)
            activeProgressJob?.cancel()
            activeProgressJob?.join()

            if (result.exitCode == 0) {
                val downloadedFile = findActualDownloadedFile(tempDir, processId)
                if (downloadedFile != null) {
                    if (!validateFileIntegrity(downloadedFile, download.downloadType)) {
                        throw Exception("Falha de integridade do arquivo. Arquivo incompleto ou corrompido.")
                    }
                    val locationUri = settingsManager.downloadLocationUri.first()
                    val finalPath = fileMover.moveToFinalLocation(downloadedFile, download.title, download.downloadType, locationUri)
                    
                    downloadDao.update(download.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        filePath = finalPath,
                        speed = null,
                        eta = null
                    ))
                    
                    // Limpeza total do diretório temporário após sucesso para remover fragmentos/logs
                    try {
                        tempDir.listFiles { _, name -> name.contains(processId) }?.forEach { it.delete() }
                    } catch (e: Exception) { /* ignore */ }
                    
                    return@withContext Result.success()
                } else {
                    throw Exception("Arquivo baixado não encontrado")
                }
            } else {
                throw Exception(result.error)
            }
        } catch (e: Exception) {
            if (isStopped) {
                AppLogger.i("Worker: Download ${download.id} parado (pausado ou cancelado)", "DownloadWorker")
                return@withContext Result.success()
            }
            AppLogger.e("Worker: Erro no download ${download.id}", "DownloadWorker", e)
            downloadDao.updateError(downloadId, DownloadStatus.FAILED, e.message ?: e.toString(), e.stackTraceToString())
            return@withContext Result.failure()
        } finally {
            withContext(NonCancellable) {
                val pid = currentProcessId
                pid?.let { engine.cancel(it) }
                
                val current = downloadDao.getDownloadById(downloadId)
                if (current?.status == DownloadStatus.CANCELLED || current?.status == DownloadStatus.FAILED) {
                    if (pid != null) {
                        try {
                            val tempDir = File(context.noBackupFilesDir, "downloads")
                            tempDir.listFiles { _, name -> name.contains(pid) }?.forEach { file ->
                                file.delete()
                            }
                        } catch (e: Exception) { /* silent */ }
                    }
                }
                currentProcessId = null
            }
        }
    }

    private fun findActualDownloadedFile(dir: File, processId: String): File? {
        return dir.listFiles()
            ?.asSequence()
            ?.filter { 
                it.name.contains(processId) &&
                !it.name.endsWith(".part") && 
                !it.name.endsWith(".temp") && 
                !it.name.endsWith(".ytdl") &&
                !it.name.endsWith(".aria2")
            }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun validateFileIntegrity(file: File, downloadType: String?): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        
        if (!file.name.endsWith(".mp4", true) &&
            !file.name.endsWith(".m4a", true) &&
            !file.name.endsWith(".mp3", true) &&
            !file.name.endsWith(".webm", true)
        ) {
            return true
        }

        val isAudioExpected = downloadType.equals("audio", ignoreCase = true) ||
            downloadType.equals("Áudio", ignoreCase = true)
        val isVideoExpected = downloadType.equals("video", ignoreCase = true) ||
            downloadType.equals("Vídeo", ignoreCase = true)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            if (duration > 0L && (hasVideo || hasAudio)) {
                if (isAudioExpected && !hasAudio) return false
                if (isVideoExpected && !hasVideo) return false
                return true
            }
        } catch (e: Exception) {
            AppLogger.w("MediaMetadataRetriever falhou, tentando MediaExtractor: ${e.message}", "DownloadWorker")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Fallback via MediaExtractor (para containers WebM/AV1/Opus válidos)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasVideoTrack = false
            var hasAudioTrack = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) hasVideoTrack = true
                if (mime.startsWith("audio/")) hasAudioTrack = true
            }
            if (!hasVideoTrack && !hasAudioTrack) return false
            if (isAudioExpected && !hasAudioTrack) return false
            if (isVideoExpected && !hasVideoTrack) return false
            return true
        } catch (e: Exception) {
            AppLogger.e("Falha na integridade MediaExtractor", "DownloadWorker", e)
            return false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Downloads do FPtube",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(title: String, content: String, platform: String? = null): ForegroundInfo {
        val notificationId = 3000
        
        val notificationTitle = if (!platform.isNullOrEmpty()) {
            "Baixando — $platform"
        } else {
            title
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(notificationTitle)
            .setContentText(content)
            .setSubText(if (!platform.isNullOrEmpty()) title else null)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
