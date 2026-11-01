package br.com.fpagency.fptube.engine

sealed class EngineOperationResult<out T> {
    data class Success<out T>(val data: T, val extraData: Any? = null) : EngineOperationResult<T>()
    data class EngineNotReady(val cause: Throwable? = null) : EngineOperationResult<Nothing>()
    data class InitializationFailed(val cause: Throwable) : EngineOperationResult<Nothing>()
    data class NetworkError(val cause: Throwable) : EngineOperationResult<Nothing>()
    data class ExtractionError(val message: String, val output: String? = null) : EngineOperationResult<Nothing>()
    data class VideoUnavailable(val reason: String) : EngineOperationResult<Nothing>()
}
