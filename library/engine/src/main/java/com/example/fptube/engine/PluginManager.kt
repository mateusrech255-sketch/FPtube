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
        val pluginFile = File(pluginsDir, id)
        return pluginFile.exists()
    }

    fun getPluginPath(id: String): String? {
        val pluginFile = File(pluginsDir, id)
        return if (pluginFile.exists()) pluginFile.absolutePath else null
    }

    suspend fun downloadPlugin(
        id: String,
        url: String,
        onProgress: (Int) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val outputFile = File(pluginsDir, "$id.tmp")
        try {
            AppLogger.i("Baixando plugin: $id de $url", "PluginManager")
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "Erro HTTP: ${connection.responseCode}"
                AppLogger.e(errorMsg, "PluginManager")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

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
            outputStream.close()
            inputStream.close()

            val finalFile = File(pluginsDir, id)
            if (outputFile.renameTo(finalFile)) {
                finalFile.setExecutable(true)
                AppLogger.i("Plugin $id instalado com sucesso", "PluginManager")
                Result.success(true)
            } else {
                Result.failure(Exception("Falha ao renomear arquivo temporário"))
            }
        } catch (e: Exception) {
            outputFile.delete()
            AppLogger.e("Erro no download do plugin $id", "PluginManager", e)
            Result.failure(e)
        }
    }

    fun uninstallPlugin(id: String): Boolean {
        val pluginFile = File(pluginsDir, id)
        return if (pluginFile.exists()) {
            val deleted = pluginFile.delete()
            AppLogger.i("Plugin $id desinstalado: $deleted", "PluginManager")
            deleted
        } else false
    }
}
