package br.com.fpagency.fptube.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.fpagency.fptube.core.StorageUtils
import br.com.fpagency.fptube.data.DownloadEntity
import br.com.fpagency.fptube.data.DownloadRepository
import br.com.fpagency.fptube.data.DownloadStatus
import br.com.fpagency.fptube.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilterType {
    ALL, MUSIC, VIDEO, IMAGE
}

enum class StatusFilter {
    ALL, ACTIVE, COMPLETED, PAUSED, FAILED
}

enum class MediaTypeFilter {
    ALL, VIDEO, AUDIO, IMAGE
}

data class DownloadSummary(
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val completedCount: Int = 0,
    val pausedCount: Int = 0,
    val failedCount: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    application: Application,
    private val repository: DownloadRepository,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter.asStateFlow()

    private val _mediaTypeFilter = MutableStateFlow(MediaTypeFilter.ALL)
    val mediaTypeFilter: StateFlow<MediaTypeFilter> = _mediaTypeFilter.asStateFlow()

    private val _platformFilter = MutableStateFlow("ALL")
    val platformFilter: StateFlow<String> = _platformFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Backward compatibility for filterType
    val filterType: StateFlow<FilterType> = _mediaTypeFilter.map { media ->
        when (media) {
            MediaTypeFilter.ALL -> FilterType.ALL
            MediaTypeFilter.VIDEO -> FilterType.VIDEO
            MediaTypeFilter.AUDIO -> FilterType.MUSIC
            MediaTypeFilter.IMAGE -> FilterType.IMAGE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterType.ALL)

    val summary: StateFlow<DownloadSummary> = repository.allDownloads
        .map { list ->
            DownloadSummary(
                totalCount = list.size,
                activeCount = list.count { it.status == DownloadStatus.DOWNLOADING },
                queuedCount = list.count { it.status == DownloadStatus.QUEUED },
                completedCount = list.count { it.status == DownloadStatus.COMPLETED },
                pausedCount = list.count { it.status == DownloadStatus.PAUSED },
                failedCount = list.count { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadSummary())

    val availablePlatforms: StateFlow<List<String>> = repository.allDownloads
        .map { list ->
            list.mapNotNull { it.platform }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = combine(
        repository.allDownloads,
        _statusFilter,
        _mediaTypeFilter,
        _platformFilter,
        _searchQuery
    ) { downloads, status, media, platform, query ->
        downloads.filter { download ->
            val matchesStatus = when (status) {
                StatusFilter.ALL -> true
                StatusFilter.ACTIVE -> download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
                StatusFilter.COMPLETED -> download.status == DownloadStatus.COMPLETED
                StatusFilter.PAUSED -> download.status == DownloadStatus.PAUSED
                StatusFilter.FAILED -> download.status == DownloadStatus.FAILED || download.status == DownloadStatus.CANCELLED
            }

            val matchesMedia = when (media) {
                MediaTypeFilter.ALL -> true
                MediaTypeFilter.VIDEO -> download.downloadType == "video" || download.downloadType == null
                MediaTypeFilter.AUDIO -> download.downloadType == "audio"
                MediaTypeFilter.IMAGE -> download.downloadType == "image"
            }

            val matchesPlatform = if (platform == "ALL") {
                true
            } else {
                download.platform.equals(platform, ignoreCase = true)
            }

            val matchesQuery = query.isBlank() ||
                download.title.contains(query, ignoreCase = true) ||
                (download.channel?.contains(query, ignoreCase = true) == true) ||
                (download.platform?.contains(query, ignoreCase = true) == true) ||
                (download.filePath?.contains(query, ignoreCase = true) == true)

            matchesStatus && matchesMedia && matchesPlatform && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatusFilter(status: StatusFilter) {
        _statusFilter.value = status
    }

    fun setMediaTypeFilter(media: MediaTypeFilter) {
        _mediaTypeFilter.value = media
    }

    fun setPlatformFilter(platform: String) {
        _platformFilter.value = platform
    }

    fun setFilter(filter: FilterType) {
        _mediaTypeFilter.value = when (filter) {
            FilterType.ALL -> MediaTypeFilter.ALL
            FilterType.MUSIC -> MediaTypeFilter.AUDIO
            FilterType.VIDEO -> MediaTypeFilter.VIDEO
            FilterType.IMAGE -> MediaTypeFilter.IMAGE
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(id)) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    fun selectAll() {
        _selectedIds.value = downloads.value.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedIds.value = emptySet()
    }

    fun pauseSelected() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = _selectedIds.value
            val list = repository.allDownloads.first().filter {
                it.id in ids && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
            }
            val activeIds = list.map { it.id }.toSet()
            list.forEach { download ->
                downloadManager.cancelDownload(download.id)
            }
            repository.updateStatusBatch(activeIds, DownloadStatus.PAUSED)
        }
    }

    fun resumeSelected() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = _selectedIds.value
            val list = repository.allDownloads.first().filter {
                it.id in ids && (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED)
            }
            val resumableIds = list.map { it.id }.toSet()
            repository.updateStatusBatch(resumableIds, DownloadStatus.QUEUED)
            list.forEach { download ->
                downloadManager.enqueueDownload(download.id)
            }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val idsToDelete = _selectedIds.value
            val downloadsToDelete = repository.allDownloads.first().filter { it.id in idsToDelete }

            downloadsToDelete.forEach { download ->
                downloadManager.cancelDownload(download.id)
                StorageUtils.deleteFile(getApplication(), download.filePath)
            }
            repository.deleteByIds(idsToDelete)

            _selectedIds.value = emptySet()
            _isSelectionMode.value = false
        }
    }

    fun onAction(download: DownloadEntity, action: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            when (action) {
                "PAUSE" -> {
                    downloadManager.cancelDownload(download.id)
                    repository.updateStatus(download.id, DownloadStatus.PAUSED)
                }
                "CANCEL" -> {
                    downloadManager.cancelDownload(download.id)
                    repository.updateStatus(download.id, DownloadStatus.CANCELLED)
                }
                "RESUME", "RETRY" -> {
                    repository.updateStatus(download.id, DownloadStatus.QUEUED)
                    downloadManager.enqueueDownload(download.id)
                }
                "DELETE" -> {
                    downloadManager.cancelDownload(download.id)
                    StorageUtils.deleteFile(getApplication(), download.filePath)
                    repository.delete(download)
                }
            }
        }
    }

    fun updateTitle(download: DownloadEntity, newTitle: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (newTitle.isNotBlank()) {
                val newPath = StorageUtils.renameFile(getApplication(), download.filePath, newTitle)
                repository.update(download.copy(title = newTitle, filePath = newPath ?: download.filePath))
            }
        }
    }
}