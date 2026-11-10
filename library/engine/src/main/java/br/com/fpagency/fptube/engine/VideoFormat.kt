package br.com.fpagency.fptube.engine

data class VideoFormat(
    val formatId: String? = null,
    val url: String? = null,
    val ext: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 30,
    val vcodec: String? = null,
    val acodec: String? = null,
    val format: String? = null,
    val formatNote: String? = null,
    val protocol: String? = null,
    val filesize: Long? = null,
    val tbr: Double? = null,
    val abr: Int? = null,
    val vbr: Double? = null
) {
    val fileSize: Long
        get() = filesize ?: 0L
}
