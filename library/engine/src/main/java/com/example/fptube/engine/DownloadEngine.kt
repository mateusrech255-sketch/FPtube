package br.com.fpagency.fptube.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    private val youtubeDL = YoutubeDL.getInstance()

    suspend fun execute(
        url: String,
        outputDir: File,
        format: String,
        options: Map<String, String> = emptyMap(),
        extraArgs: List<Pair<String, String>> = emptyList(),
        processId: String? = null,
        onProgress: (Float, Long, String) -> Unit
    ): EngineResult = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        // Usamos um template seguro com limitação de caracteres no ID para evitar OSError [Errno 36] File name too long
        // Comum em extratores genéricos (Kwai) que retornam a URL completa como ID.
        val prefix = if (processId != null) "[$processId]_" else ""
        request.addOption("-o", "${outputDir.absolutePath}/${prefix}%(id).100s.%(ext)s")
        request.addOption("-f", format)
        
        options.forEach { (key, value) ->
            if (value.isEmpty()) request.addOption(key)
            else request.addOption(key, value)
        }

        extraArgs.forEach { (key, value) ->
            if (value.isEmpty()) request.addOption(key)
            else request.addOption(key, value)
        }

        val response = youtubeDL.execute(request, processId) { progress, eta, speed ->
            onProgress(progress, eta, speed)
        }
        
        EngineResult(response.exitCode, response.out, response.err)
    }

    fun cancel(processId: String?) {
        if (processId != null) {
            try {
                youtubeDL.destroyProcessById(processId)
            } catch (e: Exception) {
                // Ignore failure to destroy process
            }
        }
    }
}
