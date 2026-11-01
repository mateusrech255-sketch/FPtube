package br.com.fpagency.fptube.data;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideSettingsManagerFactory implements Factory<SettingsManager> {
  private final Provider<Context> contextProvider;

  private DatabaseModule_ProvideSettingsManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsManager get() {
    return provideSettingsManager(contextProvider.get());
  }

  public static DatabaseModule_ProvideSettingsManagerFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideSettingsManagerFactory(contextProvider);
  }

  public static SettingsManager provideSettingsManager(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSettingsManager(context));
  }
}
