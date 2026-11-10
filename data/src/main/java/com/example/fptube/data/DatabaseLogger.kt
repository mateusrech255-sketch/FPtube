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
    private val insertCounter = java.util.concurrent.atomic.AtomicInteger(0)
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

                // Verifica contagem periodicamente (a cada 50 inserções) para evitar COUNT(*) em cada log
                if (insertCounter.incrementAndGet() % 50 == 0) {
                    val count = database.logDao().getCount()
                    if (count > MAX_LOGS) {
                        database.logDao().deleteOldest(count - MAX_LOGS + DELETE_BATCH)
                        try {
                            database.logDao().checkpoint(
                                androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
                // Silent fail
            }
        }
    }

    companion object {
        private val URL_SANITIZE_REGEX = Regex("https?://[^\\s\"'<>]+")
        private val VIDEO_ID_SANITIZE_REGEX = Regex("(?<=v=|/)([a-zA-Z0-9_-]{11})(?=[&\\s?]|$)")
        private val SECRET_PARAM_REGEX = Regex(
            "(?i)(api[_-]?key|token|access[_-]?token|auth|authorization|signature|sig|visitorData)\\s*[:=]\\s*([^\\s&\"',;]+)"
        )

        fun sanitizeLogText(input: String): String {
            val noUrls = input.replace(URL_SANITIZE_REGEX, "[URL_REMOVED]")
            val noIds = noUrls.replace(VIDEO_ID_SANITIZE_REGEX, "[ID_REMOVED]")
            return noIds.replace(SECRET_PARAM_REGEX) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }
        }
    }

    private fun sanitize(input: String): String = sanitizeLogText(input)

    private fun sendErrorToSheets(log: LogEntity) {
        var conn: HttpURLConnection? = null
        try {
            val json = JSONObject().apply {
                put("model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("tag", log.tag)
                put("message", log.message)
                put("throwable", log.throwable ?: "N/A")
            }

            val url = URL(SHEETS_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            conn.outputStream.use { os ->
                val input = json.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            conn.responseCode
        } catch (_: Exception) {
            // fail silently
        } finally {
            conn?.disconnect()
        }
    }
}
