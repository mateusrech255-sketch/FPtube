package br.com.fpagency.fptube.ui.downloads

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import br.com.fpagency.fptube.core.FormatUtils
import br.com.fpagency.fptube.core.ShareUtils
import br.com.fpagency.fptube.core.ui.R
import br.com.fpagency.fptube.core.ui.components.*
import br.com.fpagency.fptube.data.DownloadEntity
import br.com.fpagency.fptube.data.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onPlayClick: (DownloadEntity) -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val mediaTypeFilter by viewModel.mediaTypeFilter.collectAsState()
    val platformFilter by viewModel.platformFilter.collectAsState()
    val availablePlatforms by viewModel.availablePlatforms.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var downloadToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var downloadToRename by remember { mutableStateOf<DownloadEntity?>(null) }
    var downloadForErrorDetails by remember { mutableStateOf<DownloadEntity?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Text(
                            text = stringResource(R.string.selection_mode_count, selectedIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    actions = {
                        if (selectedIds.size < downloads.size) {
                            IconButton(
                                onClick = { viewModel.selectAll() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = stringResource(R.string.select_all)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.deselectAll() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Deselect,
                                    contentDescription = stringResource(R.string.deselect_all)
                                )
                            }
                        }


                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_selected),
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                )
            } else if (isSearchActive) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_downloads)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                viewModel.setSearchQuery("")
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setSearchQuery("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.downloads),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (summary.totalCount > 0) {
                                Text(
                                    text = "${summary.totalCount} ${stringResource(R.string.downloads).lowercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = stringResource(R.string.selection_mode_count, 0)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Stats Summary Header Banner
            if (summary.totalCount > 0 && !isSearchActive && !isSelectionMode) {
                DownloadSummaryHeader(
                    summary = summary,
                    currentStatusFilter = statusFilter,
                    onStatusSelect = { viewModel.setStatusFilter(it) }
                )
            }

            // Filter Chips Bar
            if (summary.totalCount > 0 && !isSelectionMode) {
                FilterChipsBar(
                    statusFilter = statusFilter,
                    mediaTypeFilter = mediaTypeFilter,
                    platformFilter = platformFilter,
                    availablePlatforms = availablePlatforms,
                    onStatusSelected = { viewModel.setStatusFilter(it) },
                    onMediaSelected = { viewModel.setMediaTypeFilter(it) },
                    onPlatformSelected = { viewModel.setPlatformFilter(it) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (downloads.isEmpty()) {
                    FPTubeEmptyState(
                        icon = Icons.Outlined.DownloadForOffline,
                        title = stringResource(R.string.no_downloads),
                        description = if (searchQuery.isNotEmpty() || statusFilter != StatusFilter.ALL || mediaTypeFilter != MediaTypeFilter.ALL || platformFilter != "ALL")
                            stringResource(R.string.no_filter_results)
                        else stringResource(R.string.no_downloads_desc),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(downloads, key = { it.id }) { download ->
                            DownloadItem(
                                download = download,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(download.id),
                                onPlayClick = { onPlayClick(download) },
                                onDeleteClick = { downloadToDelete = download },
                                onRenameClick = { downloadToRename = download },
                                onErrorDetailsClick = { downloadForErrorDetails = download },
                                onShareClick = { ShareUtils.shareFile(context, download.filePath, download.downloadType) },
                                onCancelClick = { viewModel.onAction(download, "CANCEL") },
                                onPauseClick = { viewModel.onAction(download, "PAUSE") },
                                onResumeClick = { viewModel.onAction(download, "RESUME") },
                                onToggleSelection = { viewModel.toggleSelection(download.id) },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelectionMode()
                                        viewModel.toggleSelection(download.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Single Item Delete Confirmation Dialog
            if (downloadToDelete != null) {
                AlertDialog(
                    onDismissRequest = { downloadToDelete = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text(stringResource(R.string.delete_video_title)) },
                    text = { Text(stringResource(R.string.delete_video_confirmation, downloadToDelete?.title ?: "")) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                downloadToDelete?.let { viewModel.onAction(it, "DELETE") }
                                downloadToDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadToDelete = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Multiple Delete Confirmation Dialog
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text(stringResource(R.string.delete_selected)) },
                    text = { Text(stringResource(R.string.confirm_delete_multiple, selectedIds.size)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteSelected()
                                showDeleteConfirm = false
                            }
                        ) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Rename File Dialog
            if (downloadToRename != null) {
                var text by remember { mutableStateOf(downloadToRename?.title ?: "") }
                AlertDialog(
                    onDismissRequest = { downloadToRename = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text(stringResource(R.string.rename_file_title)) },
                    text = {
                        FPTubeTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = stringResource(R.string.new_name_label),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                downloadToRename?.let { viewModel.updateTitle(it, text) }
                                downloadToRename = null
                            }
                        ) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadToRename = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Technical Error Details Dialog
            if (downloadForErrorDetails != null) {
                val errorMsg = downloadForErrorDetails?.lastError ?: stringResource(R.string.unknown)
                val stackTrace = downloadForErrorDetails?.stackTrace ?: ""
                val fullLog = if (stackTrace.isNotBlank()) "$errorMsg\n\n$stackTrace" else errorMsg
                val copiedToastText = stringResource(R.string.copied_to_clipboard)

                AlertDialog(
                    onDismissRequest = { downloadForErrorDetails = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.error_details))
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (stackTrace.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stackTrace,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(fullLog))
                                Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.copy_log))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadForErrorDetails = null }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DownloadSummaryHeader(
    summary: DownloadSummary,
    currentStatusFilter: StatusFilter,
    onStatusSelect: (StatusFilter) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryChip(
                icon = Icons.Default.Bolt,
                count = summary.activeCount,
                label = stringResource(R.string.active_downloads_count, summary.activeCount),
                color = MaterialTheme.colorScheme.primary,
                isSelected = currentStatusFilter == StatusFilter.ACTIVE,
                onClick = {
                    onStatusSelect(if (currentStatusFilter == StatusFilter.ACTIVE) StatusFilter.ALL else StatusFilter.ACTIVE)
                }
            )

            SummaryChip(
                icon = Icons.Default.HourglassEmpty,
                count = summary.queuedCount,
                label = stringResource(R.string.queued_downloads_count, summary.queuedCount),
                color = MaterialTheme.colorScheme.tertiary,
                isSelected = false,
                onClick = {
                    onStatusSelect(if (currentStatusFilter == StatusFilter.ACTIVE) StatusFilter.ALL else StatusFilter.ACTIVE)
                }
            )

            SummaryChip(
                icon = Icons.Default.CheckCircle,
                count = summary.completedCount,
                label = stringResource(R.string.completed_downloads_count, summary.completedCount),
                color = MaterialTheme.colorScheme.secondary,
                isSelected = currentStatusFilter == StatusFilter.COMPLETED,
                onClick = {
                    onStatusSelect(if (currentStatusFilter == StatusFilter.COMPLETED) StatusFilter.ALL else StatusFilter.COMPLETED)
                }
            )

            if (summary.failedCount > 0) {
                SummaryChip(
                    icon = Icons.Default.Warning,
                    count = summary.failedCount,
                    label = "${summary.failedCount}",
                    color = MaterialTheme.colorScheme.error,
                    isSelected = currentStatusFilter == StatusFilter.FAILED,
                    onClick = {
                        onStatusSelect(if (currentStatusFilter == StatusFilter.FAILED) StatusFilter.ALL else StatusFilter.FAILED)
                    }
                )
            }
        }
    }
}

@Composable
fun SummaryChip(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (count > 0 || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (count > 0 || isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsBar(
    statusFilter: StatusFilter,
    mediaTypeFilter: MediaTypeFilter,
    platformFilter: String,
    availablePlatforms: List<String>,
    onStatusSelected: (StatusFilter) -> Unit,
    onMediaSelected: (MediaTypeFilter) -> Unit,
    onPlatformSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = statusFilter == StatusFilter.ALL && mediaTypeFilter == MediaTypeFilter.ALL && platformFilter == "ALL",
                onClick = {
                    onStatusSelected(StatusFilter.ALL)
                    onMediaSelected(MediaTypeFilter.ALL)
                    onPlatformSelected("ALL")
                },
                label = { Text(stringResource(R.string.filter_all)) }
            )
        }

        // Status filters
        item {
            FilterChip(
                selected = statusFilter == StatusFilter.ACTIVE,
                onClick = { onStatusSelected(if (statusFilter == StatusFilter.ACTIVE) StatusFilter.ALL else StatusFilter.ACTIVE) },
                label = { Text(stringResource(R.string.status_active)) }
            )
        }
        item {
            FilterChip(
                selected = statusFilter == StatusFilter.COMPLETED,
                onClick = { onStatusSelected(if (statusFilter == StatusFilter.COMPLETED) StatusFilter.ALL else StatusFilter.COMPLETED) },
                label = { Text(stringResource(R.string.status_completed_filter)) }
            )
        }
        item {
            FilterChip(
                selected = statusFilter == StatusFilter.PAUSED,
                onClick = { onStatusSelected(if (statusFilter == StatusFilter.PAUSED) StatusFilter.ALL else StatusFilter.PAUSED) },
                label = { Text(stringResource(R.string.status_paused_filter)) }
            )
        }
        item {
            FilterChip(
                selected = statusFilter == StatusFilter.FAILED,
                onClick = { onStatusSelected(if (statusFilter == StatusFilter.FAILED) StatusFilter.ALL else StatusFilter.FAILED) },
                label = { Text(stringResource(R.string.status_failed_filter)) }
            )
        }

        // Media type filters
        item {
            FilterChip(
                selected = mediaTypeFilter == MediaTypeFilter.VIDEO,
                onClick = { onMediaSelected(if (mediaTypeFilter == MediaTypeFilter.VIDEO) MediaTypeFilter.ALL else MediaTypeFilter.VIDEO) },
                label = { Text(stringResource(R.string.filter_video)) },
                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        item {
            FilterChip(
                selected = mediaTypeFilter == MediaTypeFilter.AUDIO,
                onClick = { onMediaSelected(if (mediaTypeFilter == MediaTypeFilter.AUDIO) MediaTypeFilter.ALL else MediaTypeFilter.AUDIO) },
                label = { Text(stringResource(R.string.filter_music)) },
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Platform filters
        availablePlatforms.forEach { platform ->
            item(key = "platform_$platform") {
                FilterChip(
                    selected = platformFilter.equals(platform, ignoreCase = true),
                    onClick = { onPlatformSelected(if (platformFilter.equals(platform, ignoreCase = true)) "ALL" else platform) },
                    label = { Text(platform) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItem(
    download: DownloadEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onErrorDetailsClick: () -> Unit,
    onShareClick: () -> Unit,
    onCancelClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit
) {
    FPTubeCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else if (download.status == DownloadStatus.COMPLETED) onPlayClick() else Unit },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .size(120.dp, 68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = download.thumbnail,
                            contentDescription = download.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Duration pill
                        if (!download.duration.isNullOrEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = download.duration ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Media Type pill
                        val isAudio = download.downloadType == "audio"
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(2.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = download.channel ?: "N/A",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!download.platform.isNullOrEmpty()) {
                                Text(
                                    text = " • ${download.platform}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // Real Metadata Badges (Quality, Container, FPS, Codec) memoized across progress ticks
                        val quality = remember(download.resolution, download.downloadType) {
                            FormatUtils.formatQualityDisplay(download.resolution, download.downloadType)
                        }
                        val container = remember(download.filePath, download.mimeType, download.downloadType) {
                            FormatUtils.formatContainerDisplay(download.filePath, download.mimeType, download.downloadType)
                        }
                        val fps = remember(download.fps) {
                            FormatUtils.formatFpsDisplay(download.fps)
                        }
                        val codec = remember(download.codec) {
                            FormatUtils.formatCodecDisplay(download.codec)
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (quality != null) {
                                MetaBadge(text = quality, isPrimary = true)
                            }
                            if (container != null) {
                                MetaBadge(text = container)
                            }
                            if (fps != null) {
                                MetaBadge(text = fps)
                            }
                            if (codec != null) {
                                MetaBadge(text = codec)
                            }
                        }

                        // Status Badge
                        val (statusText, statusColor, statusIcon) = getStatusDetails(download.status)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }

                // Progress Bar Section for DOWNLOADING, PAUSED, QUEUED, FAILED
                if (download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PAUSED ||
                    download.status == DownloadStatus.FAILED ||
                    download.status == DownloadStatus.QUEUED
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val progressFloat = when (download.status) {
                        DownloadStatus.FAILED -> 1f
                        DownloadStatus.QUEUED -> 0f
                        else -> download.progress / 100f
                    }
                    val (_, statusColor, _) = getStatusDetails(download.status)

                    if (download.status == DownloadStatus.QUEUED) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = statusColor,
                            trackColor = statusColor.copy(alpha = 0.15f)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = statusColor,
                            trackColor = statusColor.copy(alpha = 0.15f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sizeText = FormatUtils.formatSizeProgress(
                            download.downloadedSize,
                            download.totalSize,
                            download.progress,
                            download.filePath
                        )

                        val infoText = when (download.status) {
                            DownloadStatus.DOWNLOADING -> {
                                buildString {
                                    append("${download.progress}%")
                                    if (sizeText.isNotEmpty()) append(" · $sizeText")
                                    if (!download.speed.isNullOrBlank()) append(" · ${download.speed}")
                                }
                            }
                            DownloadStatus.PAUSED -> {
                                buildString {
                                    append("${download.progress}%")
                                    if (sizeText.isNotEmpty()) append(" · $sizeText")
                                    append(" · ${stringResource(R.string.status_paused)}")
                                }
                            }
                            DownloadStatus.FAILED -> stringResource(R.string.error_friendly_msg)
                            DownloadStatus.QUEUED -> stringResource(R.string.status_waiting)
                            else -> ""
                        }

                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (download.status == DownloadStatus.DOWNLOADING && !download.eta.isNullOrBlank()) {
                            Text(
                                text = "ETA: ${download.eta}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (download.status == DownloadStatus.FAILED) {
                            TextButton(
                                onClick = onErrorDetailsClick,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.view_details),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Action Buttons Row
                if (!isSelectionMode) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Secondary Icon Actions (Share, Edit, Delete)
                        Row(modifier = Modifier.weight(1f)) {
                            if (download.status == DownloadStatus.COMPLETED) {
                                ActionIconButton(
                                    icon = Icons.Default.Share,
                                    onClick = onShareClick,
                                    contentDescription = stringResource(R.string.share)
                                )
                                ActionIconButton(
                                    icon = Icons.Default.Edit,
                                    onClick = onRenameClick,
                                    contentDescription = stringResource(R.string.rename)
                                )
                            }
                            ActionIconButton(
                                icon = Icons.Default.Delete,
                                onClick = onDeleteClick,
                                tint = MaterialTheme.colorScheme.error,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }

                        // Primary State Action Buttons
                        AnimatedContent(targetState = download.status, label = "actions") { status ->
                            when (status) {
                                DownloadStatus.DOWNLOADING -> {
                                    Row {
                                        CompactButton(
                                            text = stringResource(R.string.pause),
                                            onClick = onPauseClick
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CompactButton(
                                            text = stringResource(R.string.cancel),
                                            onClick = onCancelClick,
                                            isSecondary = true
                                        )
                                    }
                                }
                                DownloadStatus.PAUSED -> {
                                    Row {
                                        CompactButton(
                                            text = stringResource(R.string.resume),
                                            onClick = onResumeClick
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CompactButton(
                                            text = stringResource(R.string.cancel),
                                            onClick = onCancelClick,
                                            isSecondary = true
                                        )
                                    }
                                }
                                DownloadStatus.COMPLETED -> {
                                    Button(
                                        onClick = onPlayClick,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .minimumInteractiveComponentSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayCircleFilled,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.watch),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                DownloadStatus.FAILED -> {
                                    Row {
                                        CompactButton(
                                            text = stringResource(R.string.retry),
                                            onClick = onResumeClick
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CompactButton(
                                            text = stringResource(R.string.view_details),
                                            onClick = onErrorDetailsClick,
                                            isSecondary = true
                                        )
                                    }
                                }
                                DownloadStatus.CANCELLED -> {
                                    CompactButton(
                                        text = stringResource(R.string.retry),
                                        onClick = onResumeClick
                                    )
                                }
                                DownloadStatus.QUEUED -> {
                                    CompactButton(
                                        text = stringResource(R.string.cancel),
                                        onClick = onCancelClick,
                                        isSecondary = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaBadge(
    text: String,
    isPrimary: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ActionIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    isSecondary: Boolean = false
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(34.dp)
            .minimumInteractiveComponentSize(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = if (isSecondary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun getStatusDetails(status: DownloadStatus): Triple<String, Color, ImageVector> {
    return when (status) {
        DownloadStatus.DOWNLOADING -> Triple(
            stringResource(R.string.status_downloading),
            MaterialTheme.colorScheme.primary,
            Icons.Default.Downloading
        )
        DownloadStatus.QUEUED -> Triple(
            stringResource(R.string.status_queued),
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.HourglassEmpty
        )
        DownloadStatus.PAUSED -> Triple(
            stringResource(R.string.status_paused),
            MaterialTheme.colorScheme.secondary,
            Icons.Default.PauseCircleFilled
        )
        DownloadStatus.COMPLETED -> Triple(
            stringResource(R.string.status_completed),
            Color(0xFF2E7D32), // Emerald Green
            Icons.Default.CheckCircle
        )
        DownloadStatus.FAILED -> Triple(
            stringResource(R.string.status_failed),
            MaterialTheme.colorScheme.error,
            Icons.Default.Error
        )
        DownloadStatus.CANCELLED -> Triple(
            stringResource(R.string.status_cancelled),
            MaterialTheme.colorScheme.outline,
            Icons.Default.Cancel
        )
    }
}