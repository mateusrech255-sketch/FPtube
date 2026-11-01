package br.com.fpagency.fptube.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'QUEUED' LIMIT :limit")
    suspend fun getActiveDownloads(limit: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url LIMIT 1")
    suspend fun getDownloadByUrl(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity): Int

    @Delete
    suspend fun delete(download: DownloadEntity): Int

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus): Int

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun resetDownloadingToQueued(): Int

    @Query("UPDATE downloads SET title = :title, filePath = :filePath WHERE id = :id")
    suspend fun renameDownload(id: Long, title: String, filePath: String): Int

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int

    @Query("SELECT * FROM downloads ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastDownload(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getActiveCount(): Int

    @Query("UPDATE downloads SET status = :status, lastError = :error, stackTrace = :stackTrace WHERE id = :id")
    suspend fun updateError(id: Long, status: DownloadStatus, error: String?, stackTrace: String?): Int
}
