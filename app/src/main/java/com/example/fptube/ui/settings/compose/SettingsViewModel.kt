package br.com.fpagency.fptube.ui.settings.compose

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val simultaneousDownloads = settingsManager.simultaneousDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val speedLimit = settingsManager.speedLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ilimitado")

    val qualityMode = settingsManager.qualityMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Automático")

    val showProgressNotification = settingsManager.showProgressNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showCompletedNotification = settingsManager.showCompletedNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultDownloadType = settingsManager.defaultDownloadType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Vídeo")

    val audioQuality = settingsManager.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "M4A rápido (128 kbps)")

    val downloadLocationUri = settingsManager.downloadLocationUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Sistema")

    val cloudEngineMode = settingsManager.cloudEngineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Automático")

    private val _appLanguage = MutableStateFlow(getCurrentLanguageCode())
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.appLanguage.collect { lang ->
                if (lang != "System" && lang.isNotEmpty()) {
                    _appLanguage.value = lang
                } else {
                    _appLanguage.value = getCurrentLanguageCode()
                }
            }
        }
    }

    private fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return "System"
        return locales.toLanguageTags()
    }

    fun refreshLanguage() {
        viewModelScope.launch {
            val stored = settingsManager.appLanguage.first()
            if (stored != "System" && stored.isNotEmpty()) {
                _appLanguage.value = stored
            } else {
                _appLanguage.value = getCurrentLanguageCode()
            }
        }
    }

    fun updateSimultaneousDownloads(value: Int) {
        viewModelScope.launch { settingsManager.updateSimultaneousDownloads(value) }
    }

    fun updateSpeedLimit(value: String) {
        viewModelScope.launch { settingsManager.updateSpeedLimit(value) }
    }

    fun updateQualityMode(value: String) {
        viewModelScope.launch { settingsManager.updateQualityMode(value) }
    }

    fun updateShowProgressNotification(value: Boolean) {
        viewModelScope.launch { settingsManager.updateShowProgressNotification(value) }
    }

    fun updateShowCompletedNotification(value: Boolean) {
        viewModelScope.launch { settingsManager.updateShowCompletedNotification(value) }
    }

    fun updateDefaultDownloadType(value: String) {
        viewModelScope.launch { settingsManager.updateDefaultDownloadType(value) }
    }

    fun updateAudioQuality(value: String) {
        viewModelScope.launch { settingsManager.updateAudioQuality(value) }
    }

    fun updateDownloadLocationUri(value: String?) {
        viewModelScope.launch { settingsManager.updateDownloadLocationUri(value) }
    }

    fun updateCloudEngineMode(value: String) {
        viewModelScope.launch { settingsManager.updateCloudEngineMode(value) }
    }

    fun updateThemeMode(value: String) {
        viewModelScope.launch { settingsManager.updateThemeMode(value) }
    }

    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsManager.updateAppLanguage(languageCode)
            val appLocale: LocaleListCompat = if (languageCode == "System") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(appLocale)
            _appLanguage.value = languageCode
        }
    }
}
