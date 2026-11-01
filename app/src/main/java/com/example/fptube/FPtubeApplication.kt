package br.com.fpagency.fptube

import android.app.Application
import androidx.work.Configuration
import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.data.DatabaseLogger
import br.com.fpagency.fptube.engine.EngineManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory
import br.com.fpagency.fptube.engine.Platform
import br.com.fpagency.fptube.engine.PlatformDetector
import br.com.fpagency.fptube.engine.YoutubeDLConfig

@HiltAndroidApp
class FPtubeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var dbLogger: DatabaseLogger

    @Inject
    lateinit var engineManager: EngineManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(dbLogger)
        
        applicationScope.launch(Dispatchers.IO) {
            br.com.fpagency.fptube.core.StorageAuditManager.performEmergencyCleanup(this@FPtubeApplication)
            // Pre-warm PlatformDetector & YoutubeDLConfig para eliminar latência do primeiro toque
            PlatformDetector.detect("https://www.youtube.com/watch?v=prewarm")
            YoutubeDLConfig.getUserAgent(Platform.YOUTUBE)
            engineManager.initialize()
        }
    }
}
