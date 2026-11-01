package br.com.fpagency.fptube.core

import java.io.File
import java.util.Locale

object FormatUtils {
    fun convertSpeedLimit(limit: String): String {
        return when {
            limit.contains("kbps") -> limit.replace(" kbps", "K")
            limit.contains("Mbps") -> limit.replace(" Mbps", "M")
            else -> limit
        }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "Desconhecido"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var s = size.toDouble()
        var unitIndex = 0
        while (s >= 1024 && unitIndex < units.size - 1) {
            s /= 1024
            unitIndex++
        }
        return "%.2f %s".format(Locale.US, s, units[unitIndex])
    }

    fun formatQualityDisplay(resolution: String?, downloadType: String?): String? {
        if (downloadType.equals("audio", ignoreCase = true)) {
            return if (!resolution.isNullOrBlank() && resolution.contains("k", ignoreCase = true)) {
                "Áudio (${resolution.trim()})"
            } else {
                "Áudio"
            }
        }

        if (resolution.isNullOrBlank()) return null
        val trimmed = resolution.trim()

        val dimRegex = Regex("""(\d{3,4})\s*[x×]\s*(\d{3,4})""", RegexOption.IGNORE_CASE)
        val match = dimRegex.find(trimmed)
        if (match != null) {
            val w = match.groupValues[1].toInt()
            val h = match.groupValues[2].toInt()
            val heightLabel = minOf(w, h)
            return "${heightLabel}p · ${w}×${h}"
        }

        if (trimmed.contains("2160") || trimmed.contains("4k", ignoreCase = true)) return "2160p · 3840×2160"
        if (trimmed.contains("1440") || trimmed.contains("2k", ignoreCase = true)) return "1440p · 2560×1440"
        if (trimmed.contains("1080")) return "1080p · 1920×1080"
        if (trimmed.contains("720")) return "720p · 1280×720"
        if (trimmed.contains("480")) return "480p · 854×480"
        if (trimmed.contains("360")) return "360p · 640×360"

        return trimmed
    }

    fun formatContainerDisplay(filePath: String?, mimeType: String?, downloadType: String?): String? {
        if (!filePath.isNullOrEmpty()) {
            val ext = File(filePath).extension
            if (ext.isNotEmpty()) return ext.uppercase(Locale.US)
        }

        if (!mimeType.isNullOrEmpty()) {
            val cleanMime = mimeType.lowercase(Locale.US)
            return when {
                cleanMime.contains("mp4") -> "MP4"
                cleanMime.contains("webm") -> "WEBM"
                cleanMime.contains("matroska") || cleanMime.contains("mkv") -> "MKV"
                cleanMime.contains("mpeg") || cleanMime.contains("mp3") -> "MP3"
                cleanMime.contains("m4a") || cleanMime.contains("aac") -> "M4A"
                cleanMime.contains("ogg") || cleanMime.contains("opus") -> "OPUS"
                else -> mimeType.substringAfter("/").uppercase(Locale.US)
            }
        }

        if (downloadType.equals("audio", ignoreCase = true)) return "AUDIO"
        if (downloadType.equals("video", ignoreCase = true)) return "VIDEO"
        return null
    }

    fun formatCodecDisplay(codec: String?): String? {
        if (codec.isNullOrBlank() || codec.equals("none", ignoreCase = true)) return null
        val lower = codec.lowercase(Locale.US)
        return when {
            lower.contains("avc1") || lower.contains("h264") -> "AVC"
            lower.contains("vp09") || lower.contains("vp9") -> "VP9"
            lower.contains("av01") || lower.contains("av1") -> "AV1"
            lower.contains("hev1") || lower.contains("h265") || lower.contains("hevc") -> "HEVC"
            lower.contains("mp4a") || lower.contains("aac") -> "AAC"
            lower.contains("opus") -> "Opus"
            else -> codec.uppercase(Locale.US)
        }
    }

    fun formatFpsDisplay(fps: Int?): String? {
        return if (fps != null && fps > 0) "${fps} FPS" else null
    }

    fun formatSizeProgress(downloadedSize: Long, totalSize: Long, progress: Int, filePath: String? = null): String {
        if (totalSize > 0) {
            val downloaded = if (downloadedSize > 0) downloadedSize else (totalSize * progress) / 100
            return "${formatSize(downloaded)} / ${formatSize(totalSize)}"
        }

        if (filePath != null) {
            try {
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    return formatSize(file.length())
                }
            } catch (e: Exception) { /* silent */ }
        }

        if (downloadedSize > 0) return formatSize(downloadedSize)
        return ""
    }
}
