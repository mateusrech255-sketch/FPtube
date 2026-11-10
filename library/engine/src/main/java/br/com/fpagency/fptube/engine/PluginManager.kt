package br.com.fpagency.fptube.engine

import android.content.Context
import br.com.fpagency.fptube.core.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pluginsDir = File(context.noBackupFilesDir, "plugins")

    init {
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
    }

    fun isPluginInstalled(id: String): Boolean {
        val safeId = NetworkSecurityUtils.sanitizeFileId(id) ?: return false
        val pluginFile = File(pluginsDir, safeId)
        return pluginFile.exists()
    }

    fun getPluginPath(id: String): String? {
        val safeId = NetworkSecurityUtils.sanitizeFileId(id) ?: return null
        val pluginFile = File(pluginsDir, safeId)
        return if (pluginFile.exists()) pluginFile.absolutePath else null
    }

    suspend fun downloadPlugin(
        id: String,
        url: String,
        onProgress: (Int) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val safeId = NetworkSecurityUtils.sanitizeFileId(id)
            ?: return@withContext Result.failure(SecurityException("ID de plugin inválido"))

        if (!NetworkSecurityUtils.isSafeExternalUrl(url, requireHttps = true)) {
            return@withContext Result.failure(SecurityException("URL de plugin insegura ou não permitida"))
        }

        val outputFile = File(pluginsDir, "$safeId.tmp")
        var connection: HttpURLConnection? = null
        try {
            AppLogger.i("Baixando plugin: $safeId de $url", "PluginManager")
            val opened = NetworkSecurityUtils.openSafeConnectionWithRedirects(
                startUrl = url,
                maxRedirects = 5,
                connectTimeoutMs = 10000,
                readTimeoutMs = 15000,
                requireHttps = true
            ) ?: return@withContext Result.failure(SecurityException("Falha ao estabelecer conexão HTTPS segura para o plugin"))

            connection = opened.second

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "Erro HTTP: ${connection.responseCode}"
                AppLogger.e(errorMsg, "PluginManager")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val fileLength = connection.contentLength
            connection.inputStream.use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (inputStream.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress((total * 100 / fileLength).toInt())
                        }
                        outputStream.write(data, 0, count)
                    }
                    outputStream.flush()
                }
            }

            val finalFile = File(pluginsDir, safeId)
            if (outputFile.renameTo(finalFile)) {
                finalFile.setExecutable(true)
                AppLogger.i("Plugin $safeId instalado com sucesso", "PluginManager")
                Result.success(true)
            } else {
                Result.failure(Exception("Falha ao renomear arquivo temporário"))
            }
        } catch (e: Exception) {
            outputFile.delete()
            AppLogger.e("Erro no download do plugin $safeId", "PluginManager", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun uninstallPlugin(id: String): Boolean {
        val safeId = NetworkSecurityUtils.sanitizeFileId(id) ?: return false
        val pluginFile = File(pluginsDir, safeId)
        return if (pluginFile.exists()) {
            val deleted = pluginFile.delete()
            AppLogger.i("Plugin $safeId desinstalado: $deleted", "PluginManager")
            deleted
        } else false
    }
}
