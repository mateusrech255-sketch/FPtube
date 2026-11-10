package br.com.fpagency.fptube.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["url"]),
        Index(value = ["timestamp"]),
        Index(value = ["platform"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val channel: String? = null,
    val resolution: String? = null,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speed: String? = null,
    val eta: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val codec: String? = null,
    val fps: Int? = null,
    val duration: String? = null,
    val mimeType: String? = null,
    val formatId: String? = null,
    val downloadType: String? = null,
    val playerClient: String? = null, // Persiste o cliente usado na descoberta (android_vr, tv, web, etc)
    val platform: String? = null,
    val lastError: String? = null,
    val stackTrace: String? = null
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}
