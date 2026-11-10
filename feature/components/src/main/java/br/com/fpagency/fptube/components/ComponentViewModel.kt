package br.com.fpagency.fptube.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.core.ui.R
import br.com.fpagency.fptube.data.SettingsManager
import br.com.fpagency.fptube.engine.EngineManager
import br.com.fpagency.fptube.engine.PluginManager
import br.com.fpagency.fptube.engine.RepositoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComponentViewModel @Inject constructor(
    application: Application,
    private val engineManager: EngineManager,
    private val pluginManager: PluginManager,
    private val settingsManager: SettingsManager,
    private val repositoryManager: RepositoryManager
) : AndroidViewModel(application) {

    val isEngineInitialized: StateFlow<Boolean> = engineManager.isInitialized
    val dlpVersion: StateFlow<String?> = engineManager.dlpVersion
    val isUpdateAvailable: StateFlow<Boolean> = engineManager.isUpdateAvailable
    val autoUpdateComponents: StateFlow<Boolean> = settingsManager.autoUpdateComponents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isEngineUpdating = MutableStateFlow(false)
    val isEngineUpdating = _isEngineUpdating.asStateFlow()

    private val _showNoUpdateDialog = MutableStateFlow(false)
    val showNoUpdateDialog = _showNoUpdateDialog.asStateFlow()

    private val _officialComponents = MutableStateFlow(
        listOf(
            Component("aria2c", application.getString(R.string.comp_aria2_name), application.getString(R.string.comp_aria2_desc), "v1.36", "12 MB", ComponentStatus.AVAILABLE,
                downloadUrl = "https://github.com/AnInsomniacy/aria2-next/releases/download/v2.4.9/aria2-next-2.4.9-android-arm64"),
            Component("ocr", application.getString(R.string.comp_ocr_name), application.getString(R.string.comp_ocr_desc), "v1.0", "45 MB", ComponentStatus.AVAILABLE,
                downloadUrl = "https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata"),
            Component("whisper", application.getString(R.string.comp_whisper_name), application.getString(R.string.comp_whisper_desc), "v2.1", "120 MB", ComponentStatus.AVAILABLE,
                downloadUrl = "https://github.com/ggerganov/whisper.cpp/raw/master/models/for-tests-ggml-tiny.en.bin")
        )
    )

    private val _externalComponents = MutableStateFlow<List<Component>>(emptyList())

    val components = combine(
        _officialComponents,
        _externalComponents,
        settingsManager.installedPlugins
    ) { official, external, installed ->
        (official + external).map { component ->
            if (installed.contains(component.id)) {
                component.copy(status = ComponentStatus.INSTALLED, progress = 100)
            } else {
                component
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val repositories = settingsManager.externalRepositories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val installationJobs = mutableMapOf<String, Job>()

    init {
        syncRepositories()
    }

    private fun syncRepositories() {
        viewModelScope.launch {
            settingsManager.externalRepositories.collectLatest { repoUrls ->
                val allExternal = mutableListOf<Component>()
                repoUrls.forEach { url ->
                    val repoItems = repositoryManager.fetchRepository(url)
                    allExternal.addAll(repoItems.map {
                        Component(
                            id = it.id,
                            name = it.name,
                            description = it.description,
                            version = it.version,
                            size = it.size,
                            status = ComponentStatus.AVAILABLE,
                            downloadUrl = it.downloadUrl,
                            isOfficial = false,
                            repositorySource = url
                        )
                    })
                }
                _externalComponents.value = allExternal
            }
        }
    }

    fun addRepository(url: String) {
        viewModelScope.launch {
            settingsManager.addRepository(url)
        }
    }

    fun removeRepository(url: String) {
        viewModelScope.launch {
            settingsManager.removeRepository(url)
        }
    }

    fun updateEngine() {
        viewModelScope.launch {
            _isEngineUpdating.value = true
            engineManager.updateYtDlp()
            _isEngineUpdating.value = false
        }
    }

    fun reinstallEngine() {
        viewModelScope.launch {
            _isEngineUpdating.value = true
            engineManager.clearBinaries()
            engineManager.initialize()
            _isEngineUpdating.value = false
        }
    }

    fun uninstallEngine() {
        viewModelScope.launch {
            _isEngineUpdating.value = true
            engineManager.clearBinaries()
            _isEngineUpdating.value = false
        }
    }

    fun checkForEngineUpdates() {
        viewModelScope.launch {
            _isEngineUpdating.value = true
            val updateFound = engineManager.checkUpdate()
            _isEngineUpdating.value = false
            if (!updateFound) {
                _showNoUpdateDialog.value = true
            }
        }
    }

    fun dismissNoUpdateDialog() {
        _showNoUpdateDialog.value = false
    }

    fun setAutoUpdateComponents(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateAutoUpdateComponents(enabled)
        }
    }

    fun installComponent(component: Component) {
        if (installationJobs.containsKey(component.id)) return

        val url = component.downloadUrl ?: return

        val job = viewModelScope.launch {
            updateComponentStatus(component.id, ComponentStatus.DOWNLOADING, null)
            val result = pluginManager.downloadPlugin(component.id, url) { progress ->
                updateComponentProgress(component.id, progress)
            }

            if (result.isSuccess) {
                settingsManager.addInstalledPlugin(component.id)
                updateComponentStatus(component.id, ComponentStatus.INSTALLED, null)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Falha no download"
                updateComponentStatus(component.id, ComponentStatus.AVAILABLE, msg)
            }
            installationJobs.remove(component.id)
        }
        installationJobs[component.id] = job
    }

    fun pauseInstallation(id: String) {
        installationJobs[id]?.cancel()
        installationJobs.remove(id)
        updateComponentStatus(id, ComponentStatus.PAUSED, null)
    }

    fun cancelInstallation(id: String) {
        installationJobs[id]?.cancel()
        installationJobs.remove(id)
        updateComponentStatus(id, ComponentStatus.AVAILABLE, null)
        updateComponentProgress(id, 0)
    }

    fun uninstallComponent(id: String) {
        viewModelScope.launch {
            if (pluginManager.uninstallPlugin(id)) {
                settingsManager.removeInstalledPlugin(id)
                updateComponentStatus(id, ComponentStatus.AVAILABLE, null)
                updateComponentProgress(id, 0)
            }
        }
    }

    private fun updateComponentStatus(id: String, status: ComponentStatus, error: String?) {
        _officialComponents.update { list ->
            list.map { if (it.id == id) it.copy(status = status, errorMessage = error) else it }
        }
        _externalComponents.update { list ->
            list.map { if (it.id == id) it.copy(status = status, errorMessage = error) else it }
        }
    }

    private fun updateComponentProgress(id: String, progress: Int) {
        _officialComponents.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
        _externalComponents.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }
}
