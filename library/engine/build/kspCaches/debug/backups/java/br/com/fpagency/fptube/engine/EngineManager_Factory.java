package br.com.fpagency.fptube.engine;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class EngineManager_Factory implements Factory<EngineManager> {
  private final Provider<Context> contextProvider;

  private final Provider<PluginManager> pluginManagerProvider;

  private final Provider<NativeEngine> nativeEngineProvider;

  private EngineManager_Factory(Provider<Context> contextProvider,
      Provider<PluginManager> pluginManagerProvider, Provider<NativeEngine> nativeEngineProvider) {
    this.contextProvider = contextProvider;
    this.pluginManagerProvider = pluginManagerProvider;
    this.nativeEngineProvider = nativeEngineProvider;
  }

  @Override
  public EngineManager get() {
    return newInstance(contextProvider.get(), pluginManagerProvider.get(), nativeEngineProvider.get());
  }

  public static EngineManager_Factory create(Provider<Context> contextProvider,
      Provider<PluginManager> pluginManagerProvider, Provider<NativeEngine> nativeEngineProvider) {
    return new EngineManager_Factory(contextProvider, pluginManagerProvider, nativeEngineProvider);
  }

  public static EngineManager newInstance(Context context, PluginManager pluginManager,
      NativeEngine nativeEngine) {
    return new EngineManager(context, pluginManager, nativeEngine);
  }
}
