package br.com.fpagency.fptube.core

import android.content.Context
import java.io.File

object StorageAuditManager {

    data class StorageNode(
        val name: String,
        val path: String,
        val size: Long,
        val fileCount: Int,
        val children: List<StorageNode> = emptyList()
    )

    fun getStorageReport(context: Context): List<StorageNode> {
        val rootNodes = mutableListOf<StorageNode>()
        
        // Internal Files
        rootNodes.add(scanDirectory(context.filesDir, "files/"))
        rootNodes.add(scanDirectory(context.cacheDir, "cache/"))
        rootNodes.add(scanDirectory(context.noBackupFilesDir, "no_backup/"))
        
        // Databases
        val dbDir = File(context.applicationInfo.dataDir, "databases")
        if (dbDir.exists()) {
            rootNodes.add(scanDirectory(dbDir, "databases/"))
        }

        // Shared Prefs
        val spDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (spDir.exists()) {
            rootNodes.add(scanDirectory(spDir, "shared_prefs/"))
        }

        // External Files (Scoped Storage)
        context.getExternalFilesDir(null)?.let {
            rootNodes.add(scanDirectory(it, "external_files/"))
        }

        return rootNodes
    }

    private fun scanDirectory(file: File, displayName: String): StorageNode {
        var totalSize = 0L
        var totalFiles = 0
        val children = mutableListOf<StorageNode>()

        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val subNode = scanDirectory(child, child.name)
                    totalSize += subNode.size
                    totalFiles += subNode.fileCount
                    children.add(subNode)
                } else {
                    totalSize += child.length()
                    totalFiles++
                }
            }
        } else {
            totalSize = file.length()
            totalFiles = 1
        }

        return StorageNode(displayName, file.absolutePath, totalSize, totalFiles, children.sortedByDescending { it.size })
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Limpa arquivos temporários conhecidos e órfãos
     */
    fun performEmergencyCleanup(context: Context): Long {
        var bytesFreed = 0L
        
        // 1. Limpar cache do sistema
        bytesFreed += deleteDirectoryContent(context.cacheDir)
        
        // 2. Limpar downloads temporários (no_backup/downloads)
        val tempDownloads = File(context.noBackupFilesDir, "downloads")
        bytesFreed += deleteDirectoryContent(tempDownloads)
        
        // 3. Limpar relatórios de diagnóstico antigos
        context.getExternalFilesDir(null)?.listFiles()?.forEach { file ->
            if (file.name.startsWith("diagnostic_report")) {
                val size = file.length()
                if (file.delete()) bytesFreed += size
            }
        }

        return bytesFreed
    }

    private fun deleteDirectoryContent(dir: File): Long {
        var freed = 0L
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    freed += deleteDirectoryContent(file)
                    if (file.delete()) freed += 0 // Diretórios não somam tamanho significativamente aqui
                } else {
                    val size = file.length()
                    if (file.delete()) freed += size
                }
            }
        }
        return freed
    }
}
