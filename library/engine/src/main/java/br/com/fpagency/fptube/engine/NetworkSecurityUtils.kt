package br.com.fpagency.fptube.engine

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Utilitário central de segurança de rede e proteção contra SSRF, Path Traversal e DoS de memória.
 *
 * - Valida esquemas (http/https ou estritamente https quando exigido).
 * - Bloqueia conexões e redirecionamentos para endereços loopback, link-local, site-local (RFC 1918),
 *   multicast e endpoints de metadados em nuvem (169.254.169.254).
 * - Limita o tamanho máximo de leitura de respostas de texto (HTML/JSON) para prevenir OOM.
 * - Sanitiza identificadores de arquivo para prevenir Path Traversal (`../`).
 */
object NetworkSecurityUtils {

    const val DEFAULT_MAX_TEXT_BYTES = 8 * 1024 * 1024 // 8 MB máximo para HTML/JSON

    private val SAFE_ID_REGEX = Regex("""^[a-zA-Z0-9._-]+$""")

    /**
     * Verifica se a URL é segura para conexão externa (previne SSRF para redes locais/privadas).
     */
    fun isSafeExternalUrl(
        rawUrl: String?,
        requireHttps: Boolean = false,
        resolveDns: Boolean = true
    ): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        return try {
            val parsed = URL(rawUrl.trim())
            val protocol = parsed.protocol.lowercase()
            if (requireHttps) {
                if (protocol != "https") return false
            } else {
                if (protocol != "http" && protocol != "https") return false
            }

            // Rejeita credenciais embutidas na URL (user:pass@host)
            if (!parsed.userInfo.isNullOrEmpty()) return false

            val host = parsed.host?.lowercase()?.trim() ?: return false
            if (host.isEmpty()) return false

            if (isDisallowedHostName(host)) return false

            if (resolveDns) {
                val addresses = InetAddress.getAllByName(host)
                if (addresses.isEmpty()) return false
                for (addr in addresses) {
                    if (isPrivateOrReservedAddress(addr)) {
                        return false
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun isDisallowedHostName(host: String): Boolean {
        val cleanHost = host.removeSurrounding("[", "]").lowercase()
        if (cleanHost == "localhost" ||
            cleanHost.endsWith(".localhost") ||
            cleanHost.endsWith(".local") ||
            cleanHost.endsWith(".internal") ||
            cleanHost == "0.0.0.0" ||
            cleanHost == "::1" ||
            cleanHost == "0:0:0:0:0:0:0:1" ||
            cleanHost == "169.254.169.254" ||
            cleanHost == "metadata.google.internal"
        ) {
            return true
        }

        // Verificação literal rápida para IPv4 privado antes mesmo de DNS
        val ipv4Parts = cleanHost.split(".")
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
            val b0 = ipv4Parts[0].toInt()
            val b1 = ipv4Parts[1].toInt()
            if (b0 == 0 || b0 == 10 || b0 == 127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && b1 in 16..31) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 100 && b1 in 64..127) return true // CGNAT RFC 6598
            if (b0 >= 224) return true // Multicast / Reserved
        }

        if (cleanHost.startsWith("fe80:") || cleanHost.startsWith("fc") || cleanHost.startsWith("fd")) {
            return true
        }

        return false
    }

    internal fun isPrivateOrReservedAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return true
        }
        val raw = addr.address
        if (raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            if (b0 == 0 || b0 == 10 || b0 == 127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && b1 in 16..31) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 100 && b1 in 64..127) return true
            if (b0 >= 224) return true
        } else if (raw.size == 16) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            // Unique Local Address (fc00::/7)
            if ((b0 and 0xFE) == 0xFC) return true
            // IPv4-mapped IPv6 (::ffff:a.b.c.d)
            val isIpv4Mapped = (0 until 10).all { raw[it].toInt() == 0 } &&
                (raw[10].toInt() and 0xFF) == 0xFF &&
                (raw[11].toInt() and 0xFF) == 0xFF
            if (isIpv4Mapped) {
                val v4b0 = raw[12].toInt() and 0xFF
                val v4b1 = raw[13].toInt() and 0xFF
                if (v4b0 == 0 || v4b0 == 10 || v4b0 == 127) return true
                if (v4b0 == 169 && v4b1 == 254) return true
                if (v4b0 == 172 && v4b1 in 16..31) return true
                if (v4b0 == 192 && v4b1 == 168) return true
                if (v4b0 == 100 && v4b1 in 64..127) return true
            }
            // Teredo / 6to4 loopback
            if (b0 == 0 && b1 == 0 && raw.take(15).all { it.toInt() == 0 } && raw[15].toInt() == 1) {
                return true
            }
        }
        return false
    }

    /**
     * Abre uma conexão HTTP(S) seguindo redirecionamentos manualmente com validação anti-SSRF
     * a cada salto. O chamador é responsável por chamar `disconnect()` na conexão retornada.
     */
    fun openSafeConnectionWithRedirects(
        startUrl: String,
        maxRedirects: Int = 8,
        connectTimeoutMs: Int = 10000,
        readTimeoutMs: Int = 15000,
        requireHttps: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        preserveCookies: Boolean = true
    ): Pair<String, HttpURLConnection>? {
        var currentUrl = startUrl.trim()
        var redirects = 0
        val cookies = mutableMapOf<String, String>()

        while (redirects <= maxRedirects) {
            if (!isSafeExternalUrl(currentUrl, requireHttps = requireHttps)) {
                return null
            }

            var conn: HttpURLConnection? = null
            var keepConnectionOpen = false
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (preserveCookies && cookies.isNotEmpty()) {
                        setRequestProperty(
                            "Cookie",
                            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        )
                    }
                }
                conn.connect()

                if (preserveCookies) {
                    conn.headerFields["Set-Cookie"]?.forEach { header ->
                        val pair = header.substringBefore(";").split("=", limit = 2)
                        if (pair.size == 2) {
                            cookies[pair[0].trim()] = pair[1].trim()
                        }
                    }
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    val nextUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(URL(currentUrl), location).toString()
                    }
                    currentUrl = nextUrl
                    redirects++
                    continue
                }

                keepConnectionOpen = true
                return Pair(currentUrl, conn)
            } catch (_: Exception) {
                return null
            } finally {
                if (!keepConnectionOpen) {
                    conn?.disconnect()
                }
            }
        }
        return null
    }

    /**
     * Lê uma InputStream até o limite máximo de bytes (`maxBytes`), prevenindo OOM
     * caso o servidor retorne um stream infinito ou arquivo binário gigante disfarçado de texto.
     */
    fun readBoundedText(inputStream: InputStream, maxBytes: Int = DEFAULT_MAX_TEXT_BYTES): String {
        val reader = inputStream.bufferedReader(Charsets.UTF_8)
        val sb = StringBuilder(minOf(maxBytes, 64 * 1024))
        val charBuf = CharArray(8192)
        var totalChars = 0
        val maxChars = maxBytes
        while (true) {
            val read = reader.read(charBuf)
            if (read == -1) break
            val allowed = minOf(read, maxChars - totalChars)
            if (allowed > 0) {
                sb.append(charBuf, 0, allowed)
                totalChars += allowed
            }
            if (totalChars >= maxChars) break
        }
        return sb.toString()
    }

    /**
     * Valida e sanitiza um ID de arquivo/plugin para impedir Path Traversal (`../`, `/`, `\`).
     */
    fun sanitizeFileId(rawId: String?): String? {
        if (rawId.isNullOrBlank()) return null
        val trimmed = rawId.trim()
        if (trimmed == "." || trimmed == ".." || trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return null
        }
        return if (SAFE_ID_REGEX.matches(trimmed)) trimmed else null
    }
}
