package br.com.fpagency.fptube.ui.about.compose

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fpagency.fptube.R
import br.com.fpagency.fptube.core.ui.R as UiR
import br.com.fpagency.fptube.core.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onVersionClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onCopyrightClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLegalNoticeClick: () -> Unit
) {
    val context = LocalContext.current
    val versionInfo = remember { getVersionName(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(UiR.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo Section
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colorResource(id = R.color.logo_start),
                                colorResource(id = R.color.logo_end)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(id = UiR.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = versionInfo,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onVersionClick() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            FPTubeSectionHeader(stringResource(id = UiR.string.libraries_label))
            
            FPTubeCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = UiR.string.libraries_list),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 22.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            FPTubeSectionHeader(stringResource(UiR.string.advanced_support))

            FPTubeSettingItem(
                title = stringResource(UiR.string.privacy_policy),
                onClick = onPrivacyClick,
                icon = Icons.Default.Gavel
            )
            FPTubeSettingItem(
                title = stringResource(UiR.string.copyright_policy),
                onClick = onCopyrightClick,
                icon = Icons.Default.Copyright
            )
            FPTubeSettingItem(
                title = stringResource(UiR.string.terms_of_service),
                onClick = onTermsClick,
                icon = Icons.Default.Description
            )
            FPTubeSettingItem(
                title = stringResource(UiR.string.legal_notice),
                onClick = onLegalNoticeClick,
                icon = Icons.Default.Warning
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun getVersionName(context: Context): String {
    return try {
        val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        context.getString(UiR.string.version_label, pInfo.versionName, versionCode)
    } catch (e: Exception) {
        "N/A"
    }
}
