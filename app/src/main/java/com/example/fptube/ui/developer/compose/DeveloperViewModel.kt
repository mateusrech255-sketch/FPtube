package br.com.fpagency.fptube.ui.developer.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.data.DownloadDao
import br.com.fpagency.fptube.data.LogDao
import br.com.fpagency.fptube.data.LogEntity
import br.com.fpagency.fptube.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val logDao: LogDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val logs: StateFlow<List<LogEntity>> = logDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Adicionar outras estatísticas conforme necessário
}
