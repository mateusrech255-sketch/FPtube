package br.com.fpagency.fptube.engine

data class VideoInfo(
    val id: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val uploaderId: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val webpageUrl: String? = null,
    val extractor: String? = null,
    val formats: List<VideoFormat>? = null
)
