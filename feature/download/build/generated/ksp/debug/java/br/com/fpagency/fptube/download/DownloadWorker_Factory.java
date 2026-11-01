package br.com.fpagency.fptube.download;

import android.content.Context;
import androidx.work.WorkerParameters;
import br.com.fpagency.fptube.data.DownloadDao;
import br.com.fpagency.fptube.data.SettingsManager;
import br.com.fpagency.fptube.engine.DownloadEngine;
import br.com.fpagency.fptube.engine.EngineManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DownloadWorker_Factory {
  private final Provider<DownloadDao> downloadDaoProvider;

  private final Provider<SettingsManager> settingsManagerProvider;

  private final Provider<DownloadEngine> engineProvider;

  private final Provider<FileMover> fileMoverProvider;

  private final Provider<EngineManager> engineManagerProvider;

  private DownloadWorker_Factory(Provider<DownloadDao> downloadDaoProvider,
      Provider<SettingsManager> settingsManagerProvider, Provider<DownloadEngine> engineProvider,
      Provider<FileMover> fileMoverProvider, Provider<EngineManager> engineManagerProvider) {
    this.downloadDaoProvider = downloadDaoProvider;
    this.settingsManagerProvider = settingsManagerProvider;
    this.engineProvider = engineProvider;
    this.fileMoverProvider = fileMoverProvider;
    this.engineManagerProvider = engineManagerProvider;
  }

  public DownloadWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, downloadDaoProvider.get(), settingsManagerProvider.get(), engineProvider.get(), fileMoverProvider.get(), engineManagerProvider.get());
  }

  public static DownloadWorker_Factory create(Provider<DownloadDao> downloadDaoProvider,
      Provider<SettingsManager> settingsManagerProvider, Provider<DownloadEngine> engineProvider,
      Provider<FileMover> fileMoverProvider, Provider<EngineManager> engineManagerProvider) {
    return new DownloadWorker_Factory(downloadDaoProvider, settingsManagerProvider, engineProvider, fileMoverProvider, engineManagerProvider);
  }

  public static DownloadWorker newInstance(Context context, WorkerParameters workerParams,
      DownloadDao downloadDao, SettingsManager settingsManager, DownloadEngine engine,
      FileMover fileMover, EngineManager engineManager) {
    return new DownloadWorker(context, workerParams, downloadDao, settingsManager, engine, fileMover, engineManager);
  }
}
