package br.com.fpagency.fptube.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    fun shareFile(context: Context, path: String?, type: String?) {
        if (path == null) return
        try {
            val uri = if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                val file = File(path)
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else null
            }

            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    this.type = if (type == "audio") "audio/*" else "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)))
            } else {
                Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppLogger.e("Erro ao compartilhar", "ShareUtils", e)
            Toast.makeText(context, context.getString(R.string.error_prefix, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
}
