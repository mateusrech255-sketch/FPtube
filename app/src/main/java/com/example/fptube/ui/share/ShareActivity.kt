package br.com.fpagency.fptube.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import br.com.fpagency.fptube.core.ui.theme.FPtubeTheme

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUrl = extractUrlFromIntent(intent)

        setContent {
            FPtubeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    ShareScreen(
                        sharedUrl = sharedUrl,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val urlRegex = "https?://[^\\s]+"
                val pattern = java.util.regex.Pattern.compile(urlRegex, java.util.regex.Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(sharedText)
                if (matcher.find()) {
                    return matcher.group()
                }
            }
        }
        return null
    }
}
