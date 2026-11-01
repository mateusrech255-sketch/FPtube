package br.com.fpagency.fptube.download;

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
public final class FileMover_Factory implements Factory<FileMover> {
  private final Provider<Context> contextProvider;

  private FileMover_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public FileMover get() {
    return newInstance(contextProvider.get());
  }

  public static FileMover_Factory create(Provider<Context> contextProvider) {
    return new FileMover_Factory(contextProvider);
  }

  public static FileMover newInstance(Context context) {
    return new FileMover(context);
  }
}
