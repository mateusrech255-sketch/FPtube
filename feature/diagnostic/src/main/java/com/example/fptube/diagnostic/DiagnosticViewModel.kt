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
import kotlinx.coroutines.flow.combine
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

    val cloudUrl = settingsManager.cloudWorkerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cloudApiKey = settingsManager.cloudApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val cloudStatus = MutableStateFlow(context.getString(R.string.checking))

    init {
        startMonitoring()
        loadStats()
        checkCloudStatus()
    }

    private fun checkCloudStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(cloudUrl, cloudApiKey) { url, key -> url to key }.collectLatest { (url, key) ->
                val targetUrl = url ?: "https://fptube-cache.fpagency.com.br/"
                val isDefault = url == null
                
                try {
                    val connection = java.net.URL("$targetUrl?url=https://www.youtube.com/watch?v=dQw4w9WgXcQ").openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    if (key.isNotEmpty()) {
                        connection.setRequestProperty("X-API-Key", key)
                    }
                    
                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        cloudStatus.value = if (isDefault) context.getString(R.string.connected_official) else context.getString(R.string.connected_custom)
                    } else {
                        cloudStatus.value = context.getString(R.string.http_error_prefix, responseCode)
                    }
                } catch (e: Exception) {
                    cloudStatus.value = context.getString(R.string.connection_failure)
                }
            }
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                updateStats()
                delay(2000)
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            totalDownloads.value = downloadDao.getTotalCount()
            completedDownloads.value = downloadDao.getCompletedCount()
            failedDownloads.value = downloadDao.getFailedCount()
        }
    }

    private fun updateStats() {
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

            _storageDetails.value = br.com.fpagency.fptube.core.StorageAuditManager.getStorageReport(context)
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

    fun updateCloudUrl(url: String?) {
        viewModelScope.launch { 
            val cleanUrl = if (url.isNullOrBlank()) null else url.trim().let { 
                if (it.endsWith("/")) it else "$it/" 
            }
            settingsManager.updateCloudWorkerUrl(cleanUrl) 
        }
    }

    fun updateCloudApiKey(key: String) {
        viewModelScope.launch { settingsManager.updateCloudApiKey(key.trim()) }
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
