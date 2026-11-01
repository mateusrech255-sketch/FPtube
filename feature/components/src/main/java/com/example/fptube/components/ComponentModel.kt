package br.com.fpagency.fptube.components

data class Component(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val status: ComponentStatus,
    val downloadUrl: String? = null,
    val progress: Int = 0,
    val isOfficial: Boolean = true,
    val errorMessage: String? = null,
    val repositorySource: String? = null
)

enum class ComponentStatus {
    AVAILABLE, DOWNLOADING, PAUSED, INSTALLED
}
