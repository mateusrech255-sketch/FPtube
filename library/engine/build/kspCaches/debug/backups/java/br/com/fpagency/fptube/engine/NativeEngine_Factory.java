package br.com.fpagency.fptube.engine;

import br.com.fpagency.fptube.data.SettingsManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class NativeEngine_Factory implements Factory<NativeEngine> {
  private final Provider<SettingsManager> settingsManagerProvider;

  private NativeEngine_Factory(Provider<SettingsManager> settingsManagerProvider) {
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public NativeEngine get() {
    return newInstance(settingsManagerProvider.get());
  }

  public static NativeEngine_Factory create(Provider<SettingsManager> settingsManagerProvider) {
    return new NativeEngine_Factory(settingsManagerProvider);
  }

  public static NativeEngine newInstance(SettingsManager settingsManager) {
    return new NativeEngine(settingsManager);
  }
}
