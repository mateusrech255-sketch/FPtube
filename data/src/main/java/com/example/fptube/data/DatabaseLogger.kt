package br.com.fpagency.fptube.data

import br.com.fpagency.fptube.core.ILogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseLogger @Inject constructor(
    private val database: AppDatabase,
    private val settingsManager: SettingsManager
) : ILogger {

    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val MAX_LOGS = 1000
    private val DELETE_BATCH = 200
    private val SHEETS_URL = "https://script.google.com/macros/s/AKfycbz-5N7TVgT0bWznDo8LAxc2rXSdV2CM5xrGaxVoyXwv0oxHnNmedb9JUJ1YzePTu37z/exec"

    override fun i(message: String, tag: String) = log("INFO", tag, message)
    override fun w(message: String, tag: String, throwable: Throwable?) = log("WARNING", tag, message, throwable)
    override fun e(message: String, tag: String, throwable: Throwable?) = log("ERROR", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        loggerScope.launch {
            val enabled = settingsManager.logsEnabled.first()
            if (!enabled) return@launch

            val isSpam = message.contains("[download]") || (message.contains("at") && message.contains("ETA"))
            if (isSpam) return@launch

            // Sanitização de dados sensíveis antes de salvar no banco ou enviar para nuvem
            val sanitizedMessage = sanitize(message)
            val stackTrace = throwable?.stackTraceToString()?.let { sanitize(it) }

            try {
                val entity = LogEntity(
                    level = level,
                    tag = tag,
                    message = sanitizedMessage,
                    throwable = stackTrace
                )
                database.logDao().insert(entity)

                val toSheets = settingsManager.logsToSheets.first()
                // Apenas envia se for erro e se a telemetria estiver explicitamente ativa
                if (toSheets && level == "ERROR") {
                    sendErrorToSheets(entity)
                }

                // Limpeza mais agressiva e compactação periódica
                val count = database.logDao().getCount()
                if (count > MAX_LOGS) {
                    database.logDao().deleteOldest(count - MAX_LOGS + DELETE_BATCH)
                    // Executa VACUUM se a limpeza foi significativa para reclamar espaço em disco
                    try {
                        database.logDao().checkpoint(androidx.sqlite.db.SimpleSQLiteQuery("VACUUM"))
                    } catch (e: Exception) {
                        // Vacuum pode falhar se o banco estiver em uso intenso, ignoramos
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private fun sanitize(input: String): String {
        // Remove URLs completas
        val noUrls = input.replace(Regex("https?://[^\\s]+"), "[URL_REMOVED]")
        // Remove IDs de vídeo prováveis (11 caracteres alfanuméricos com - ou _) 
        // apenas se estiverem em contextos suspeitos ou isolados
        return noUrls.replace(Regex("(?<=v=|/)([a-zA-Z0-9_-]{11})(?=[&\\s?]|$)"), "[ID_REMOVED]")
    }

    private fun sendErrorToSheets(log: LogEntity) {
        try {
            val json = JSONObject().apply {
                put("model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("tag", log.tag)
                put("message", log.message)
                put("throwable", log.throwable ?: "N/A")
            }

            val url = URL(SHEETS_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            conn.outputStream.use { os ->
                val input = json.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            // fail silently
        }
    }
}
