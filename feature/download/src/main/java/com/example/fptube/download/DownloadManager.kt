package br.com.fpagency.fptube.download

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueDownload(downloadId: Long) {
        val data = Data.Builder()
            .putLong("DOWNLOAD_ID", downloadId)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("DOWNLOAD_$downloadId")
            .build()

        workManager.enqueueUniqueWork(
            "DOWNLOAD_$downloadId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelDownload(downloadId: Long) {
        workManager.cancelUniqueWork("DOWNLOAD_$downloadId")
    }
}
