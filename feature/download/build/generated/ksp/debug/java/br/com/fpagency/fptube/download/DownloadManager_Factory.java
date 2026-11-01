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
public final class DownloadManager_Factory implements Factory<DownloadManager> {
  private final Provider<Context> contextProvider;

  private DownloadManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DownloadManager get() {
    return newInstance(contextProvider.get());
  }

  public static DownloadManager_Factory create(Provider<Context> contextProvider) {
    return new DownloadManager_Factory(contextProvider);
  }

  public static DownloadManager newInstance(Context context) {
    return new DownloadManager(context);
  }
}
