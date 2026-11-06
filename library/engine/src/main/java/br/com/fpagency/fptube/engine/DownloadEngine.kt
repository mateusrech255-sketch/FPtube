package br.com.fpagency.fptube.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import br.com.fpagency.fptube.core.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class EngineResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeProcesses = Collections.synchronizedSet(mutableSetOf<String>())

    companion object {
        private const val CHUNK_SIZE = 4L * 1024L * 1024L // 4 MB por chunk para evitar throttling do YouTube
        private const val PARALLEL_CONNECTIONS = 6
    }

    suspend fun execute(
        url: String,
        outputDir: File,
        format: String,
        options: Map<String, String> = emptyMap(),
        extraArgs: List<Pair<String, String>> = emptyList(),
        processId: String? = null,
        onProgress: (Float, Long, String) -> Unit
    ): EngineResult = withContext(Dispatchers.IO) {
        if (processId != null) {
            activeProcesses.add(processId)
        }
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val ext = options["ext"]?.takeIf { it.isNotBlank() } ?: "mp4"
            val userAgent = options["User-Agent"]?.takeIf { it.isNotBlank() }
                ?: YoutubeDLUtils.VISIONOS_USER_AGENT
            val audioUrl = options["audio_url"]?.takeIf { it.isNotBlank() }

            val baseId = processId ?: "dl_${System.currentTimeMillis()}"
            val outputFile = File(outputDir, "media_${baseId}.$ext")

            if (audioUrl != null) {
                val tempVideoFile = File(outputDir, "temp_v_${baseId}.part")
                val tempAudioFile = File(outputDir, "temp_a_${baseId}.part")

                try {
                    val videoSuccess = downloadStreamToFile(
                        streamUrl = url,
                        targetFile = tempVideoFile,
                        userAgent = userAgent,
                        processId = processId
                    ) { progress, eta, speedStr ->
                        onProgress(progress * 0.90f, eta, speedStr)
                    }
                    if (!videoSuccess.first) {
                        tempVideoFile.delete()
                        return@withContext EngineResult(1, "", videoSuccess.second)
                    }

                    val audioSuccess = downloadStreamToFile(
                        streamUrl = audioUrl,
                        targetFile = tempAudioFile,
                        userAgent = userAgent,
                        processId = processId
                    ) { progress, eta, speedStr ->
                        onProgress(90f + (progress * 0.07f), eta, speedStr)
                    }

                    if (audioSuccess.first && tempAudioFile.exists() && tempAudioFile.length() > 0L) {
                        onProgress(98f, 1L, "Mesclando áudio/vídeo...")
                        val muxed = muxVideoAndAudio(tempVideoFile, tempAudioFile, outputFile, ext)
                        if (muxed && outputFile.exists() && outputFile.length() > 0L) {
                            tempVideoFile.delete()
                            tempAudioFile.delete()
                        } else {
                            outputFile.delete()
                            tempVideoFile.renameTo(outputFile)
                            tempAudioFile.delete()
                        }
                    } else {
                        tempAudioFile.delete()
                        tempVideoFile.renameTo(outputFile)
                    }
                } finally {
                    if (tempVideoFile.exists()) tempVideoFile.delete()
                    if (tempAudioFile.exists()) tempAudioFile.delete()
                }
            } else {
                val targetHeight = options["target_height"]?.toIntOrNull() ?: 0
                val dlWeight = if (targetHeight > 0) 0.85f else 1.0f
                val result = downloadStreamToFile(
                    streamUrl = url,
                    targetFile = outputFile,
                    userAgent = userAgent,
                    processId = processId
                ) { progress, eta, speedStr ->
                    onProgress(progress * dlWeight, eta, speedStr)
                }
                if (!result.first) {
                    outputFile.delete()
                    return@withContext EngineResult(1, "", result.second)
                }
            }

            val targetHeight = options["target_height"]?.toIntOrNull() ?: 0
            if (targetHeight > 0 && ext.equals("mp4", ignoreCase = true) && outputFile.exists()) {
                val upscaledTemp = File(outputDir, "temp_up_${baseId}.part")
                try {
                    val didUpscale = VideoUpscaler.upscaleIfNeeded(
                        inputFile = outputFile,
                        outputFile = upscaledTemp,
                        targetShortSide = targetHeight
                    ) { upProg ->
                        onProgress(85f + (upProg * 0.14f), 1L, "Processando ${targetHeight}p...")
                    }
                    if (didUpscale && upscaledTemp.exists() && upscaledTemp.length() > 0L) {
                        outputFile.delete()
                        upscaledTemp.renameTo(outputFile)
                    } else {
                        upscaledTemp.delete()
                    }
                } finally {
                    if (upscaledTemp.exists()) upscaledTemp.delete()
                }
            }

            val shouldExtractAudio = options["extract_audio"] == "true"
            if (shouldExtractAudio && outputFile.exists()) {
                val audioOnlyTemp = File(outputDir, "temp_audio_${baseId}.part")
                try {
                    onProgress(98f, 1L, "Extraindo áudio...")
                    val didExtract = extractAudioTrack(outputFile, audioOnlyTemp)
                    if (didExtract && audioOnlyTemp.exists() && audioOnlyTemp.length() > 0L) {
                        outputFile.delete()
                        audioOnlyTemp.renameTo(outputFile)
                    } else {
                        audioOnlyTemp.delete()
                    }
                } finally {
                    if (audioOnlyTemp.exists()) audioOnlyTemp.delete()
                }
            }

            onProgress(100f, 0L, "Concluído")
            EngineResult(0, "Download completed successfully: ${outputFile.absolutePath}", "")
        } catch (e: Exception) {
            AppLogger.e("Erro em DownloadEngine.execute", "DownloadEngine", e)
            EngineResult(1, "", e.message ?: "Unknown error during download")
        } finally {
            if (processId != null) {
                activeProcesses.remove(processId)
            }
        }
    }

    private fun isCancelled(processId: String?): Boolean {
        return processId != null && !activeProcesses.contains(processId)
    }

    private suspend fun downloadStreamToFile(
        streamUrl: String,
        targetFile: File,
        userAgent: String,
        processId: String?,
        onProgress: (Float, Long, String) -> Unit
    ): Pair<Boolean, String> = coroutineScope {
        val clenMatch = Regex("""[?&]clen=(\d+)""").find(streamUrl)
        val clenBytes = clenMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val isGoogleVideo = streamUrl.contains("googlevideo.com")

        if (isGoogleVideo && clenBytes > 0L) {
            return@coroutineScope downloadChunkedGoogleVideo(
                streamUrl = streamUrl,
                totalBytes = clenBytes,
                targetFile = targetFile,
                userAgent = userAgent,
                processId = processId,
                onProgress = onProgress
            )
        }

        // Download padrão para outras plataformas ou streams sem clen
        try {
            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@coroutineScope Pair(false, "HTTP error code: $responseCode")
            }

            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.contains("text/html")) {
                return@coroutineScope Pair(false, "Servidor retornou página HTML em vez de mídia ($contentType)")
            }

            val fileLength = connection.contentLengthLong
            val startTime = System.currentTimeMillis()
            var bytesDownloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled(processId)) {
                            return@coroutineScope Pair(false, "Download cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progress = if (fileLength > 0) {
                            (bytesDownloaded * 100f) / fileLength
                        } else {
                            0f
                        }
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsedSec > 0) bytesDownloaded / elapsedSec else 0.0
                        val speedStr = formatSpeed(speed)
                        val eta = if (speed > 0 && fileLength > 0) ((fileLength - bytesDownloaded) / speed).toLong() else 0L
                        onProgress(progress, eta, speedStr)
                    }
                    output.flush()
                }
            }

            if (fileLength > 0 && bytesDownloaded < fileLength) {
                return@coroutineScope Pair(false, "Download incompleto ($bytesDownloaded de $fileLength bytes)")
            }
            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Erro de conexão durante o download")
        }
    }

    private suspend fun downloadChunkedGoogleVideo(
        streamUrl: String,
        totalBytes: Long,
        targetFile: File,
        userAgent: String,
        processId: String?,
        onProgress: (Float, Long, String) -> Unit
    ): Pair<Boolean, String> = coroutineScope {
        try {
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }

            val totalChunks = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            val nextChunkIndex = AtomicInteger(0)
            val totalDownloaded = AtomicLong(0L)
            val startTime = System.currentTimeMillis()
            val workerCount = minOf(PARALLEL_CONNECTIONS, totalChunks).coerceAtLeast(1)

            val baseStreamUrl = streamUrl.replace(Regex("""([?&])range=[^&]+"""), "")

            val workers = (0 until workerCount).map {
                async(Dispatchers.IO) {
                    while (true) {
                        if (isCancelled(processId)) {
                            return@async Pair(false, "Download cancelled")
                        }
                        val chunkIdx = nextChunkIndex.getAndIncrement()
                        if (chunkIdx >= totalChunks) break

                        val rangeStart = chunkIdx.toLong() * CHUNK_SIZE
                        val rangeEnd = minOf(rangeStart + CHUNK_SIZE - 1, totalBytes - 1)
                        val expectedChunkLen = rangeEnd - rangeStart + 1

                        var chunkSuccess = false
                        var lastErr = ""

                        for (attempt in 0..2) {
                            if (isCancelled(processId)) {
                                return@async Pair(false, "Download cancelled")
                            }
                            var writtenInAttempt = 0L
                            try {
                                val sep = if (baseStreamUrl.contains("?")) "&" else "?"
                                val chunkUrl = "${baseStreamUrl}${sep}range=$rangeStart-$rangeEnd"
                                val conn = URL(chunkUrl).openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 10000
                                conn.readTimeout = 20000
                                conn.setRequestProperty("User-Agent", userAgent)
                                conn.connect()

                                val code = conn.responseCode
                                if (code !in 200..299) {
                                    lastErr = "HTTP $code no chunk $chunkIdx"
                                    delay(300L * (attempt + 1))
                                    continue
                                }

                                val cType = conn.contentType?.lowercase() ?: ""
                                if (cType.contains("text/html")) {
                                    return@async Pair(false, "Recebido HTML em vez de mídia no chunk $chunkIdx")
                                }

                                RandomAccessFile(targetFile, "rw").use { raf ->
                                    raf.seek(rangeStart)
                                    conn.inputStream.use { input ->
                                        val buf = ByteArray(64 * 1024)
                                        var r: Int
                                        while (input.read(buf).also { r = it } != -1) {
                                            if (isCancelled(processId)) {
                                                return@async Pair(false, "Download cancelled")
                                            }
                                            raf.write(buf, 0, r)
                                            writtenInAttempt += r
                                            val downloadedNow = totalDownloaded.addAndGet(r.toLong())

                                            val progress = ((downloadedNow * 100f) / totalBytes).coerceIn(0f, 100f)
                                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                                            val speed = if (elapsedSec > 0.05) downloadedNow / elapsedSec else 0.0
                                            val speedStr = formatSpeed(speed)
                                            val eta = if (speed > 0) ((totalBytes - downloadedNow) / speed).toLong().coerceAtLeast(0L) else 0L
                                            onProgress(progress, eta, speedStr)
                                        }
                                    }
                                }

                                if (writtenInAttempt == expectedChunkLen) {
                                    chunkSuccess = true
                                    break
                                } else {
                                    totalDownloaded.addAndGet(-writtenInAttempt)
                                    lastErr = "Chunk $chunkIdx incompleto ($writtenInAttempt/$expectedChunkLen)"
                                    delay(300L * (attempt + 1))
                                }
                            } catch (e: Exception) {
                                if (writtenInAttempt > 0) {
                                    totalDownloaded.addAndGet(-writtenInAttempt)
                                }
                                lastErr = e.message ?: "Erro de rede no chunk $chunkIdx"
                                delay(300L * (attempt + 1))
                            }
                        }

                        if (!chunkSuccess) {
                            return@async Pair(false, lastErr)
                        }
                    }
                    Pair(true, "")
                }
            }

            val results = workers.awaitAll()
            val failure = results.firstOrNull { !it.first }
            if (failure != null) {
                return@coroutineScope failure
            }

            if (targetFile.length() != totalBytes) {
                return@coroutineScope Pair(false, "Tamanho final divergente (${targetFile.length()} vs $totalBytes)")
            }

            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Falha no download em chunks")
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File, ext: String): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoFormat = format
                    break
                }
            }

            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioFormat = format
                    break
                }
            }

            if (videoFormat == null || audioFormat == null) {
                return false
            }

            val muxerFormat = if (ext.equals("webm", ignoreCase = true)) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)
            val videoTrackIndex = muxer.addTrack(videoFormat)
            val audioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                val sampleTime = videoExtractor.sampleTime
                if (sampleSize < 0 || sampleTime < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            while (true) {
                buffer.clear()
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                val sampleTime = audioExtractor.sampleTime
                if (sampleSize < 0 || sampleTime < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            return outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            AppLogger.w("Muxing falhou (${e.message}), mantendo stream de vídeo", "DownloadEngine")
            return false
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun extractAudioTrack(inputFile: File, outputFile: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var hasVideo = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                } else if (mime.startsWith("video/")) {
                    hasVideo = true
                }
            }

            // Se não tem vídeo, o arquivo já é puramente áudio
            if (!hasVideo && audioTrackIndex >= 0) {
                return false
            }

            if (audioTrackIndex < 0 || audioFormat == null) return false
            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = try {
                muxer.addTrack(audioFormat)
            } catch (e: Exception) {
                val cleanFormat = MediaFormat.createAudioFormat(
                    audioFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_AUDIO_AAC,
                    audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                )
                if (audioFormat.containsKey("csd-0")) {
                    cleanFormat.setByteBuffer("csd-0", audioFormat.getByteBuffer("csd-0"))
                }
                muxer.addTrack(cleanFormat)
            }
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastPts = -1L
            var skippedNegative = 0

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 && skippedNegative < 10) {
                    skippedNegative++
                    extractor.advance()
                    continue
                }
                val safePts = if (sampleTime <= lastPts) lastPts + 1000L else sampleTime
                lastPts = safePts
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = safePts
                bufferInfo.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0

                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            return outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            AppLogger.e("Erro ao extrair áudio de vídeo: ${e.message}", "DownloadEngine", e)
            return false
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024.0) {
            "${String.format("%.1f", kb / 1024.0)} MB/s"
        } else {
            "${String.format("%.1f", kb)} KB/s"
        }
    }

    fun cancel(processId: String?) {
        if (processId != null) {
            activeProcesses.remove(processId)
        }
    }
}
