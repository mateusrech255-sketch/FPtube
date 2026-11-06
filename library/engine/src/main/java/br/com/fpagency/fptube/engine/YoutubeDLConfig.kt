package br.com.fpagency.fptube.engine

object YoutubeDLConfig {

    private const val UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    private const val UA_MOBILE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36"

    fun getUserAgent(platform: Platform): String {
        return if (platform == Platform.TIKTOK) UA_DESKTOP else UA_MOBILE
    }

    fun getBaseOptions(platform: String? = null): Map<String, String> {
        val platformEnum = try { Platform.valueOf(platform ?: "UNKNOWN") } catch (e: Exception) { Platform.UNKNOWN }
        val ua = getUserAgent(platformEnum)

        return mutableMapOf(
            "User-Agent" to ua
        )
    }

    fun getExtraArgs(): List<Pair<String, String>> {
        return listOf(
            "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
        )
    }
}
