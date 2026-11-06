package br.com.fpagency.fptube.engine

data class FastVideoInfo(
    val title: String,
    val author: String,
    val thumbnail: String,
    val url: String,
    val durationSeconds: Int = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val videoId: String? = null
)
