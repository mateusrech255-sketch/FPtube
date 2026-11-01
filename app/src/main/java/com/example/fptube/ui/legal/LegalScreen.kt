package br.com.fpagency.fptube.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import br.com.fpagency.fptube.core.ui.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    title: String,
    content: String,
    onBackClick: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            LegalTextParser(content)
        }
    }
}

@Composable
private fun LegalTextParser(content: String) {
    val lines = content.lines()
    lines.forEach { line ->
        when {
            line.startsWith("## ") -> {
                MarkdownText(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            line.startsWith("### ") -> {
                MarkdownText(
                    text = line.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            line.startsWith("# ") -> {
                MarkdownText(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            line.trim().startsWith("- ") -> {
                Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    MarkdownText(
                        text = line.trim().removePrefix("- "),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                    )
                }
            }
            line.isBlank() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
            else -> {
                MarkdownText(
                    text = line.trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = parseMarkdownInline(text, linkColor)

    ClickableText(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
        }
    )
}

private fun parseMarkdownInline(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val email = "suporte@fpagency.com.br"
        val boldParts = text.split("**")
        
        boldParts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendWithLinks(part, email, linkColor)
                }
            } else {
                appendWithLinks(part, email, linkColor)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendWithLinks(text: String, email: String, linkColor: Color) {
    val parts = text.split(email)
    parts.forEachIndexed { index, part ->
        append(part)
        if (index < parts.size - 1) {
            pushStringAnnotation(tag = "URL", annotation = "mailto:$email")
            withStyle(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(email)
            }
            pop()
        }
    }
}
