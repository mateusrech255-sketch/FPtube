package br.com.fpagency.fptube.diagnostic

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.fpagency.fptube.data.LogEntity
import br.com.fpagency.fptube.core.ui.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.device),
        stringResource(R.string.logs),
        stringResource(R.string.settings)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.diagnostic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DeviceInfoTab(viewModel)
                1 -> LogsTab(viewModel)
                2 -> LogSettingsTab(viewModel)
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DeviceInfoTab(viewModel: DiagnosticViewModel) {
    val ramUsage by viewModel.ramUsage.collectAsState()
    val diskUsage by viewModel.diskUsage.collectAsState()
    val total by viewModel.totalDownloads.collectAsState()
    val completed by viewModel.completedDownloads.collectAsState()
    val failed by viewModel.failedDownloads.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.cloud_infrastructure), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        DiagnosticItem("API Gateway Status", cloudStatus)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.hardware_system), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        DiagnosticItem(stringResource(R.string.model), Build.MODEL)
        DiagnosticItem(stringResource(R.string.manufacturer), Build.MANUFACTURER)
        DiagnosticItem(stringResource(R.string.android_version_label), "v${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        DiagnosticItem(stringResource(R.string.architecture), Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A")
        DiagnosticItem(stringResource(R.string.ram_usage), ramUsage)
        DiagnosticItem(stringResource(R.string.internal_storage), diskUsage)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(stringResource(R.string.app_statistics), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        DiagnosticItem(stringResource(R.string.total_downloads), total.toString())
        DiagnosticItem(stringResource(R.string.completed), completed.toString())
        DiagnosticItem(stringResource(R.string.failures), failed.toString())

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                viewModel.exportReport { path ->
                    val msg = context.resources.getString(R.string.report_saved, path)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.export_technical_report))
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun LogsTab(viewModel: DiagnosticViewModel) {
    val logs by viewModel.logs.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isEmpty()) logs
        else logs.filter { it.message.contains(searchQuery, ignoreCase = true) || it.tag.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text(stringResource(R.string.search_logs)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { viewModel.clearLogs() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.clear))
            }
            OutlinedButton(onClick = { 
                viewModel.copyLogsToClipboard {
                    android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy_all))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredLogs) { log ->
                LogItem(log)
                HorizontalDivider(modifier = Modifier.alpha(0.1f))
            }
        }
    }
}

@Composable
fun LogSettingsTab(viewModel: DiagnosticViewModel) {
    val logsEnabled by viewModel.logsEnabled.collectAsState()
    val logsToSheets by viewModel.logsToSheets.collectAsState()
    val logFailures by viewModel.logFailures.collectAsState()
    val logPerformance by viewModel.logPerformance.collectAsState()
    
    val cloudUrl by viewModel.cloudUrl.collectAsState()
    val cloudApiKey by viewModel.cloudApiKey.collectAsState()

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.diagnostic_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        LogSwitchItem(
            title = stringResource(R.string.enable_local_logs),
            description = stringResource(R.string.local_logs_desc),
            checked = logsEnabled,
            onCheckedChange = { viewModel.setLogsEnabled(it) }
        )
        
        LogSwitchItem(
            title = stringResource(R.string.send_errors_cloud),
            description = stringResource(R.string.errors_cloud_desc),
            checked = logsToSheets,
            onCheckedChange = { viewModel.setLogsToSheets(it) }
        )

        LogSwitchItem(
            title = stringResource(R.string.record_detailed_failures),
            description = stringResource(R.string.detailed_failures_desc),
            checked = logFailures,
            onCheckedChange = { viewModel.setLogFailures(it) }
        )

        LogSwitchItem(
            title = stringResource(R.string.monitor_performance),
            description = stringResource(R.string.monitor_performance_desc),
            checked = logPerformance,
            onCheckedChange = { viewModel.setLogPerformance(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(stringResource(R.string.cloud_settings_advanced), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.custom_worker_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.7f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = cloudUrl ?: "",
            onValueChange = { viewModel.updateCloudUrl(it) },
            label = { Text(stringResource(R.string.worker_url_label)) },
            placeholder = { Text("https://seu-worker.workers.dev/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = cloudApiKey,
            onValueChange = { viewModel.updateCloudApiKey(it) },
            label = { Text(stringResource(R.string.api_key_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun LogSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LogItem(log: LogEntity) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = when (log.level) {
        "ERROR" -> Color.Red
        "WARNING" -> Color(0xFFFFA500)
        "INFO" -> Color(0xFF2196F3)
        else -> Color.Gray
    }

    Column(modifier = Modifier.padding(8.dp)) {
        Row {
            Text(text = dateFormat.format(Date(log.timestamp)), fontSize = 10.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.level, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.tag, fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
        }
        Text(
            text = log.message, 
            fontSize = 12.sp, 
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun DiagnosticItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp)
    }
}
