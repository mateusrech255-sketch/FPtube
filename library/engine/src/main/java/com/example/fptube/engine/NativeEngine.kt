package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeEngine @Inject constructor(
    private val settingsManager: SettingsManager
) {

    companion object {
        private const val WORKER_URL = "https://fptube-cache.fpagency.com.br/"
    }

    /**
     * Tenta buscar metadados via Cloudflare Worker ou localmente.
     * Restrito estritamente ao YouTube.
     */
    suspend fun fetchNativeMetadata(videoUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        val urlToExtract = videoUrl.trim()
        
        // Proteção extra: Evita qualquer chamada cloud para TikTok, Kwai ou outras plataformas.
        if (PlatformDetector.detect(urlToExtract) != Platform.YOUTUBE) {
            return@withContext null
        }

        val engineMode = try {
            settingsManager.cloudEngineMode.first()
        } catch (e: Exception) {
            "Automático"
        }

        if (engineMode == "Somente Local") {
            return@withContext fetchFromLocalInnerTube(urlToExtract)
        }

        val cloudResult = fetchFromCloud(urlToExtract)
        if (cloudResult != null) return@withContext cloudResult
        
        return@withContext fetchFromLocalInnerTube(urlToExtract)
    }

    private suspend fun fetchFromCloud(videoUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val videoId = extractVideoId(videoUrl) ?: "unknown"
        
        val customUrl = settingsManager.cloudWorkerUrl.first()
        val apiKey = settingsManager.cloudApiKey.first()
        val baseUrl = customUrl ?: WORKER_URL
        
        val target = "$baseUrl?url=${java.net.URLEncoder.encode(videoUrl, "UTF-8")}"
        
        var responseCode = -1
        var cacheStatus = "NONE"
        var success = false

        try {
            val url = URL(target)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            if (apiKey.isNotEmpty()) {
                conn.setRequestProperty("X-API-Key", apiKey)
            }
            
            responseCode = conn.responseCode
            cacheStatus = conn.getHeaderField("X-FPtube-Cache") ?: "NONE"

            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)
                if (json.optBoolean("success")) {
                    success = true
                    return@withContext json.optJSONObject("data")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Erro na requisição cloud: ${e.message}", "FPtube_CLOUD")
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val fallback = if (success) "Não" else "Sim"
            AppLogger.i("""
                [FPtube CLOUD]
                Worker Request
                URL: $baseUrl
                Video ID: $videoId
                Tempo: ${duration}ms
                Cache: $cacheStatus
                Resposta: $responseCode
                Fallback: $fallback
            """.trimIndent(), "FPtube_CLOUD")
        }
        null
    }

    private suspend fun fetchFromLocalInnerTube(videoUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(videoUrl) ?: return@withContext null
            AppLogger.i("[NativeEngine] LOCAL_METADATA_START | ID: $videoId", "FPtube_AUDIT")
            
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "17.31.35")
                        put("platform", "MOBILE")
                        put("hl", "pt-BR")
                        put("gl", "BR")
                    })
                })
            }

            conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            AppLogger.i("[NativeEngine] LOCAL_METADATA_RESP | Code: $responseCode", "FPtube_AUDIT")

            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                AppLogger.i("[NativeEngine] LOCAL_METADATA_SUCCESS", "FPtube_AUDIT")
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("[NativeEngine] LOCAL_METADATA_FAIL | ${e.message}", "FPtube_AUDIT", e)
            null
        }
    }

    private fun extractVideoId(url: String): String? {
        val regex = Regex("""(?:v=|\/shorts\/|\/live\/|youtu\.be\/|\/embed\/|\/v\/|vi\/|e\/|watch\?v=|&v=)([^#\&\?]{11})""")
        return regex.find(url)?.groupValues?.get(1)
    }
}
