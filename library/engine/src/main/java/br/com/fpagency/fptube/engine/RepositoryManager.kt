package br.com.fpagency.fptube.engine

import br.com.fpagency.fptube.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
        try {
            AppLogger.i("Buscando repositório: $url", "RepositoryManager")
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                AppLogger.e("Erro ao buscar repositório: HTTP ${connection.responseCode}", "RepositoryManager")
                return@withContext emptyList()
            }

            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<RepoComponent>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RepoComponent(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        version = obj.optString("version", "1.0"),
                        size = obj.optString("size", "Desconhecido"),
                        downloadUrl = obj.getString("downloadUrl")
                    )
                )
            }
            list
        } catch (e: Exception) {
            AppLogger.e("Falha ao processar repositório: $url", "RepositoryManager", e)
            emptyList()
        }
    }
}
