package br.com.fpagency.fptube.engine

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoInfo(
    @field:JsonProperty("id") val id: String? = null,
    @field:JsonProperty("title") val title: String? = null,
    @field:JsonProperty("uploader") val uploader: String? = null,
    @field:JsonProperty("uploader_id") val uploaderId: String? = null,
    @field:JsonProperty("thumbnail") val thumbnail: String? = null,
    @field:JsonProperty("duration") val duration: Int? = null,
    @field:JsonProperty("webpage_url") val webpageUrl: String? = null,
    @field:JsonProperty("extractor") val extractor: String? = null,
    @field:JsonProperty("formats") val formats: List<VideoFormat>? = null
)
