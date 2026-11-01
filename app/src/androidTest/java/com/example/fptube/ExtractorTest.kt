package com.example.fptube

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fpagency.fptube.engine.KwaiExtractor
import br.com.fpagency.fptube.engine.EngineOperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RunWith(AndroidJUnit4::class)
class ExtractorTest {
    @Test
    fun testSnackVideo() {
        runBlocking {
            System.out.println("TEST_EXTRACT: Starting SnackVideo test")
            // Fetch some HTML to see if SnackVideo uses same architecture
            val url = "https://s.snackvideo.com/p/B4bmdB0A" // An arbitrary string or shortlink. Wait, let's search for a real URL on github or something?
            val (finalUrl, html) = fetchHtmlAndFinalUrl("https://s.snackvideo.com/p/23mSgU7t") // just a guess, or I can search online. Let's just use yt-dlp's generic logic manually to see what happens.
            System.out.println("TEST_EXTRACT: Final URL: $finalUrl, HTML length: ${html.length}")
            val match = Regex(""""main_mv_urls"\s*:\s*\[\s*\{\s*"cdn"\s*:\s*"[^"]+"\s*,\s*"url"\s*:\s*"([^"]+)"""").find(html)
            if (match != null) {
                System.out.println("TEST_EXTRACT: Found main_mv_urls: ${match.groupValues[1]}")
            } else {
                System.out.println("TEST_EXTRACT: No main_mv_urls found")
            }
        }
    }

    private suspend fun fetchHtmlAndFinalUrl(initialUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl
        var redirects = 0
        var conn: HttpURLConnection? = null

        while (redirects < 5) {
            val urlObj = URL(currentUrl)
            conn = urlObj.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            
            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val location = conn.headerFields.entries
                    .find { it.key?.equals("Location", ignoreCase = true) == true }
                    ?.value?.firstOrNull()

                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) location else "https://${urlObj.host}$location"
                    redirects++
                    conn.disconnect()
                    continue
                }
            }
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                return@withContext Pair(currentUrl, html)
            }
            break
        }
        return@withContext Pair(currentUrl, "")
    }
}
