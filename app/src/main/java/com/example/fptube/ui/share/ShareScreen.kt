package br.com.fpagency.fptube.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.fpagency.fptube.ui.search.compose.SearchViewModel
import br.com.fpagency.fptube.ui.search.compose.SearchUiState
import br.com.fpagency.fptube.ui.search.compose.DownloadBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    sharedUrl: String?,
    onDismiss: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    val qualityMode by viewModel.qualityMode.collectAsState(initial = "Perguntar sempre")
    val defaultDownloadType by viewModel.defaultDownloadType.collectAsState(initial = "Vídeo")

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.startSearch(sharedUrl)
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(uiState, qualityMode) {
        val state = uiState
        if (state is SearchUiState.FastMetadataReady || state is SearchUiState.DeepScanReady) {
            if (qualityMode == "Perguntar sempre") {
                showSheet = true
            } else {
                showSheet = false
                val type = if (defaultDownloadType.equals("Áudio", ignoreCase = true)) "audio" else "video"
                val url: String
                val title: String
                val thumb: String
                val channel: String
                
                when (state) {
                    is SearchUiState.FastMetadataReady -> {
                        url = state.fastInfo.url
                        title = state.fastInfo.title
                        thumb = state.fastInfo.thumbnail
                        channel = state.fastInfo.author
                    }
                    is SearchUiState.DeepScanReady -> {
                        url = state.fastInfo.url
                        title = state.fullInfo.title ?: state.fastInfo.title
                        thumb = state.fullInfo.thumbnail ?: state.fastInfo.thumbnail
                        channel = state.fullInfo.uploader ?: state.fastInfo.author
                    }
                    else -> return@LaunchedEffect
                }

                viewModel.download(
                    url = url,
                    title = title,
                    thumb = thumb,
                    channel = channel,
                    format = null,
                    type = type,
                    playerClient = (state as? SearchUiState.DeepScanReady)?.winningClient
                )
                showSuccessDialog = true
            }
        } else if (uiState is SearchUiState.Error) {
            showSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Loading state
        if (uiState is SearchUiState.Loading) {
            val msg = (uiState as SearchUiState.Loading).message
            Card(
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(msg)
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets(0) },
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) {
            if (uiState is SearchUiState.Error) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Erro",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (uiState as SearchUiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onDismiss() }) {
                        Text("Fechar")
                    }
                }
            } else {
                DownloadBottomSheetContent(
                    uiState = uiState,
                    onDownloadClick = { format, type ->
                        val state = uiState
                        showSheet = false
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
                        showSuccessDialog = true
                    }
                )
            }
        }
    }

    if (showSuccessDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(
                    text = "Download iniciado!",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Seu vídeo está sendo baixado em segundo plano.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    val intent = android.content.Intent(context, br.com.fpagency.fptube.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("INITIAL_ROUTE", "downloads")
                    }
                    context.startActivity(intent)
                    onDismiss()
                }) {
                    Text("Ver")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text("Agora não")
                }
            }
        )
    }
}
