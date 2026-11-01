package br.com.fpagency.fptube.engine

import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Provedor central de configuração para o yt-dlp.
 * Garante que a extração e o download usem os mesmos headers e User-Agent,
 * evitando erros HTTP 403 por inconsistência de assinatura.
 */
object YoutubeDLConfig {

    /**
     * User-Agents padronizados.
     * TIKTOK exige Desktop para evitar erros de reidratação.
     * KWAI e outros exigem Mobile para o Extrator Genérico funcionar.
     */
    private const val UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    private const val UA_MOBILE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36"

    /**
     * Retorna o User-Agent apropriado para a plataforma.
     */
    fun getUserAgent(platform: Platform): String {
        return if (platform == Platform.TIKTOK) UA_DESKTOP else UA_MOBILE
    }

    /**
     * Aplica as configurações base a um pedido do yt-dlp.
     * @param playerClient Client específico do YouTube para forçar (ex: "tv", "android_vr").
     *                     Se nulo, usa os clients padrão.
     */
    fun apply(request: YoutubeDLRequest, platform: Platform = Platform.UNKNOWN, playerClient: String? = null) {
        if (platform != Platform.YOUTUBE) {
            val ua = getUserAgent(platform)
            request.addOption("--user-agent", ua)
            getReferer(platform)?.let { referer ->
                request.addOption("--add-header", "Referer: $referer")
            }
        }
        
        // Headers base para simular uma requisição legítima
        request.addOption("--add-header", "Accept-Language: pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        
        // Opções de segurança e estabilidade
        request.addOption("--no-check-certificate")
        request.addOption("--no-cache-dir")
        request.addOption("--socket-timeout", "10")

        // Configurações específicas por plataforma
        if (platform == Platform.YOUTUBE) {
            if (!playerClient.isNullOrBlank() && playerClient != "android_vr" && playerClient != "android") {
                request.addOption("--extractor-args", "youtube:player-client=$playerClient")
            }
        }
    }

    private fun getReferer(platform: Platform): String? {
        return when (platform) {
            Platform.TIKTOK -> "https://www.tiktok.com/"
            Platform.INSTAGRAM -> "https://www.instagram.com/"
            Platform.FACEBOOK -> "https://www.facebook.com/"
            Platform.TWITTER -> "https://twitter.com/"
            Platform.KWAI -> "https://www.kwai.com/"
            else -> null
        }
    }

    /**
     * Retorna o mapa de opções padrão para o Worker de download.
     */
    fun getBaseOptions(platform: String? = null): Map<String, String> {
        val platformEnum = try { Platform.valueOf(platform ?: "UNKNOWN") } catch (e: Exception) { Platform.UNKNOWN }
        val ua = getUserAgent(platformEnum)

        return mutableMapOf(
            "--user-agent" to ua,
            "--no-check-certificate" to "",
            "--no-cache-dir" to "",
            "--restrict-filenames" to "",
            "--no-part" to "",
            "--no-mtime" to ""
        )
    }

    /**
     * Retorna a lista de argumentos extras (como headers repetidos).
     */
    fun getExtraArgs(): List<Pair<String, String>> {
        return listOf(
            "--add-header" to "Accept-Language: pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
        )
    }
}
