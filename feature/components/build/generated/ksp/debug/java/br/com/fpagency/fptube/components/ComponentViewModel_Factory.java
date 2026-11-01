package br.com.fpagency.fptube.components;

import android.app.Application;
import br.com.fpagency.fptube.data.SettingsManager;
import br.com.fpagency.fptube.engine.EngineManager;
import br.com.fpagency.fptube.engine.PluginManager;
import br.com.fpagency.fptube.engine.RepositoryManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ComponentViewModel_Factory implements Factory<ComponentViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<EngineManager> engineManagerProvider;

  private final Provider<PluginManager> pluginManagerProvider;

  private final Provider<SettingsManager> settingsManagerProvider;

  private final Provider<RepositoryManager> repositoryManagerProvider;

  private ComponentViewModel_Factory(Provider<Application> applicationProvider,
      Provider<EngineManager> engineManagerProvider, Provider<PluginManager> pluginManagerProvider,
      Provider<SettingsManager> settingsManagerProvider,
      Provider<RepositoryManager> repositoryManagerProvider) {
    this.applicationProvider = applicationProvider;
    this.engineManagerProvider = engineManagerProvider;
    this.pluginManagerProvider = pluginManagerProvider;
    this.settingsManagerProvider = settingsManagerProvider;
    this.repositoryManagerProvider = repositoryManagerProvider;
  }

  @Override
  public ComponentViewModel get() {
    return newInstance(applicationProvider.get(), engineManagerProvider.get(), pluginManagerProvider.get(), settingsManagerProvider.get(), repositoryManagerProvider.get());
  }

  public static ComponentViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<EngineManager> engineManagerProvider, Provider<PluginManager> pluginManagerProvider,
      Provider<SettingsManager> settingsManagerProvider,
      Provider<RepositoryManager> repositoryManagerProvider) {
    return new ComponentViewModel_Factory(applicationProvider, engineManagerProvider, pluginManagerProvider, settingsManagerProvider, repositoryManagerProvider);
  }

  public static ComponentViewModel newInstance(Application application, EngineManager engineManager,
      PluginManager pluginManager, SettingsManager settingsManager,
      RepositoryManager repositoryManager) {
    return new ComponentViewModel(application, engineManager, pluginManager, settingsManager, repositoryManager);
  }
}
