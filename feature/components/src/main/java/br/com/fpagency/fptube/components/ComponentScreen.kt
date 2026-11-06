package br.com.fpagency.fptube.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.fpagency.fptube.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentScreen(
    viewModel: ComponentViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.installed),
        stringResource(R.string.updates),
        stringResource(R.string.available)
    )
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val isUpdating by viewModel.isEngineUpdating.collectAsState()
    val showNoUpdateDialog by viewModel.showNoUpdateDialog.collectAsState()

    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddRepoDialog = false },
            onConfirm = { url ->
                viewModel.addRepository(url)
                showAddRepoDialog = false
            }
        )
    }

    if (showNoUpdateDialog) {
        NoUpdateDialog(onDismiss = { viewModel.dismissNoUpdateDialog() })
    }

    if (showSettingsDialog) {
        val autoUpdate by viewModel.autoUpdateComponents.collectAsState()
        ComponentSettingsDialog(
            autoUpdate = autoUpdate,
            onDismiss = { showSettingsDialog = false },
            onAutoUpdateChange = { viewModel.setAutoUpdateComponents(it) }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.manage_components)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { viewModel.checkForEngineUpdates() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                    IconButton(onClick = { showAddRepoDialog = true }) {
                        Icon(Icons.Default.AddLink, contentDescription = stringResource(R.string.add_source))
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                0 -> InstalledTab(viewModel)
                1 -> UpdatesTab(viewModel)
                2 -> AvailableTab(viewModel)
            }
        }
    }
}

@Composable
fun ComponentSettingsDialog(
    autoUpdate: Boolean,
    onDismiss: () -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.component_settings)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.auto_update),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.auto_update_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = autoUpdate,
                        onClick = { onAutoUpdateChange(true) }
                    )
                    Text(stringResource(R.string.enabled), modifier = Modifier.padding(start = 8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !autoUpdate,
                        onClick = { onAutoUpdateChange(false) }
                    )
                    Text(stringResource(R.string.disabled), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun NoUpdateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.components_updated_title)) },
        text = { Text(stringResource(R.string.no_updates_found)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun AddRepositoryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var showWarning by remember { mutableStateOf(false) }

    if (!showWarning) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.add_repository_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.add_source))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.repo_url_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showWarning = true }, enabled = url.startsWith("http")) {
                    Text(stringResource(R.string.next))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.security_warning_title)) },
            text = { Text(stringResource(R.string.security_warning_desc)) },
            confirmButton = {
                Button(
                    onClick = { onConfirm(url) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.trust_and_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) { Text(stringResource(R.string.back)) }
            }
        )
    }
}

@Composable
fun InstalledTab(viewModel: ComponentViewModel) {
    val isInitialized by viewModel.isEngineInitialized.collectAsState()
    val dlpVersion by viewModel.dlpVersion.collectAsState()
    val isUpdating by viewModel.isEngineUpdating.collectAsState()
    val components by viewModel.components.collectAsState()

    var componentToUninstall by remember { mutableStateOf<String?>(null) }

    if (componentToUninstall != null) {
        val isCore = componentToUninstall == "engine"
        AlertDialog(
            onDismissRequest = { componentToUninstall = null },
            title = { Text(if (isCore) stringResource(R.string.uninstall_engine_title) else stringResource(R.string.uninstall)) },
            text = {
                Text(if (isCore)
                    stringResource(R.string.uninstall_engine_desc)
                    else stringResource(R.string.uninstall_extension_desc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCore) viewModel.uninstallEngine()
                        else viewModel.uninstallComponent(componentToUninstall!!)
                        componentToUninstall = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.confirm_removal)) }
            },
            dismissButton = {
                TextButton(onClick = { componentToUninstall = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.core_engines), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        ComponentCard(
            name = "yt-dlp (Python)",
            version = dlpVersion ?: "N/A",
            status = if (isInitialized) stringResource(R.string.enabled) else stringResource(R.string.disabled),
            onReinstall = { viewModel.reinstallEngine() },
            onUninstall = { componentToUninstall = "engine" },
            isLoading = isUpdating
        )
        Spacer(modifier = Modifier.height(16.dp))
        ComponentCard(
            name = "FFmpeg (Binário)",
            version = "v0.18.1",
            status = stringResource(R.string.installed),
            onReinstall = { },
            onUninstall = { },
            isLoading = false
        )

        val installedPlugins = components.filter { it.status == ComponentStatus.INSTALLED }
        if (installedPlugins.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.extensions_plugins), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            installedPlugins.forEach { component ->
                ComponentCard(
                    name = component.name,
                    version = component.version,
                    status = stringResource(R.string.installed),
                    onReinstall = { viewModel.installComponent(component) },
                    onUninstall = { componentToUninstall = component.id },
                    isLoading = false
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun UpdatesTab(viewModel: ComponentViewModel) {
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsState()
    val isUpdating by viewModel.isEngineUpdating.collectAsState()
    val dlpVersion by viewModel.dlpVersion.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isUpdateAvailable) {
            Text(stringResource(R.string.pending_updates), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            ComponentCard(
                name = "yt-dlp",
                version = "$dlpVersion -> New",
                status = stringResource(R.string.new_version_detected),
                onReinstall = { },
                onUninstall = { },
                onUpdate = { viewModel.updateEngine() },
                isLoading = isUpdating
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.all_components_updated), style = MaterialTheme.typography.bodyMedium)

                    if (isUpdating) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        TextButton(onClick = { viewModel.checkForEngineUpdates() }) {
                            Text(stringResource(R.string.check_again))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableTab(viewModel: ComponentViewModel) {
    val components by viewModel.components.collectAsState()
    val availableComponents = components.filter { it.status != ComponentStatus.INSTALLED }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (availableComponents.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_extra_components))
            }
        } else {
            availableComponents.forEach { component ->
                AvailableCard(
                    component = component,
                    onInstall = { viewModel.installComponent(component) },
                    onPause = { viewModel.pauseInstallation(component.id) },
                    onCancel = { viewModel.cancelInstallation(component.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val repositories by viewModel.repositories.collectAsState()
        Text(stringResource(R.string.third_party_repos), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(8.dp))

        if (repositories.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.no_external_sources),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        } else {
            repositories.forEach { repo ->
                RepoItem(url = repo, onRemove = { viewModel.removeRepository(repo) })
            }
        }
    }
}

@Composable
fun RepoItem(url: String, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.Red)
            }
        }
    }
}

@Composable
fun ComponentCard(
    name: String,
    version: String,
    status: String,
    onReinstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    isLoading: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${stringResource(R.string.version)}: $version", style = MaterialTheme.typography.bodySmall)
            Text("${stringResource(R.string.status)}: $status", style = MaterialTheme.typography.bodySmall)

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onUninstall, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text(stringResource(R.string.uninstall))
                    }
                    TextButton(onClick = onReinstall) {
                        Text(stringResource(R.string.reinstall))
                    }
                    if (onUpdate != null) {
                        Button(onClick = onUpdate, modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.update))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableCard(
    component: Component,
    onInstall: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(component.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!component.isOfficial) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.Gray.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    stringResource(R.string.not_official),
                                    fontSize = 8.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    Text(component.description, style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.estimated_size, component.size)} • ${component.version}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                    if (component.errorMessage != null) {
                        Text(
                            text = component.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (component.repositorySource != null) {
                        Text("${stringResource(R.string.source_prefix)}: ${component.repositorySource}", style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.5f))
                    }
                }

                if (component.status == ComponentStatus.AVAILABLE) {
                    Button(onClick = onInstall) {
                        Text(if (component.errorMessage != null) stringResource(R.string.try_again) else stringResource(R.string.install))
                    }
                }
            }

            if (component.status == ComponentStatus.DOWNLOADING || component.status == ComponentStatus.PAUSED) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { component.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (component.status == ComponentStatus.PAUSED) Color.Gray else MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (component.status == ComponentStatus.PAUSED) "${stringResource(R.string.paused)}: ${component.progress}%" else "${stringResource(R.string.downloading)}: ${component.progress}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row {
                        IconButton(onClick = if (component.status == ComponentStatus.PAUSED) onInstall else onPause) {
                            Icon(
                                imageVector = if (component.status == ComponentStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
