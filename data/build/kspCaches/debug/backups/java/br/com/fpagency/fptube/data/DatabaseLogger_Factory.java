package br.com.fpagency.fptube.data;

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
public final class DatabaseLogger_Factory implements Factory<DatabaseLogger> {
  private final Provider<AppDatabase> databaseProvider;

  private final Provider<SettingsManager> settingsManagerProvider;

  private DatabaseLogger_Factory(Provider<AppDatabase> databaseProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    this.databaseProvider = databaseProvider;
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public DatabaseLogger get() {
    return newInstance(databaseProvider.get(), settingsManagerProvider.get());
  }

  public static DatabaseLogger_Factory create(Provider<AppDatabase> databaseProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    return new DatabaseLogger_Factory(databaseProvider, settingsManagerProvider);
  }

  public static DatabaseLogger newInstance(AppDatabase database, SettingsManager settingsManager) {
    return new DatabaseLogger(database, settingsManager);
  }
}
