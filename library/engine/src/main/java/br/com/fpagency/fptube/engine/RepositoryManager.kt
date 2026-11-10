package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.engine.JsonSafeUtils.optCleanString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

data class RepoComponent(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val downloadUrl: String
)

@Singleton
class RepositoryManager @Inject constructor() {

    suspend fun fetchRepository(url: String): List<RepoComponent> = withContext(Dispatchers.IO) {
        if (!NetworkSecurityUtils.isSafeExternalUrl(url, requireHttps = true)) {
            AppLogger.w("Repositório rejeitado por segurança (HTTPS obrigatório / SSRF): $url", "RepositoryManager")
            return@withContext emptyList()
        }

        var connection: HttpURLConnection? = null
        try {
            AppLogger.i("Buscando repositório: $url", "RepositoryManager")
            val opened = NetworkSecurityUtils.openSafeConnectionWithRedirects(
                startUrl = url,
                maxRedirects = 3,
                connectTimeoutMs = 5000,
                readTimeoutMs = 5000,
                requireHttps = true
            ) ?: return@withContext emptyList()

            connection = opened.second

            if (connection.responseCode != 200) {
                AppLogger.e("Erro ao buscar repositório: HTTP ${connection.responseCode}", "RepositoryManager")
                return@withContext emptyList()
            }

            val jsonStr = connection.inputStream.use { NetworkSecurityUtils.readBoundedText(it, maxBytes = 1024 * 1024) }
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<RepoComponent>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val rawId = obj.optCleanString("id")
                val safeId = NetworkSecurityUtils.sanitizeFileId(rawId) ?: continue
                val dlUrl = obj.optCleanString("downloadUrl")
                if (!NetworkSecurityUtils.isSafeExternalUrl(dlUrl, requireHttps = true)) continue

                list.add(
                    RepoComponent(
                        id = safeId,
                        name = obj.optCleanString("name", safeId),
                        description = obj.optCleanString("description", ""),
                        version = obj.optCleanString("version", "1.0"),
                        size = obj.optCleanString("size", "Desconhecido"),
                        downloadUrl = dlUrl
                    )
                )
            }
            list
        } catch (e: Exception) {
            AppLogger.e("Falha ao processar repositório: $url", "RepositoryManager", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
