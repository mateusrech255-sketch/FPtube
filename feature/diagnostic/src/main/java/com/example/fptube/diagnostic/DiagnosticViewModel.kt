package br.com.fpagency.fptube.diagnostic

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.data.DownloadDao
import br.com.fpagency.fptube.data.LogDao
import br.com.fpagency.fptube.data.LogEntity
import br.com.fpagency.fptube.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import br.com.fpagency.fptube.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logDao: LogDao,
    private val downloadDao: DownloadDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _ramUsage = MutableStateFlow(context.getString(R.string.calculating))
    val ramUsage = _ramUsage.asStateFlow()

    private val _diskUsage = MutableStateFlow(context.getString(R.string.calculating))
    val diskUsage = _diskUsage.asStateFlow()

    private val _storageDetails = MutableStateFlow<List<br.com.fpagency.fptube.core.StorageAuditManager.StorageNode>>(emptyList())
    val storageDetails = _storageDetails.asStateFlow()

    val logs: StateFlow<List<LogEntity>> = logDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDownloads = MutableStateFlow(0)
    val completedDownloads = MutableStateFlow(0)
    val failedDownloads = MutableStateFlow(0)

    val logsEnabled = settingsManager.logsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val logsToSheets = settingsManager.logsToSheets
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val logFailures = settingsManager.logFailures
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val logPerformance = settingsManager.logPerformance
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        startMonitoring()
        loadStats()
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            // Executa leitura inicial imediata e depois reduz frequência (5s RAM/Disco, 30s StorageTree)
            // para evitar uso contínuo de CPU/I/O em background.
            var tick = 0
            while (true) {
                updateStats(includeStorageAudit = (tick % 6 == 0))
                tick++
                delay(5000)
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            totalDownloads.value = downloadDao.getTotalCount()
            completedDownloads.value = downloadDao.getCompletedCount()
            failedDownloads.value = downloadDao.getFailedCount()
        }
    }

    private fun updateStats(includeStorageAudit: Boolean = true) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val usedRam = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
            val totalRam = memoryInfo.totalMem / (1024 * 1024)
            _ramUsage.value = "$usedRam MB / $totalRam MB"

            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            
            val availableGb = bytesAvailable / (1024 * 1024 * 1024)
            val totalGb = totalBytes / (1024 * 1024 * 1024)
            _diskUsage.value = context.getString(R.string.disk_usage_format, availableGb.toString(), totalGb.toString())

            if (includeStorageAudit) {
                _storageDetails.value = br.com.fpagency.fptube.core.StorageAuditManager.getStorageReport(context)
            }
        } catch (e: Exception) {
            _ramUsage.value = context.getString(R.string.error_unexpected)
            _diskUsage.value = context.getString(R.string.error_unexpected)
        }
    }

    fun performCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            br.com.fpagency.fptube.core.StorageAuditManager.performEmergencyCleanup(context)
            updateStats()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logDao.clearAll()
        }
    }

    fun setLogsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateLogsEnabled(enabled) }
    }

    fun setLogsToSheets(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateLogsToSheets(enabled) }
    }

    fun setLogFailures(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateLogFailures(enabled) }
    }

    fun setLogPerformance(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateLogPerformance(enabled) }
    }

    fun copyLogsToClipboard(onCopied: () -> Unit) {
        viewModelScope.launch {
            val allLogs = logs.value.joinToString("\n") { "[${it.level}] ${it.tag}: ${it.message}" }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FPtube Logs", allLogs))
            onCopied()
        }
    }

    fun exportReport(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val report = StringBuilder()
            report.append("--- RELATÓRIO TÉCNICO FPtube ---\n")
            report.append("Dispositivo: ${Build.MODEL}\n")
            report.append("Android: ${Build.VERSION.RELEASE}\n")
            report.append("RAM: ${ramUsage.value}\n")
            report.append("Disco: ${diskUsage.value}\n\n")

            report.append("ESTRUTURA DE ARMAZENAMENTO:\n")
            storageDetails.value.forEach { node ->
                report.append("${node.name}: ${br.com.fpagency.fptube.core.StorageAuditManager.formatSize(node.size)} (${node.fileCount} arquivos)\n")
                node.children.take(10).forEach { child ->
                    report.append("  |- ${child.name}: ${br.com.fpagency.fptube.core.StorageAuditManager.formatSize(child.size)}\n")
                }
            }
            report.append("\n")

            report.append("LOGS:\n")
            logs.value.take(500).forEach { 
                report.append("${it.level} | ${it.tag}: ${it.message}\n")
            }
            
            val file = File(context.getExternalFilesDir(null), "diagnostic_report.txt")
            file.writeText(report.toString())
            onSaved(file.absolutePath)
        }
    }
}
