package br.com.fpagency.fptube.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import br.com.fpagency.fptube.core.checkAllPermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.fpagency.fptube.ui.about.compose.AboutScreen
import br.com.fpagency.fptube.ui.legal.LegalScreen
import br.com.fpagency.fptube.ui.legal.LegalContent
import br.com.fpagency.fptube.ui.developer.compose.DeveloperScreen
import br.com.fpagency.fptube.ui.downloads.DownloadsScreen
import br.com.fpagency.fptube.ui.search.compose.SearchScreen
import br.com.fpagency.fptube.ui.settings.compose.SettingsScreen
import br.com.fpagency.fptube.components.ComponentScreen
import br.com.fpagency.fptube.diagnostic.DiagnosticScreen
import br.com.fpagency.fptube.core.ui.R as UiR

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Search : Screen("search", UiR.string.search, Icons.Outlined.Search, Icons.Filled.Search)
    object Downloads : Screen("downloads", UiR.string.downloads, Icons.Outlined.Download, Icons.Filled.Download)
    object Settings : Screen("settings", UiR.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)
    
    // Internal screens
    object Components : Screen("components", UiR.string.manage_components, Icons.Default.Build, Icons.Filled.Build)
    object About : Screen("about", UiR.string.about, Icons.Default.Info, Icons.Filled.Info)
    object Developer : Screen("developer", UiR.string.developer_mode, Icons.Default.BugReport, Icons.Filled.BugReport)
    object Diagnostic : Screen("diagnostic", UiR.string.diagnostic_panel, Icons.Default.Assessment, Icons.Filled.Assessment)
    object PrivacyPolicy : Screen("privacy_policy", UiR.string.privacy_policy, Icons.Default.Gavel, Icons.Filled.Gavel)
    object CopyrightPolicy : Screen("copyright_policy", UiR.string.copyright_policy, Icons.Default.Copyright, Icons.Filled.Copyright)
    object TermsOfService : Screen("terms_of_service", UiR.string.terms_of_service, Icons.Default.Description, Icons.Filled.Description)
    object LegalNotice : Screen("legal_notice", UiR.string.legal_notice, Icons.Default.Warning, Icons.Filled.Warning)
}

@Composable
fun MainScreen(
    initialSharedUrl: String? = null,
    initialRoute: String? = null,
    settingsManager: br.com.fpagency.fptube.data.SettingsManager,
    onUrlConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val items = listOf(
        Screen.Search,
        Screen.Downloads,
        Screen.Settings
    )

    var hasAllPermissions: Boolean by remember { mutableStateOf(checkAllPermissions(context)) }
    var showPermissionPrompt: Boolean by remember { mutableStateOf(!hasAllPermissions) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAllPermissions = checkAllPermissions(context)
        showPermissionPrompt = false
    }

    if (showPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showPermissionPrompt = false },
            title = { Text("Permissões Necessárias") },
            text = { Text("Para gerenciar downloads em segundo plano, o FPtube precisa de acesso aos seus arquivos de mídia e notificações.") },
            confirmButton = {
                Button(onClick = {
                    val permissionsToRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                        permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                        permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    } else {
                        showPermissionPrompt = false
                    }
                }) {
                    Text("Conceder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionPrompt = false }) {
                    Text("Agora não")
                }
            }
        )
    }

    LaunchedEffect(initialSharedUrl) {
        if (initialSharedUrl != null) {
            navController.navigate(Screen.Search.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val hasAskedOverlay by settingsManager.hasAskedOverlayPermission.collectAsState(initial = true)
    var showOverlayPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(hasAskedOverlay, hasAllPermissions) {
        if (hasAllPermissions && !hasAskedOverlay && !Settings.canDrawOverlays(context)) {
            showOverlayPrompt = true
        }
    }

    if (showOverlayPrompt) {
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = {
                showOverlayPrompt = false
                coroutineScope.launch { settingsManager.updateHasAskedOverlayPermission(true) }
            },
            title = { Text("Bolha flutuante") },
            text = { Text("Deseja habilitar a sobreposição de tela para que o FPtube possa abrir popups rápidos sobre outros aplicativos quando você compartilhar links com ele?") },
            confirmButton = {
                Button(onClick = {
                    showOverlayPrompt = false
                    coroutineScope.launch { settingsManager.updateHasAskedOverlayPermission(true) }
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayPrompt = false
                    coroutineScope.launch { settingsManager.updateHasAskedOverlayPermission(true) }
                }) {
                    Text("Agora não")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute in items.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.icon, 
                                    contentDescription = null
                                ) 
                            },
                            label = { 
                                Text(
                                    text = stringResource(screen.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ) 
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                try {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } catch (e: Exception) {
                                    // Previne crash por navegação inválida
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute ?: Screen.Search.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Search.route) {
                SearchScreen(
                    sharedUrl = initialSharedUrl,
                    onUrlConsumed = onUrlConsumed
                )
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onPlayClick = { download ->
                        val path = download.filePath ?: return@DownloadsScreen
                        try {
                            val parsedUri = if (path.startsWith("content://")) {
                                android.net.Uri.parse(path)
                            } else {
                                android.net.Uri.fromFile(java.io.File(path))
                            }
                            val intent = android.content.Intent(context, br.com.fpagency.fptube.ui.player.VideoPlayerActivity::class.java).apply {
                                data = parsedUri
                                putExtra("VIDEO_PATH", path)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Previne crash se activity não existir
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onAboutClick = { navController.navigate(Screen.About.route) },
                    onComponentCenterClick = { navController.navigate(Screen.Components.route) },
                    onDiagnosticClick = { navController.navigate(Screen.Diagnostic.route) }
                )
            }
            composable(Screen.About.route) {
                AboutScreen(
                    onBackClick = { navController.popBackStack() },
                    onVersionClick = { navController.navigate(Screen.Developer.route) },
                    onPrivacyClick = { navController.navigate(Screen.PrivacyPolicy.route) },
                    onCopyrightClick = { navController.navigate(Screen.CopyrightPolicy.route) },
                    onTermsClick = { navController.navigate(Screen.TermsOfService.route) },
                    onLegalNoticeClick = { navController.navigate(Screen.LegalNotice.route) }
                )
            }
            composable(Screen.Developer.route) {
                DeveloperScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Components.route) {
                ComponentScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Diagnostic.route) {
                DiagnosticScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.PrivacyPolicy.route) {
                LegalScreen(
                    title = stringResource(UiR.string.privacy_policy),
                    content = LegalContent.PRIVACY_POLICY,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.CopyrightPolicy.route) {
                LegalScreen(
                    title = stringResource(UiR.string.copyright_policy),
                    content = LegalContent.COPYRIGHT_POLICY,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.TermsOfService.route) {
                LegalScreen(
                    title = stringResource(UiR.string.terms_of_service),
                    content = LegalContent.TERMS_OF_SERVICE,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.LegalNotice.route) {
                LegalScreen(
                    title = stringResource(UiR.string.legal_notice),
                    content = LegalContent.LEGAL_NOTICE,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
