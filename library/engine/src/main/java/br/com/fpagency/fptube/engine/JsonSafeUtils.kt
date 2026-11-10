package br.com.fpagency.fptube.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Utilitários seguros e de baixa alocação para parsing JSON e SSR HTML.
 * Evita armadilhas do org.json no Android (como optString retornando "null" literal
 * quando a chave possui JSONObject.NULL) e suporta múltiplos formatos SSR (LD+JSON,
 * __INITIAL_STATE__, __NUXT__, SIGI_STATE, __UNIVERSAL_DATA_FOR_REHYDRATION__).
 */
object JsonSafeUtils {

    private val LD_JSON_SCRIPT_REGEX = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE
    )
    private val INITIAL_STATE_REGEX = Regex(
        """window\.__INITIAL_STATE__\s*=\s*(\{[\s\S]*?\})\s*;?\s*</script>""",
        RegexOption.IGNORE_CASE
    )
    private val SIGI_STATE_REGEX = Regex(
        """<script[^>]*id=["']SIGI_STATE["'][^>]*>\s*(\{[\s\S]*?\})\s*</script>""",
        RegexOption.IGNORE_CASE
    )
    private val HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")
    private val DEC_ENTITY_REGEX = Regex("""&#(\d+);""")

    fun JSONObject.optCleanString(key: String, default: String = ""): String {
        if (!has(key) || isNull(key)) return default
        val raw = opt(key) ?: return default
        val str = raw.toString().trim()
        return if (str.isEmpty() || str.equals("null", ignoreCase = true) || str.equals("undefined", ignoreCase = true)) {
            default
        } else {
            str
        }
    }

    fun JSONObject.optSafeInt(key: String, default: Int = 0, minValue: Int = 0): Int {
        if (!has(key) || isNull(key)) return default
        val raw = opt(key) ?: return default
        val parsed = when (raw) {
            is Number -> raw.toInt()
            is String -> {
                val cleaned = raw.trim().removeSuffix("p").removeSuffix("P")
                cleaned.toIntOrNull() ?: cleaned.toDoubleOrNull()?.toInt() ?: default
            }
            else -> default
        }
        return if (parsed < minValue) default else parsed
    }

    fun JSONObject.optSafeLong(key: String, default: Long = 0L, minValue: Long = 0L): Long {
        if (!has(key) || isNull(key)) return default
        val raw = opt(key) ?: return default
        val parsed = when (raw) {
            is Number -> raw.toLong()
            is String -> {
                val cleaned = raw.trim()
                cleaned.toLongOrNull() ?: cleaned.toDoubleOrNull()?.toLong() ?: default
            }
            else -> default
        }
        return if (parsed < minValue) default else parsed
    }

    fun JSONObject.optSafeDouble(key: String, default: Double = 0.0, minValue: Double = 0.0): Double {
        if (!has(key) || isNull(key)) return default
        val raw = opt(key) ?: return default
        val parsed = when (raw) {
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) default else d
            }
            is String -> raw.trim().toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: default
            else -> default
        }
        return if (parsed < minValue) default else parsed
    }

    /**
     * Decodifica sequências de escape JS/HTML em passagem única apenas quando necessário,
     * evitando alocações intermediárias desnecessárias.
     */
    fun decodeEscapedString(raw: String): String {
        if (raw.isEmpty()) return raw
        val hasBackslash = raw.indexOf('\\') >= 0
        val hasAmpersand = raw.indexOf('&') >= 0
        if (!hasBackslash && !hasAmpersand) return raw.trim()

        var result = raw
        if (hasBackslash) {
            result = result
                .replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003D", "=")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\n", " ")
        }
        if (hasAmpersand) {
            result = result
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
            if (result.contains("&#x")) {
                result = HEX_ENTITY_REGEX.replace(result) { m ->
                    m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
                }
            }
            if (result.contains("&#")) {
                result = DEC_ENTITY_REGEX.replace(result) { m ->
                    m.groupValues[1].toIntOrNull(10)?.toChar()?.toString() ?: m.value
                }
            }
        }
        return result.trim()
    }

    /**
     * Extrai um objeto VideoObject de blocos <script type="application/ld+json">
     * como fallback resiliente a mudanças de SSR (Kwai, SnackVideo, Instagram, TikTok).
     */
    fun extractLdJsonVideoObject(html: String): JSONObject? {
        if (!html.contains("ld+json", ignoreCase = true)) return null
        for (match in LD_JSON_SCRIPT_REGEX.findAll(html)) {
            val rawJson = match.groupValues[1].trim()
            if (rawJson.isEmpty()) continue
            try {
                if (rawJson.startsWith("[")) {
                    val arr = JSONArray(rawJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        findVideoObjectInJson(obj)?.let { return it }
                    }
                } else if (rawJson.startsWith("{")) {
                    val obj = JSONObject(rawJson)
                    findVideoObjectInJson(obj)?.let { return it }
                }
            } catch (_: Exception) {
                // Ignora blocos LD+JSON malformados e tenta o próximo
            }
        }
        return null
    }

    private fun findVideoObjectInJson(obj: JSONObject): JSONObject? {
        val type = obj.optCleanString("@type")
        if (type.equals("VideoObject", ignoreCase = true) || obj.optCleanString("contentUrl").isNotEmpty()) {
            return obj
        }
        obj.optJSONObject("video")?.let { videoObj ->
            if (videoObj.optCleanString("contentUrl").isNotEmpty() || videoObj.optCleanString("url").isNotEmpty()) {
                return videoObj
            }
        }
        val graph = obj.optJSONArray("@graph")
        if (graph != null) {
            for (i in 0 until graph.length()) {
                val item = graph.optJSONObject(i) ?: continue
                findVideoObjectInJson(item)?.let { return it }
            }
        }
        return null
    }

    fun extractInitialStateJson(html: String): JSONObject? {
        if (!html.contains("__INITIAL_STATE__")) return null
        val raw = INITIAL_STATE_REGEX.find(html)?.groupValues?.get(1)?.trim() ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun extractSigiStateJson(html: String): JSONObject? {
        if (!html.contains("SIGI_STATE")) return null
        val raw = SIGI_STATE_REGEX.find(html)?.groupValues?.get(1)?.trim() ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }
}
