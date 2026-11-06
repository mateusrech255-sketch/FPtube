package br.com.fpagency.fptube.engine

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoFormat(
    @field:JsonProperty("format_id") val formatId: String? = null,
    @field:JsonProperty("url") val url: String? = null,
    @field:JsonProperty("ext") val ext: String? = null,
    @field:JsonProperty("width") val width: Int = 0,
    @field:JsonProperty("height") val height: Int = 0,
    @field:JsonProperty("fps") val fps: Int = 30,
    @field:JsonProperty("vcodec") val vcodec: String? = null,
    @field:JsonProperty("acodec") val acodec: String? = null,
    @field:JsonProperty("format") val format: String? = null,
    @field:JsonProperty("format_note") val formatNote: String? = null,
    @field:JsonProperty("protocol") val protocol: String? = null,
    @field:JsonProperty("filesize") val filesize: Long? = null,
    @field:JsonProperty("tbr") val tbr: Double? = null,
    @field:JsonProperty("abr") val abr: Int? = null,
    @field:JsonProperty("vbr") val vbr: Double? = null
) {
    val fileSize: Long
        get() = filesize ?: 0L
}
