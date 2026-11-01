package br.com.fpagency.fptube.engine;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RepositoryManager_Factory implements Factory<RepositoryManager> {
  @Override
  public RepositoryManager get() {
    return newInstance();
  }

  public static RepositoryManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RepositoryManager newInstance() {
    return new RepositoryManager();
  }

  private static final class InstanceHolder {
    static final RepositoryManager_Factory INSTANCE = new RepositoryManager_Factory();
  }
}
