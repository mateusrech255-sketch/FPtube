package br.com.fpagency.fptube.engine

import java.util.regex.Pattern

object PlatformDetector {

    private val platformPatterns = listOf(
        Platform.YOUTUBE to Pattern.compile(
            """^(https?://)?(www\.|m\.)?(youtube\.com|youtu\.be|youtube-nocookie\.com)/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.TIKTOK to Pattern.compile(
            """^(https?://)?(www\.|vm\.|vt\.)?tiktok\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.INSTAGRAM to Pattern.compile(
            """^(https?://)?(www\.|m\.)?instagram\.com/(p|reels|reel|tv)/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.FACEBOOK to Pattern.compile(
            """^(https?://)?(www\.|m\.|fb\.)?(facebook\.com|fb\.watch)/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.TWITTER to Pattern.compile(
            """^(https?://)?(www\.|mobile\.)?(twitter\.com|x\.com)/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.PINTEREST to Pattern.compile(
            """^(https?://)?(www\.|pin\.)?pinterest\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.THREADS to Pattern.compile(
            """^(https?://)?(www\.)?threads\.net/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.SNAPCHAT to Pattern.compile(
            """^(https?://)?(www\.)?snapchat\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.DAILYMOTION to Pattern.compile(
            """^(https?://)?(www\.)?dailymotion\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.SOUNDCLOUD to Pattern.compile(
            """^(https?://)?(www\.|on\.)?soundcloud\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.BLUESKY to Pattern.compile(
            """^(https?://)?(www\.)?bsky\.app/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.OK_RU to Pattern.compile(
            """^(https?://)?(www\.|m\.)?ok\.ru/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.KWAI to Pattern.compile(
            """^(https?://)?(www\.|v\.|s\.|k\.)?kwai\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.SNACKVIDEO to Pattern.compile(
            """^(https?://)?(www\.|v\.|s\.)?snackvideo\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.VIMEO to Pattern.compile(
            """^(https?://)?(www\.|player\.)?vimeo\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.REDDIT to Pattern.compile(
            """^(https?://)?(www\.|v\.)?reddit\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.TWITCH to Pattern.compile(
            """^(https?://)?(www\.|m\.|clips\.)?twitch\.tv/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.VK to Pattern.compile(
            """^(https?://)?(www\.|m\.)?vk\.com/.*$""",
            Pattern.CASE_INSENSITIVE
        ),
        Platform.TELEGRAM to Pattern.compile(
            """^(https?://)?(www\.|t\.)?me/.*$""",
            Pattern.CASE_INSENSITIVE
        )
    )

    fun detect(url: String?): Platform {
        if (url.isNullOrBlank()) return Platform.UNKNOWN
        val trimmedUrl = url.trim()
        
        for ((platform, pattern) in platformPatterns) {
            if (pattern.matcher(trimmedUrl).matches()) {
                return platform
            }
        }
        
        return if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            Platform.OTHER
        } else {
            Platform.UNKNOWN
        }
    }

    fun normalize(url: String): String {
        var normalized = url.trim()
        
        // Remove tracking parameters common in sharing
        if (normalized.contains("?")) {
            val base = normalized.substringBefore("?")
            val params = normalized.substringAfter("?")
            val filteredParams = params.split("&").filter { param ->
                !param.startsWith("si=") && 
                !param.startsWith("utm_") && 
                !param.startsWith("fbclid=") &&
                !param.startsWith("ref=")
            }.joinToString("&")
            
            normalized = if (filteredParams.isNotEmpty()) "$base?$filteredParams" else base
        }
        
        return normalized
    }
}
