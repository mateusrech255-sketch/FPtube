package br.com.fpagency.fptube.ui.settings.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.fpagency.fptube.core.ui.R
import br.com.fpagency.fptube.core.ui.components.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onAboutClick: () -> Unit,
    onComponentCenterClick: () -> Unit,
    onDiagnosticClick: () -> Unit
) {
    val simultaneousDownloads by viewModel.simultaneousDownloads.collectAsState()
    val speedLimit by viewModel.speedLimit.collectAsState()
    val qualityMode by viewModel.qualityMode.collectAsState()
    val showProgressNotification by viewModel.showProgressNotification.collectAsState()
    val showCompletedNotification by viewModel.showCompletedNotification.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshLanguage()
    }

    val speedLimits = listOf("64 kbps", "128 kbps", "256 kbps", "512 kbps", "1 Mbps", "2 Mbps", "5 Mbps", "10 Mbps", "20 Mbps", "50 Mbps", "100 Mbps", "Ilimitado")

    val systemDefaultLabel = stringResource(R.string.system_default)
    val supportedLanguageCodes = remember {
        listOf(
            "System", "pt", "en", "es", "fr", "de", "it", "ru", "ja", "zh-CN", "zh-TW",
            "ar", "hi", "ko", "tr", "pl", "nl", "vi", "id", "th", "he",
            "sv", "uk", "el", "cs", "ro", "hu", "da", "fi", "no", "sk"
        )
    }

    val languageOptions = remember(systemDefaultLabel) {
        supportedLanguageCodes.map { code ->
            if (code == "System") {
                code to systemDefaultLabel
            } else {
                val nativeName = try {
                    val locale = java.util.Locale.forLanguageTag(code)
                    val name = locale.getDisplayName(locale)
                    if (name.isNotEmpty()) name.substring(0, 1).uppercase() + name.substring(1) else code
                } catch (e: Exception) {
                    code
                }
                code to nativeName
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Section: Downloads
        FPTubeSectionHeader(stringResource(R.string.downloads))
        
        FPTubeSliderItem(
            title = stringResource(R.string.simultaneous_downloads),
            value = simultaneousDownloads.toFloat(),
            onValueChange = { viewModel.updateSimultaneousDownloads(it.toInt()) },
            valueRange = 1f..50f,
            steps = 49,
            valueDisplay = simultaneousDownloads.toString()
        )
        
        FPTubeSliderItem(
            title = stringResource(R.string.speed_limit),
            value = speedLimits.indexOf(speedLimit).coerceAtLeast(0).toFloat(),
            onValueChange = { viewModel.updateSpeedLimit(speedLimits[it.toInt()]) },
            valueRange = 0f..(speedLimits.size - 1).toFloat(),
            steps = speedLimits.size - 2,
            valueDisplay = speedLimit
        )

        FPTubeSettingItem(
            title = stringResource(R.string.quality_mode),
            description = qualityMode,
            onClick = { /* Could open a dialog if preferred, but keeping inline options for simplicity as requested */ }
        )
        
        // Quality Inline Options
        Column(modifier = Modifier.padding(start = 16.dp)) {
            val qualityModes = listOf(
                "Perguntar sempre" to stringResource(R.string.quality_ask),
                "Automático" to stringResource(R.string.quality_auto),
                "Sempre máxima" to stringResource(R.string.quality_max)
            )
            qualityModes.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateQualityMode(key) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = qualityMode == key, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FPTubeSettingItem(
            title = stringResource(R.string.show_progress_notification),
            trailingContent = {
                Switch(checked = showProgressNotification, onCheckedChange = { viewModel.updateShowProgressNotification(it) })
            }
        )
        FPTubeSettingItem(
            title = stringResource(R.string.show_completed_notification),
            trailingContent = {
                Switch(checked = showCompletedNotification, onCheckedChange = { viewModel.updateShowCompletedNotification(it) })
            }
        )

        // Section: Language & Appearance
        FPTubeSectionHeader(stringResource(R.string.interface_language))
        
        var showLanguageDialog by remember { mutableStateOf(false) }
        val currentLanguageName = languageOptions.find { (code, _) ->
            code == appLanguage || (code != "System" && appLanguage.startsWith(code))
        }?.second ?: if (appLanguage == "System" || appLanguage.isEmpty()) systemDefaultLabel else appLanguage
        
        FPTubeSettingItem(
            title = stringResource(R.string.app_language),
            description = currentLanguageName,
            icon = Icons.Outlined.Translate,
            onClick = { showLanguageDialog = true }
        )

        FPTubeSettingItem(
            title = stringResource(R.string.app_theme),
            description = themeMode,
            icon = Icons.Outlined.Palette
        )
        
        Column(modifier = Modifier.padding(start = 16.dp)) {
            val themes = listOf(
                "Sistema" to stringResource(R.string.theme_system),
                "Claro" to stringResource(R.string.theme_light),
                "Escuro" to stringResource(R.string.theme_dark)
            )
            themes.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateThemeMode(key) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = themeMode == key, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        // Section: Compatibility
        FPTubeSectionHeader(stringResource(R.string.compatibility_header))
        
        var showPlatformsDialog by remember { mutableStateOf(false) }
        FPTubeSettingItem(
            title = stringResource(R.string.supported_platforms),
            description = stringResource(R.string.supported_platforms_desc),
            icon = Icons.Outlined.CheckCircle,
            onClick = { showPlatformsDialog = true }
        )

        // Section: Support
        FPTubeSectionHeader(stringResource(R.string.support_header))

        FPTubeSettingItem(
            title = stringResource(R.string.component_center),
            icon = Icons.Outlined.Build,
            onClick = onComponentCenterClick
        )
        FPTubeSettingItem(
            title = stringResource(R.string.diagnostic_panel),
            icon = Icons.Outlined.Assessment,
            onClick = onDiagnosticClick
        )
        
        val uriHandler = LocalUriHandler.current
        FPTubeSettingItem(
            title = stringResource(R.string.contact_us),
            icon = Icons.Outlined.SupportAgent,
            onClick = { uriHandler.openUri("https://wa.me/message/3YJWHJM7UOR6C1") }
        )
        FPTubeSettingItem(
            title = stringResource(R.string.app_page),
            icon = Icons.Outlined.Language,
            onClick = { uriHandler.openUri("https://fpagency.com.br/fptube") }
        )
        FPTubeSettingItem(
            title = stringResource(R.string.monetize),
            icon = Icons.Outlined.MonetizationOn,
            onClick = { uriHandler.openUri("https://fpagency.com.br/monetize") }
        )
        FPTubeSettingItem(
            title = stringResource(R.string.learning_to_edit),
            icon = Icons.Outlined.School,
            onClick = { uriHandler.openUri("https://fpagency.com.br/treinamento") }
        )

        FPTubeSettingItem(
            title = stringResource(R.string.about_fptube),
            icon = Icons.Outlined.Info,
            onClick = onAboutClick
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (showLanguageDialog) {
            var searchFilter by remember { mutableStateOf("") }
            val filteredOptions = remember(searchFilter, languageOptions) {
                if (searchFilter.isBlank()) languageOptions
                else languageOptions.filter { (code, label) ->
                    label.contains(searchFilter, ignoreCase = true) || code.contains(searchFilter, ignoreCase = true)
                }
            }

            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                title = { Text(stringResource(R.string.select_language)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        OutlinedTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            placeholder = { Text(stringResource(R.string.search)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Outlined.Search, null) }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredOptions.forEach { (code, label) ->
                                val isSelected = (code == "System" && (appLanguage == "System" || appLanguage.isEmpty())) ||
                                                (code != "System" && (appLanguage == code || appLanguage.startsWith(code)))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.updateLanguage(code)
                                            showLanguageDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (showPlatformsDialog) {
            val platforms = listOf(
                "YouTube (Vídeos, Shorts, Live)",
                "TikTok",
                "Instagram (Reels, Posts, Stories)",
                "Facebook (Watch, Reels, Vídeos)",
                "Twitter / X",
                "Snapchat (Spotlight)",
                "Pinterest",
                "SoundCloud",
                "Dailymotion",
                "Vimeo",
                "Reddit",
                "Twitch (VODs, Clips)",
                "Bluesky",
                "VK (Vkontakte)",
                "OK.ru (Odnoklassniki)",
                "Telegram (Links públicos)",
                "Kwai (Suporte Experimental)",
                "Threads (Suporte Experimental)",
                "SnackVideo (Suporte Experimental)",
                "E centenas de outros via Extractor Genérico"
            )
            AlertDialog(
                onDismissRequest = { showPlatformsDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                title = { Text(stringResource(R.string.compatible_platforms_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            stringResource(R.string.compatible_platforms_desc),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp).alpha(0.7f)
                        )
                        platforms.forEach { platform ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    platform,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp).alpha(0.1f))
                        
                        Text(
                            stringResource(R.string.drm_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.alpha(0.8f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlatformsDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
