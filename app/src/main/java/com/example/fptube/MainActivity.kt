package br.com.fpagency.fptube

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import br.com.fpagency.fptube.core.ui.theme.FPtubeTheme
import br.com.fpagency.fptube.data.SettingsManager
import br.com.fpagency.fptube.ui.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.Intent
import br.com.fpagency.fptube.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager

    private val sharedUrlFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) android.util.Log.d("FPtube", "[MainActivity] ON_CREATE")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val storedLang = settingsManager.appLanguage.first()
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentTag = if (currentLocales.isEmpty) "System" else currentLocales.toLanguageTags()
            
            if (storedLang != "System" && storedLang.isNotEmpty() && currentTag != storedLang) {
                val appLocale = LocaleListCompat.forLanguageTags(storedLang)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
        
        handleIntent(intent)

        setContent {
            val themeMode by settingsManager.themeMode.collectAsState(initial = "Sistema")
            val sharedUrl by sharedUrlFlow.collectAsState()

            FPtubeTheme(themeMode = themeMode) {
                MainScreen(initialSharedUrl = sharedUrl) {
                    if (BuildConfig.DEBUG) android.util.Log.d("FPtube", "[MainActivity] URL_CONSUMED")
                    sharedUrlFlow.value = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (BuildConfig.DEBUG) android.util.Log.d("FPtube", "[MainActivity] ON_NEW_INTENT")
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val url = extractUrl(sharedText)
                if (BuildConfig.DEBUG) android.util.Log.d("FPtube", "[MainActivity] SHARED_URL_EXTRACTED: $url")
                if (url != sharedUrlFlow.value) {
                    sharedUrlFlow.value = url
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = "https?://[^\\s]+"
        val pattern = java.util.regex.Pattern.compile(urlRegex, java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else null
    }
}
