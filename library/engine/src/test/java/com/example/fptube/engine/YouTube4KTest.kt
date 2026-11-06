package br.com.fpagency.fptube.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTube4KTest {

    private val testUrls = listOf(
        "https://www.youtube.com/watch?v=LXb3EKWsInQ", // Tears of Steel 4K
        "https://www.youtube.com/watch?v=aqz-KE-bpKQ", // Big Buck Bunny 4K
        "https://www.youtube.com/watch?v=jNQXAC9IVRw", // Me at the zoo
        "https://www.youtube.com/watch?v=9bZkp7q19f0", // Gangnam Style
        "https://www.youtube.com/watch?v=kJQP7kiw5Fk", // Despacito
        "https://www.youtube.com/watch?v=SqcY0GlETPk", // Sintel 4K
        "https://www.youtube.com/watch?v=1La4QzGeaaQ", // Peru 4K
        "https://www.youtube.com/watch?v=09R8_2nJtjg", // Japan 4K
        "https://www.youtube.com/watch?v=C0DPdy98e4c", // Costa Rica 4K
        "https://www.youtube.com/watch?v=Bey4XXJAqS8"  // Dubai 4K
    )

    @Test
    fun test10RandomYouTubeUrlsForHighResAnd4K() = runBlocking {
        var successCount = 0
        var totalFormatsFound = 0
        var maxResFound = 0

        for ((index, url) in testUrls.withIndex()) {
            println("Testando URL ${index + 1}: $url")
            val result = YoutubeDLUtils.fetchVideoFormats(url, false)
            if (result is EngineOperationResult.Success) {
                successCount++
                val info = result.data
                val formats = info.formats ?: emptyList()
                totalFormatsFound += formats.size
                val maxH = formats.maxOfOrNull { it.height } ?: 0
                if (maxH > maxResFound) {
                    maxResFound = maxH
                }
                println(" -> Sucesso! Título: '${info.title}' | Formatos: ${formats.size} | Maior Resolução: ${maxH}p")
                assertTrue("Vídeo ${index + 1} deve ter formatos", formats.isNotEmpty())
            } else {
                println(" -> Falha ao extrair formatos para $url")
            }
        }

        println("=== RESUMO DOS TESTES 4K/HD ===")
        println("URLs testadas com sucesso: $successCount / ${testUrls.size}")
        println("Total de formatos extraídos: $totalFormatsFound")
        println("Maior resolução encontrada: ${maxResFound}p")

        assertTrue("Pelo menos 8 de 10 URLs devem retornar com sucesso", successCount >= 8)
        assertTrue("A maior resolução encontrada deve ser >= 1080p (Full HD / 4K)", maxResFound >= 1080)
    }
}
