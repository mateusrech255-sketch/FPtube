package br.com.fpagency.fptube.diagnostic;

import android.content.Context;
import br.com.fpagency.fptube.data.DownloadDao;
import br.com.fpagency.fptube.data.LogDao;
import br.com.fpagency.fptube.data.SettingsManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DiagnosticViewModel_Factory implements Factory<DiagnosticViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<LogDao> logDaoProvider;

  private final Provider<DownloadDao> downloadDaoProvider;

  private final Provider<SettingsManager> settingsManagerProvider;

  private DiagnosticViewModel_Factory(Provider<Context> contextProvider,
      Provider<LogDao> logDaoProvider, Provider<DownloadDao> downloadDaoProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    this.contextProvider = contextProvider;
    this.logDaoProvider = logDaoProvider;
    this.downloadDaoProvider = downloadDaoProvider;
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public DiagnosticViewModel get() {
    return newInstance(contextProvider.get(), logDaoProvider.get(), downloadDaoProvider.get(), settingsManagerProvider.get());
  }

  public static DiagnosticViewModel_Factory create(Provider<Context> contextProvider,
      Provider<LogDao> logDaoProvider, Provider<DownloadDao> downloadDaoProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    return new DiagnosticViewModel_Factory(contextProvider, logDaoProvider, downloadDaoProvider, settingsManagerProvider);
  }

  public static DiagnosticViewModel newInstance(Context context, LogDao logDao,
      DownloadDao downloadDao, SettingsManager settingsManager) {
    return new DiagnosticViewModel(context, logDao, downloadDao, settingsManager);
  }
}
