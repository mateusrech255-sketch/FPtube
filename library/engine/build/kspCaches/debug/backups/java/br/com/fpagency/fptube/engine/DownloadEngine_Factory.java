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
public final class DownloadEngine_Factory implements Factory<DownloadEngine> {
  private final Provider<Context> contextProvider;

  private DownloadEngine_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DownloadEngine get() {
    return newInstance(contextProvider.get());
  }

  public static DownloadEngine_Factory create(Provider<Context> contextProvider) {
    return new DownloadEngine_Factory(contextProvider);
  }

  public static DownloadEngine newInstance(Context context) {
    return new DownloadEngine(context);
  }
}
