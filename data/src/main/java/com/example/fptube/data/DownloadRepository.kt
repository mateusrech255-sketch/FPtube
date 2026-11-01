package br.com.fpagency.fptube.data

import kotlinx.coroutines.flow.Flow

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(private val downloadDao: DownloadDao) {

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    suspend fun insert(download: DownloadEntity): Long {
        val existing = downloadDao.getDownloadByUrl(download.url)
        if (existing != null) {
            // Se o download já existe e não falhou, não duplica
            if (existing.status == DownloadStatus.COMPLETED || existing.status == DownloadStatus.DOWNLOADING) {
                return existing.id
            }
            // Se falhou ou foi cancelado, reutiliza o ID para atualizar os metadados
            return downloadDao.insert(download.copy(id = existing.id))
        }
        return downloadDao.insert(download)
    }

    suspend fun update(download: DownloadEntity) {
        downloadDao.update(download)
    }

    suspend fun delete(download: DownloadEntity) {
        downloadDao.delete(download)
    }

    suspend fun getDownloadById(id: Long): DownloadEntity? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun updateStatus(id: Long, status: DownloadStatus) {
        downloadDao.updateStatus(id, status)
    }

    suspend fun getActiveDownloads(limit: Int): List<DownloadEntity> {
        return downloadDao.getActiveDownloads(limit)
    }

    suspend fun resetDownloadingToQueued() {
        downloadDao.resetDownloadingToQueued()
    }
}
