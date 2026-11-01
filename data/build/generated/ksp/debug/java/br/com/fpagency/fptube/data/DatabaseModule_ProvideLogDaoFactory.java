package br.com.fpagency.fptube.data;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideLogDaoFactory implements Factory<LogDao> {
  private final Provider<AppDatabase> databaseProvider;

  private DatabaseModule_ProvideLogDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public LogDao get() {
    return provideLogDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideLogDaoFactory create(Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideLogDaoFactory(databaseProvider);
  }

  public static LogDao provideLogDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideLogDao(database));
  }
}
