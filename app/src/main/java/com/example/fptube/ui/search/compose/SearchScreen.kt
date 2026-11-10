package br.com.fpagency.fptube.ui.search.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import br.com.fpagency.fptube.core.ui.R
import br.com.fpagency.fptube.core.ui.components.*
import br.com.fpagency.fptube.engine.YoutubeDLUtils as EngineUtils
import br.com.fpagency.fptube.engine.VideoFormat
import br.com.fpagency.fptube.engine.VideoInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    sharedUrl: String? = null,
    onUrlConsumed: () -> Unit = {}
) {
    var urlText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null && sharedUrl != urlText) {
            urlText = sharedUrl
            viewModel.startSearch(sharedUrl)
            onUrlConsumed()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is SearchUiState.FastMetadataReady || uiState is SearchUiState.DeepScanReady) {
            focusManager.clearFocus()
            showSheet = true
        } else if (uiState is SearchUiState.Error || uiState is SearchUiState.Idle) {
            showSheet = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern Header
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // URL Input with Actions
        FPTubeTextField(
            value = urlText,
            onValueChange = { 
                urlText = it
                viewModel.onUrlChanged(it)
            },
            placeholder = stringResource(R.string.url_hint),
            leadingIcon = {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                Row(modifier = Modifier.padding(end = 8.dp)) {
                    if (urlText.isNotEmpty()) {
                        IconButton(onClick = { urlText = ""; viewModel.onUrlChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { 
                        clipboardManager.getText()?.text?.let { 
                            urlText = it
                            viewModel.onUrlChanged(it)
                        }
                    }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        FPTubePrimaryButton(
            text = stringResource(R.string.download_button),
            onClick = {
                if (uiState is SearchUiState.FastMetadataReady || uiState is SearchUiState.DeepScanReady) {
                    showSheet = true
                } else {
                    viewModel.startSearch(urlText)
                }
            },
            enabled = urlText.isNotBlank() && uiState !is SearchUiState.Loading
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Status Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "status"
            ) { state ->
                when (state) {
                    is SearchUiState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is SearchUiState.Error -> {
                        FPTubeEmptyState(
                            icon = Icons.Default.ErrorOutline,
                            title = stringResource(R.string.ops),
                            description = state.message
                        )
                    }
                    else -> {
                        FPTubeEmptyState(
                            icon = Icons.Outlined.FileDownload,
                            title = stringResource(R.string.ready_to_download),
                            description = stringResource(R.string.paste_link_hint)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets(0) },
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) {
            DownloadBottomSheetContent(
                uiState = uiState,
                onDownloadClick = { format, type ->
                    val state = uiState
                    showSheet = false
                    urlText = ""
                    if (state is SearchUiState.FastMetadataReady) {
                        viewModel.download(
                            url = state.fastInfo.url,
                            title = state.fastInfo.title,
                            thumb = state.fastInfo.thumbnail,
                            channel = state.fastInfo.author,
                            format = format,
                            type = type
                        )
                    } else if (state is SearchUiState.DeepScanReady) {
                        viewModel.download(
                            url = state.fastInfo.url,
                            title = state.fullInfo.title ?: state.fastInfo.title,
                            thumb = state.fullInfo.thumbnail ?: state.fastInfo.thumbnail,
                            channel = state.fullInfo.uploader ?: state.fastInfo.author,
                            format = format,
                            type = type,
                            playerClient = state.winningClient
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun SkeletonFormatItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val skeletonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = skeletonAlpha))
    )
}

@Composable
fun DownloadBottomSheetContent(
    uiState: SearchUiState,
    onDownloadClick: (VideoFormat?, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("video") }
    var selectedFormat by remember { mutableStateOf<VideoFormat?>(null) }

    val fastInfo = when (uiState) {
        is SearchUiState.FastMetadataReady -> uiState.fastInfo
        is SearchUiState.DeepScanReady -> uiState.fastInfo
        else -> null
    }

    val fullInfo = if (uiState is SearchUiState.DeepScanReady) uiState.fullInfo else null
    val consolidatedFormats = if (uiState is SearchUiState.DeepScanReady) uiState.consolidatedFormats else null
    val filteredFormats = remember(consolidatedFormats, selectedType) {
        consolidatedFormats?.let { filterFormats(it, selectedType) }
    }

    LaunchedEffect(filteredFormats) {
        if (!filteredFormats.isNullOrEmpty()) {
            selectedFormat = filteredFormats[0]
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
        ) {
        
        // Premium Info Section
        fastInfo?.let { info ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.size(120.dp, 68.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = info.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
            space = 8.dp
        ) {
            SegmentedButton(
                selected = selectedType == "video",
                onClick = { selectedType = "video"; selectedFormat = null },
                shape = RoundedCornerShape(12.dp),
                icon = { Icon(Icons.Default.Movie, null) }
            ) {
                Text(stringResource(R.string.video_format))
            }
            SegmentedButton(
                selected = selectedType == "audio",
                onClick = { selectedType = "audio"; selectedFormat = null },
                shape = RoundedCornerShape(12.dp),
                icon = { Icon(Icons.Default.MusicNote, null) }
            ) {
                Text(stringResource(R.string.audio_format))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (selectedType == "audio") "Qualidades de Áudio Disponíveis" else stringResource(R.string.available_resolutions),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredFormats != null) {
            if (filteredFormats.isEmpty()) {
                Text(stringResource(R.string.no_formats_found), modifier = Modifier.padding(16.dp))
            } else {
                filteredFormats.forEach { format ->
                    FormatItem(
                        format = format,
                        isSelected = selectedFormat?.formatId == format.formatId,
                        onClick = { selectedFormat = format }
                    )
                }
            }
        } else if (uiState is SearchUiState.FastMetadataReady && uiState.isScanningFormats) {
            FPTubeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Buscando resoluções (1080p, 720p...)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aguarde enquanto extraímos todas as qualidades disponíveis...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (uiState is SearchUiState.FastMetadataReady) {
            // Caso de Fallback (Extração simples)
            FPTubeCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.single_quality), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.single_quality_desc), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Column {
                repeat(4) {
                    SkeletonFormatItem()
                }
            }
        }

        }

        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                val buttonText = when {
                    consolidatedFormats != null -> stringResource(R.string.download_now)
                    uiState is SearchUiState.FastMetadataReady -> stringResource(R.string.download_now)
                    else -> stringResource(R.string.fetching_resolutions)
                }

                FPTubePrimaryButton(
                    text = buttonText,
                    onClick = { onDownloadClick(selectedFormat, selectedType) },
                    enabled = (selectedFormat != null) || (fullInfo == null && uiState is SearchUiState.FastMetadataReady)
                )
            }
        }
    }
}

@Composable
fun FormatItem(format: VideoFormat, isSelected: Boolean, onClick: () -> Unit) {
    val (friendlyName, subtext) = getFriendlyInfo(format)
    
    FPTubeCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friendlyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f)
                )
                val ext = (format.ext ?: "BIN").uppercase()
                val fps = format.fps
                val fpsStr = if (fps > 30) " • $fps fps" else ""
                val sizeStr = if (format.fileSize > 0) " • ~${EngineUtils.formatSize(format.fileSize)}" else ""
                Text(
                    text = "$ext$fpsStr$sizeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private val HEIGHT_NOTE_REGEX = Regex("""(\d{3,4})p""")
private val DIMENSIONS_REGEX = Regex("""\d+x(\d{3,4})""")

private fun parseHeightFromFormat(format: VideoFormat): Int {
    if (format.width > 0 && format.height > 0) {
        return minOf(format.width, format.height)
    }
    if (format.height > 0) return format.height
    format.formatNote?.let { note ->
        val match = HEIGHT_NOTE_REGEX.find(note)
        if (match != null) return match.groupValues[1].toIntOrNull() ?: 0
    }
    format.format?.let { fmt ->
        val matchNote = HEIGHT_NOTE_REGEX.find(fmt)
        if (matchNote != null) return matchNote.groupValues[1].toIntOrNull() ?: 0
        val matchRes = DIMENSIONS_REGEX.find(fmt)
        if (matchRes != null) return matchRes.groupValues[1].toIntOrNull() ?: 0
    }
    return 0
}

private fun filterFormats(allFormats: List<VideoFormat>, type: String): List<VideoFormat> {
    return when (type) {
        "video" -> allFormats.filter { 
            parseHeightFromFormat(it) > 0 || 
            (it.vcodec != null && it.vcodec != "none") || 
            allFormats.size == 1
        }
        "audio" -> allFormats.filter { 
            (it.vcodec == "none") || 
            (parseHeightFromFormat(it) <= 0 && it.acodec != null && it.acodec != "none")
        }
        else -> allFormats
    }.sortedWith(compareByDescending<VideoFormat> { parseHeightFromFormat(it) }
        .thenByDescending { it.fps }
        .thenByDescending { it.tbr }
    )
}

@Composable
private fun getFriendlyInfo(format: VideoFormat): Pair<String, String> {
    val height = parseHeightFromFormat(format)
    val isVideo = height > 0 || (format.vcodec != null && format.vcodec != "none")
    
    return if (!isVideo) {
        val abr = format.abr ?: 0
        val bitrate = if (abr > 0) "${abr}K" else ""
        stringResource(R.string.audio_mp3_m4a, bitrate) to stringResource(R.string.compatible_most_devices)
    } else {
        when {
            height >= 2160 -> stringResource(R.string.quality_ultra_hd) to stringResource(R.string.quality_max_desc, height)
            height >= 1440 -> stringResource(R.string.quality_quad_hd) to stringResource(R.string.quality_superior_desc, height)
            height >= 1080 -> stringResource(R.string.quality_full_hd) to stringResource(R.string.quality_cinema_desc)
            height >= 720 -> stringResource(R.string.quality_high) to stringResource(R.string.quality_high_desc)
            height >= 480 -> stringResource(R.string.quality_medium) to stringResource(R.string.quality_medium_desc)
            height > 0 -> stringResource(R.string.quality_low, height) to stringResource(R.string.quality_low_desc)
            else -> stringResource(R.string.quality_default) to stringResource(R.string.quality_default_desc)
        }
    }
}
