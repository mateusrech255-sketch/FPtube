package br.com.fpagency.fptube.core

import android.util.Log
import br.com.fpagency.fptube.core.BuildConfig

interface ILogger {
    fun i(message: String, tag: String = "INFO")
    fun w(message: String, tag: String = "WARNING", throwable: Throwable? = null)
    fun e(message: String, tag: String = "ERROR", throwable: Throwable? = null)
}

object AppLogger {
    private var implementation: ILogger? = null

    fun init(logger: ILogger) {
        implementation = logger
    }

    fun i(message: String, tag: String = "INFO") {
        try { Log.i(tag, message) } catch (e: Throwable) {}
        implementation?.i(message, tag)
    }

    fun w(message: String, tag: String = "WARNING", throwable: Throwable? = null) {
        try { Log.w(tag, "$message ${throwable?.message ?: ""}") } catch (e: Throwable) {}
        implementation?.w(message, tag, throwable)
    }

    fun e(message: String, tag: String = "ERROR", throwable: Throwable? = null) {
        if (throwable is kotlinx.coroutines.CancellationException) return
        try { Log.e(tag, message, throwable) } catch (e: Throwable) {}
        implementation?.e(message, tag, throwable)
    }
}
