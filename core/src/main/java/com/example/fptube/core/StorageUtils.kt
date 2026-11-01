package br.com.fpagency.fptube.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StorageUtils {

    fun deleteFile(context: Context, path: String?): Boolean {
        if (path == null) return false
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                // Tenta via ContentResolver primeiro (MediaStore ou SAF)
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) return true
                
                // Fallback via DocumentFile
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc?.delete() ?: false
            } else {
                val file = File(path)
                if (file.exists()) file.delete() else false
            }
        } catch (e: Exception) {
            AppLogger.e("Erro ao deletar arquivo: $path", "StorageUtils", e)
            false
        }
    }

    fun renameFile(context: Context, path: String?, newName: String): String? {
        if (path == null) return null
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                
                // Tenta via MediaStore se for um URI de mídia
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                try {
                    val updated = context.contentResolver.update(uri, values, null, null)
                    if (updated > 0) return path
                } catch (e: Exception) {
                    // Ignora erro e tenta via DocumentFile
                }

                // Fallback via DocumentFile (SAF)
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc?.renameTo(newName) == true) {
                    path // URI geralmente permanece o mesmo no SAF
                } else null
            } else {
                val file = File(path)
                if (file.exists()) {
                    val ext = file.extension
                    val finalName = if (newName.contains(".")) newName else "$newName.$ext"
                    val newFile = File(file.parent, finalName)
                    if (file.renameTo(newFile)) newFile.absolutePath else null
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e("Erro ao renomear arquivo: $path", "StorageUtils", e)
            null
        }
    }
}
