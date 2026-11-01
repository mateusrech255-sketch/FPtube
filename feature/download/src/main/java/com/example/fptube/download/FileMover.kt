package br.com.fpagency.fptube.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import br.com.fpagency.fptube.core.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileMover @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun moveToFinalLocation(
        file: File,
        customTitle: String,
        downloadType: String?,
        locationUri: String? = null
    ): String = withContext(Dispatchers.IO) {
        val safeTitle = customTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val finalFileName = "$safeTitle.${file.extension}"

        // 1. SAF (Pasta personalizada)
        if (locationUri != null) {
            try {
                AppLogger.i("Movendo via SAF para: $locationUri", "FileMover")
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(locationUri))
                val newFile = rootDoc?.createFile("*/*", finalFileName)
                if (newFile != null) {
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    file.delete()
                    return@withContext newFile.uri.toString()
                }
            } catch (e: Exception) {
                AppLogger.e("Erro no SAF", "FileMover", e)
            }
        }

        // 2. MediaStore
        try {
            val isAudio = downloadType == "audio" || (file.extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg", "flac", "opus") && !file.name.contains("video", ignoreCase = true))
            
            val mimeType = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "webm" -> "video/webm" // MediaStore de áudio no Android freq rejeita audio/webm. Trataremos webm como video para não dar crash.
                "mp4" -> if (isAudio) "audio/mp4" else "video/mp4"
                "mkv" -> "video/x-matroska"
                else -> if (isAudio) "audio/*" else "video/*"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isAudio && file.extension.lowercase() != "webm") MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                if (isAudio && file.extension.lowercase() != "webm") MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = context.contentResolver.insert(collection, contentValues)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, contentValues, null, null)
                }
                
                file.delete()
                return@withContext itemUri.toString()
            }
        } catch (e: Exception) {
            AppLogger.e("Erro ao salvar via MediaStore", "FileMover", e)
        }

        // Fallback
        val pastaDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dest = File(pastaDownloads, finalFileName)
        if (file.renameTo(dest)) return@withContext dest.absolutePath
        
        return@withContext file.absolutePath
    }
}
