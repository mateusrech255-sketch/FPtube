package br.com.fpagency.fptube.data

import kotlinx.coroutines.flow.Flow

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(private val downloadDao: DownloadDao) {

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    suspend fun insert(download: DownloadEntity): Long {
        return downloadDao.upsertByUrl(download)
    }

    suspend fun update(download: DownloadEntity) {
        downloadDao.update(download)
    }

    suspend fun delete(download: DownloadEntity) {
        downloadDao.delete(download)
    }

    suspend fun deleteByIds(ids: Set<Long>) {
        if (ids.isNotEmpty()) {
            downloadDao.deleteByIds(ids)
        }
    }

    suspend fun getDownloadById(id: Long): DownloadEntity? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun updateStatus(id: Long, status: DownloadStatus) {
        downloadDao.updateStatus(id, status)
    }

    suspend fun updateStatusBatch(ids: Set<Long>, status: DownloadStatus) {
        if (ids.isNotEmpty()) {
            downloadDao.updateStatusBatch(ids, status)
        }
    }

    suspend fun getActiveDownloads(limit: Int): List<DownloadEntity> {
        return downloadDao.getActiveDownloads(limit)
    }

    suspend fun resetDownloadingToQueued() {
        downloadDao.resetDownloadingToQueued()
    }
}
