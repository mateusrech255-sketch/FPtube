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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeEngine @Inject constructor() {

    companion object {
        private val VIDEO_ID_REGEX = Regex("""(?:v=|\/shorts\/|\/live\/|youtu\.be\/|\/embed\/|\/v\/|vi\/|e\/|watch\?v=|&v=)([^#\&\?]{11})""")
    }

    suspend fun fetchNativeMetadata(videoUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        val urlToExtract = videoUrl.trim()

        if (PlatformDetector.detect(urlToExtract) != Platform.YOUTUBE) {
            return@withContext null
        }

        return@withContext fetchFromLocalInnerTube(urlToExtract)
    }

    private suspend fun fetchFromLocalInnerTube(videoUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val videoId = extractVideoId(videoUrl) ?: return@withContext null
            AppLogger.i("[NativeEngine] LOCAL_METADATA_START | ID: $videoId", "FPtube_AUDIT")

            val url = URL("https://www.youtube.com/youtubei/v1/player")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

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

            conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            AppLogger.i("[NativeEngine] LOCAL_METADATA_RESP | Code: $responseCode", "FPtube_AUDIT")

            if (responseCode == 200) {
                val response = conn.inputStream.use { NetworkSecurityUtils.readBoundedText(it) }
                val rawJson = JSONObject(response)
                val videoDetails = rawJson.optJSONObject("videoDetails") ?: return@withContext null
                val title = videoDetails.optCleanString("title")
                if (title.isBlank()) return@withContext null

                val normalized = JSONObject().apply {
                    put("id", videoDetails.optCleanString("videoId", videoId))
                    put("title", title)
                    put("channelTitle", videoDetails.optCleanString("author", ""))
                    put("author", videoDetails.optCleanString("author", ""))
                    put("duration", videoDetails.optSafeInt("lengthSeconds", 0, minValue = 0).toString())
                    put("viewCount", videoDetails.optSafeLong("viewCount", 0L, minValue = 0L))
                    put("thumbnail", "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                }
                AppLogger.i("[NativeEngine] LOCAL_METADATA_SUCCESS", "FPtube_AUDIT")
                normalized
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("[NativeEngine] LOCAL_METADATA_FAIL | ${e.message}", "FPtube_AUDIT", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractVideoId(url: String): String? {
        return VIDEO_ID_REGEX.find(url)?.groupValues?.get(1)
    }
}
