package br.com.fpagency.fptube.ui.developer.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.fpagency.fptube.data.LogEntity
import br.com.fpagency.fptube.core.ui.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_mode)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    LogItem(log)
                    HorizontalDivider(modifier = Modifier.alpha(0.1f))
                }
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntity) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = when (log.level) {
        "ERROR" -> Color.Red
        "WARNING" -> Color(0xFFFFA500)
        else -> Color.Blue
    }

    Column(modifier = Modifier.padding(8.dp)) {
        Row {
            Text(text = dateFormat.format(Date(log.timestamp)), fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.level, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = log.tag, fontSize = 10.sp, color = Color.DarkGray)
        }
        Text(text = log.message, fontSize = 13.sp)
    }
}
